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
        if (delta == Long.MIN_VALUE) {
            throw new NumericOverflowException();
        }
        return incrBy(key, -delta);
    }

    @Override
    public boolean exists(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        return store.containsKey(key);
    }

    @Override
    public int exists(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String key : keys) {
            if (key != null && store.containsKey(key)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean del(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        return store.remove(key) != null;
    }

    @Override
    public int del(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String key : keys) {
            if (key != null && store.remove(key) != null) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<String> mget(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            RedisValue val = (key != null) ? store.get(key) : null;
            if (val instanceof StringValue stringVal) {
                result.add(stringVal.getValue());
            } else {
                result.add(null);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void mset(Map<String, String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public RedisType type(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisValue val = store.get(key);
        if (val == null) {
            return RedisType.NONE;
        }
        return val.getType();
    }

    @Override
    public Set<String> keys(String globPattern) {
        if (globPattern == null || globPattern.isEmpty()) {
            return Collections.emptySet();
        }
        Pattern pattern = globToRegex(globPattern);
        Set<String> matched = new HashSet<>();
        for (String key : store.keySet()) {
            if (pattern.matcher(key).matches()) {
                matched.add(key);
            }
        }
        return Collections.unmodifiableSet(matched);
    }

    @Override
    public long dbsize() {
        return store.size();
    }

    @Override
    public void flushDb() {
        store.clear();
    }

    @Override
