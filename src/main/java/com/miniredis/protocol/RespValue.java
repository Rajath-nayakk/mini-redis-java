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
