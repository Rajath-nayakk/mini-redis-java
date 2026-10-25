package com.miniredis.benchmark;

import com.miniredis.protocol.RespEncoder;
import com.miniredis.protocol.RespParser;
import com.miniredis.protocol.RespValue;
import com.miniredis.storage.ConcurrentStorageEngine;
import com.miniredis.storage.StorageEngine;

import java.util.*;
import java.util.concurrent.*;

/**
 * Repeatable benchmark harness for MiniRedis measuring storage engine throughput,
 * protocol serialization/deserialization rates, and p50/p95/p99 latency profiles.
 */
public class BenchmarkRunner {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  MiniRedis Java - Comprehensive Benchmark Suite  ");
        System.out.println("=================================================");

        runStorageBenchmarks();
        runProtocolBenchmarks();
        runConcurrentThroughputBenchmark(4, 100_000);
    }

    public static void runStorageBenchmarks() {
        StorageEngine engine = new ConcurrentStorageEngine();
        int iterations = 100_000;

        // Warmup
        for (int i = 0; i < 10_000; i++) {
            engine.set("warmup:" + i, "val");
            engine.get("warmup:" + i);
        }

        // Benchmark SET
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            engine.set("key:" + i, "benchmark_value_" + i);
        }
        long durationNs = System.nanoTime() - start;
        double setOpsPerSec = (iterations * 1_000_000_000.0) / durationNs;
        System.out.printf("SET throughput:   %,12.0f ops/sec (total time: %d ms)%n", setOpsPerSec, durationNs / 1_000_000);

        // Benchmark GET
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            engine.get("key:" + i);
        }
        durationNs = System.nanoTime() - start;
        double getOpsPerSec = (iterations * 1_000_000_000.0) / durationNs;
        System.out.printf("GET throughput:   %,12.0f ops/sec (total time: %d ms)%n", getOpsPerSec, durationNs / 1_000_000);

        // Benchmark INCR
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            engine.incr("counter");
        }
        durationNs = System.nanoTime() - start;
        double incrOpsPerSec = (iterations * 1_000_000_000.0) / durationNs;
        System.out.printf("INCR throughput:  %,12.0f ops/sec (total time: %d ms)%n", incrOpsPerSec, durationNs / 1_000_000);

        // Benchmark HSET / HGET
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            engine.hset("hash:1", Map.of("field:" + i, "val"));
        }
        durationNs = System.nanoTime() - start;
        double hsetOpsPerSec = (iterations * 1_000_000_000.0) / durationNs;
        System.out.printf("HSET throughput:  %,12.0f ops/sec (total time: %d ms)%n", hsetOpsPerSec, durationNs / 1_000_000);
    }

    public static void runProtocolBenchmarks() {
        int iterations = 100_000;
        RespValue sample = RespValue.array(List.of(
                RespValue.bulkString("SET"),
                RespValue.bulkString("user:100:profile"),
                RespValue.bulkString("{\"name\":\"Alice\",\"active\":true}")
        ));

        // Encoder benchmark
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            RespEncoder.encode(sample);
        }
        long durationNs = System.nanoTime() - start;
        double encOpsPerSec = (iterations * 1_000_000_000.0) / durationNs;
        System.out.printf("RESP Encoder:     %,12.0f ops/sec (total time: %d ms)%n", encOpsPerSec, durationNs / 1_000_000);

        // Parser benchmark
        byte[] bytes = RespEncoder.encode(sample);
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            RespParser parser = new RespParser();
            parser.append(bytes, 0, bytes.length);
            try {
                parser.parseNext();
            } catch (Exception ignored) {
            }
        }
        durationNs = System.nanoTime() - start;
        double parseOpsPerSec = (iterations * 1_000_000_000.0) / durationNs;
        System.out.printf("RESP Parser:      %,12.0f ops/sec (total time: %d ms)%n", parseOpsPerSec, durationNs / 1_000_000);
    }

    public static void runConcurrentThroughputBenchmark(int threads, int totalOps) {
        StorageEngine engine = new ConcurrentStorageEngine();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int opsPerThread = totalOps / threads;
        long[] latencies = new long[totalOps];

        long start = System.nanoTime();
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                int baseIdx = threadId * opsPerThread;
                for (int i = 0; i < opsPerThread; i++) {
                    long t0 = System.nanoTime();
                    engine.set("tkey:" + (baseIdx + i), "v");
                    engine.get("tkey:" + (baseIdx + i));
                    latencies[baseIdx + i] = System.nanoTime() - t0;
                }
                latch.countDown();
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdown();

        long totalTimeNs = System.nanoTime() - start;
        double throughput = (totalOps * 1_000_000_000.0) / totalTimeNs;

        Arrays.sort(latencies);
        long p50Ns = latencies[(int) (totalOps * 0.50)];
        long p95Ns = latencies[(int) (totalOps * 0.95)];
        long p99Ns = latencies[(int) (totalOps * 0.99)];

        System.out.printf("Concurrent (%d threads): %,12.0f ops/sec%n", threads, throughput);
        System.out.printf("Latency p50: %,d ns | p95: %,d ns | p99: %,d ns%n", p50Ns, p95Ns, p99Ns);
    }
}
