package com.miniredis.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    @Test
    void testDefaultConfig() {
        ServerConfig config = ServerConfig.defaultConfig();
        assertEquals("127.0.0.1", config.getHost());
        assertEquals(6379, config.getPort());
        assertEquals(1000, config.getMaxClients());
        assertEquals(10 * 1024 * 1024, config.getMaxRequestSize());
        assertEquals(0, config.getTimeoutMs());
        assertEquals(Paths.get("data"), config.getDataDir());
        assertFalse(config.isPersistenceEnabled());
        assertEquals("everysec", config.getAofSyncPolicy());
        assertEquals(100L, config.getCleanupIntervalMs());
        assertEquals(0L, config.getMaxMemoryBytes());
        assertEquals("notice", config.getLogLevel());
    }

    @Test
    void testCustomBuilder() {
        ServerConfig config = ServerConfig.builder()
                .host("192.168.1.10")
                .port(6380)
                .maxClients(500)
                .maxRequestSize(5 * 1024 * 1024)
                .timeoutMs(5000)
                .dataDir(Paths.get("/custom/data"))
                .persistenceEnabled(true)
                .aofSyncPolicy("always")
                .cleanupIntervalMs(50L)
                .maxMemoryBytes(100_000_000L)
                .logLevel("debug")
                .build();

        assertEquals("192.168.1.10", config.getHost());
        assertEquals(6380, config.getPort());
        assertEquals(500, config.getMaxClients());
        assertEquals(5 * 1024 * 1024, config.getMaxRequestSize());
        assertEquals(5000, config.getTimeoutMs());
        assertEquals(Paths.get("/custom/data"), config.getDataDir());
        assertTrue(config.isPersistenceEnabled());
        assertEquals("always", config.getAofSyncPolicy());
        assertEquals(50L, config.getCleanupIntervalMs());
        assertEquals(100_000_000L, config.getMaxMemoryBytes());
        assertEquals("debug", config.getLogLevel());
    }

    @Test
    void testInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().port(0).build());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().port(-1).build());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().port(65536).build());
    }

    @Test
    void testInvalidHost() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().host(null).build());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().host("").build());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().host("   ").build());
    }

    @Test
    void testInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().maxClients(0).build());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().maxRequestSize(0).build());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().timeoutMs(-1).build());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().cleanupIntervalMs(0).build());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().maxMemoryBytes(-10).build());
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().aofSyncPolicy("unknown").build());
    }

    @Test
    void testParseArgs() {
        String[] args = new String[]{
                "--host", "10.0.0.1",
                "--port", "7000",
                "--max-clients", "250",
                "--max-memory", "50000000",
                "--appendonly", "true",
                "--dir", "mydata"
        };
        ServerConfig config = ServerConfig.parseArgs(args);
        assertEquals("10.0.0.1", config.getHost());
        assertEquals(7000, config.getPort());
        assertEquals(250, config.getMaxClients());
        assertEquals(50_000_000L, config.getMaxMemoryBytes());
        assertTrue(config.isPersistenceEnabled());
