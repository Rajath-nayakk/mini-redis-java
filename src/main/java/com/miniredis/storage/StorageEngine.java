package com.miniredis.storage;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contract for the in-memory Redis storage engine.
 */
public interface StorageEngine {
    void set(String key, String value);
    String get(String key);
    String getSet(String key, String value);
    int append(String key, String value);
    int strlen(String key);
    long incr(String key);
    long incrBy(String key, long delta);
    long decr(String key);
    long decrBy(String key, long delta);
    boolean exists(String key);
    int exists(List<String> keys);
    boolean del(String key);
    int del(List<String> keys);
    List<String> mget(List<String> keys);
    void mset(Map<String, String> entries);
    RedisType type(String key);
    Set<String> keys(String globPattern);
    long dbsize();
