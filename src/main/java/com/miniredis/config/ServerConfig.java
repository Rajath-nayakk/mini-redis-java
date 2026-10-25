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
    public static final String DEFAULT_DATA_DIR = "data";
    public static final boolean DEFAULT_PERSISTENCE_ENABLED = false;
    public static final String DEFAULT_AOF_SYNC_POLICY = "everysec";
    public static final long DEFAULT_CLEANUP_INTERVAL_MS = 100L;
    public static final long DEFAULT_MAX_MEMORY_BYTES = 0L; // 0 = unlimited
    public static final String DEFAULT_LOG_LEVEL = "notice";

    private final String host;
    private final int port;
    private final int maxClients;
    private final int maxRequestSize;
    private final int timeoutMs;
    private final Path dataDir;
    private final boolean persistenceEnabled;
    private final String aofSyncPolicy;
    private final long cleanupIntervalMs;
    private final long maxMemoryBytes;
    private final String logLevel;

    private ServerConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.maxClients = builder.maxClients;
        this.maxRequestSize = builder.maxRequestSize;
        this.timeoutMs = builder.timeoutMs;
        this.dataDir = builder.dataDir;
        this.persistenceEnabled = builder.persistenceEnabled;
        this.aofSyncPolicy = builder.aofSyncPolicy;
