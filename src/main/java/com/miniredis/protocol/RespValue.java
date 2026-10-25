package com.miniredis.protocol;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents a typed RESP2 protocol value.
 */
public interface RespValue {

    RespType getType();

    static RespSimpleString ok() {
        return new RespSimpleString("OK");
    }

    static RespSimpleString pong() {
        return new RespSimpleString("PONG");
    }

    static RespSimpleString simpleString(String value) {
        return new RespSimpleString(value);
    }

    static RespError error(String message) {
        return new RespError(message);
    }

    static RespInteger integer(long value) {
