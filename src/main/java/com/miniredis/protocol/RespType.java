package com.miniredis.protocol;

/**
 * Supported RESP2 data types.
 */
public enum RespType {
    SIMPLE_STRING, // +
    ERROR,         // -
    INTEGER,       // :
    BULK_STRING,   // $
    ARRAY          // *
