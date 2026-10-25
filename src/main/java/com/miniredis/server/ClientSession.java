package com.miniredis.server;

import com.miniredis.command.CommandDispatcher;
import com.miniredis.command.TransactionContext;
import com.miniredis.protocol.ProtocolException;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.protocol.RespParser;
import com.miniredis.protocol.RespValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * Handles communication with a connected TCP client.
 * Manages request parsing, command execution, per-connection authentication,
 * per-connection transactions, response serialization, and connection lifecycle.
 */
public class ClientSession implements Runnable {

    private final Socket socket;
    private final CommandDispatcher dispatcher;
    private final MiniRedisServer server;
    private final RespParser parser;
    private final TransactionContext txContext = new TransactionContext();
    private boolean authenticated = false;

    public ClientSession(Socket socket, CommandDispatcher dispatcher, MiniRedisServer server, int maxRequestSize) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.server = server;
        this.parser = new RespParser(maxRequestSize, RespParser.DEFAULT_MAX_ARRAY_LENGTH);
    }

    @Override
    public void run() {
        try (socket;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while (!socket.isClosed() && (bytesRead = in.read(buffer)) != -1) {
                parser.append(buffer, 0, bytesRead);

                try {
                    RespValue request;
                    while ((request = parser.parseNext()) != null) {
                        RespValue response = dispatcher.dispatch(request, txContext, authenticated);
                        if (!authenticated && response instanceof RespValue.RespSimpleString ss && "OK".equals(ss.value())) {
                            if (request instanceof RespValue.RespArray arr && !arr.elements().isEmpty()
                                    && arr.elements().get(0) instanceof RespValue.RespBulkString bs
                                    && "AUTH".equalsIgnoreCase(bs.asUtf8String())) {
                                this.authenticated = true;
                            }
                        }

                        byte[] responseBytes = RespEncoder.encode(response);
                        out.write(responseBytes);
                        out.flush();
                    }
                } catch (ProtocolException e) {
                    byte[] errBytes = RespEncoder.encode(RespValue.error("ERR Protocol error: " + e.getMessage()));
                    out.write(errBytes);
                    out.flush();
                    break;
                }
            }
        } catch (SocketException e) {
            // Normal socket termination upon client disconnect or server shutdown
        } catch (IOException e) {
            // Socket IO exception
        } finally {
            txContext.discard();
            server.onClientDisconnected(socket);
        }
    }
}
