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
                RespValue.bulkString("SET"),
                RespValue.bulkString("k"),
                RespValue.bulkString("v")
        ));
        byte[] encoded = RespEncoder.encode(array);
        assertEquals("*3\r\n$3\r\nSET\r\n$1\r\nk\r\n$1\r\nv\r\n", new String(encoded, StandardCharsets.UTF_8));
    }

    @Test
    void testEncodeEmptyAndNullArray() {
        assertEquals("*0\r\n", new String(RespEncoder.encode(RespValue.array(List.of())), StandardCharsets.UTF_8));
        assertEquals("*-1\r\n", new String(RespEncoder.encode(RespValue.nullArray()), StandardCharsets.UTF_8));
    }

    @Test
    void testRoundTrip() {
        RespValue.RespArray original = RespValue.array(List.of(
                RespValue.bulkString("MGET"),
                RespValue.bulkString("user:1"),
                RespValue.bulkString("user:2"),
                RespValue.nullBulkString()
        ));
        byte[] encoded = RespEncoder.encode(original);

        RespParser parser = new RespParser();
        parser.append(encoded);
        RespValue decoded = parser.parseNext();

        assertNotNull(decoded);
        assertEquals(RespType.ARRAY, decoded.getType());
