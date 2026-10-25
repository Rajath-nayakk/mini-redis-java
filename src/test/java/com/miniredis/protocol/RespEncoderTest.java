package com.miniredis.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespEncoderTest {

    @Test
    void testEncodeSimpleString() {
        byte[] encoded = RespEncoder.encode(RespValue.ok());
        assertEquals("+OK\r\n", new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeError() {
        byte[] encoded = RespEncoder.encode(RespValue.error("ERR unknown"));
        assertEquals("-ERR unknown\r\n", new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeInteger() {
        assertEquals(":123\r\n", new String(RespEncoder.encode(RespValue.integer(123)), StandardCharsets.UTF_8));
        assertEquals(":-5\r\n", new String(RespEncoder.encode(RespValue.integer(-5)), StandardCharsets.UTF_8));
        assertEquals(":0\r\n", new String(RespEncoder.encode(RespValue.integer(0)), StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeBulkString() {
        byte[] encoded = RespEncoder.encode(RespValue.bulkString("hello"));
        assertEquals("$5\r\nhello\r\n", new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeNullBulkString() {
        byte[] encoded = RespEncoder.encode(RespValue.nullBulkString());
        assertEquals("$-1\r\n", new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeArray() {
        RespValue.RespArray array = RespValue.array(List.of(
