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
        }
    }

    record RespError(String message) implements RespValue {
        public RespError {
            Objects.requireNonNull(message, "Error message cannot be null");
            if (message.indexOf('\r') != -1 || message.indexOf('\n') != -1) {
                throw new IllegalArgumentException("Error message cannot contain CRLF");
            }
        }

        @Override
        public RespType getType() {
            return RespType.ERROR;
        }
    }

    record RespInteger(long value) implements RespValue {
        @Override
        public RespType getType() {
            return RespType.INTEGER;
        }
    }

    record RespBulkString(byte[] data) implements RespValue {
        public static final RespBulkString NULL = new RespBulkString(null);

        public boolean isNull() {
            return data == null;
        }

        public String asUtf8String() {
            return data == null ? null : new String(data, StandardCharsets.UTF_8);
        }

        @Override
        public RespType getType() {
            return RespType.BULK_STRING;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RespBulkString that = (RespBulkString) o;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
