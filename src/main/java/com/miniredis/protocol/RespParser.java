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
        try {
            bulkLength = Integer.parseInt(lenStr);
        } catch (NumberFormatException e) {
            throw new ProtocolException("Invalid bulk string length: " + lenStr);
        }

        if (bulkLength == -1) {
            readPos = crlfIndex + 2;
            return RespValue.RespBulkString.NULL;
        }
        if (bulkLength < -1) {
            throw new ProtocolException("Negative bulk string length invalid: " + bulkLength);
        }
        if (bulkLength > maxBulkStringLength) {
            throw new ProtocolException("Bulk string length " + bulkLength + " exceeds limit " + maxBulkStringLength);
        }

        int payloadStart = crlfIndex + 2;
        int requiredBytes = bulkLength + 2; // payload + CRLF
        if (writePos - payloadStart < requiredBytes) {
            // Incomplete payload, wait for more data without allocating array
            return null;
        }

        int trailingCrIndex = payloadStart + bulkLength;
        if (buffer[trailingCrIndex] != '\r' || buffer[trailingCrIndex + 1] != '\n') {
            throw new ProtocolException("Bulk string missing terminating CRLF");
        }

        byte[] payload = new byte[bulkLength];
        System.arraycopy(buffer, payloadStart, payload, 0, bulkLength);
        readPos = payloadStart + requiredBytes;
        return new RespValue.RespBulkString(payload);
    }

    private RespValue.RespArray parseArray() {
        int crlfIndex = findCrlf(readPos + 1);
        if (crlfIndex == -1) {
            return null;
        }
        int start = readPos + 1;
        int lenStrLen = crlfIndex - start;
        String countStr = new String(buffer, start, lenStrLen, StandardCharsets.US_ASCII);
        int arrayCount;
        try {
            arrayCount = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            throw new ProtocolException("Invalid array length: " + countStr);
        }

        if (arrayCount == -1) {
            readPos = crlfIndex + 2;
            return RespValue.RespArray.NULL;
        }
        if (arrayCount < -1) {
            throw new ProtocolException("Negative array length invalid: " + arrayCount);
        }
        if (arrayCount > maxArrayLength) {
            throw new ProtocolException("Array length " + arrayCount + " exceeds limit " + maxArrayLength);
        }

        int currentReadPos = crlfIndex + 2;
        readPos = currentReadPos;

        List<RespValue> elements = new ArrayList<>(arrayCount);
        for (int i = 0; i < arrayCount; i++) {
            RespValue elem = parseValue();
            if (elem == null) {
                // Not all elements are available yet
                return null;
            }
            elements.add(elem);
        }
        return new RespValue.RespArray(Collections.unmodifiableList(elements));
    }

    private int findCrlf(int start) {
        for (int i = start; i < writePos - 1; i++) {
            if (buffer[i] == '\r' && buffer[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private int available() {
        return writePos - readPos;
    }

    private void ensureCapacity(int needed) {
        if (buffer.length - writePos < needed) {
            int unread = writePos - readPos;
            if (readPos > 0) {
                System.arraycopy(buffer, readPos, buffer, 0, unread);
