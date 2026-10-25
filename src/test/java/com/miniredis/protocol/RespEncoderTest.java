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
