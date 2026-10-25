# Getting Started with MiniRedis Java

Welcome to **MiniRedis**! This guide walks you through building, starting, and interacting with your Java-based in-memory Redis database from scratch.

---

## 1. Prerequisites

Before running MiniRedis, verify that you have Java 21 LTS installed on your machine.

Open PowerShell or Command Prompt and run:
```powershell
java -version
```
**Expected Output:**
```
openjdk version "21.0.12.1" ...
```

The project includes the Maven Wrapper (`mvnw.cmd`), so you do not need to install Maven separately.

---

## 2. How to Build the Project

From the project root directory (`mini-redis-java`), run:
```powershell
.\mvnw.cmd clean verify
```

This compiles all source files, runs all 82 automated unit and integration tests, and generates the runnable JAR in the `target/` folder:
- **Location:** `target/mini-redis-java-1.0.0-SNAPSHOT.jar`

---

## 3. How to Start the Server

### Default Startup (Port 6379)
```powershell
java -jar target/mini-redis-java-1.0.0-SNAPSHOT.jar
```

> [!NOTE]
> If another application (like Redis or Memurai) is already using port 6379, MiniRedis will display a port conflict error. You can run on an alternate port such as `6380`:
> ```powershell
> java -jar target/mini-redis-java-1.0.0-SNAPSHOT.jar --port 6380
> ```

### Available Startup Options
View all supported options anytime:
```powershell
java -jar target/mini-redis-java-1.0.0-SNAPSHOT.jar --help
```

| Option | Description | Default |
|---|---|---|
| `--port <port>` | Port to listen on | `6379` |
| `--host <host>` | IP address to bind to | `127.0.0.1` |
| `--dir <path>` | Data directory for AOF persistence | `data` |
| `--max-clients <num>` | Maximum concurrent connections | `1000` |
| `--max-memory <bytes>` | Max memory budget (0 = unlimited) | `0` |
| `--appendonly <true\|false>` | Enable Append-Only File persistence | `false` |
| `--requirepass <password>` | Require client password authentication | None |

---

## 4. How to Connect a Client

### Option A: Using `redis-cli` (If installed)
```bash
redis-cli -h 127.0.0.1 -p 6380
```

### Option B: Using Java (Zero Dependencies)
You can connect and send commands using standard Java sockets:
```java
try (java.net.Socket socket = new java.net.Socket("127.0.0.1", 6380)) {
    java.io.OutputStream out = socket.getOutputStream();
    java.io.InputStream in = socket.getInputStream();

    // Send PING in RESP2 format
    out.write("*1\r\n$4\r\nPING\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    out.flush();

    byte[] buffer = new byte[1024];
    int read = in.read(buffer);
    System.out.println(new String(buffer, 0, read)); // +PONG\r\n
}
```

---

## 5. Copy-Pasteable Command Examples & Expected Responses

### Basic Key-Value & Counters
```
mini-redis> PING
+PONG

mini-redis> SET name Rajath
+OK

mini-redis> GET name
"Rajath"

mini-redis> EXISTS name
:1

mini-redis> INCR visits
:1

mini-redis> GET visits
"1"

mini-redis> DEL name
:1

mini-redis> EXISTS name
:0
```

### Lists
```
mini-redis> LPUSH mylist item1 item2 item3
:3

mini-redis> LLEN mylist
:3

mini-redis> LRANGE mylist 0 -1
1) "item3"
2) "item2"
3) "item1"
```

### Sets
```
mini-redis> SADD skills java redis distributed
:3

mini-redis> SISMEMBER skills java
:1

mini-redis> SISMEMBER skills python
:0

mini-redis> SMEMBERS skills
1) "java"
2) "redis"
3) "distributed"
```

### Hashes
```
mini-redis> HSET profile username rajath role engineer
:2

mini-redis> HGET profile username
"rajath"

mini-redis> HGETALL profile
1) "username"
2) "rajath"
3) "role"
4) "engineer"
```

### Expiration (TTL)
```
mini-redis> SET session_token "xyz123"
+OK

mini-redis> PEXPIRE session_token 10000
:1

mini-redis> PTTL session_token
:9998
```

### Atomic Transactions
```
mini-redis> MULTI
+OK

mini-redis> SET order:101 "placed"
+QUEUED

mini-redis> INCR order_count
+QUEUED

mini-redis> EXEC
1) +OK
2) :1
```

---

## 6. How to Enable Persistence and Authentication

### Enabling Password Authentication
Start the server with `--requirepass`:
```powershell
java -jar target/mini-redis-java-1.0.0-SNAPSHOT.jar --port 6380 --requirepass MySecret123
```

When connecting, all commands will return `-NOAUTH` until you authenticate:
```
mini-redis> PING
-NOAUTH Authentication required.

mini-redis> AUTH MySecret123
+OK

mini-redis> PING
+PONG
```

### Enabling AOF Persistence
Start the server with `--appendonly true` and specify a directory:
```powershell
java -jar target/mini-redis-java-1.0.0-SNAPSHOT.jar --port 6380 --appendonly true --dir runtime-data
```

1. Any state-changing command (`SET`, `INCR`, `HSET`, `LPUSH`, etc.) is logged immediately to `runtime-data/appendonly.aof`.
2. When the server is stopped and restarted with the same directory, it automatically replays all logged operations:
   ```
   [MiniRedis] Replayed 42 commands from AOF.
   [MiniRedis] Server started on 127.0.0.1:6380
   ```

---

## 7. How to Stop the Server Safely

Press `Ctrl + C` in the terminal where MiniRedis is running.

MiniRedis registers a JVM shutdown hook that handles graceful termination:
```
[MiniRedis] Shutting down gracefully...
[MiniRedis] Server stopped.
```
- Stops accepting new client connections.
- Flushes and closes the AOF log file safely.
- Closes all active client socket connections.

---

## 8. Troubleshooting Common Errors

### Error: `Address already in use: bind`
- **Cause:** Another process (such as a local Redis or Memurai service) is already using port 6379.
- **Fix:** Specify an alternative port using `--port 6380`.

### Error: `NOAUTH Authentication required.`
- **Cause:** The server was started with `--requirepass`, but the client hasn't authenticated yet.
- **Fix:** Send `AUTH <your-password>`.

### Error: `WRONGTYPE Operation against a key holding the wrong kind of value`
- **Cause:** You attempted to execute a list command on a string key (e.g. `LPUSH my_string_key val`).
- **Fix:** Use key names corresponding to their data types, or delete the key with `DEL` before reusing it.
