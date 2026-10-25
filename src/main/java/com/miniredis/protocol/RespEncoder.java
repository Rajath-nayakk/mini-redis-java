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

    private RespEncoder() {
    }

    public static byte[] encode(RespValue value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            encode(value, baos);
        } catch (IOException e) {
            throw new RuntimeException("Encoding failure", e);
        }
        return baos.toByteArray();
    }

    public static void encode(RespValue value, OutputStream out) throws IOException {
        if (value == null) {
            out.write(NULL_BULK_STRING);
            return;
        }
        switch (value.getType()) {
            case SIMPLE_STRING -> {
                RespValue.RespSimpleString ss = (RespValue.RespSimpleString) value;
                out.write(PREFIX_SIMPLE_STRING);
                out.write(ss.value().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case ERROR -> {
                RespValue.RespError err = (RespValue.RespError) value;
                out.write(PREFIX_ERROR);
                out.write(err.message().getBytes(StandardCharsets.UTF_8));
                out.write(CRLF);
            }
            case INTEGER -> {
                RespValue.RespInteger integer = (RespValue.RespInteger) value;
                out.write(PREFIX_INTEGER);
                out.write(Long.toString(integer.value()).getBytes(StandardCharsets.US_ASCII));
                out.write(CRLF);
            }
            case BULK_STRING -> {
                RespValue.RespBulkString bs = (RespValue.RespBulkString) value;
                if (bs.isNull()) {
                    out.write(NULL_BULK_STRING);
                } else {
                    out.write(PREFIX_BULK_STRING);
                    out.write(Integer.toString(bs.data().length).getBytes(StandardCharsets.US_ASCII));
                    out.write(CRLF);
                    out.write(bs.data());
                    out.write(CRLF);
                }
            }
            case ARRAY -> {
                RespValue.RespArray arr = (RespValue.RespArray) value;
                if (arr.isNull()) {
                    out.write(NULL_ARRAY);
                } else {
                    out.write(PREFIX_ARRAY);
                    out.write(Integer.toString(arr.elements().size()).getBytes(StandardCharsets.US_ASCII));
                    out.write(CRLF);
                    for (RespValue elem : arr.elements()) {
                        encode(elem, out);
                    }
