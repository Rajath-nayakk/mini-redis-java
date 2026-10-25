package com.miniredis.storage;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Thread-safe in-memory storage engine backed by ConcurrentHashMap,
 * supporting Strings, Lists, Sets, Hashes, and TTL key expiration.
 */
public class ConcurrentStorageEngine implements StorageEngine {

    private final ConcurrentHashMap<String, RedisValue> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> expires = new ConcurrentHashMap<>();
    private volatile Clock clock;

    public ConcurrentStorageEngine() {
        this(Clock.systemUTC());
    }

    public ConcurrentStorageEngine(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    }

    @Override
    public void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    // ==========================================
    // Expiration Internal Helpers
    // ==========================================

    private boolean isExpired(String key) {
        Long expireAt = expires.get(key);
        if (expireAt == null) {
            return false;
        }
        if (clock.millis() >= expireAt) {
            store.remove(key);
            expires.remove(key);
            return true;
        }
        return false;
    }

    private void checkExpiration(String key) {
        isExpired(key);
    }

    // ==========================================
    // String Operations
    // ==========================================

    @Override
    public void set(String key, String value) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        store.put(key, new StringValue(value));
        expires.remove(key);
    }

    @Override
    public String get(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return null;
        }
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
        if (isExpired(key)) {
            store.remove(key);
            expires.remove(key);
        }
        StringValue newVal = new StringValue(value);
        RedisValue oldVal = store.put(key, newVal);
        expires.remove(key);
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
        checkExpiration(key);
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
        if (isExpired(key)) {
            return 0;
        }
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
        return incrBy(key, 1);
    }

    @Override
    public long incrBy(String key, long delta) {
        Objects.requireNonNull(key, "Key cannot be null");
        checkExpiration(key);
        final long[] result = new long[1];
        store.compute(key, (k, old) -> {
            long currentVal = 0;
            if (old != null) {
                if (!(old instanceof StringValue stringVal)) {
                    throw new WrongTypeException();
                }
                try {
                    currentVal = Long.parseLong(stringVal.getValue());
                } catch (NumberFormatException e) {
                    throw new InvalidIntegerException("ERR value is not an integer or out of range");
                }
            }
            try {
                result[0] = Math.addExact(currentVal, delta);
            } catch (ArithmeticException e) {
                throw new NumericOverflowException("ERR increment or decrement would overflow");
            }
            return new StringValue(Long.toString(result[0]));
        });
        return result[0];
    }

    @Override
    public long decr(String key) {
        return decrBy(key, 1);
    }

    @Override
    public long decrBy(String key, long delta) {
        return incrBy(key, -delta);
    }

    @Override
    public List<String> mget(List<String> keys) {
        Objects.requireNonNull(keys, "Keys list cannot be null");
        List<String> results = new ArrayList<>(keys.size());
        for (String k : keys) {
            try {
                results.add(get(k));
            } catch (WrongTypeException e) {
                results.add(null);
            }
        }
        return results;
    }

    @Override
    public void mset(Map<String, String> entries) {
        Objects.requireNonNull(entries, "Entries cannot be null");
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
    }

    // ==========================================
    // Key Management
    // ==========================================

    @Override
    public boolean exists(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return false;
        }
        return store.containsKey(key);
    }

    @Override
    public int exists(List<String> keys) {
        Objects.requireNonNull(keys, "Keys cannot be null");
        int count = 0;
        for (String k : keys) {
            if (exists(k)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean del(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        expires.remove(key);
        return store.remove(key) != null;
    }

    @Override
    public int del(List<String> keys) {
        Objects.requireNonNull(keys, "Keys cannot be null");
        int count = 0;
        for (String k : keys) {
            if (del(k)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public RedisType type(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return RedisType.NONE;
        }
        RedisValue val = store.get(key);
        return val != null ? val.getType() : RedisType.NONE;
    }

    @Override
    public Set<String> keys(String globPattern) {
        Objects.requireNonNull(globPattern, "Pattern cannot be null");
        Pattern regex = globToRegex(globPattern);
        Set<String> matched = new HashSet<>();
        for (String key : store.keySet()) {
            if (!isExpired(key) && regex.matcher(key).matches()) {
                matched.add(key);
            }
        }
        return matched;
    }

    @Override
    public long dbsize() {
        long count = 0;
        for (String k : store.keySet()) {
            if (!isExpired(k)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void flushDb() {
        store.clear();
        expires.clear();
    }

    @Override
    public long approximateMemoryBytes() {
        long total = 64L;
        for (Map.Entry<String, RedisValue> entry : store.entrySet()) {
            if (!isExpired(entry.getKey())) {
                total += 32L + entry.getKey().length() * 2L + entry.getValue().estimatedMemoryBytes();
            }
        }
        return total;
    }

    // ==========================================
    // TTL and Expiration
    // ==========================================

    @Override
    public boolean expire(String key, long seconds) {
        if (!exists(key)) {
            return false;
        }
        if (seconds <= 0) {
            del(key);
            return true;
        }
        long ms;
        try {
            ms = Math.multiplyExact(seconds, 1000L);
        } catch (ArithmeticException e) {
            ms = Long.MAX_VALUE - clock.millis();
        }
        return pexpire(key, ms);
    }

    @Override
    public boolean pexpire(String key, long milliseconds) {
        if (!exists(key)) {
            return false;
        }
        if (milliseconds <= 0) {
            del(key);
            return true;
        }
        long expireAt;
        try {
            expireAt = Math.addExact(clock.millis(), milliseconds);
        } catch (ArithmeticException e) {
            expireAt = Long.MAX_VALUE;
        }
        expires.put(key, expireAt);
        return true;
    }

    @Override
    public boolean expireAt(String key, long unixTimeSeconds) {
        if (!exists(key)) {
            return false;
        }
        long ms;
        try {
            ms = Math.multiplyExact(unixTimeSeconds, 1000L);
        } catch (ArithmeticException e) {
            ms = Long.MAX_VALUE;
        }
        return pexpireAt(key, ms);
    }

    @Override
    public boolean pexpireAt(String key, long unixTimeMilliseconds) {
        if (!exists(key)) {
            return false;
        }
        if (unixTimeMilliseconds <= clock.millis()) {
            del(key);
            return true;
        }
        expires.put(key, unixTimeMilliseconds);
        return true;
    }

    @Override
    public long ttl(String key) {
        if (!exists(key)) {
            return -2;
        }
        Long expireAt = expires.get(key);
        if (expireAt == null) {
            return -1;
        }
        long diff = expireAt - clock.millis();
        if (diff <= 0) {
            del(key);
            return -2;
        }
        return (diff + 999) / 1000;
    }

    @Override
    public long pttl(String key) {
        if (!exists(key)) {
            return -2;
        }
        Long expireAt = expires.get(key);
        if (expireAt == null) {
            return -1;
        }
        long diff = expireAt - clock.millis();
        if (diff <= 0) {
            del(key);
            return -2;
        }
        return diff;
    }

    @Override
    public boolean persist(String key) {
        if (!exists(key)) {
            return false;
        }
        return expires.remove(key) != null;
    }

    @Override
    public void setex(String key, long seconds, String value) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("ERR invalid expire time in 'setex' command");
        }
        set(key, value);
        expire(key, seconds);
    }

    @Override
    public void psetex(String key, long milliseconds, String value) {
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("ERR invalid expire time in 'psetex' command");
        }
        set(key, value);
        pexpire(key, milliseconds);
    }

    @Override
    public int cleanupExpiredKeys(int sampleSize) {
        int cleaned = 0;
        int checked = 0;
        for (String key : expires.keySet()) {
            if (checked++ >= sampleSize) {
                break;
            }
            if (isExpired(key)) {
                cleaned++;
            }
        }
        return cleaned;
    }

    // ==========================================
    // List Operations
    // ==========================================

    @Override
    public long lpush(String key, List<String> values) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(values, "Values cannot be null");
        checkExpiration(key);
        RedisValue val = store.compute(key, (k, old) -> {
            ListValue listVal;
            if (old == null) {
                listVal = new ListValue();
            } else if (old instanceof ListValue lv) {
                listVal = lv;
            } else {
                throw new WrongTypeException();
            }
            listVal.lpush(values);
            return listVal;
        });
        return ((ListValue) val).size();
    }

    @Override
    public long rpush(String key, List<String> values) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(values, "Values cannot be null");
        checkExpiration(key);
        RedisValue val = store.compute(key, (k, old) -> {
            ListValue listVal;
            if (old == null) {
                listVal = new ListValue();
            } else if (old instanceof ListValue lv) {
                listVal = lv;
            } else {
                throw new WrongTypeException();
            }
            listVal.rpush(values);
            return listVal;
        });
        return ((ListValue) val).size();
    }

    @Override
    public String lpop(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return null;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return null;
        }
        if (!(val instanceof ListValue listVal)) {
            throw new WrongTypeException();
        }
        String popped = listVal.lpop();
        if (listVal.isEmpty()) {
            del(key);
        }
        return popped;
    }

    @Override
    public String rpop(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return null;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return null;
        }
        if (!(val instanceof ListValue listVal)) {
            throw new WrongTypeException();
        }
        String popped = listVal.rpop();
        if (listVal.isEmpty()) {
            del(key);
        }
        return popped;
    }

    @Override
    public long llen(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return 0;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return 0;
        }
        if (!(val instanceof ListValue listVal)) {
            throw new WrongTypeException();
        }
        return listVal.size();
    }

    @Override
    public List<String> lrange(String key, long start, long stop) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return Collections.emptyList();
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return Collections.emptyList();
        }
        if (!(val instanceof ListValue listVal)) {
            throw new WrongTypeException();
        }
        return listVal.range(start, stop);
    }

    @Override
    public String lindex(String key, long index) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return null;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return null;
        }
        if (!(val instanceof ListValue listVal)) {
            throw new WrongTypeException();
        }
        return listVal.get(index);
    }

    @Override
    public void lset(String key, long index, String value) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key) || !store.containsKey(key)) {
            throw new StorageException("ERR no such key");
        }
        RedisValue val = store.get(key);
        if (!(val instanceof ListValue listVal)) {
            throw new WrongTypeException();
        }
        listVal.set(index, value);
    }

    @Override
    public void ltrim(String key, long start, long stop) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return;
        }
        if (!(val instanceof ListValue listVal)) {
            throw new WrongTypeException();
        }
        listVal.trim(start, stop);
        if (listVal.isEmpty()) {
            del(key);
        }
    }

    // ==========================================
    // Set Operations
    // ==========================================

    @Override
    public long sadd(String key, List<String> members) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(members, "Members cannot be null");
        checkExpiration(key);
        final int[] added = new int[1];
        store.compute(key, (k, old) -> {
            SetValue setVal;
            if (old == null) {
                setVal = new SetValue();
            } else if (old instanceof SetValue sv) {
                setVal = sv;
            } else {
                throw new WrongTypeException();
            }
            added[0] = setVal.sadd(members);
            return setVal;
        });
        return added[0];
    }

    @Override
    public long srem(String key, List<String> members) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return 0;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return 0;
        }
        if (!(val instanceof SetValue setVal)) {
            throw new WrongTypeException();
        }
        int removed = setVal.srem(members);
        if (setVal.isEmpty()) {
            del(key);
        }
        return removed;
    }

    @Override
    public boolean sismember(String key, String member) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return false;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return false;
        }
        if (!(val instanceof SetValue setVal)) {
            throw new WrongTypeException();
        }
        return setVal.sismember(member);
    }

    @Override
    public Set<String> smembers(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return Collections.emptySet();
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return Collections.emptySet();
        }
        if (!(val instanceof SetValue setVal)) {
            throw new WrongTypeException();
        }
        return setVal.smembers();
    }

    @Override
    public long scard(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return 0;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return 0;
        }
        if (!(val instanceof SetValue setVal)) {
            throw new WrongTypeException();
        }
        return setVal.scard();
    }

    @Override
    public Set<String> sinter(List<String> keys) {
        Objects.requireNonNull(keys, "Keys cannot be null");
        if (keys.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>(smembers(keys.get(0)));
        for (int i = 1; i < keys.size(); i++) {
            result.retainAll(smembers(keys.get(i)));
        }
        return result;
    }

    @Override
    public Set<String> sunion(List<String> keys) {
        Objects.requireNonNull(keys, "Keys cannot be null");
        Set<String> result = new HashSet<>();
        for (String k : keys) {
            result.addAll(smembers(k));
        }
        return result;
    }

    @Override
    public Set<String> sdiff(List<String> keys) {
        Objects.requireNonNull(keys, "Keys cannot be null");
        if (keys.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>(smembers(keys.get(0)));
        for (int i = 1; i < keys.size(); i++) {
            result.removeAll(smembers(keys.get(i)));
        }
        return result;
    }

    // ==========================================
    // Hash Operations
    // ==========================================

    @Override
    public long hset(String key, Map<String, String> fieldValues) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(fieldValues, "FieldValues cannot be null");
        checkExpiration(key);
        final int[] added = new int[1];
        store.compute(key, (k, old) -> {
            HashValue hashVal;
            if (old == null) {
                hashVal = new HashValue();
            } else if (old instanceof HashValue hv) {
                hashVal = hv;
            } else {
                throw new WrongTypeException();
            }
            added[0] = hashVal.hset(fieldValues);
            return hashVal;
        });
        return added[0];
    }

    @Override
    public String hget(String key, String field) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return null;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return null;
        }
        if (!(val instanceof HashValue hashVal)) {
            throw new WrongTypeException();
        }
        return hashVal.hget(field);
    }

    @Override
    public List<String> hmget(String key, List<String> fields) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return Collections.nCopies(fields.size(), null);
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return Collections.nCopies(fields.size(), null);
        }
        if (!(val instanceof HashValue hashVal)) {
            throw new WrongTypeException();
        }
        return hashVal.hmget(fields);
    }

    @Override
    public long hdel(String key, List<String> fields) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return 0;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return 0;
        }
        if (!(val instanceof HashValue hashVal)) {
            throw new WrongTypeException();
        }
        int removed = hashVal.hdel(fields);
        if (hashVal.isEmpty()) {
            del(key);
        }
        return removed;
    }

    @Override
    public boolean hexists(String key, String field) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return false;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return false;
        }
        if (!(val instanceof HashValue hashVal)) {
            throw new WrongTypeException();
        }
        return hashVal.hexists(field);
    }

    @Override
    public Map<String, String> hgetall(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return Collections.emptyMap();
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return Collections.emptyMap();
        }
        if (!(val instanceof HashValue hashVal)) {
            throw new WrongTypeException();
        }
        return hashVal.hgetall();
    }

    @Override
    public Set<String> hkeys(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return Collections.emptySet();
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return Collections.emptySet();
        }
        if (!(val instanceof HashValue hashVal)) {
            throw new WrongTypeException();
        }
        return hashVal.hkeys();
    }

    @Override
    public List<String> hvals(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return Collections.emptyList();
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return Collections.emptyList();
        }
        if (!(val instanceof HashValue hashVal)) {
            throw new WrongTypeException();
        }
        return hashVal.hvals();
    }

    @Override
    public long hlen(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return 0;
        }
        RedisValue val = store.get(key);
        if (val == null) {
            return 0;
        }
        if (!(val instanceof HashValue hashVal)) {
            throw new WrongTypeException();
        }
        return hashVal.hlen();
    }

    @Override
    public long hincrby(String key, String field, long increment) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(field, "Field cannot be null");
        checkExpiration(key);
        final long[] result = new long[1];
        store.compute(key, (k, old) -> {
            HashValue hashVal;
            if (old == null) {
                hashVal = new HashValue();
            } else if (old instanceof HashValue hv) {
                hashVal = hv;
            } else {
                throw new WrongTypeException();
            }
            result[0] = hashVal.hincrby(field, increment);
            return hashVal;
        });
        return result[0];
    }

    private Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '[' -> sb.append("[");
                case ']' -> sb.append("]");
                case '\\', '.', '(', ')', '+', '^', '$', '|' -> sb.append("\\").append(c);
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString());
    }
}
