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
