package com.miniredis.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RespParserTest {

    private RespParser parser;

    @BeforeEach
    void setUp() {
        parser = new RespParser();
    }

    @Test
    void testParseSimpleString() {
        parser.append("+OK\r\n".getBytes(StandardCharsets.UTF_8));
        RespValue val = parser.parseNext();
        assertNotNull(val);
        assertEquals(RespType.SIMPLE_STRING, val.getType());
        assertEquals("OK", ((RespValue.RespSimpleString) val).value());
        assertNull(parser.parseNext());
    }

    @Test
    void testParseError() {
        parser.append("-ERR unknown command\r\n".getBytes(StandardCharsets.UTF_8));
        RespValue val = parser.parseNext();
        assertNotNull(val);
        assertEquals(RespType.ERROR, val.getType());
        assertEquals("ERR unknown command", ((RespValue.RespError) val).message());
    }

    @Test
    void testParseInteger() {
        parser.append(":1000\r\n:-42\r\n:0\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(1000L, ((RespValue.RespInteger) parser.parseNext()).value());
        assertEquals(-42L, ((RespValue.RespInteger) parser.parseNext()).value());
        assertEquals(0L, ((RespValue.RespInteger) parser.parseNext()).value());
        assertNull(parser.parseNext());
    }

    @Test
    void testParseBulkString() {
        parser.append("$5\r\nhello\r\n".getBytes(StandardCharsets.UTF_8));
        RespValue.RespBulkString bs = (RespValue.RespBulkString) parser.parseNext();
        assertNotNull(bs);
        assertEquals("hello", bs.asUtf8String());
    }

    @Test
    void testParseEmptyBulkString() {
        parser.append("$0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        RespValue.RespBulkString bs = (RespValue.RespBulkString) parser.parseNext();
        assertNotNull(bs);
        assertEquals("", bs.asUtf8String());
        assertEquals(0, bs.data().length);
    }

    @Test
    void testParseNullBulkString() {
        parser.append("$-1\r\n".getBytes(StandardCharsets.UTF_8));
        RespValue.RespBulkString bs = (RespValue.RespBulkString) parser.parseNext();
        assertNotNull(bs);
        assertTrue(bs.isNull());
        assertNull(bs.asUtf8String());
    }

    @Test
    void testParseUnicodeBulkString() {
        String unicode = "æ—¥æœ¬èªž / MiniRedis ðŸš€";
        byte[] bytes = unicode.getBytes(StandardCharsets.UTF_8);
        String header = "$" + bytes.length + "\r\n";
        parser.append(header.getBytes(StandardCharsets.US_ASCII));
        parser.append(bytes);
        parser.append("\r\n".getBytes(StandardCharsets.US_ASCII));

        RespValue.RespBulkString bs = (RespValue.RespBulkString) parser.parseNext();
        assertNotNull(bs);
        assertEquals(unicode, bs.asUtf8String());
    }

    @Test
    void testParseArray() {
        String raw = "*2\r\n$3\r\nGET\r\n$4\r\nuser\r\n";
        parser.append(raw.getBytes(StandardCharsets.UTF_8));

        RespValue.RespArray array = (RespValue.RespArray) parser.parseNext();
        assertNotNull(array);
        assertEquals(2, array.elements().size());
        assertEquals("GET", ((RespValue.RespBulkString) array.elements().get(0)).asUtf8String());
        assertEquals("user", ((RespValue.RespBulkString) array.elements().get(1)).asUtf8String());
    }

    @Test
    void testParseEmptyAndNullArray() {
        parser.append("*0\r\n*-1\r\n".getBytes(StandardCharsets.UTF_8));
        RespValue.RespArray empty = (RespValue.RespArray) parser.parseNext();
        assertNotNull(empty);
        assertFalse(empty.isNull());
        assertTrue(empty.elements().isEmpty());

        RespValue.RespArray nullArr = (RespValue.RespArray) parser.parseNext();
        assertNotNull(nullArr);
        assertTrue(nullArr.isNull());
    }

    @Test
    void testPartialInputAcrossChunks() {
        byte[] full = "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n".getBytes(StandardCharsets.UTF_8);
        // Feed 3 bytes at a time
        for (int i = 0; i < full.length - 1; i += 3) {
            int len = Math.min(3, full.length - 1 - i);
            parser.append(full, i, len);
