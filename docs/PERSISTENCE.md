# MiniRedis AOF Persistence Guide

MiniRedis implements durable state persistence using an Append-Only File (`appendonly.aof`).

## Synchronization Policies

1. **`always`**:
   Calls `FileChannel.force(true)` immediately after appending each mutation. Provides maximal durability against sudden power loss at the expense of write throughput.
2. **`everysec`** (Recommended):
   Batches file synchronizations on a background thread once per second. Provides high performance with at most 1 second of potential data loss upon an OS crash.
3. **`no`**:
   Rely on operating system page cache writeback buffers. Provides highest throughput.

## Crash Recovery
During startup with `--appendonly true`:
- The server checks if `appendonly.aof` exists in the configured data directory.
- A streaming RESP parser reconstructs all state mutations in sequential order.
- Incomplete or corrupted trailing bytes resulting from a mid-write crash are isolated and cleanly truncated, preserving all prior valid records.

## Compaction & Rewrite
- Calling `aofManager.rewrite(engine)` generates a compact representation of the live keyspace, filtering out expired keys and coalescing multiple updates into single state representations.
- The replacement file is validated and swapped atomically via `Files.move(..., ATOMIC_MOVE)`.
