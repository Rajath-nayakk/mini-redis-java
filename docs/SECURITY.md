# MiniRedis Security Policy & Hardening Guide

## 1. Authentication
When `--requirepass <password>` is configured:
- Unauthenticated client connections are prohibited from executing database commands.
- Commands issued before authentication return `-NOAUTH Authentication required.`.
- `AUTH <password>` uses constant-time comparison via `java.security.MessageDigest.isEqual()` to prevent timing attacks.
- Authentication state is isolated strictly per connection.

## 2. Network Protections
- **Loopback Default**: MiniRedis defaults to binding to `127.0.0.1`. Do not bind to `0.0.0.0` on untrusted networks without firewall rules and authentication.
- **Request Size Limits**: Max request size bounded to 10MB by default (`maxRequestSize`), preventing heap exhaustion from oversized frames.
- **Connection Caps**: Server enforces max client limits (`maxClients`), gracefully replying with `-ERR max number of clients reached` and closing the connection.
- **Client Isolation**: One crashing or disconnecting client does not affect other active sessions.

## 3. Data Protection
- Sensitive passwords are never logged to console or committed to version control.
- Safe file path normalization prevents directory traversal outside the configured data directory.
