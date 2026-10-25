# Contributing to MiniRedis

Thank you for your interest in contributing to MiniRedis!

## Development Guidelines

1. **Java Version**: MiniRedis is compiled against Java 21 LTS. Use modern Java features (records, pattern matching, sealed interfaces, try-with-resources) where appropriate.
2. **Build and Verification**:
   Always ensure the complete test suite passes before submitting changes:
   ```powershell
   .\mvnw.cmd clean verify
   ```
3. **Architecture Principles**:
   - Keep network handling, protocol parsing, command dispatching, and storage separated.
   - All state mutations must maintain strict thread safety.
   - Any new state-changing command must be registered with AOF persistence.
   - Keep unit tests fast, deterministic, and free from thread sleeps by injecting `java.time.Clock`.
