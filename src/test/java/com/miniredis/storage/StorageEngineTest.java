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
