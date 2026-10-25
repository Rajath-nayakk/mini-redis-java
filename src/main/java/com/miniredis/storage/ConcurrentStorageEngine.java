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
        if (!(val instanceof StringValue stringVal)) {
            throw new WrongTypeException();
        }
        return stringVal.getValue();
    }

    @Override
    public String getSet(String key, String value) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        StringValue newVal = new StringValue(value);
        RedisValue oldVal = store.put(key, newVal);
        if (oldVal == null) {
            return null;
        }
        if (!(oldVal instanceof StringValue stringOldVal)) {
            store.put(key, oldVal);
            throw new WrongTypeException();
        }
        return stringOldVal.getValue();
    }

    @Override
    public int append(String key, String value) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        RedisValue updated = store.compute(key, (k, old) -> {
            if (old == null) {
                return new StringValue(value);
            }
            if (!(old instanceof StringValue strOld)) {
                throw new WrongTypeException();
            }
            return new StringValue(strOld.getValue() + value);
        });
        return ((StringValue) updated).length();
    }

    @Override
    public int strlen(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisValue val = store.get(key);
        if (val == null) {
            return 0;
        }
        if (!(val instanceof StringValue stringVal)) {
            throw new WrongTypeException();
        }
        return stringVal.length();
    }

    @Override
    public long incr(String key) {
        return incrBy(key, 1L);
    }

    @Override
    public long incrBy(String key, long delta) {
        Objects.requireNonNull(key, "Key cannot be null");
        final long[] resultHolder = new long[1];
        store.compute(key, (k, old) -> {
            long current = 0L;
            if (old != null) {
                if (!(old instanceof StringValue stringVal)) {
                    throw new WrongTypeException();
                }
                try {
                    current = Long.parseLong(stringVal.getValue());
                } catch (NumberFormatException e) {
                    throw new InvalidIntegerException();
                }
            }
            try {
                long newVal = Math.addExact(current, delta);
                resultHolder[0] = newVal;
                return new StringValue(String.valueOf(newVal));
            } catch (ArithmeticException e) {
                throw new NumericOverflowException();
            }
        });
        return resultHolder[0];
    }

    @Override
    public long decr(String key) {
        return decrBy(key, 1L);
    }

    @Override
    public long decrBy(String key, long delta) {
