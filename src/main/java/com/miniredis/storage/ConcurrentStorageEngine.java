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
