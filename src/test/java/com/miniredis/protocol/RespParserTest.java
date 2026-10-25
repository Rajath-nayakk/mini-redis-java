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

