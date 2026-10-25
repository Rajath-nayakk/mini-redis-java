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
