package com.miniredis.protocol;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Streaming RESP2 protocol parser.
 * Handles partial network reads, multi-command buffers, and frame boundary validation.
 */
public class RespParser {

    public static final int DEFAULT_MAX_BULK_STRING_LENGTH = 512 * 1024 * 1024; // 512MB
    public static final int DEFAULT_MAX_ARRAY_LENGTH = 1024 * 1024; // 1M elements

    private final int maxBulkStringLength;
    private final int maxArrayLength;

    private byte[] buffer = new byte[4096];
    private int readPos = 0;
    private int writePos = 0;

    public RespParser() {
        this(DEFAULT_MAX_BULK_STRING_LENGTH, DEFAULT_MAX_ARRAY_LENGTH);
    }

    public RespParser(int maxBulkStringLength, int maxArrayLength) {
        this.maxBulkStringLength = maxBulkStringLength;
        this.maxArrayLength = maxArrayLength;
    }

    public void append(byte[] data) {
        if (data != null && data.length > 0) {
            append(data, 0, data.length);
        }
    }

    public void append(byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            return;
        }
        ensureCapacity(length);
        System.arraycopy(data, offset, buffer, writePos, length);
        writePos += length;
    }

    public RespValue parseNext() {
        if (available() == 0) {
            return null;
        }
        int savedReadPos = readPos;
        try {
            RespValue value = parseValue();
            if (value != null) {
                compactIfNeeded();
                return value;
            }
            readPos = savedReadPos;
            return null;
        } catch (ProtocolException e) {
            readPos = savedReadPos;
            throw e;
        }
    }

    private RespValue parseValue() {
        if (available() == 0) {
            return null;
        }
        byte prefix = buffer[readPos];
        switch (prefix) {
            case '+':
                return parseSimpleString();
            case '-':
                return parseError();
            case ':':
                return parseInteger();
            case '$':
                return parseBulkString();
            case '*':
                return parseArray();
            default:
                throw new ProtocolException("Unexpected RESP prefix: '" + (char) prefix + "' (0x" + Integer.toHexString(prefix & 0xFF) + ")");
        }
    }

    private RespValue.RespSimpleString parseSimpleString() {
        int crlfIndex = findCrlf(readPos + 1);
        if (crlfIndex == -1) {
            return null;
        }
        int start = readPos + 1;
        int len = crlfIndex - start;
        String str = new String(buffer, start, len, StandardCharsets.UTF_8);
        readPos = crlfIndex + 2;
        return new RespValue.RespSimpleString(str);
    }

    private RespValue.RespError parseError() {
        int crlfIndex = findCrlf(readPos + 1);
        if (crlfIndex == -1) {
            return null;
        }
        int start = readPos + 1;
        int len = crlfIndex - start;
        String msg = new String(buffer, start, len, StandardCharsets.UTF_8);
        readPos = crlfIndex + 2;
        return new RespValue.RespError(msg);
    }

    private RespValue.RespInteger parseInteger() {
        int crlfIndex = findCrlf(readPos + 1);
        if (crlfIndex == -1) {
            return null;
        }
        int start = readPos + 1;
        int len = crlfIndex - start;
        String str = new String(buffer, start, len, StandardCharsets.US_ASCII);
        readPos = crlfIndex + 2;
        try {
            long val = Long.parseLong(str);
            return new RespValue.RespInteger(val);
        } catch (NumberFormatException e) {
            throw new ProtocolException("Invalid RESP integer value: " + str);
        }
    }

    private RespValue.RespBulkString parseBulkString() {
        int crlfIndex = findCrlf(readPos + 1);
        if (crlfIndex == -1) {
            return null;
        }
        int start = readPos + 1;
        int lenStrLen = crlfIndex - start;
        String lenStr = new String(buffer, start, lenStrLen, StandardCharsets.US_ASCII);
        int bulkLength;
