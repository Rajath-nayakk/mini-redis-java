package com.miniredis.storage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Represents a binary-safe Redis string value.
 */
public final class StringValue implements RedisValue {

    private final String value;
    private final byte[] bytes;

    public StringValue(String value) {
        this.value = Objects.requireNonNull(value, "Value cannot be null");
        this.bytes = value.getBytes(StandardCharsets.UTF_8);
    }

    public String getValue() {
        return value;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int length() {
        return bytes.length;
    }

    @Override
    public RedisType getType() {
        return RedisType.STRING;
    }

    @Override
    public long estimatedMemoryBytes() {
        return 24L + bytes.length;
    }

    @Override
