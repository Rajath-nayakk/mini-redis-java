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
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ServerIntegrationTest {

    private MiniRedisServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        ServerConfig config = ServerConfig.builder()
                .host("127.0.0.1")
                .port(0) // Ephemeral port assigned by OS
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
            assertEquals(RespType.SIMPLE_STRING, setResp.getType());
            assertEquals("OK", ((RespValue.RespSimpleString) setResp).value());

            RespValue getResp = sendAndReceive(socket, "*2\r\n$3\r\nGET\r\n$4\r\ncity\r\n");
            assertNotNull(getResp);
            assertEquals(RespType.BULK_STRING, getResp.getType());
            assertEquals("Boston", ((RespValue.RespBulkString) getResp).asUtf8String());
        }
    }

    @Test
    void testMultipleRequestsOverOneConnection() throws Exception {
        try (Socket socket = connect()) {
            for (int i = 0; i < 5; i++) {
                RespValue resp = sendAndReceive(socket, "*2\r\n$4\r\nECHO\r\n$5\r\nmsg-" + i + "\r\n");
                assertNotNull(resp);
                assertEquals("msg-" + i, ((RespValue.RespBulkString) resp).asUtf8String());
            }
        }
    }

    @Test
    void testMultipleSimultaneousClients() throws Exception {
        try (Socket c1 = connect(); Socket c2 = connect()) {
            RespValue r1 = sendAndReceive(c1, "*3\r\n$3\r\nSET\r\n$5\r\nuser1\r\n$5\r\nAlice\r\n");
            RespValue r2 = sendAndReceive(c2, "*3\r\n$3\r\nSET\r\n$5\r\nuser2\r\n$3\r\nBob\r\n");

            assertEquals("OK", ((RespValue.RespSimpleString) r1).value());
            assertEquals("OK", ((RespValue.RespSimpleString) r2).value());

            RespValue g1 = sendAndReceive(c2, "*2\r\n$3\r\nGET\r\n$5\r\nuser1\r\n");
            RespValue g2 = sendAndReceive(c1, "*2\r\n$3\r\nGET\r\n$5\r\nuser2\r\n");

            assertEquals("Alice", ((RespValue.RespBulkString) g1).asUtf8String());
            assertEquals("Bob", ((RespValue.RespBulkString) g2).asUtf8String());
        }
    }

    @Test
    void testDisconnectionCleansUpResources() throws Exception {
        assertEquals(0, server.getActiveClientCount());
        Socket s = connect();
        // Give listener thread a moment to accept
        Thread.sleep(50);
        assertEquals(1, server.getActiveClientCount());

        s.close();
        // Allow session cleanup
        Thread.sleep(100);
        assertEquals(0, server.getActiveClientCount());
    }

    @Test
    void testInvalidCommandsReturnProtocolErrors() throws Exception {
        try (Socket socket = connect()) {
