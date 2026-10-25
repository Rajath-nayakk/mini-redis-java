# Changelog

All notable changes to MiniRedis will be documented in this file.

## [1.0.0-SNAPSHOT] - 2026-10-25

### Added
- **Phase 0 & 1**: Clean Java 21 LTS foundation, Maven Wrapper, buildable POM, `ServerConfig` builder and CLI parsing.
- **Phase 2**: High-performance thread-safe storage engine (`ConcurrentStorageEngine`), atomic numeric increments (`INCR`, `DECR`, `INCRBY`, `DECRBY`) with strict overflow detection.
- **Phase 3**: Full streaming RESP2 protocol parser (`RespParser`) and response encoder (`RespEncoder`), Command Dispatcher, and protocol specs.
- **Phase 4**: Multi-threaded TCP server (`MiniRedisServer`) and per-connection worker sessions (`ClientSession`) with connection limits.
- **Phase 5**: TTL & Key Expiration engine supporting `EXPIRE`, `PEXPIRE`, `EXPIREAT`, `PEXPIREAT`, `TTL`, `PTTL`, `PERSIST`, `SETEX`, `PSETEX`, passive lazy expiration, and active background sampling sweeper with injectable `Clock`.
- **Phase 6**: Append-Only File (AOF) persistence (`AofManager`) with configurable sync policies (`always`, `everysec`, `no`), crash-recovery replay, and atomic compaction rewrite.
- **Phase 7**: Comprehensive support for Redis collection types:
  - Lists: `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LLEN`, `LRANGE`, `LINDEX`, `LSET`, `LTRIM`.
  - Sets: `SADD`, `SREM`, `SISMEMBER`, `SMEMBERS`, `SCARD`, `SINTER`, `SUNION`, `SDIFF`.
  - Hashes: `HSET`, `HGET`, `HMGET`, `HDEL`, `HEXISTS`, `HGETALL`, `HKEYS`, `HVALS`, `HLEN`, `HINCRBY`.
- **Phase 8**: Per-client isolated transactions (`MULTI`, `EXEC`, `DISCARD`) with contiguous atomic execution.
- **Phase 9 & 10**: Production hardening and security:
  - Constant-time password authentication (`AUTH`).
  - Request length and framing bounds.
  - Resource cleanup and graceful shutdown hooks.
- **Phase 11**: Repeatable latency and throughput benchmarking harness (`BenchmarkRunner`).
- **Phase 12**: GitHub Actions CI workflow for Java 21 build, test verification, and JaCoCo coverage uploads.
