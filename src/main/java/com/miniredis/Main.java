package com.miniredis;

import com.miniredis.command.CommandDispatcher;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.AofManager;
import com.miniredis.server.MiniRedisServer;
import com.miniredis.storage.ConcurrentStorageEngine;
import com.miniredis.storage.StorageEngine;

/**
 * Main entry point for the MiniRedis database server.
 */
public final class Main {

    public static final String VERSION = "1.0.0-SNAPSHOT";

    public static void main(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                    printHelp();
                    return;
                }
            }
        }

        try {
            ServerConfig config = ServerConfig.parseArgs(args);
            printBanner(config);

            StorageEngine engine = new ConcurrentStorageEngine();

            AofManager aofManager = null;
            if (config.isPersistenceEnabled()) {
                aofManager = new AofManager(config);
                aofManager.start();
                int replayed = aofManager.replay(engine);
                System.out.println("[MiniRedis] Replayed " + replayed + " commands from AOF.");
            }

            final AofManager finalAof = aofManager;
            CommandDispatcher dispatcher = new CommandDispatcher(engine, aofManager, config.getRequirePass());
            MiniRedisServer server = new MiniRedisServer(config, dispatcher);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[MiniRedis] Shutting down gracefully...");
                server.stop();
                if (finalAof != null) {
                    try {
                        finalAof.close();
                    } catch (Exception ignored) {
                    }
                }
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

    private static void printHelp() {
        System.out.println("MiniRedis " + VERSION);
        System.out.println("Usage: java -jar mini-redis-java.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --port <port>             TCP port to listen on (default: 6379)");
        System.out.println("  --host <host>             Network interface IP to bind to (default: 127.0.0.1)");
        System.out.println("  --dir <path>              Data directory for AOF persistence (default: data)");
        System.out.println("  --max-clients <num>       Maximum simultaneous connections (default: 1000)");
        System.out.println("  --max-memory <bytes>      Maximum database memory in bytes (default: 0 = unlimited)");
        System.out.println("  --appendonly <true|false> Enable Append-Only File persistence (default: false)");
        System.out.println("  --requirepass <password>  Require authentication password");
        System.out.println("  --help, -h                Display this help message and exit");
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
        System.out.println("* Max clients=" + config.getMaxClients() + ", Persistence=" + (config.isPersistenceEnabled() ? "enabled" : "disabled"));
        System.out.println("-------------------------------------------------------");
    }
}
