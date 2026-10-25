package com.miniredis.server;

import com.miniredis.command.CommandDispatcher;
import com.miniredis.config.ServerConfig;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.protocol.RespValue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-client TCP server for MiniRedis.
 * Manages ServerSocket, bounded thread pool, connection limits, and graceful shutdown.
 */
public class MiniRedisServer {

    private final ServerConfig config;
    private final CommandDispatcher dispatcher;
    private final AtomicInteger activeClients = new AtomicInteger(0);
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();

    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;
    private Thread listenerThread;
    private volatile boolean running = false;

    public MiniRedisServer(ServerConfig config, CommandDispatcher dispatcher) {
        this.config = config;
        this.dispatcher = dispatcher;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(config.getHost(), config.getPort()));

        clientExecutor = Executors.newFixedThreadPool(config.getMaxClients());
        running = true;

        listenerThread = new Thread(this::listenLoop, "miniredis-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                if (config.getTimeoutMs() > 0) {
                    clientSocket.setSoTimeout(config.getTimeoutMs());
                }

                if (activeClients.get() >= config.getMaxClients()) {
                    try (clientSocket; OutputStream out = clientSocket.getOutputStream()) {
                        byte[] err = RespEncoder.encode(RespValue.error("ERR max number of clients reached"));
                        out.write(err);
                        out.flush();
                    }
                    continue;
                }

                activeClients.incrementAndGet();
                activeSockets.add(clientSocket);
