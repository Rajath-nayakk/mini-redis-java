# Redis Serialization Protocol (RESP2) Specification

MiniRedis implements the RESP2 wire protocol for communication between clients and the database engine.

## 1. Framing and Delimiters
All RESP messages are framed by a type identifier prefix byte and terminated with CRLF (`\r\n` or `0x0D 0x0A`):

| Prefix | Type | Wire Example | Description |
|---|---|---|---|
| `+` | Simple String | `+OK\r\n` | Single-line ASCII/UTF-8 status reply (cannot contain CRLF) |
| `-` | Error | `-ERR message\r\n` | Single-line error reply |
| `:` | Integer | `:1000\r\n` | 64-bit signed integer |
| `$` | Bulk String | `$5\r\nhello\r\n` | Binary-safe payload with byte-length declaration |
| `$` | Null Bulk String | `$-1\r\n` | Represents a non-existent or null key |
| `*` | Array | `*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n` | Sequence of RESP elements |
| `*` | Null Array | `*-1\r\n` | Represents a null array |

## 2. Streaming Parser Architecture
The `RespParser` class decodes incoming byte streams incrementally:
1. **Partial Reads**: If an incoming network buffer contains only part of a command (e.g. split across TCP packets), `parseNext()` returns `null` without consuming the partial bytes.
2. **Multi-Command Buffers**: If multiple complete commands arrive in a single network read, consecutive calls to `parseNext()` yield each command sequentially.
3. **Memory Safety**: Declared lengths for bulk strings are validated against `maxBulkStringLength` (default 512MB) before any allocation. The parser waits for bytes to arrive before allocating memory.
4. **Buffer Compaction**: Consumed bytes are periodically compacted to prevent buffer drift.
