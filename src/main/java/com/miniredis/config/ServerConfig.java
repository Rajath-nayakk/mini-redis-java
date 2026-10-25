package com.miniredis.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Configuration parameters for the MiniRedis server instance.
 */
public final class ServerConfig {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 6379;
    public static final int DEFAULT_MAX_CLIENTS = 1000;
    public static final int DEFAULT_MAX_REQUEST_SIZE = 10 * 1024 * 1024; // 10MB
    public static final int DEFAULT_TIMEOUT_MS = 0; // 0 = no timeout
