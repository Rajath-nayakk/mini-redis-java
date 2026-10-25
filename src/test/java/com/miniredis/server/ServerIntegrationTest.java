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
        return new Socket("127.0.0.1", port);
    }

    private RespValue sendAndReceive(Socket socket, String respCommand) throws Exception {
        OutputStream out = socket.getOutputStream();
        out.write(respCommand.getBytes(StandardCharsets.UTF_8));
        out.flush();

        InputStream in = socket.getInputStream();
        RespParser parser = new RespParser();
        byte[] buf = new byte[1024];
        while (!socket.isClosed()) {
            int read = in.read(buf);
            if (read == -1) {
                break;
            }
            parser.append(buf, 0, read);
            RespValue value = parser.parseNext();
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Test
    void testPingReturnsPong() throws Exception {
        try (Socket socket = connect()) {
            RespValue resp = sendAndReceive(socket, "*1\r\n$4\r\nPING\r\n");
            assertNotNull(resp);
            assertEquals(RespType.SIMPLE_STRING, resp.getType());
            assertEquals("PONG", ((RespValue.RespSimpleString) resp).value());
        }
    }

    @Test
    void testSetFollowedByGet() throws Exception {
        try (Socket socket = connect()) {
            RespValue setResp = sendAndReceive(socket, "*3\r\n$3\r\nSET\r\n$4\r\ncity\r\n$6\r\nBoston\r\n");
            assertNotNull(setResp);
