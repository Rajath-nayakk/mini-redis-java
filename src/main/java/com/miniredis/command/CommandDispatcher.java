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
        }

        List<String> args = tokens.subList(1, tokens.size());
        String upperCommand = commandName.toUpperCase(Locale.ROOT);

        try {
            return switch (upperCommand) {
                case "PING" -> handlePing(args);
                case "ECHO" -> handleEcho(args);
                case "SET" -> handleSet(args);
                case "GET" -> handleGet(args);
                case "DEL" -> handleDel(args);
                case "EXISTS" -> handleExists(args);
                case "DBSIZE" -> handleDbsize(args);
                case "INCR" -> handleIncr(args);
                case "INCRBY" -> handleIncrBy(args);
                case "DECR" -> handleDecr(args);
                case "DECRBY" -> handleDecrBy(args);
                default -> RespValue.error("ERR unknown command '" + commandName + "'");
            };
        } catch (WrongTypeException | InvalidIntegerException | NumericOverflowException e) {
            return RespValue.error(e.getMessage());
        } catch (StorageException e) {
            return RespValue.error("ERR " + e.getMessage());
        } catch (Exception e) {
            return RespValue.error("ERR internal server error: " + e.getMessage());
        }
    }

    private RespValue handlePing(List<String> args) {
        if (args.isEmpty()) {
            return RespValue.pong();
        }
        if (args.size() == 1) {
            return RespValue.bulkString(args.get(0));
        }
        return RespValue.error("ERR wrong number of arguments for 'ping' command");
    }

    private RespValue handleEcho(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'echo' command");
        }
        return RespValue.bulkString(args.get(0));
    }

    private RespValue handleSet(List<String> args) {
        if (args.size() != 2) {
            return RespValue.error("ERR wrong number of arguments for 'set' command");
        }
        engine.set(args.get(0), args.get(1));
        return RespValue.ok();
    }

    private RespValue handleGet(List<String> args) {
        if (args.size() != 1) {
            return RespValue.error("ERR wrong number of arguments for 'get' command");
        }
        String val = engine.get(args.get(0));
        return val == null ? RespValue.nullBulkString() : RespValue.bulkString(val);
    }

    private RespValue handleDel(List<String> args) {
        if (args.isEmpty()) {
            return RespValue.error("ERR wrong number of arguments for 'del' command");
