package com.miniredis.persistence;

import com.miniredis.config.ServerConfig;
import com.miniredis.protocol.ProtocolException;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.protocol.RespParser;
import com.miniredis.protocol.RespValue;
import com.miniredis.storage.StorageEngine;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-Only File (AOF) manager handling command logging, durability policies
 * (always, everysec, no), crash recovery, and safe rewrite/compaction.
 */
public class AofManager implements Closeable {

    private final ServerConfig config;
    private final Path aofPath;
    private final ReentrantLock lock = new ReentrantLock();

    private FileOutputStream fos;
    private FileChannel channel;
    private ScheduledExecutorService flusher;
    private volatile boolean open = false;

    public AofManager(ServerConfig config) {
        this.config = Objects.requireNonNull(config, "ServerConfig cannot be null");
        Path dir = config.getDataDir();
        this.aofPath = dir.resolve("appendonly.aof");
    }

    public synchronized void start() throws IOException {
        if (!config.isPersistenceEnabled()) {
            return;
        }

        Path parent = aofPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        this.fos = new FileOutputStream(aofPath.toFile(), true);
        this.channel = fos.getChannel();
        this.open = true;

        if ("everysec".equalsIgnoreCase(config.getAofSyncPolicy())) {
            this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "miniredis-aof-flusher");
                t.setDaemon(true);
                return t;
            });
            this.flusher.scheduleAtFixedRate(this::fsyncQuietly, 1, 1, TimeUnit.SECONDS);
        }
    }

    public void logCommand(RespValue command) {
        if (!open || !config.isPersistenceEnabled()) {
            return;
        }

        byte[] encoded = RespEncoder.encode(command);
        lock.lock();
        try {
            if (!open || channel == null) {
                return;
            }
            channel.write(ByteBuffer.wrap(encoded));
            if ("always".equalsIgnoreCase(config.getAofSyncPolicy())) {
                channel.force(true);
            }
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to write to AOF: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public int replay(StorageEngine engine) throws IOException {
        if (!Files.exists(aofPath)) {
            return 0;
        }

        long fileLength = Files.size(aofPath);
        if (fileLength == 0) {
            return 0;
        }

        byte[] allBytes = Files.readAllBytes(aofPath);
        RespParser parser = new RespParser(config.getMaxRequestSize(), RespParser.DEFAULT_MAX_ARRAY_LENGTH);
        parser.append(allBytes, 0, allBytes.length);

        int replayed = 0;
        try {
            RespValue val;
            while ((val = parser.parseNext()) != null) {
                if (val instanceof RespValue.RespArray array) {
                    replayCommand(engine, array);
                    replayed++;
                }
            }
        } catch (ProtocolException e) {
            // Truncated final record or partial crash record
            System.err.println("AOF replay encountered trailing or malformed data: " + e.getMessage() + ". Replayed " + replayed + " valid records.");
        }
        return replayed;
    }

    private void replayCommand(StorageEngine engine, RespValue.RespArray array) {
        List<RespValue> elements = array.elements();
        if (elements == null || elements.isEmpty()) {
            return;
        }
        List<String> tokens = new ArrayList<>(elements.size());
        for (RespValue elem : elements) {
            if (elem instanceof RespValue.RespBulkString bs) {
                tokens.add(bs.asUtf8String());
            } else if (elem instanceof RespValue.RespSimpleString ss) {
                tokens.add(ss.value());
            }
        }
        if (tokens.isEmpty()) {
            return;
        }

        String cmd = tokens.get(0).toUpperCase(Locale.ROOT);
        List<String> args = tokens.subList(1, tokens.size());

        try {
            switch (cmd) {
                case "SET" -> {
                    if (args.size() >= 2) engine.set(args.get(0), args.get(1));
                }
                case "SETEX" -> {
                    if (args.size() >= 3) engine.setex(args.get(0), Long.parseLong(args.get(1)), args.get(2));
                }
                case "PSETEX" -> {
                    if (args.size() >= 3) engine.psetex(args.get(0), Long.parseLong(args.get(1)), args.get(2));
                }
                case "DEL" -> engine.del(args);
                case "INCR" -> { if (!args.isEmpty()) engine.incr(args.get(0)); }
                case "INCRBY" -> { if (args.size() >= 2) engine.incrBy(args.get(0), Long.parseLong(args.get(1))); }
                case "DECR" -> { if (!args.isEmpty()) engine.decr(args.get(0)); }
                case "DECRBY" -> { if (args.size() >= 2) engine.decrBy(args.get(0), Long.parseLong(args.get(1))); }
                case "APPEND" -> { if (args.size() >= 2) engine.append(args.get(0), args.get(1)); }
                case "EXPIRE" -> { if (args.size() >= 2) engine.expire(args.get(0), Long.parseLong(args.get(1))); }
                case "PEXPIRE" -> { if (args.size() >= 2) engine.pexpire(args.get(0), Long.parseLong(args.get(1))); }
                case "EXPIREAT" -> { if (args.size() >= 2) engine.expireAt(args.get(0), Long.parseLong(args.get(1))); }
                case "PEXPIREAT" -> { if (args.size() >= 2) engine.pexpireAt(args.get(0), Long.parseLong(args.get(1))); }
                case "PERSIST" -> { if (!args.isEmpty()) engine.persist(args.get(0)); }
                case "FLUSHDB" -> engine.flushDb();
                case "LPUSH" -> { if (args.size() >= 2) engine.lpush(args.get(0), args.subList(1, args.size())); }
                case "RPUSH" -> { if (args.size() >= 2) engine.rpush(args.get(0), args.subList(1, args.size())); }
                case "LPOP" -> { if (!args.isEmpty()) engine.lpop(args.get(0)); }
                case "RPOP" -> { if (!args.isEmpty()) engine.rpop(args.get(0)); }
                case "LSET" -> { if (args.size() >= 3) engine.lset(args.get(0), Long.parseLong(args.get(1)), args.get(2)); }
                case "LTRIM" -> { if (args.size() >= 3) engine.ltrim(args.get(0), Long.parseLong(args.get(1)), Long.parseLong(args.get(2))); }
                case "SADD" -> { if (args.size() >= 2) engine.sadd(args.get(0), args.subList(1, args.size())); }
                case "SREM" -> { if (args.size() >= 2) engine.srem(args.get(0), args.subList(1, args.size())); }
                case "HSET" -> {
                    if (args.size() >= 3) {
                        Map<String, String> fvs = new LinkedHashMap<>();
                        for (int i = 1; i < args.size(); i += 2) {
                            if (i + 1 < args.size()) fvs.put(args.get(i), args.get(i + 1));
                        }
                        engine.hset(args.get(0), fvs);
                    }
                }
                case "HDEL" -> { if (args.size() >= 2) engine.hdel(args.get(0), args.subList(1, args.size())); }
                case "HINCRBY" -> { if (args.size() >= 3) engine.hincrby(args.get(0), args.get(1), Long.parseLong(args.get(2))); }
            }
        } catch (Exception e) {
            System.err.println("Warning: failed to replay command " + cmd + ": " + e.getMessage());
        }
    }

    public synchronized void rewrite(StorageEngine engine) throws IOException {
        if (!config.isPersistenceEnabled()) {
            return;
        }

        Path tempPath = aofPath.resolveSibling("appendonly.aof.tmp");
        try (FileOutputStream tempFos = new FileOutputStream(tempPath.toFile());
             FileChannel tempChan = tempFos.getChannel()) {

            for (String key : engine.keys("*")) {
                long pttl = engine.pttl(key);
                if (pttl == -2) {
                    continue; // Key expired or nonexistent
                }

                switch (engine.type(key)) {
                    case STRING -> {
                        String val = engine.get(key);
                        if (val != null) {
                            writeCommand(tempChan, "SET", key, val);
                        }
                    }
                    case LIST -> {
                        List<String> list = engine.lrange(key, 0, -1);
                        if (!list.isEmpty()) {
                            List<String> cmd = new ArrayList<>();
                            cmd.add("RPUSH");
                            cmd.add(key);
                            cmd.addAll(list);
                            writeCommandList(tempChan, cmd);
                        }
                    }
                    case SET -> {
                        Set<String> set = engine.smembers(key);
                        if (!set.isEmpty()) {
                            List<String> cmd = new ArrayList<>();
                            cmd.add("SADD");
                            cmd.add(key);
                            cmd.addAll(set);
                            writeCommandList(tempChan, cmd);
                        }
                    }
                    case HASH -> {
                        Map<String, String> hash = engine.hgetall(key);
                        if (!hash.isEmpty()) {
                            List<String> cmd = new ArrayList<>();
                            cmd.add("HSET");
                            cmd.add(key);
                            for (Map.Entry<String, String> e : hash.entrySet()) {
                                cmd.add(e.getKey());
                                cmd.add(e.getValue());
                            }
                            writeCommandList(tempChan, cmd);
                        }
                    }
                    case ZSET, NONE -> {
                        // ZSET not yet supported; NONE represents nonexistent/expired key
                    }
                }

                if (pttl > 0) {
                    long expireAt = engine.getClock().millis() + pttl;
                    writeCommand(tempChan, "PEXPIREAT", key, Long.toString(expireAt));
                }
            }

            tempChan.force(true);
        }

        lock.lock();
        try {
            if (open && channel != null) {
                channel.close();
                fos.close();
            }
            Files.move(tempPath, aofPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            this.fos = new FileOutputStream(aofPath.toFile(), true);
            this.channel = fos.getChannel();
        } finally {
            lock.unlock();
        }
    }

    private void writeCommand(FileChannel chan, String... parts) throws IOException {
        List<RespValue> items = new ArrayList<>(parts.length);
        for (String p : parts) {
            items.add(RespValue.bulkString(p));
        }
        chan.write(ByteBuffer.wrap(RespEncoder.encode(RespValue.array(items))));
    }

    private void writeCommandList(FileChannel chan, List<String> parts) throws IOException {
        List<RespValue> items = new ArrayList<>(parts.size());
        for (String p : parts) {
            items.add(RespValue.bulkString(p));
        }
        chan.write(ByteBuffer.wrap(RespEncoder.encode(RespValue.array(items))));
    }

    private void fsyncQuietly() {
        lock.lock();
        try {
            if (open && channel != null) {
                channel.force(true);
            }
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!open) {
            return;
        }
        open = false;

        if (flusher != null) {
            flusher.shutdown();
            try {
                flusher.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lock.lock();
        try {
            if (channel != null && channel.isOpen()) {
                channel.force(true);
                channel.close();
            }
            if (fos != null) {
                fos.close();
            }
        } finally {
            lock.unlock();
        }
    }

    public Path getAofPath() {
        return aofPath;
    }

    public boolean isOpen() {
        return open;
    }
}
