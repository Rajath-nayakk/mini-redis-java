# Redis Serialization Protocol (RESP2) Specification

MiniRedis implements the RESP2 wire protocol for communication between clients and the database engine.

## 1. Framing and Delimiters
All RESP messages are framed by a type identifier prefix byte and terminated with CRLF (`\r\n` or `0x0D 0x0A`):

| Prefix | Type | Wire Example | Description |
|---|---|---|---|
| `+` | Simple String | `+OK\r\n` | Single-line ASCII/UTF-8 status reply (cannot contain CRLF) |
