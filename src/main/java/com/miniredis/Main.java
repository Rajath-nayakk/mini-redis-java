package com.miniredis;

import com.miniredis.command.CommandDispatcher;
import com.miniredis.config.ServerConfig;
import com.miniredis.server.MiniRedisServer;
import com.miniredis.storage.ConcurrentStorageEngine;
import com.miniredis.storage.StorageEngine;

/**
 * Main entry point for the MiniRedis database server.
 */
public final class Main {

    public static final String VERSION = "1.0.0-SNAPSHOT";

    public static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.parseArgs(args);
            printBanner(config);

            StorageEngine engine = new ConcurrentStorageEngine();
            CommandDispatcher dispatcher = new CommandDispatcher(engine);
            MiniRedisServer server = new MiniRedisServer(config, dispatcher);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
