package com.miniredis.storage;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents a Redis Hash value type.
 * Thread-safety is guaranteed through synchronized methods.
 */
public final class HashValue implements RedisValue {

    private final Map<String, String> fields;

    public HashValue() {
        this.fields = new LinkedHashMap<>();
    }

    public HashValue(Map<String, String> initialFields) {
        this.fields = new LinkedHashMap<>(initialFields);
    }

    public synchronized int hset(Map<String, String> entries) {
        int newlyAdded = 0;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!fields.containsKey(entry.getKey())) {
                newlyAdded++;
            }
            fields.put(entry.getKey(), entry.getValue());
        }
        return newlyAdded;
    }

    public synchronized String hget(String field) {
        return fields.get(field);
    }

    public synchronized List<String> hmget(List<String> fieldList) {
        List<String> result = new ArrayList<>(fieldList.size());
        for (String f : fieldList) {
            result.add(fields.get(f));
        }
        return result;
    }

    public synchronized int hdel(List<String> fieldList) {
        int removed = 0;
        for (String f : fieldList) {
            if (fields.remove(f) != null) {
                removed++;
            }
        }
        return removed;
    }

    public synchronized boolean hexists(String field) {
        return fields.containsKey(field);
    }

    public synchronized Map<String, String> hgetall() {
        return new LinkedHashMap<>(fields);
    }

    public synchronized Set<String> hkeys() {
        return new LinkedHashSet<>(fields.keySet());
    }

    public synchronized List<String> hvals() {
        return new ArrayList<>(fields.values());
    }

    public synchronized int hlen() {
        return fields.size();
    }

    public synchronized boolean isEmpty() {
        return fields.isEmpty();
    }

    public synchronized long hincrby(String field, long delta) {
        String current = fields.get(field);
        long currentVal = 0;
        if (current != null) {
            try {
                currentVal = Long.parseLong(current);
            } catch (NumberFormatException e) {
                throw new InvalidIntegerException("ERR hash value is not an integer");
            }
        }

        try {
            long result = Math.addExact(currentVal, delta);
            fields.put(field, Long.toString(result));
            return result;
        } catch (ArithmeticException e) {
            throw new NumericOverflowException("ERR increment or decrement would overflow");
        }
    }

    @Override
    public RedisType getType() {
        return RedisType.HASH;
    }

    @Override
    public synchronized long estimatedMemoryBytes() {
        long bytes = 32L;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            bytes += 32L
                    + entry.getKey().getBytes(StandardCharsets.UTF_8).length
                    + entry.getValue().getBytes(StandardCharsets.UTF_8).length;
        }
        return bytes;
    }
}
