package com.miniredis.storage;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contract for the in-memory Redis storage engine.
 */
public interface StorageEngine {

    // String operations
    void set(String key, String value);
    String get(String key);
    String getSet(String key, String value);
    int append(String key, String value);
    int strlen(String key);
    long incr(String key);
    long incrBy(String key, long delta);
    long decr(String key);
    long decrBy(String key, long delta);
    List<String> mget(List<String> keys);
    void mset(Map<String, String> entries);

    // Key management
    boolean exists(String key);
    int exists(List<String> keys);
    boolean del(String key);
    int del(List<String> keys);
    RedisType type(String key);
    Set<String> keys(String globPattern);
    long dbsize();
    void flushDb();
    long approximateMemoryBytes();

    // TTL & Expiration
    boolean expire(String key, long seconds);
    boolean pexpire(String key, long milliseconds);
    boolean expireAt(String key, long unixTimeSeconds);
    boolean pexpireAt(String key, long unixTimeMilliseconds);
    long ttl(String key);
    long pttl(String key);
    boolean persist(String key);
    void setex(String key, long seconds, String value);
    void psetex(String key, long milliseconds, String value);
    int cleanupExpiredKeys(int sampleSize);
    void setClock(Clock clock);
    Clock getClock();

    // List operations
    long lpush(String key, List<String> values);
    long rpush(String key, List<String> values);
    String lpop(String key);
    String rpop(String key);
    long llen(String key);
    List<String> lrange(String key, long start, long stop);
    String lindex(String key, long index);
    void lset(String key, long index, String value);
    void ltrim(String key, long start, long stop);

    // Set operations
    long sadd(String key, List<String> members);
    long srem(String key, List<String> members);
    boolean sismember(String key, String member);
    Set<String> smembers(String key);
    long scard(String key);
    Set<String> sinter(List<String> keys);
    Set<String> sunion(List<String> keys);
    Set<String> sdiff(List<String> keys);

    // Hash operations
    long hset(String key, Map<String, String> fieldValues);
    String hget(String key, String field);
    List<String> hmget(String key, List<String> fields);
    long hdel(String key, List<String> fields);
    boolean hexists(String key, String field);
    Map<String, String> hgetall(String key);
    Set<String> hkeys(String key);
    List<String> hvals(String key);
    long hlen(String key);
    long hincrby(String key, String field, long increment);
}
