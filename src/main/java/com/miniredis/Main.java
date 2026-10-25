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
                System.out.println("\n[MiniRedis] Shutting down gracefully...");
                server.stop();
                System.out.println("[MiniRedis] Server stopped.");
            }));

            server.start();
            System.out.println("[MiniRedis] Server started on " + config.getHost() + ":" + server.getPort());
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printBanner(ServerConfig config) {
        System.out.println("                _       _ _____          _ _       ");
        System.out.println("  /\\/\\   ___   (_)     | |  __ \\        | (_)      ");
        System.out.println(" /    \\ / _ \\  | |     | | |__) |___  __| |_ ___   ");
        System.out.println("/ /\\/\\ \\ (_) | | |_ /\\| |  _  // _ \\/ _` | / __|  ");
        System.out.println("\\/    \\/\\___/  |_(_)\\__/ |_| \\_\\\\___/\\__,_|_\\___|  ");
        System.out.println("                                                   ");
        System.out.println("* Educational Redis-inspired in-memory database in Java");
        System.out.println("* MiniRedis version=" + VERSION + ", Java=" + System.getProperty("java.version"));
        System.out.println("* Listening on host=" + config.getHost() + ", port=" + config.getPort());
