# MiniRedis Performance Benchmarks

Conducted on Java 21 LTS (Eclipse Adoptium OpenJDK 64-Bit Server VM, Windows 11).

## 1. Single-Threaded Storage Operations
Measured over 100,000 operations following a 10,000 iteration JIT warmup:

| Operation | Throughput | Total Time (100k ops) |
|---|---|---|
| **`GET`** | **4,280,437 ops/sec** | 23 ms |
| **`INCR`** | **1,601,968 ops/sec** | 62 ms |
| **`SET`** | **1,163,169 ops/sec** | 85 ms |
| **`HSET`** | **635,309 ops/sec** | 157 ms |

## 2. RESP2 Protocol Serialization & Deserialization
Measured on composite command arrays (`SET user:100:profile {"name":"Alice","active":true}`):

| Component | Throughput | Total Time (100k ops) |
|---|---|---|
| **RESP2 Encoder** | **674,728 ops/sec** | 148 ms |
| **RESP2 Parser** | **581,296 ops/sec** | 172 ms |

## 3. Concurrent Multi-Threaded Scalability & Latency Profile
Measured over 100,000 concurrent operations across 4 worker threads:

- **Aggregated Throughput**: **1,354,324 ops/sec**
- **Latency p50**: **1,000 ns** (1.0 μs)
- **Latency p95**: **2,200 ns** (2.2 μs)
- **Latency p99**: **3,600 ns** (3.6 μs)

## How to Reproduce
Run the benchmark harness directly via Java:
```powershell
.\mvnw.cmd test-compile
java -cp "target/classes;target/test-classes" com.miniredis.benchmark.BenchmarkRunner
```
