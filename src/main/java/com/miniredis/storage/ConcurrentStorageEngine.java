package com.miniredis.storage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Thread-safe, non-blocking in-memory storage engine backed by ConcurrentHashMap.
 */
public class ConcurrentStorageEngine implements StorageEngine {

    private final ConcurrentHashMap<String, RedisValue> store = new ConcurrentHashMap<>();

    @Override
    public void set(String key, String value) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        store.put(key, new StringValue(value));
    }

    @Override
    public String get(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisValue val = store.get(key);
        if (val == null) {
            return null;
        }
