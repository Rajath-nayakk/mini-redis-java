package com.miniredis.storage;

/**
 * Represents Redis data types supported by the storage engine.
 */
public enum RedisType {
    STRING("string"),
    LIST("list"),
    SET("set"),
    HASH("hash"),
    ZSET("zset"),
    NONE("none");

    private final String code;

    RedisType(String code) {
        this.code = code;
    }
