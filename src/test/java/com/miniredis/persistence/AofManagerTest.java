package com.miniredis.persistence;

import com.miniredis.config.ServerConfig;
import com.miniredis.protocol.RespValue;
import com.miniredis.storage.ConcurrentStorageEngine;
import com.miniredis.storage.StorageEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AofManagerTest {

    @TempDir
    Path tempDir;

    private ServerConfig config;
    private AofManager aof;
    private StorageEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        config = ServerConfig.builder()
                .dataDir(tempDir)
                .persistenceEnabled(true)
                .aofSyncPolicy("always")
                .build();
        aof = new AofManager(config);
        aof.start();
        engine = new ConcurrentStorageEngine();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (aof != null) {
            aof.close();
        }
    }

    @Test
    void testLogAndReplayCommands() throws IOException {
        // Log a sequence of mutations
        aof.logCommand(RespValue.array(List.of(RespValue.bulkString("SET"), RespValue.bulkString("name"), RespValue.bulkString("MiniRedis"))));
        aof.logCommand(RespValue.array(List.of(RespValue.bulkString("INCR"), RespValue.bulkString("counter"))));
        aof.logCommand(RespValue.array(List.of(RespValue.bulkString("RPUSH"), RespValue.bulkString("items"), RespValue.bulkString("x"), RespValue.bulkString("y"))));
        aof.logCommand(RespValue.array(List.of(RespValue.bulkString("HSET"), RespValue.bulkString("user"), RespValue.bulkString("role"), RespValue.bulkString("admin"))));

        // Create fresh engine and replay
        StorageEngine replayEngine = new ConcurrentStorageEngine();
        int count = aof.replay(replayEngine);

        assertEquals(4, count);
        assertEquals("MiniRedis", replayEngine.get("name"));
        assertEquals("1", replayEngine.get("counter"));
        assertEquals(List.of("x", "y"), replayEngine.lrange("items", 0, -1));
        assertEquals("admin", replayEngine.hget("user", "role"));
    }

    @Test
    void testReplayWithTruncatedFinalRecord() throws IOException {
        aof.logCommand(RespValue.array(List.of(RespValue.bulkString("SET"), RespValue.bulkString("greeting"), RespValue.bulkString("hello"))));

        // Append corrupted/truncated bytes to AOF
        try (FileOutputStream fos = new FileOutputStream(aof.getAofPath().toFile(), true)) {
            fos.write("*2\r\n$3\r\nSET\r\n$3\r\nfoo".getBytes(StandardCharsets.UTF_8)); // incomplete command
        }

        StorageEngine replayEngine = new ConcurrentStorageEngine();
        int count = aof.replay(replayEngine);
        assertEquals(1, count, "Should replay the 1 valid record and recover safely from truncated tail");
        assertEquals("hello", replayEngine.get("greeting"));
    }

    @Test
    void testAofRewriteCompaction() throws IOException {
        // Populate engine with many mutations that can be compacted
        engine.set("temp", "v1");
        engine.set("temp", "v2");
        engine.set("temp", "final_val");
        engine.rpush("log", List.of("1", "2", "3"));
        engine.hset("hash", Map.of("f1", "v1", "f2", "v2"));

        aof.rewrite(engine);

        // Replay compacted AOF into clean engine
        StorageEngine fresh = new ConcurrentStorageEngine();
        aof.replay(fresh);

        assertEquals("final_val", fresh.get("temp"));
        assertEquals(List.of("1", "2", "3"), fresh.lrange("log", 0, -1));
        assertEquals("v1", fresh.hget("hash", "f1"));
    }
}
