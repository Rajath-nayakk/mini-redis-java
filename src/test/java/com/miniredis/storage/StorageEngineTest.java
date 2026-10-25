package com.miniredis.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class StorageEngineTest {

    private StorageEngine engine;
    private MutableClock testClock;

    static class MutableClock extends Clock {
        private Instant currentInstant = Instant.parse("2026-10-25T10:00:00Z");

        public void advanceSeconds(long seconds) {
            currentInstant = currentInstant.plusSeconds(seconds);
        }

        public void advanceMillis(long millis) {
            currentInstant = currentInstant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    @BeforeEach
    void setUp() {
        testClock = new MutableClock();
        engine = new ConcurrentStorageEngine(testClock);
    }

    @Test
    void testBasicGetSet() {
        assertNull(engine.get("missingKey"), "Missing key should return null");
        engine.set("foo", "bar");
        assertEquals("bar", engine.get("foo"));
        engine.set("foo", "baz");
        assertEquals("baz", engine.get("foo"));
    }

    @Test
    void testGetSet() {
        assertNull(engine.getSet("counter", "100"), "getSet on missing key returns null");
        assertEquals("100", engine.get("counter"));
        assertEquals("100", engine.getSet("counter", "200"), "getSet returns previous value");
        assertEquals("200", engine.get("counter"));
    }

    @Test
    void testAppendAndStrlen() {
        assertEquals(0, engine.strlen("missing"));
        assertEquals(5, engine.append("msg", "Hello"));
        assertEquals("Hello", engine.get("msg"));
        assertEquals(11, engine.append("msg", " World"));
        assertEquals("Hello World", engine.get("msg"));
        assertEquals(11, engine.strlen("msg"));
    }

    @Test
    void testIncrAndDecr() {
        assertEquals(1L, engine.incr("counter"), "Incr on missing key initializes to 1");
        assertEquals(2L, engine.incr("counter"));
        assertEquals(5L, engine.incrBy("counter", 3L));
        assertEquals(4L, engine.decr("counter"));
        assertEquals(0L, engine.decrBy("counter", 4L));
        assertEquals(-5L, engine.decrBy("counter", 5L));
    }

    @Test
    void testAtomicConcurrentIncrements() throws InterruptedException {
        int threads = 50;
        int incrementsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        engine.incr("concurrent_counter");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent increments timed out");
        executor.shutdown();

        assertEquals(String.valueOf(threads * incrementsPerThread), engine.get("concurrent_counter"),
                "Final value must accurately match total increments without race conditions");
    }

    @Test
    void testInvalidIntegerAndOverflow() {
        engine.set("notanumber", "hello");
        assertThrows(InvalidIntegerException.class, () -> engine.incr("notanumber"));

        engine.set("maxLong", String.valueOf(Long.MAX_VALUE));
        assertThrows(NumericOverflowException.class, () -> engine.incr("maxLong"));

        engine.set("minLong", String.valueOf(Long.MIN_VALUE));
        assertThrows(NumericOverflowException.class, () -> engine.decr("minLong"));
    }

    @Test
    void testExistsAndDel() {
        assertFalse(engine.exists("key1"));
        engine.set("key1", "val1");
        engine.set("key2", "val2");
        assertTrue(engine.exists("key1"));
        assertEquals(2, engine.exists(List.of("key1", "key2", "key3")));

        assertTrue(engine.del("key1"));
        assertFalse(engine.exists("key1"));
        assertFalse(engine.del("key1"), "Second del should return false");

        assertEquals(1, engine.del(List.of("key2", "missing")));
    }

    @Test
    void testMgetAndMset() {
        engine.mset(Map.of("k1", "v1", "k2", "v2"));
        List<String> results = engine.mget(List.of("k1", "k2", "k3"));
        assertEquals(List.of("v1", "v2"), results.subList(0, 2));
        assertNull(results.get(2));
    }

    @Test
    void testKeysPatternMatching() {
        engine.set("user:1:profile", "p1");
        engine.set("user:2:profile", "p2");
        engine.set("user:1:settings", "s1");
        engine.set("order:1", "o1");

        Set<String> userKeys = engine.keys("user:*");
        assertEquals(3, userKeys.size());
        assertTrue(userKeys.contains("user:1:profile"));
        assertTrue(userKeys.contains("user:2:profile"));
        assertTrue(userKeys.contains("user:1:settings"));

        Set<String> allKeys = engine.keys("*");
        assertEquals(4, allKeys.size());
    }

    @Test
    void testFlushDbAndDbsize() {
        assertEquals(0, engine.dbsize());
        engine.set("k1", "v1");
        engine.set("k2", "v2");
        assertEquals(2, engine.dbsize());
        engine.flushDb();
        assertEquals(0, engine.dbsize());
        assertNull(engine.get("k1"));
    }

    // ==========================================
    // TTL & Expiration Tests
    // ==========================================

    @Test
    void testTtlAndExpirationWithControllableClock() {
        engine.set("session", "abc");
        assertEquals(-1, engine.ttl("session"), "Key without TTL should return -1");
        assertEquals(-1, engine.pttl("session"));

        assertTrue(engine.expire("session", 10), "Expire should return true on existing key");
        assertEquals(10, engine.ttl("session"));
        assertTrue(engine.pttl("session") <= 10000 && engine.pttl("session") > 0);

        // Advance clock by 5 seconds
        testClock.advanceSeconds(5);
        assertEquals(5, engine.ttl("session"));
        assertEquals("abc", engine.get("session"));

        // Advance clock by 6 seconds (total 11s elapsed -> expired)
        testClock.advanceSeconds(6);
        assertEquals(-2, engine.ttl("session"), "Expired key returns -2");
        assertNull(engine.get("session"), "Expired key should not be accessible");
        assertFalse(engine.exists("session"));
        assertEquals(0, engine.dbsize());
    }

    @Test
    void testImmediateExpirationForNonPositiveValues() {
        engine.set("temp", "val");
        assertTrue(engine.expire("temp", 0));
        assertNull(engine.get("temp"), "Zero seconds should immediately expire");

        engine.set("temp2", "val2");
        assertTrue(engine.pexpire("temp2", -100));
        assertNull(engine.get("temp2"), "Negative ms should immediately expire");
    }

    @Test
    void testPersistAndSetClearsTtl() {
        engine.setex("key", 60, "value");
        assertTrue(engine.ttl("key") > 0);

        assertTrue(engine.persist("key"), "Persist should remove expiration");
        assertEquals(-1, engine.ttl("key"));

        // Ordinary SET should also remove existing expiration
        engine.expire("key", 30);
        assertTrue(engine.ttl("key") > 0);
        engine.set("key", "newvalue");
        assertEquals(-1, engine.ttl("key"), "Standard SET must clear previous TTL");
    }

    @Test
    void testActiveCleanerPurgesExpiredKeys() {
        engine.setex("k1", 1, "v1");
        engine.setex("k2", 1, "v2");
        engine.set("permanent", "v3");

        testClock.advanceSeconds(2);
        int cleaned = engine.cleanupExpiredKeys(10);
        assertEquals(2, cleaned);
        assertEquals(1, engine.dbsize());
        assertTrue(engine.exists("permanent"));
    }

    // ==========================================
    // List Tests
    // ==========================================

    @Test
    void testListOperations() {
        assertEquals(3, engine.rpush("list", List.of("a", "b", "c")));
        assertEquals(3, engine.llen("list"));
        assertEquals(RedisType.LIST, engine.type("list"));

        assertEquals(List.of("a", "b", "c"), engine.lrange("list", 0, -1));
        assertEquals(List.of("b", "c"), engine.lrange("list", 1, 2));

        assertEquals("a", engine.lpop("list"));
        assertEquals("c", engine.rpop("list"));
        assertEquals(1, engine.llen("list"));
        assertEquals("b", engine.lindex("list", 0));

        engine.lset("list", 0, "updated");
        assertEquals("updated", engine.lpop("list"));
        assertNull(engine.lpop("list"), "Popping last item removes list");
        assertFalse(engine.exists("list"));
    }

    // ==========================================
    // Set Tests
    // ==========================================

    @Test
    void testSetOperations() {
        assertEquals(3, engine.sadd("s1", List.of("apple", "banana", "cherry")));
        assertEquals(0, engine.sadd("s1", List.of("apple"))); // Duplicate
        assertEquals(3, engine.scard("s1"));
        assertTrue(engine.sismember("s1", "banana"));
        assertFalse(engine.sismember("s1", "grape"));

        engine.sadd("s2", List.of("banana", "cherry", "date"));
        Set<String> inter = engine.sinter(List.of("s1", "s2"));
        assertEquals(Set.of("banana", "cherry"), inter);

        Set<String> union = engine.sunion(List.of("s1", "s2"));
        assertEquals(Set.of("apple", "banana", "cherry", "date"), union);

        Set<String> diff = engine.sdiff(List.of("s1", "s2"));
        assertEquals(Set.of("apple"), diff);

        assertEquals(1, engine.srem("s1", List.of("apple")));
        assertEquals(2, engine.scard("s1"));
    }

    // ==========================================
    // Hash Tests
    // ==========================================

    @Test
    void testHashOperations() {
        assertEquals(2, engine.hset("user:100", Map.of("name", "Alice", "age", "30")));
        assertEquals("Alice", engine.hget("user:100", "name"));
        assertTrue(engine.hexists("user:100", "age"));
        assertEquals(2, engine.hlen("user:100"));

        assertEquals(31, engine.hincrby("user:100", "age", 1));
        assertEquals("31", engine.hget("user:100", "age"));

        assertEquals(Map.of("name", "Alice", "age", "31"), engine.hgetall("user:100"));
        assertEquals(Set.of("name", "age"), engine.hkeys("user:100"));

        assertEquals(2, engine.hdel("user:100", List.of("name", "age")));
        assertFalse(engine.exists("user:100"), "Deleting last field removes key");
    }

    @Test
    void testWrongTypeExceptionAcrossDataStructures() {
        engine.set("str", "hello");
        assertThrows(WrongTypeException.class, () -> engine.lpush("str", List.of("x")));
        assertThrows(WrongTypeException.class, () -> engine.sadd("str", List.of("x")));
        assertThrows(WrongTypeException.class, () -> engine.hset("str", Map.of("k", "v")));
        assertThrows(WrongTypeException.class, () -> engine.llen("str"));
    }
}
