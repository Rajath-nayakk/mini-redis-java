package com.miniredis.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class StorageEngineTest {

    private StorageEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ConcurrentStorageEngine();
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
                        engine.incr("concurrent-counter");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(completed, "All increment threads should finish within timeout");

        long expected = (long) threads * incrementsPerThread;
        assertEquals(String.valueOf(expected), engine.get("concurrent-counter"),
                "Final counter value must equal total increments with zero lost updates");
    }

    @Test
    void testInvalidInteger() {
        engine.set("nan", "not-a-number");
        assertThrows(InvalidIntegerException.class, () -> engine.incr("nan"));
        assertThrows(InvalidIntegerException.class, () -> engine.incrBy("nan", 10));
        assertThrows(InvalidIntegerException.class, () -> engine.decr("nan"));
    }

    @Test
    void testIntegerOverflow() {
        engine.set("overflowKey", String.valueOf(Long.MAX_VALUE));
        assertThrows(NumericOverflowException.class, () -> engine.incr("overflowKey"));
        assertThrows(NumericOverflowException.class, () -> engine.incrBy("overflowKey", 10));
    }

    @Test
    void testIntegerUnderflow() {
        engine.set("underflowKey", String.valueOf(Long.MIN_VALUE));
        assertThrows(NumericOverflowException.class, () -> engine.decr("underflowKey"));
        assertThrows(NumericOverflowException.class, () -> engine.decrBy("underflowKey", 10));
    }

    @Test
    void testExistsAndDel() {
        assertFalse(engine.exists("k1"));
        assertEquals(0, engine.exists(List.of("k1", "k2")));

        engine.set("k1", "v1");
        engine.set("k2", "v2");
        assertTrue(engine.exists("k1"));
        assertEquals(2, engine.exists(List.of("k1", "k2", "k3")));

        assertTrue(engine.del("k1"));
        assertFalse(engine.del("k1"));
        assertEquals(1, engine.del(List.of("k2", "k3")));
    }

