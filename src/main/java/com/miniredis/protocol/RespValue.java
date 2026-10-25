package com.miniredis.protocol;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents a typed RESP2 protocol value.
 */
public interface RespValue {

    RespType getType();

    static RespSimpleString ok() {
        return new RespSimpleString("OK");
    }

    static RespSimpleString pong() {
        return new RespSimpleString("PONG");
    }

    static RespSimpleString simpleString(String value) {
        return new RespSimpleString(value);
    }

    static RespError error(String message) {
        return new RespError(message);
    }

    static RespInteger integer(long value) {
        return new RespInteger(value);
    }

    static RespBulkString bulkString(byte[] bytes) {
        return new RespBulkString(bytes);
    }

    static RespBulkString bulkString(String string) {
        return string == null ? RespBulkString.NULL : new RespBulkString(string.getBytes(StandardCharsets.UTF_8));
    }

    static RespBulkString nullBulkString() {
        return RespBulkString.NULL;
    }

    static RespArray array(List<RespValue> elements) {
        return new RespArray(elements);
    }

    static RespArray nullArray() {
        return RespArray.NULL;
    }

    record RespSimpleString(String value) implements RespValue {
        public RespSimpleString {
            Objects.requireNonNull(value, "Simple string value cannot be null");
            if (value.indexOf('\r') != -1 || value.indexOf('\n') != -1) {
                throw new IllegalArgumentException("Simple string cannot contain CRLF");
            }
        }

        @Override
        public RespType getType() {
            return RespType.SIMPLE_STRING;
