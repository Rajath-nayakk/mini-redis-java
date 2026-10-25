# MiniRedis Configuration Reference

MiniRedis can be configured via command-line arguments or using programmatic builder properties.

| CLI Argument | Config Property | Default | Description |
|--------------|-----------------|---------|-------------|
| `--host` | `host` | `127.0.0.1` | Network interface IP to bind to |
| `--port` | `port` | `6379` | TCP port to listen on (1-65535) |
| `--max-clients` | `maxClients` | `1000` | Maximum simultaneous client connections |
| `--dir` | `dataDir` | `data` | Working directory for AOF persistence |
| `--appendonly` | `persistenceEnabled` | `false` | Enable or disable Append-Only File (AOF) |
| `--requirepass` | `requirePass` | `null` | Require client authentication password (`AUTH`) |
| `--max-memory` | `maxMemoryBytes` | `0` (unlimited) | Advisory memory threshold in bytes |

## Example Startup Commands

Start server on custom port with password:
```bash
java -jar target/mini-redis-java-1.0.0-SNAPSHOT.jar --port 6380 --requirepass mysecret
```

Start server with AOF persistence enabled:
```bash
java -jar target/mini-redis-java-1.0.0-SNAPSHOT.jar --appendonly true --dir ./redis_data
```
