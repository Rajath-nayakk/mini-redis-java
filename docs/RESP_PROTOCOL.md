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

