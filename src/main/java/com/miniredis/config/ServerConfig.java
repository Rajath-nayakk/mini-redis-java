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
        this.cleanupIntervalMs = builder.cleanupIntervalMs;
        this.maxMemoryBytes = builder.maxMemoryBytes;
        this.logLevel = builder.logLevel;
        validate();
    }

    public void validate() {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535: " + port);
        }
        if (maxClients <= 0) {
            throw new IllegalArgumentException("Max clients must be positive: " + maxClients);
        }
        if (maxRequestSize <= 0) {
            throw new IllegalArgumentException("Max request size must be positive: " + maxRequestSize);
        }
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative: " + timeoutMs);
        }
        if (dataDir == null) {
            throw new IllegalArgumentException("Data directory cannot be null");
        }
        if (cleanupIntervalMs <= 0) {
            throw new IllegalArgumentException("Cleanup interval must be positive: " + cleanupIntervalMs);
        }
        if (maxMemoryBytes < 0) {
            throw new IllegalArgumentException("Max memory cannot be negative: " + maxMemoryBytes);
        }
        if (!"always".equalsIgnoreCase(aofSyncPolicy) && !"everysec".equalsIgnoreCase(aofSyncPolicy) && !"no".equalsIgnoreCase(aofSyncPolicy)) {
            throw new IllegalArgumentException("Invalid AOF sync policy: " + aofSyncPolicy + ". Must be 'always', 'everysec', or 'no'");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ServerConfig defaultConfig() {
        return builder().build();
    }

    public static ServerConfig parseArgs(String[] args) {
        Builder builder = builder();
        if (args == null) {
            return builder.build();
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--port".equalsIgnoreCase(arg) && i + 1 < args.length) {
                builder.port(Integer.parseInt(args[++i]));
            } else if ("--host".equalsIgnoreCase(arg) && i + 1 < args.length) {
                builder.host(args[++i]);
            } else if ("--dir".equalsIgnoreCase(arg) && i + 1 < args.length) {
                builder.dataDir(Paths.get(args[++i]));
            } else if ("--max-clients".equalsIgnoreCase(arg) && i + 1 < args.length) {
                builder.maxClients(Integer.parseInt(args[++i]));
            } else if ("--max-memory".equalsIgnoreCase(arg) && i + 1 < args.length) {
                builder.maxMemoryBytes(Long.parseLong(args[++i]));
            } else if ("--appendonly".equalsIgnoreCase(arg) && i + 1 < args.length) {
                builder.persistenceEnabled(Boolean.parseBoolean(args[++i]));
