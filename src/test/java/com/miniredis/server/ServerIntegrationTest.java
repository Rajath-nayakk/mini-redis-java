package com.miniredis.server;

import com.miniredis.command.CommandDispatcher;
import com.miniredis.config.ServerConfig;
import com.miniredis.protocol.RespParser;
import com.miniredis.protocol.RespType;
import com.miniredis.protocol.RespValue;
import com.miniredis.storage.ConcurrentStorageEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ServerIntegrationTest {

    private MiniRedisServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        ServerConfig config = ServerConfig.builder()
                .host("127.0.0.1")
                .port(port)
                .maxClients(100)
                .build();
        CommandDispatcher dispatcher = new CommandDispatcher(new ConcurrentStorageEngine());
        server = new MiniRedisServer(config, dispatcher);
        server.start();
        port = server.getPort();
        assertTrue(port > 0, "Server should bind to a valid dynamic port");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private Socket connect() throws Exception {
