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
