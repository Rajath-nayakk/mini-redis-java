package com.miniredis;

import com.miniredis.config.ServerConfig;

/**
 * Main entry point for the MiniRedis database server.
 */
public final class Main {

    public static final String VERSION = "1.0.0-SNAPSHOT";

    public static void main(String[] args) {
        try {
            ServerConfig config = ServerConfig.parseArgs(args);
            printBanner(config);
            System.out.println("[MiniRedis] Configuration validated successfully.");
            System.out.println("[MiniRedis] Ready for client connections (networking scheduled for Phase 5).");
