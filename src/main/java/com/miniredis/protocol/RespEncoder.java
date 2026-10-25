package com.miniredis.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serializes RespValue objects into RESP2 wire-format byte sequences.
 */
public final class RespEncoder {

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte PREFIX_SIMPLE_STRING = '+';
    private static final byte PREFIX_ERROR = '-';
    private static final byte PREFIX_INTEGER = ':';
    private static final byte PREFIX_BULK_STRING = '$';
    private static final byte PREFIX_ARRAY = '*';
    private static final byte[] NULL_BULK_STRING = "$-1\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NULL_ARRAY = "*-1\r\n".getBytes(StandardCharsets.US_ASCII);

