package com.miniredis.command;

import com.miniredis.protocol.RespValue;
import com.miniredis.storage.*;

import java.util.*;

/**
 * Dispatches incoming parsed RESP command arrays to the storage engine
 * and maps results and domain exceptions to standard RESP2 replies.
 */
public class CommandDispatcher {

    private final StorageEngine engine;

    public CommandDispatcher(StorageEngine engine) {
        this.engine = Objects.requireNonNull(engine, "StorageEngine cannot be null");
    }

    public RespValue dispatch(RespValue request) {
        if (!(request instanceof RespValue.RespArray array) || array.isNull()) {
            return RespValue.error("ERR Protocol error: expected array of bulk strings");
        }
        List<RespValue> elements = array.elements();
        if (elements == null || elements.isEmpty()) {
            return RespValue.error("ERR Protocol error: empty command");
        }

        List<String> tokens = new ArrayList<>(elements.size());
        for (RespValue elem : elements) {
            if (elem instanceof RespValue.RespBulkString bulkStr) {
                tokens.add(bulkStr.asUtf8String());
            } else if (elem instanceof RespValue.RespSimpleString ss) {
                tokens.add(ss.value());
            } else {
                return RespValue.error("ERR Protocol error: command elements must be strings");
            }
        }

        String commandName = tokens.get(0);
        if (commandName == null || commandName.isEmpty()) {
            return RespValue.error("ERR Protocol error: empty command name");
