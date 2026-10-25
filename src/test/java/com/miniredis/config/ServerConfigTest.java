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
