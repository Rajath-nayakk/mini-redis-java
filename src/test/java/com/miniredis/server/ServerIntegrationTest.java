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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerIntegrationTest {

    private MiniRedisServer server;
    private CommandDispatcher dispatcher;
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
        dispatcher = new CommandDispatcher(new ConcurrentStorageEngine());
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
        for (int i = 0; i < 50 && server.getActiveClientCount() == 0; i++) {
            Thread.sleep(20);
        }
        assertEquals(1, server.getActiveClientCount());

        s.close();
        for (int i = 0; i < 50 && server.getActiveClientCount() > 0; i++) {
            Thread.sleep(20);
        }
        assertEquals(0, server.getActiveClientCount());
    }

    @Test
    void testInvalidCommandsReturnProtocolErrors() throws Exception {
        try (Socket socket = connect()) {
            RespValue err = sendAndReceive(socket, "*1\r\n$8\r\nBOGUSCMD\r\n");
            assertNotNull(err);
            assertEquals(RespType.ERROR, err.getType());
            assertTrue(((RespValue.RespError) err).message().contains("unknown command"));

            // Connection should remain usable
            RespValue ping = sendAndReceive(socket, "*1\r\n$4\r\nPING\r\n");
            assertNotNull(ping);
            assertEquals("PONG", ((RespValue.RespSimpleString) ping).value());
        }
    }

    @Test
    void testMalformedRequestsDoNotCrashServer() throws Exception {
        try (Socket socket = connect()) {
            // Send malformed non-RESP bytes
            OutputStream out = socket.getOutputStream();
            out.write("INVALID PROTOCOL BYTES\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = socket.getInputStream();
            byte[] buf = new byte[256];
            int read = in.read(buf);
            assertTrue(read > 0, "Server should reply with error before closing malformed connection");
            String response = new String(buf, 0, read, StandardCharsets.UTF_8);
            assertTrue(response.startsWith("-ERR Protocol error"));
        }

        // Verify server is healthy and accepts new clients
        try (Socket healthyClient = connect()) {
            RespValue ping = sendAndReceive(healthyClient, "*1\r\n$4\r\nPING\r\n");
            assertNotNull(ping);
            assertEquals("PONG", ((RespValue.RespSimpleString) ping).value());
        }
    }

    @Test
    void testServerShutdownClosesResources() throws Exception {
        Socket client = connect();
        assertTrue(server.isRunning());

        server.stop();
        assertFalse(server.isRunning());

        // Client read should detect closure (EOF -1 or SocketException)
        InputStream in = client.getInputStream();
        try {
            int b = in.read();
            assertEquals(-1, b, "Socket should receive EOF on server shutdown");
        } catch (SocketException expected) {
            // Socket closure by remote peer can raise SocketException on Windows
        }
        client.close();
    }

    @Test
    void testTransactionsOverTcp() throws Exception {
        try (Socket socket = connect()) {
            assertEquals("OK", ((RespValue.RespSimpleString) sendAndReceive(socket, "*1\r\n$5\r\nMULTI\r\n")).value());
            assertEquals("QUEUED", ((RespValue.RespSimpleString) sendAndReceive(socket, "*3\r\n$3\r\nSET\r\n$5\r\ntxkey\r\n$7\r\nval_one\r\n")).value());
            assertEquals("QUEUED", ((RespValue.RespSimpleString) sendAndReceive(socket, "*2\r\n$3\r\nGET\r\n$5\r\ntxkey\r\n")).value());

            RespValue exec = sendAndReceive(socket, "*1\r\n$4\r\nEXEC\r\n");
            assertNotNull(exec);
            assertEquals(RespType.ARRAY, exec.getType());
            List<RespValue> items = ((RespValue.RespArray) exec).elements();
            assertEquals(2, items.size());
            assertEquals("OK", ((RespValue.RespSimpleString) items.get(0)).value());
            assertEquals("val_one", ((RespValue.RespBulkString) items.get(1)).asUtf8String());
        }
    }

    @Test
    void testListAndHashOverTcp() throws Exception {
        try (Socket socket = connect()) {
            RespValue rpush = sendAndReceive(socket, "*4\r\n$5\r\nRPUSH\r\n$4\r\nnums\r\n$1\r\n1\r\n$1\r\n2\r\n");
            assertEquals(2L, ((RespValue.RespInteger) rpush).value());

            RespValue lpop = sendAndReceive(socket, "*2\r\n$4\r\nLPOP\r\n$4\r\nnums\r\n");
            assertEquals("1", ((RespValue.RespBulkString) lpop).asUtf8String());

            RespValue hset = sendAndReceive(socket, "*4\r\n$4\r\nHSET\r\n$4\r\nuser\r\n$4\r\nname\r\n$3\r\nBob\r\n");
            assertEquals(1L, ((RespValue.RespInteger) hset).value());

            RespValue hget = sendAndReceive(socket, "*3\r\n$4\r\nHGET\r\n$4\r\nuser\r\n$4\r\nname\r\n");
            assertEquals("Bob", ((RespValue.RespBulkString) hget).asUtf8String());
        }
    }

    @Test
    void testAuthOverTcp() throws Exception {
        dispatcher.setRequirePass("supersecret");

        try (Socket socket = connect()) {
            // First attempt without AUTH
            RespValue blocked = sendAndReceive(socket, "*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n");
            assertTrue(((RespValue.RespError) blocked).message().contains("NOAUTH"));

            // Wrong password
            RespValue badAuth = sendAndReceive(socket, "*2\r\n$4\r\nAUTH\r\n$5\r\nwrong\r\n");
            assertTrue(((RespValue.RespError) badAuth).message().contains("invalid password"));

            // Correct password
            RespValue goodAuth = sendAndReceive(socket, "*2\r\n$4\r\nAUTH\r\n$11\r\nsupersecret\r\n");
            assertEquals("OK", ((RespValue.RespSimpleString) goodAuth).value());

            // Protected commands now work
            RespValue ping = sendAndReceive(socket, "*1\r\n$4\r\nPING\r\n");
            assertEquals("PONG", ((RespValue.RespSimpleString) ping).value());
        }
    }

    @Test
    void testPipeliningOverTcp() throws Exception {
        try (Socket socket = connect()) {
            OutputStream out = socket.getOutputStream();
            String pipeline = "*3\r\n$3\r\nSET\r\n$2\r\np1\r\n$3\r\none\r\n" +
                              "*3\r\n$3\r\nSET\r\n$2\r\np2\r\n$3\r\ntwo\r\n" +
                              "*2\r\n$3\r\nGET\r\n$2\r\np1\r\n" +
                              "*2\r\n$3\r\nGET\r\n$2\r\np2\r\n";
            out.write(pipeline.getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = socket.getInputStream();
            RespParser parser = new RespParser();
            byte[] buf = new byte[1024];
            List<RespValue> replies = new java.util.ArrayList<>();
            while (replies.size() < 4) {
                int read = in.read(buf);
                assertTrue(read > 0, "Socket read must return bytes during pipelining");
                parser.append(buf, 0, read);
                RespValue val;
                while ((val = parser.parseNext()) != null) {
                    replies.add(val);
                }
            }

            assertEquals(4, replies.size());
            assertEquals("OK", ((RespValue.RespSimpleString) replies.get(0)).value());
            assertEquals("OK", ((RespValue.RespSimpleString) replies.get(1)).value());
            assertEquals("one", ((RespValue.RespBulkString) replies.get(2)).asUtf8String());
            assertEquals("two", ((RespValue.RespBulkString) replies.get(3)).asUtf8String());
        }
    }

    @Test
    void testDiscardOverTcp() throws Exception {
        try (Socket socket = connect()) {
            assertEquals("OK", ((RespValue.RespSimpleString) sendAndReceive(socket, "*1\r\n$5\r\nMULTI\r\n")).value());
            assertEquals("QUEUED", ((RespValue.RespSimpleString) sendAndReceive(socket, "*3\r\n$3\r\nSET\r\n$10\r\ndiscardkey\r\n$7\r\ndropped\r\n")).value());
            assertEquals("OK", ((RespValue.RespSimpleString) sendAndReceive(socket, "*1\r\n$7\r\nDISCARD\r\n")).value());

            RespValue getResp = sendAndReceive(socket, "*2\r\n$3\r\nGET\r\n$10\r\ndiscardkey\r\n");
            assertNotNull(getResp);
            assertTrue(((RespValue.RespBulkString) getResp).isNull(), "Discarded command must not mutate storage");
        }
    }

    @Test
    void testConcurrentTransactionAtomicity() throws Exception {
        try (Socket clientA = connect(); Socket clientB = connect()) {
            sendAndReceive(clientA, "*3\r\n$3\r\nSET\r\n$6\r\npair_1\r\n$4\r\ninit\r\n");
            sendAndReceive(clientA, "*3\r\n$3\r\nSET\r\n$6\r\npair_2\r\n$4\r\ninit\r\n");

            sendAndReceive(clientA, "*1\r\n$5\r\nMULTI\r\n");
            sendAndReceive(clientA, "*3\r\n$3\r\nSET\r\n$6\r\npair_1\r\n$6\r\nupdate\r\n");
            sendAndReceive(clientA, "*3\r\n$3\r\nSET\r\n$6\r\npair_2\r\n$6\r\nupdate\r\n");
            RespValue execResp = sendAndReceive(clientA, "*1\r\n$4\r\nEXEC\r\n");
            assertNotNull(execResp);
            assertEquals(RespType.ARRAY, execResp.getType());

            RespValue g1 = sendAndReceive(clientB, "*2\r\n$3\r\nGET\r\n$6\r\npair_1\r\n");
            RespValue g2 = sendAndReceive(clientB, "*2\r\n$3\r\nGET\r\n$6\r\npair_2\r\n");
            assertEquals("update", ((RespValue.RespBulkString) g1).asUtf8String());
            assertEquals("update", ((RespValue.RespBulkString) g2).asUtf8String());
        }
    }
}
