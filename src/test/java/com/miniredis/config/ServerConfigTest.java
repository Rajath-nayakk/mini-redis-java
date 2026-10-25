package com.miniredis.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    @Test
    void testDefaultConfig() {
        ServerConfig config = ServerConfig.defaultConfig();
