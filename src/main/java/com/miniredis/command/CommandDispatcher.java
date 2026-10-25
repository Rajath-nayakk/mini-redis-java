package com.miniredis.command;

import com.miniredis.persistence.AofManager;
import com.miniredis.protocol.RespValue;
import com.miniredis.storage.*;

import java.security.MessageDigest;
import java.util.*;

/**
 * Dispatches incoming parsed RESP command arrays to the storage engine,
 * handles authentication, transaction queuing, AOF logging, and maps
 * domain exceptions to standard RESP2 replies.
 */
public class CommandDispatcher {

    private final StorageEngine engine;
    private volatile AofManager aofManager;
    private volatile String requirePass;

    public CommandDispatcher(StorageEngine engine) {
        this(engine, null, null);
    }

    public CommandDispatcher(StorageEngine engine, AofManager aofManager) {
        this(engine, aofManager, null);
    }

    public CommandDispatcher(StorageEngine engine, AofManager aofManager, String requirePass) {
        this.engine = Objects.requireNonNull(engine, "StorageEngine cannot be null");
        this.aofManager = aofManager;
        this.requirePass = requirePass;
    }

    public void setAofManager(AofManager aofManager) {
        this.aofManager = aofManager;
    }

    public void setRequirePass(String requirePass) {
        this.requirePass = requirePass;
    }

    public StorageEngine getEngine() {
        return engine;
    }

    public RespValue dispatch(RespValue request) {
        return dispatch(request, null, true);
    }

    public RespValue dispatch(RespValue request, TransactionContext tx, boolean authenticated) {
        if (!(request instanceof RespValue.RespArray array) || array.isNull()) {
            return RespValue.error("ERR Protocol error: expected array of bulk strings");
        }
        List<RespValue> elements = array.elements();
        if (elements == null || elements.isEmpty()) {
            return RespValue.error("ERR Protocol error: empty command");
        }

        List<String> tokens = new ArrayList<>(elements.size());
        for (RespValue elem : elements) {
            if (elem instanceof RespValue.RespBulkString bulkStr) {
                tokens.add(bulkStr.asUtf8String());
            } else if (elem instanceof RespValue.RespSimpleString ss) {
                tokens.add(ss.value());
            } else {
                return RespValue.error("ERR Protocol error: command elements must be strings");
            }
        }

        String commandName = tokens.get(0);
        if (commandName == null || commandName.isEmpty()) {
            return RespValue.error("ERR Protocol error: empty command name");
        }

        List<String> args = tokens.subList(1, tokens.size());
        String upperCommand = commandName.toUpperCase(Locale.ROOT);

        // Security / Authentication Check
        if (requirePass != null && !requirePass.isEmpty() && !authenticated) {
            if ("AUTH".equals(upperCommand)) {
                return handleAuth(args);
            }
            if ("QUIT".equals(upperCommand)) {
                return RespValue.ok();
            }
            return RespValue.error("NOAUTH Authentication required.");
        }

        if ("AUTH".equals(upperCommand)) {
            return handleAuth(args);
        }

        // Transaction handling
        if (tx != null) {
            if ("MULTI".equals(upperCommand)) {
                if (tx.isInTransaction()) {
                    return RespValue.error("ERR MULTI calls can not be nested");
                }
                tx.startTransaction();
                return RespValue.ok();
            }
            if ("DISCARD".equals(upperCommand)) {
                if (!tx.isInTransaction()) {
                    return RespValue.error("ERR DISCARD without MULTI");
                }
                tx.discard();
                return RespValue.ok();
            }
            if ("EXEC".equals(upperCommand)) {
                if (!tx.isInTransaction()) {
                    return RespValue.error("ERR EXEC without MULTI");
                }
                List<RespValue> queued = new ArrayList<>(tx.getQueuedCommands());
                tx.reset();
                List<RespValue> results = new ArrayList<>(queued.size());
                synchronized (engine) {
                    for (RespValue queuedReq : queued) {
                        results.add(executeCommand(queuedReq));
                    }
                }
                return RespValue.array(results);
            }
            if (tx.isInTransaction()) {
                try {
                    tx.queue(request);
                    return RespValue.simpleString("QUEUED");
                } catch (IllegalStateException e) {
                    return RespValue.error(e.getMessage());
                }
            }
        }

        synchronized (engine) {
            return executeCommand(request, upperCommand, commandName, args);
        }
    }

    private RespValue executeCommand(RespValue request) {
        if (!(request instanceof RespValue.RespArray array)) {
            return RespValue.error("ERR invalid transaction command");
        }
        List<RespValue> elements = array.elements();
        List<String> tokens = new ArrayList<>(elements.size());
        for (RespValue elem : elements) {
            if (elem instanceof RespValue.RespBulkString bs) {
                tokens.add(bs.asUtf8String());
            } else if (elem instanceof RespValue.RespSimpleString ss) {
                tokens.add(ss.value());
            }
        }
        String cmd = tokens.get(0);
        return executeCommand(request, cmd.toUpperCase(Locale.ROOT), cmd, tokens.subList(1, tokens.size()));
    }

    private RespValue executeCommand(RespValue request, String upperCommand, String originalCommandName, List<String> args) {
        try {
            RespValue response = switch (upperCommand) {
                // Connection & Info
                case "PING" -> handlePing(args);
                case "ECHO" -> handleEcho(args);
                case "INFO" -> handleInfo(args);

                // Strings
                case "SET" -> handleSet(args);
                case "GET" -> handleGet(args);
                case "GETSET" -> handleGetSet(args);
                case "APPEND" -> handleAppend(args);
                case "STRLEN" -> handleStrlen(args);
                case "INCR" -> handleIncr(args);
                case "INCRBY" -> handleIncrBy(args);
                case "DECR" -> handleDecr(args);
                case "DECRBY" -> handleDecrBy(args);
                case "MGET" -> handleMget(args);
                case "MSET" -> handleMset(args);

                // Keys & DB
                case "DEL" -> handleDel(args);
                case "EXISTS" -> handleExists(args);
                case "DBSIZE" -> handleDbsize(args);
                case "FLUSHDB" -> handleFlushdb(args);
                case "TYPE" -> handleType(args);
                case "KEYS" -> handleKeys(args);

                // TTL & Expiration
                case "EXPIRE" -> handleExpire(args);
                case "PEXPIRE" -> handlePexpire(args);
                case "EXPIREAT" -> handleExpireAt(args);
                case "PEXPIREAT" -> handlePexpireAt(args);
                case "TTL" -> handleTtl(args);
                case "PTTL" -> handlePttl(args);
                case "PERSIST" -> handlePersist(args);
                case "SETEX" -> handleSetex(args);
                case "PSETEX" -> handlePsetex(args);

                // Lists
                case "LPUSH" -> handleLpush(args);
                case "RPUSH" -> handleRpush(args);
                case "LPOP" -> handleLpop(args);
                case "RPOP" -> handleRpop(args);
                case "LLEN" -> handleLlen(args);
                case "LRANGE" -> handleLrange(args);
                case "LINDEX" -> handleLindex(args);
                case "LSET" -> handleLset(args);
                case "LTRIM" -> handleLtrim(args);

                // Sets
                case "SADD" -> handleSadd(args);
                case "SREM" -> handleSrem(args);
                case "SISMEMBER" -> handleSismember(args);
                case "SMEMBERS" -> handleSmembers(args);
                case "SCARD" -> handleScard(args);
                case "SINTER" -> handleSinter(args);
                case "SUNION" -> handleSunion(args);
                case "SDIFF" -> handleSdiff(args);

                // Hashes
                case "HSET" -> handleHset(args);
                case "HGET" -> handleHget(args);
                case "HMGET" -> handleHmget(args);
                case "HDEL" -> handleHdel(args);
                case "HEXISTS" -> handleHexists(args);
                case "HGETALL" -> handleHgetall(args);
                case "HKEYS" -> handleHkeys(args);
                case "HVALS" -> handleHvals(args);
                case "HLEN" -> handleHlen(args);
                case "HINCRBY" -> handleHincrby(args);

                default -> RespValue.error("ERR unknown command '" + originalCommandName + "'");
            };

            // Log mutating operations to AOF if successful
            if (aofManager != null && isMutatingCommand(upperCommand) && !(response instanceof RespValue.RespError)) {
                aofManager.logCommand(request);
            }

            return response;

        } catch (WrongTypeException | InvalidIntegerException | NumericOverflowException e) {
            return RespValue.error(e.getMessage());
        } catch (StorageException e) {
            return RespValue.error("ERR " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return RespValue.error(e.getMessage().startsWith("ERR") ? e.getMessage() : "ERR " + e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            return RespValue.error(e.getMessage());
        } catch (Exception e) {
            return RespValue.error("ERR internal server error: " + e.getMessage());
        }
    }

    private boolean isMutatingCommand(String cmd) {
        return switch (cmd) {
            case "SET", "SETEX", "PSETEX", "GETSET", "APPEND", "INCR", "INCRBY", "DECR", "DECRBY",
                 "DEL", "MSET", "FLUSHDB", "EXPIRE", "PEXPIRE", "EXPIREAT", "PEXPIREAT", "PERSIST",
                 "LPUSH", "RPUSH", "LPOP", "RPOP", "LSET", "LTRIM",
                 "SADD", "SREM",
                 "HSET", "HDEL", "HINCRBY" -> true;
            default -> false;
        };
    }

    // ==========================================
    // Command Handlers
    // ==========================================

    private RespValue handleAuth(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'auth' command");
        }
        if (requirePass == null || requirePass.isEmpty()) {
            return RespValue.error("ERR Client sent AUTH, but no password is set");
        }
        byte[] expected = requirePass.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] provided = args.get(0).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(expected, provided)) {
            return RespValue.ok();
        }
        return RespValue.error("ERR invalid password");
    }

    private RespValue handlePing(List<String> args) {
        if (args.isEmpty()) {
            return RespValue.pong();
        }
        if (args.size() == 1) {
            return RespValue.bulkString(args.get(0));
        }
        return RespValue.error("ERR wrong number of arguments for 'ping' command");
    }

    private RespValue handleEcho(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'echo' command");
        }
        return RespValue.bulkString(args.get(0));
    }

    private RespValue handleInfo(List<String> args) {
        long dbsize = engine.dbsize();
        long mem = engine.approximateMemoryBytes();
        String info = "# Server\r\n" +
                "miniredis_version:1.0.0\r\n" +
                "# Memory\r\n" +
                "used_memory:" + mem + "\r\n" +
                "used_memory_human:" + (mem / 1024) + "K\r\n" +
                "# Keyspace\r\n" +
                "db0:keys=" + dbsize + ",expires=0\r\n";
        return RespValue.bulkString(info);
    }

    private RespValue handleSet(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'set' command");
        }
        engine.set(args.get(0), args.get(1));
        return RespValue.ok();
    }

    private RespValue handleGet(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'get' command");
        }
        String value = engine.get(args.get(0));
        return value == null ? RespValue.nullBulkString() : RespValue.bulkString(value);
    }

    private RespValue handleGetSet(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'getset' command");
        }
        String old = engine.getSet(args.get(0), args.get(1));
        return old == null ? RespValue.nullBulkString() : RespValue.bulkString(old);
    }

    private RespValue handleAppend(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'append' command");
        }
        return RespValue.integer(engine.append(args.get(0), args.get(1)));
    }

    private RespValue handleStrlen(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'strlen' command");
        }
        return RespValue.integer(engine.strlen(args.get(0)));
    }

    private RespValue handleIncr(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'incr' command");
        }
        return RespValue.integer(engine.incr(args.get(0)));
    }

    private RespValue handleIncrBy(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'incrby' command");
        }
        try {
            long delta = Long.parseLong(args.get(1));
            return RespValue.integer(engine.incrBy(args.get(0), delta));
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handleDecr(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'decr' command");
        }
        return RespValue.integer(engine.decr(args.get(0)));
    }

    private RespValue handleDecrBy(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'decrby' command");
        }
        try {
            long delta = Long.parseLong(args.get(1));
            return RespValue.integer(engine.decrBy(args.get(0), delta));
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handleMget(List<String> args) {
        if (args.isEmpty()) {
            return RespValue.error("ERR wrong number of arguments for 'mget' command");
        }
        List<String> values = engine.mget(args);
        List<RespValue> elements = new ArrayList<>(values.size());
        for (String v : values) {
            elements.add(v == null ? RespValue.nullBulkString() : RespValue.bulkString(v));
        }
        return RespValue.array(elements);
    }

    private RespValue handleMset(List<String> args) {
        if (args.isEmpty() || args.size() % 2 != 0) {
            return RespValue.error("ERR wrong number of arguments for 'mset' command");
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < args.size(); i += 2) {
            entries.put(args.get(i), args.get(i + 1));
        }
        engine.mset(entries);
        return RespValue.ok();
    }

    private RespValue handleDel(List<String> args) {
        if (args.isEmpty()) {
            return RespValue.error("ERR wrong number of arguments for 'del' command");
        }
        return RespValue.integer(engine.del(args));
    }

    private RespValue handleExists(List<String> args) {
        if (args.isEmpty()) {
            return RespValue.error("ERR wrong number of arguments for 'exists' command");
        }
        return RespValue.integer(engine.exists(args));
    }

    private RespValue handleDbsize(List<String> args) {
        if (!args.isEmpty()) {
            return RespValue.error("ERR wrong number of arguments for 'dbsize' command");
        }
        return RespValue.integer(engine.dbsize());
    }

    private RespValue handleFlushdb(List<String> args) {
        engine.flushDb();
        return RespValue.ok();
    }

    private RespValue handleType(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'type' command");
        }
        RedisType type = engine.type(args.get(0));
        return RespValue.simpleString(type.name().toLowerCase(Locale.ROOT));
    }

    private RespValue handleKeys(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'keys' command");
        }
        Set<String> matched = engine.keys(args.get(0));
        List<RespValue> elements = new ArrayList<>(matched.size());
        for (String k : matched) {
            elements.add(RespValue.bulkString(k));
        }
        return RespValue.array(elements);
    }

    // ==========================================
    // TTL Handlers
    // ==========================================

    private RespValue handleExpire(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'expire' command");
        }
        try {
            long sec = Long.parseLong(args.get(1));
            return RespValue.integer(engine.expire(args.get(0), sec) ? 1 : 0);
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handlePexpire(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'pexpire' command");
        }
        try {
            long ms = Long.parseLong(args.get(1));
            return RespValue.integer(engine.pexpire(args.get(0), ms) ? 1 : 0);
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handleExpireAt(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'expireat' command");
        }
        try {
            long sec = Long.parseLong(args.get(1));
            return RespValue.integer(engine.expireAt(args.get(0), sec) ? 1 : 0);
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handlePexpireAt(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'pexpireat' command");
        }
        try {
            long ms = Long.parseLong(args.get(1));
            return RespValue.integer(engine.pexpireAt(args.get(0), ms) ? 1 : 0);
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handleTtl(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'ttl' command");
        }
        return RespValue.integer(engine.ttl(args.get(0)));
    }

    private RespValue handlePttl(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'pttl' command");
        }
        return RespValue.integer(engine.pttl(args.get(0)));
    }

    private RespValue handlePersist(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'persist' command");
        }
        return RespValue.integer(engine.persist(args.get(0)) ? 1 : 0);
    }

    private RespValue handleSetex(List<String> args) {
        if (args.size() != 3) {
            return RespValue.error("ERR wrong number of arguments for 'setex' command");
        }
        try {
            long sec = Long.parseLong(args.get(1));
            engine.setex(args.get(0), sec, args.get(2));
            return RespValue.ok();
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handlePsetex(List<String> args) {
        if (args.size() != 3) {
            return RespValue.error("ERR wrong number of arguments for 'psetex' command");
        }
        try {
            long ms = Long.parseLong(args.get(1));
            engine.psetex(args.get(0), ms, args.get(2));
            return RespValue.ok();
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    // ==========================================
    // List Handlers
    // ==========================================

    private RespValue handleLpush(List<String> args) {
        if (args.size() < 2) {
            return RespValue.error("ERR wrong number of arguments for 'lpush' command");
        }
        return RespValue.integer(engine.lpush(args.get(0), args.subList(1, args.size())));
    }

    private RespValue handleRpush(List<String> args) {
        if (args.size() < 2) {
            return RespValue.error("ERR wrong number of arguments for 'rpush' command");
        }
        return RespValue.integer(engine.rpush(args.get(0), args.subList(1, args.size())));
    }

    private RespValue handleLpop(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'lpop' command");
        }
        String popped = engine.lpop(args.get(0));
        return popped == null ? RespValue.nullBulkString() : RespValue.bulkString(popped);
    }

    private RespValue handleRpop(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'rpop' command");
        }
        String popped = engine.rpop(args.get(0));
        return popped == null ? RespValue.nullBulkString() : RespValue.bulkString(popped);
    }

    private RespValue handleLlen(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'llen' command");
        }
        return RespValue.integer(engine.llen(args.get(0)));
    }

    private RespValue handleLrange(List<String> args) {
        if (args.size() != 3) {
            return RespValue.error("ERR wrong number of arguments for 'lrange' command");
        }
        try {
            long start = Long.parseLong(args.get(1));
            long stop = Long.parseLong(args.get(2));
            List<String> items = engine.lrange(args.get(0), start, stop);
            List<RespValue> elements = new ArrayList<>(items.size());
            for (String item : items) {
                elements.add(RespValue.bulkString(item));
            }
            return RespValue.array(elements);
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handleLindex(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'lindex' command");
        }
        try {
            long index = Long.parseLong(args.get(1));
            String item = engine.lindex(args.get(0), index);
            return item == null ? RespValue.nullBulkString() : RespValue.bulkString(item);
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handleLset(List<String> args) {
        if (args.size() != 3) {
            return RespValue.error("ERR wrong number of arguments for 'lset' command");
        }
        try {
            long index = Long.parseLong(args.get(1));
            engine.lset(args.get(0), index, args.get(2));
            return RespValue.ok();
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    private RespValue handleLtrim(List<String> args) {
        if (args.size() != 3) {
            return RespValue.error("ERR wrong number of arguments for 'ltrim' command");
        }
        try {
            long start = Long.parseLong(args.get(1));
            long stop = Long.parseLong(args.get(2));
            engine.ltrim(args.get(0), start, stop);
            return RespValue.ok();
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }

    // ==========================================
    // Set Handlers
    // ==========================================

    private RespValue handleSadd(List<String> args) {
        if (args.size() < 2) {
            return RespValue.error("ERR wrong number of arguments for 'sadd' command");
        }
        return RespValue.integer(engine.sadd(args.get(0), args.subList(1, args.size())));
    }

    private RespValue handleSrem(List<String> args) {
        if (args.size() < 2) {
            return RespValue.error("ERR wrong number of arguments for 'srem' command");
        }
        return RespValue.integer(engine.srem(args.get(0), args.subList(1, args.size())));
    }

    private RespValue handleSismember(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'sismember' command");
        }
        return RespValue.integer(engine.sismember(args.get(0), args.get(1)) ? 1 : 0);
    }

    private RespValue handleSmembers(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'smembers' command");
        }
        Set<String> set = engine.smembers(args.get(0));
        List<RespValue> elements = new ArrayList<>(set.size());
        for (String m : set) {
            elements.add(RespValue.bulkString(m));
        }
        return RespValue.array(elements);
    }

    private RespValue handleScard(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'scard' command");
        }
        return RespValue.integer(engine.scard(args.get(0)));
    }

    private RespValue handleSinter(List<String> args) {
        if (args.isEmpty()) {
            return RespValue.error("ERR wrong number of arguments for 'sinter' command");
        }
        Set<String> set = engine.sinter(args);
        List<RespValue> elements = new ArrayList<>(set.size());
        for (String m : set) {
            elements.add(RespValue.bulkString(m));
        }
        return RespValue.array(elements);
    }

    private RespValue handleSunion(List<String> args) {
        if (args.isEmpty()) {
            return RespValue.error("ERR wrong number of arguments for 'sunion' command");
        }
        Set<String> set = engine.sunion(args);
        List<RespValue> elements = new ArrayList<>(set.size());
        for (String m : set) {
            elements.add(RespValue.bulkString(m));
        }
        return RespValue.array(elements);
    }

    private RespValue handleSdiff(List<String> args) {
        if (args.isEmpty()) {
            return RespValue.error("ERR wrong number of arguments for 'sdiff' command");
        }
        Set<String> set = engine.sdiff(args);
        List<RespValue> elements = new ArrayList<>(set.size());
        for (String m : set) {
            elements.add(RespValue.bulkString(m));
        }
        return RespValue.array(elements);
    }

    // ==========================================
    // Hash Handlers
    // ==========================================

    private RespValue handleHset(List<String> args) {
        if (args.size() < 3 || (args.size() - 1) % 2 != 0) {
            return RespValue.error("ERR wrong number of arguments for 'hset' command");
        }
        Map<String, String> fieldValues = new LinkedHashMap<>();
        for (int i = 1; i < args.size(); i += 2) {
            fieldValues.put(args.get(i), args.get(i + 1));
        }
        return RespValue.integer(engine.hset(args.get(0), fieldValues));
    }

    private RespValue handleHget(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'hget' command");
        }
        String val = engine.hget(args.get(0), args.get(1));
        return val == null ? RespValue.nullBulkString() : RespValue.bulkString(val);
    }

    private RespValue handleHmget(List<String> args) {
        if (args.size() < 2) {
            return RespValue.error("ERR wrong number of arguments for 'hmget' command");
        }
        List<String> values = engine.hmget(args.get(0), args.subList(1, args.size()));
        List<RespValue> elements = new ArrayList<>(values.size());
        for (String v : values) {
            elements.add(v == null ? RespValue.nullBulkString() : RespValue.bulkString(v));
        }
        return RespValue.array(elements);
    }

    private RespValue handleHdel(List<String> args) {
        if (args.size() < 2) {
            return RespValue.error("ERR wrong number of arguments for 'hdel' command");
        }
        return RespValue.integer(engine.hdel(args.get(0), args.subList(1, args.size())));
    }

    private RespValue handleHexists(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'hexists' command");
        }
        return RespValue.integer(engine.hexists(args.get(0), args.get(1)) ? 1 : 0);
    }

    private RespValue handleHgetall(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'hgetall' command");
        }
        Map<String, String> map = engine.hgetall(args.get(0));
        List<RespValue> elements = new ArrayList<>(map.size() * 2);
        for (Map.Entry<String, String> e : map.entrySet()) {
            elements.add(RespValue.bulkString(e.getKey()));
            elements.add(RespValue.bulkString(e.getValue()));
        }
        return RespValue.array(elements);
    }

    private RespValue handleHkeys(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'hkeys' command");
        }
        Set<String> keys = engine.hkeys(args.get(0));
        List<RespValue> elements = new ArrayList<>(keys.size());
        for (String k : keys) {
            elements.add(RespValue.bulkString(k));
        }
        return RespValue.array(elements);
    }

    private RespValue handleHvals(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'hvals' command");
        }
        List<String> vals = engine.hvals(args.get(0));
        List<RespValue> elements = new ArrayList<>(vals.size());
        for (String v : vals) {
            elements.add(RespValue.bulkString(v));
        }
        return RespValue.array(elements);
    }

    private RespValue handleHlen(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'hlen' command");
        }
        return RespValue.integer(engine.hlen(args.get(0)));
    }

    private RespValue handleHincrby(List<String> args) {
        if (args.size() != 3) {
            return RespValue.error("ERR wrong number of arguments for 'hincrby' command");
        }
        try {
            long delta = Long.parseLong(args.get(2));
            return RespValue.integer(engine.hincrby(args.get(0), args.get(1), delta));
        } catch (NumberFormatException e) {
            return RespValue.error("ERR value is not an integer or out of range");
        }
    }
}
