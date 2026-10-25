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
