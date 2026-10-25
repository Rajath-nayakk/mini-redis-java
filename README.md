# MiniRedis

MiniRedis is a clean, educational, Redis-inspired in-memory database built from scratch in Java 21 LTS.

> **Disclaimer:** MiniRedis is an educational project and is not affiliated with or endorsed by Redis Ltd.

## Features & Architectural Goals
- **Core In-Memory Storage**: Fast key-value storage engine supporting strings, lists, sets, and hashes.
- **RESP2 Protocol**: Compliant Redis Serialization Protocol (RESP2) parsing binary-safe bulk strings.
- **TCP Networking**: Multithreaded network layer listening on loopback (127.0.0.1:6379).
- **Atomic Numeric Operations**: Thread-safe operations for INCR, DECR, INCRBY, DECRBY.
