# MiniRedis Java

MiniRedis is an educational, production-grade in-memory database built from scratch in Java 21 LTS implementing the Redis Serialization Protocol (RESP2), multithreaded TCP networking, rich data structures, key expiration (TTL), Append-Only File (AOF) persistence, transactions, and security hardening.

> **Disclaimer:** MiniRedis is an educational project created for systems programming study and is not affiliated with or endorsed by Redis Ltd.

---

## Architectural Highlights
- **100% Native Java 21**: No external database wrappers, no Netty or Spring dependencies — built entirely on core Java concurrency and standard networking APIs (`java.net.ServerSocket`, `java.nio`, `ConcurrentHashMap`, virtual/daemon thread pools).
- **Binary-Safe RESP2 Protocol**: Full streaming protocol parser supporting chunked inputs, pipelining, and all RESP data types.
- **Rich Data Structures**: Complete support for Strings, Lists, Sets, and Hashes with Redis-compatible semantics and `-WRONGTYPE` guards.
- **Deterministic TTL Engine**: Passive expiration on access combined with active background cleaner and an injectable `Clock` abstraction for testability.
- **Append-Only File (AOF) Persistence**: Binary-safe durability with configurable sync policies (`always`, `everysec`, `no`), crash-recovery replay, and atomic compaction rewrite.
- **ACID-Inspired Transactions**: Connection-isolated `MULTI`, `EXEC`, and `DISCARD` queuing with contiguous atomic execution.
- **Production Hardening**: Optional `AUTH` password protection using constant-time verification, request bounds, and connection throttling.

---

## Supported Commands

| Category | Commands |
|---|---|
| **Server & Connection** | `PING`, `ECHO`, `AUTH`, `INFO`, `DBSIZE`, `FLUSHDB`, `TYPE`, `KEYS` |
| **Strings & Numerics** | `SET`, `GET`, `GETSET`, `APPEND`, `STRLEN`, `INCR`, `INCRBY`, `DECR`, `DECRBY`, `MGET`, `MSET` |
| **Key Expiration (TTL)** | `EXPIRE`, `PEXPIRE`, `EXPIREAT`, `PEXPIREAT`, `TTL`, `PTTL`, `PERSIST`, `SETEX`, `PSETEX` |
| **Lists** | `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LLEN`, `LRANGE`, `LINDEX`, `LSET`, `LTRIM` |
| **Sets** | `SADD`, `SREM`, `SISMEMBER`, `SMEMBERS`, `SCARD`, `SINTER`, `SUNION`, `SDIFF` |
| **Hashes** | `HSET`, `HGET`, `HMGET`, `HDEL`, `HEXISTS`, `HGETALL`, `HKEYS`, `HVALS`, `HLEN`, `HINCRBY` |
| **Transactions** | `MULTI`, `EXEC`, `DISCARD` |

---

## Quick Start

### Prerequisites
- **JDK**: Java 21 LTS (e.g. Eclipse Temurin 21)
- **Maven**: Maven 3.8+ (or included Maven Wrapper `mvnw` / `mvnw.cmd`)

### Build and Test
```powershell
.\mvnw.cmd clean verify
```

### Start the Server
```powershell
# Default port 6379, loopback 127.0.0.1
.\mvnw.cmd exec:java -Dexec.mainClass="com.miniredis.Main"

# Or run the built JAR directly
java -jar target/mini-redis-java-1.0.0-SNAPSHOT.jar --port 6379 --appendonly true
```

### Connect with Redis CLI
```bash
redis-cli -h 127.0.0.1 -p 6379
127.0.0.1:6379> PING
PONG
127.0.0.1:6379> SET user:100 "Alice"
OK
127.0.0.1:6379> GET user:100
"Alice"
127.0.0.1:6379> EXPIRE user:100 60
(integer) 1
127.0.0.1:6379> TTL user:100
(integer) 59
```

---

## Documentation Links
- [Architecture & Concurrency Guide](docs/ARCHITECTURE.md)
- [Configuration Reference](docs/CONFIGURATION.md)
- [AOF Persistence & Recovery](docs/PERSISTENCE.md)
- [RESP2 Protocol Specification](docs/RESP_PROTOCOL.md)
- [Security & Hardening Policy](docs/SECURITY.md)
- [Development Changelog](CHANGELOG.md)

---

## License
Distributed under the [MIT License](LICENSE).
