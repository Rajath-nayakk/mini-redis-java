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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded TCP server for MiniRedis handling client connections,
 * connection pooling, life-cycle management, and clean shutdown.
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
        this.config = Objects.requireNonNull(config, "ServerConfig cannot be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "CommandDispatcher cannot be null");
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(config.getHost(), config.getPort()));

        clientExecutor = Executors.newFixedThreadPool(config.getMaxClients(), r -> {
            Thread t = new Thread(r, "miniredis-client");
            t.setDaemon(true);
            return t;
        });
        running = true;

        listenerThread = new Thread(this::listenLoop, "miniredis-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                if (!running) {
                    try {
                        clientSocket.close();
                    } catch (IOException ignored) {
                    }
                    break;
                }

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

                ClientSession session = new ClientSession(clientSocket, dispatcher, this, config.getMaxRequestSize());
                try {
                    clientExecutor.submit(session);
                } catch (RejectedExecutionException e) {
                    onClientDisconnected(clientSocket);
                    try {
                        clientSocket.close();
                    } catch (IOException ignored) {
                    }
                }

            } catch (IOException e) {
                if (!running) {
                    break;
                }
            }
        }
    }

    public void onClientDisconnected(Socket socket) {
        if (socket != null) {
            activeSockets.remove(socket);
        }
        activeClients.decrementAndGet();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        for (Socket s : activeSockets) {
            try {
                if (!s.isClosed()) {
                    s.close();
                }
            } catch (IOException ignored) {
            }
        }
        activeSockets.clear();
        activeClients.set(0);

        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
            try {
                clientExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
