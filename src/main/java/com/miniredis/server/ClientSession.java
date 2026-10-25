package com.miniredis.server;

import com.miniredis.command.CommandDispatcher;
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
 * Manages request parsing, command execution, response serialization, and connection lifecycle.
 */
public class ClientSession implements Runnable {

    private final Socket socket;
    private final CommandDispatcher dispatcher;
    private final MiniRedisServer server;
