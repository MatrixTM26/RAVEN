<div align="center">
    <img src="public/raven.svg" width="200px" />
</div>

# RAVEN C2 Framework

A multi-platform adversary emulation framework.

---

## Overview

> This tool is currently under development. In some release versions, you may encounter functional errors or logic flaws.

<details>
<summary>LEGAL DISCLAIMER</summary>

> **RAVEN C2 Framework is an offensive security tool designed exclusively for:**
>
> - Authorized penetration testing and red team engagements
> - Controlled lab and research environments
> - Cybersecurity education under supervised conditions
>
> **You MUST have explicit written authorization from the system/network owner before deployment.**
> Unauthorized use constitutes a criminal offense under applicable international and local cybercrime laws, including but not limited to:
>
> | Jurisdiction      | Applicable Law                                                |
> | ----------------- | ------------------------------------------------------------- |
> | 🇺🇸 United States  | Computer Fraud and Abuse Act (CFAA) — 18 U.S.C. § 1030        |
> | 🇬🇧 United Kingdom | Computer Misuse Act 1990 (CMA)                                |
> | 🇪🇺 European Union | Directive on Attacks Against Information Systems (2013/40/EU) |
> | 🇦🇺 Australia      | Criminal Code Act 1995 — Part 10.7                            |
> | 🇮🇩 Indonesia      | UU ITE No. 19 Tahun 2016 — Pasal 30-32                        |
> | 🌐 International  | Budapest Convention on Cybercrime (ETS No. 185)               |
>
> **The author ([MatrixTM26](https://github.com/MatrixTM26)) provides this tool for legitimate security research and assumes NO liability for:**
>
> - Unauthorized access or intrusion conducted with this framework
> - Data loss, damage, or exposure resulting from misuse
> - Legal consequences arising from unlawful deployment
> - Any direct or indirect harm caused by third-party usage
>
> By downloading, cloning, building, or executing RAVEN in any form, you acknowledge that:
>
> 1. You are a qualified security professional acting within legal and ethical boundaries
> 2. You hold valid written authorization for all target systems
> 3. You accept full legal and moral responsibility for your actions
> 4. Misuse of this tool is a violation of this license and applicable law
>
> **If you are unsure whether your use case is authorized — it is not. Stop and consult a legal professional.**

</details>

---

## Features

- **Multi-Interface Support** — Web Panel (HTTP), CLI, JavaFX GUI
- **AES-256-GCM Encryption** — All agent communication is encrypted end-to-end
- **Mutual TLS (mTLS)** — Agent authentication via PKCS12 certificates
- **Multi-Protocol Sessions** — RAVEN agents, Meterpreter, Reverse Shells
- **Certificate Manager** — Full CA, server, and agent cert lifecycle management
- **File Transfer** — Upload and download files to/from agents
- **Session Management** — Thread-safe concurrent session handling
- **Event System** — Decoupled event-driven architecture
- **Cross-Platform** — Runs on Windows, Linux, macOS via JVM
- **Database Support** — In-memory, SQLite, PostgreSQL, and MongoDB backends
- **Operator Roles** — Role-based access control (SUPER, ADMIN, OPERATOR, MEMBER)
- **TeamServer Mode** — Multi-operator collaborative C2 with dedicated REST API
- **Operator Profiles** — Save, load, clone, and edit session profiles
- **Configurable** — All settings via `config/server/raven.properties`

---

## Requirements

- **Java 17+** (JDK with JavaFX for GUI mode)
- **Maven 3.8+**
- **Git**

---

## Installation & Usage

### 1. Clone the Repository

```bash
# Main branch (stable)
git clone --branch main https://github.com/MatrixTM26/RAVEN.git
cd RAVEN
```

<details>
<summary>Other branches</summary>

> CONTRIB branch — for contributions, pull requests, and development

```bash
git clone --branch contrib https://github.com/MatrixTM26/RAVEN.git
cd RAVEN
```

> **MASTER | SEC | DEV** — Reserved for owner/admin commits and upcoming version development only.

</details>

### 2. Build the Project

```bash
mvn clean package -q -X
```

Output: `target/raven-3.0.0.jar`

### 3. Run the Server

```bash
# Web Panel mode (default — browser at http://localhost:5000)
java -jar target/raven-3.0.0.jar

# CLI mode
java -jar target/raven-3.0.0.jar -C

# JavaFX GUI mode
java -jar target/raven-3.0.0.jar -G
```

---

## Configuration

All server settings are managed in `config/server/raven.properties`:

```properties
# C2 listener
server.host=0.0.0.0
server.port=4444
server.mode=multi

# Web panel
web.host=0.0.0.0
web.port=5000

# Database backend (none | sqlite | postgres | mongo)
db.type=none

# TLS protocol
cert.tls.protocol=TLSv1.3

# Logging
logging.level=INFO
logging.file.enabled=false
```

---

## Command Line Arguments

### General

| Option      | Long Option    | Description                                           |
| ----------- | -------------- | ----------------------------------------------------- |
| `-h`        | `-help`        | Show help and exit                                    |
| `-s <addr>` | `-host <addr>` | C2 server bind address (default: `server.properties`) |
| `-p <port>` | `-port <port>` | C2 listener port (default: `server.properties`)       |

```bash
# Show help
java -jar target/raven-3.0.0.jar -h

# Start with custom host and port
java -jar target/raven-3.0.0.jar -s 0.0.0.0 -p 4444
```

### Listener Mode

| Option | Long Option | Description                |
| ------ | ----------- | -------------------------- |
| `-A`   | `-multi`    | Multi-protocol auto-detect |
| `-R`   | `-raw`      | Raw TCP reverse shell only |
| `-b`   | `-http`     | HTTP beacon only           |
| `-B`   | `-https`    | HTTPS beacon only          |
| `-T`   | `-tls`      | TCP TLS — RAVEN agent      |
| `-M`   | `-mtls`     | Mutual TLS — RAVEN agent   |
| `-F`   | `-fmtls`    | Full mTLS + HTTPS beacon   |

```bash
java -jar target/raven-3.0.0.jar -A   # multi-protocol
java -jar target/raven-3.0.0.jar -R   # raw TCP
java -jar target/raven-3.0.0.jar -b   # HTTP beacon
java -jar target/raven-3.0.0.jar -B   # HTTPS beacon
java -jar target/raven-3.0.0.jar -T   # TLS
java -jar target/raven-3.0.0.jar -M   # mTLS
java -jar target/raven-3.0.0.jar -F   # full mTLS + HTTPS beacon
```

### Interface Mode

| Option       | Long Option               | Description                                        |
| ------------ | ------------------------- | -------------------------------------------------- |
| `-C`         | `-cli-mode`               | Start in CLI mode                                  |
| `-G`         | `-gui-mode`               | Start in JavaFX GUI mode                           |
| `-W`         | `-web-mode`               | Start in Web Panel mode                            |
| `-TSC`       | `-teamserver-cli`         | TeamServer with CLI interface                      |
| `-TSW`       | `-teamserver-web`         | TeamServer with Web interface                      |
| `-TSG`       | `-teamserver-gui`         | TeamServer with GUI interface                      |
| `-tp <port>` | `-teamserver-port <port>` | TeamServer API port (default: `server.properties`) |

```bash
java -jar target/raven-3.0.0.jar -C               # CLI mode
java -jar target/raven-3.0.0.jar -G               # JavaFX GUI mode
java -jar target/raven-3.0.0.jar -W               # Web Panel mode
java -jar target/raven-3.0.0.jar -TSC             # TeamServer CLI
java -jar target/raven-3.0.0.jar -TSW -tp 5001    # TeamServer Web on port 5001
java -jar target/raven-3.0.0.jar -TSG             # TeamServer GUI

# Combine listener + interface
java -jar target/raven-3.0.0.jar -M -C -s 0.0.0.0 -p 4444   # mTLS + CLI
java -jar target/raven-3.0.0.jar -A -W -s 0.0.0.0 -p 4444   # multi + Web Panel
java -jar target/raven-3.0.0.jar -F -TSW -tp 5001            # fmTLS + TeamServer Web
```

---

## CLI Commands

### System

| Command | Description                                                    |
| ------- | -------------------------------------------------------------- |
| `help`  | Show command reference                                         |
| `clean` | Clear the terminal screen (local only, does not send to agent) |
| `exit`  | Shutdown server and exit                                       |
| `quit`  | Alias for `exit`                                               |

### Server

| Command  | Description                                   |
| -------- | --------------------------------------------- |
| `status` | Show server mode, uptime, and database status |
| `logs`   | Show recent server event logs                 |

### Session

| Command                                | Description                                          | Permission |
| -------------------------------------- | ---------------------------------------------------- | ---------- |
| `sessions`                             | List all active agent sessions                       | all        |
| `agents`                               | Alias for `sessions`                                 | all        |
| `stats`                                | Show session type statistics                         | all        |
| `use <id>`                             | Enter interactive shell with agent                   | all        |
| `sysinfo <id>`                         | Show full system info for agent                      | all        |
| `info <id>`                            | Alias for `sysinfo`                                  | all        |
| `exec <id> <command>`                  | Execute arbitrary command on agent (raw passthrough) | all        |
| `shell <id> <command>`                 | Execute via shell interpreter (`sh -c` / `cmd /c`)   | all        |
| `broadcast <id,id,...\|all> <command>` | Broadcast command to selected or all agents          | all        |
| `kill <id>`                            | Terminate an agent session                           | all        |
| `ping <id>`                            | Ping agent to verify liveness (raven: protocol)      | all        |
| `reconnect <id>`                       | Ask RAVEN agent to reconnect (raven: protocol only)  | all        |
| `self-destruct <id>`                   | Wipe agent and terminate session                     | ADMIN+     |
| `sleep <id> <seconds>`                 | Set agent sleep interval (raven: protocol)           | all        |
| `jitter <id> <ms>`                     | Set agent jitter delay in ms (raven: protocol)       | all        |

### Recon

| Command                      | Description                                                          |
| ---------------------------- | -------------------------------------------------------------------- |
| `whoami <id>`                | Current user — Linux: `whoami` / Windows: `whoami /all`              |
| `id <id>`                    | User ID/groups — Linux: `id` / Windows: `whoami /groups`             |
| `hostname <id>`              | Show agent hostname                                                  |
| `uname <id>`                 | OS/kernel info — Linux: `uname -a` / Windows: `ver + systeminfo`     |
| `ps <id>`                    | Process list — Linux: `ps aux` / Windows: `tasklist /v`              |
| `env <id>`                   | Environment variables — Linux: `env` / Windows: `set`                |
| `netstat <id>`               | Network connections — Linux: `ss -tulpn` / Windows: `netstat -an`    |
| `ifconfig <id>`              | Network interfaces — Linux: `ip addr` / Windows: `ipconfig /all`     |
| `arp <id>`                   | ARP table — Linux: `arp -n` / Windows: `arp -a`                      |
| `route <id>`                 | Routing table — Linux: `ip route` / Windows: `route print`           |
| `users <id>`                 | Local users — Linux: `/etc/passwd` / Windows: `net user`             |
| `groups <id>`                | Groups — Linux: `groups` / Windows: `net localgroup`                 |
| `services <id>`              | Running services — Linux: `systemctl` / Windows: `sc query`          |
| `screenshot <id>`            | Capture desktop screenshot (raven: protocol)                         |
| `privcheck <id>`             | Privilege check — Linux: `id+sudo -l` / Windows: `whoami /priv`      |
| `antivirus <id>`             | Detect AV/EDR — Linux: `ps grep` / Windows: `wmic AntivirusProduct`  |
| `crontab <id>`               | Scheduled tasks — Linux: `crontab -l` / Windows: `schtasks /query`   |
| `clipboard <id>`             | Read clipboard — Linux: `xclip/xsel` / Windows: PowerShell           |
| `keystroke <id> <on\|off>`   | Toggle keylogger on agent (raven: protocol)                          |
| `hashdump <id>`              | Dump hashes — Linux: `/etc/shadow` / Windows: SAM (raven: protocol)  |
| `searchfiles <id> <pattern>` | File search — Linux: `find` / Windows: `where /r`                    |
| `wifidump <id>`              | WiFi credentials — Linux: `nmcli` / Windows: `netsh wlan`            |
| `dumpbrowsers <id>`          | Saved browser credentials (raven: protocol)                          |
| `lastlog <id>`               | Recent logins — Linux: `last -n 20` / Windows: `net user + wevtutil` |
| `osquery <id> <sql>`         | Run osquery SQL on agent (requires osquery installed)                |

### Filesystem

| Command                                  | Description                                                    |
| ---------------------------------------- | -------------------------------------------------------------- |
| `ls <id> [path]`                         | List directory — Linux: `ls -la` / Windows: `dir`              |
| `pwd <id>`                               | Working directory — Linux: `pwd` / Windows: `cd`               |
| `cd <id> <path>`                         | Change directory on agent                                      |
| `cat <id> <file>`                        | Read file — Linux: `cat` / Windows: `type`                     |
| `head <id> <file> [n]`                   | First N lines of a file                                        |
| `tail <id> <file> [n]`                   | Last N lines of a file                                         |
| `rm <id> <path>`                         | Delete file/dir — Linux: `rm -rf` / Windows: `del/rmdir`       |
| `mkdir <id> <path>`                      | Create directory — Linux: `mkdir -p` / Windows: `mkdir`        |
| `cp <id> <src> <dst>`                    | Copy — Linux: `cp -r` / Windows: `copy`                        |
| `mv <id> <src> <dst>`                    | Move — Linux: `mv` / Windows: `move`                           |
| `chmod <id> <mode> <file>`               | Permissions — Linux: `chmod` / Windows: `icacls` (best-effort) |
| `find <id> <path> [name]`                | Find files — Linux: `find` / Windows: `where /r`               |
| `grep <id> <pattern> <file>`             | Search text — Linux: `grep -n` / Windows: `findstr`            |
| `hash <id> <file> [sha256\|md5]`         | File hash — Linux: `sha256sum/md5sum` / Windows: `certutil`    |
| `download <id> <remote-path>`            | Download file from agent (raven: protocol)                     |
| `upload <id> <local-path> [remote-path]` | Upload file to agent (raven: protocol)                         |

### Task

| Command                | Description                                    |
| ---------------------- | ---------------------------------------------- |
| `tasks`                | Show pending task queue                        |
| `history [id] [limit]` | Command history (all agents or a specific one) |
| `sesshistory [limit]`  | Session connection history from database       |
| `note <id> <text>`     | Set a note for an agent                        |
| `getnote <id>`         | Get the note for an agent                      |

### Lateral Movement

| Command                                | Description                                                                 |
| -------------------------------------- | --------------------------------------------------------------------------- |
| `pivot <id> <host:port>`               | Register pivot route through agent (raven: protocol)                        |
| `portfwd <id> <lport> <rhost> <rport>` | Port forward through agent (raven: protocol)                                |
| `socks <id> <lport>`                   | SOCKS5 proxy through agent (raven: protocol)                                |
| `spawn <id>`                           | Spawn new agent process on target (raven: protocol)                         |
| `shellcode <id> <hex>`                 | Inject shellcode — Linux: `ptrace` / Windows: `VirtualAllocEx`              |
| `persist <id> [method]`                | Install persistence — Linux: `cron/bashrc/systemd` / Windows: `reg/schtask` |
| `unpersist <id> [method]`              | Remove persistence entry (raven: protocol)                                  |
| `runas <id> <user> <pass> <cmd>`       | Run as user — Linux: `su` / Windows: `runas`                                |

### Operator Management

| Command                                                 | Description                        | Permission |
| ------------------------------------------------------- | ---------------------------------- | ---------- |
| `listopt`                                               | List all operators and their roles | all        |
| `addopt <user> <pass> [SUPER\|ADMIN\|OPERATOR\|MEMBER]` | Add a new operator account         | ADMIN+     |
| `delopt <username>`                                     | Delete an operator account         | ADMIN+     |
| `kick <username>`                                       | Kick and remove operator token     | SUPER only |
| `setrole <user> <SUPER\|ADMIN\|OPERATOR\|MEMBER>`       | Change operator role               | ADMIN+     |
| `passwd <user> <newpass>`                               | Change operator password           | ADMIN+     |

### Chat (TeamServer only)

| Command                        | Description                            |
| ------------------------------ | -------------------------------------- |
| `chat`                         | Show in-memory chat messages           |
| `chathistory [limit]`          | Show chat history from database        |
| `ch <recipient> <message>`     | Send a direct message to an operator   |
| `gc <all\|name,...> <message>` | Send a group or broadcast chat message |

### Export

| Command                    | Description                                                                                                        |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `export <target> <format>` | Export data — targets: `all`, `logs`, `chat`, `history`, `sessions`, `operators`, `notes` — formats: `txt`, `json` |

### Profiles

| Command                            | Description                                       |
| ---------------------------------- | ------------------------------------------------- |
| `profiles`                         | List all saved operator profiles                  |
| `profile [name]`                   | Show active profile or details of a named profile |
| `loadprofile <name>`               | Load and apply a profile to current session       |
| `saveprofile <name> [description]` | Save current session settings as a new profile    |
| `delprofile <name>`                | Delete a saved profile (cannot delete `default`)  |
| `cloneprofile <source> <target>`   | Clone an existing profile under a new name        |
| `editprofile <name> <key> <value>` | Set a single key in a saved profile               |

### Web Panel

| Command                  | Description                   |
| ------------------------ | ----------------------------- |
| `webstart [host] [port]` | Start the web panel server    |
| `webstop`                | Stop the web panel server     |
| `webstatus`              | Show web panel current status |

### Live Session Commands

When inside an interactive session (entered via `use <id>`):

| Command     | Description                |
| ----------- | -------------------------- |
| `back`      | Return to the main console |
| `clean`     | Clear the terminal screen  |
| `<command>` | Run any system command     |

---

## Certificate Management (mTLS)

### Initialize CA and Server Certificate

```bash
java -jar target/raven-3.0.0.jar -i

# Initialize with a specific server host
java -jar target/raven-3.0.0.jar -i -s 192.168.1.10
```

### Certificate CLI Options

| Option        | Long Option          | Description                                                  |
| ------------- | -------------------- | ------------------------------------------------------------ |
| `-i`          | `-init-certs`        | Initialize CA and server certificates                        |
| `-s <host>`   | `-server <host>`     | Server host used during cert init                            |
| `-a <id>`     | `-agent <id>`        | Generate a single agent certificate                          |
| `-m`          | `-multi`             | Generate multiple agent certificates                         |
| `-c <count>`  | `-count <count>`     | Number of agents to generate (used with `-m`, default: `10`) |
| `-u <prefix>` | `-prefix <prefix>`   | Username prefix for bulk agent certs (default: `agent`)      |
| `-ah <host>`  | `-agent-host <host>` | Agent callback host                                          |
| `-ap <port>`  | `-agent-port <port>` | Agent callback port                                          |
| `-am`         | `-agent-mtls`        | Enable mTLS in the generated agent                           |
| `-ps`         | `-persistent`        | Enable persistence in the generated agent                    |
| `-hc`         | `-hide-console`      | Hide the console window in the generated agent               |
| `-l`          | `-list`              | List all generated agent certificates                        |
| `-r <id>`     | `-revoke <id>`       | Revoke an agent certificate by ID                            |

```bash
# Generate a single agent certificate
java -jar target/raven-3.0.0.jar -a myagent -ah 192.168.1.10 -ap 4444

# Generate single agent with mTLS + persistence + hidden console
java -jar target/raven-3.0.0.jar -a myagent -ah 192.168.1.10 -ap 4444 -am -ps -hc

# Bulk generate 10 agent certificates
java -jar target/raven-3.0.0.jar -m -c 10 -u agent -ah 192.168.1.10 -ap 4444 -am

# List all generated agents
java -jar target/raven-3.0.0.jar -l

# Revoke an agent certificate
java -jar target/raven-3.0.0.jar -r myagent
```

---

## Operator Management

| Option       | Long Option            | Description                                           |
| ------------ | ---------------------- | ----------------------------------------------------- |
| `-AO`        | `-add-operator`        | Add a new operator                                    |
| `-RO`        | `-remove-operator`     | Remove an existing operator                           |
| `-OP`        | `-operator-permission` | View or update operator role                          |
| `-u <user>`  | `-username <user>`     | Operator username                                     |
| `-pw <pass>` | `-password <pass>`     | Operator password (min 8 characters)                  |
| `-r <role>`  | `-role <role>`         | Operator role: `SUPER`, `ADMIN`, `OPERATOR`, `MEMBER` |

```bash
# Add operator with default role (OPERATOR)
java -jar target/raven-3.0.0.jar -AO -u op1 -pw securepass

# Add operator with a specific role
java -jar target/raven-3.0.0.jar -AO -u op1 -pw securepass -r ADMIN

# Remove operator
java -jar target/raven-3.0.0.jar -RO -u op1

# Update operator role
java -jar target/raven-3.0.0.jar -OP -u op1 -r VIEWER

# List all available roles
java -jar target/raven-3.0.0.jar -OP
```

### Operator Roles

| Role       | Description            | Permissions                      |
| ---------- | ---------------------- | -------------------------------- |
| `SUPER`    | Top operator hierarchy | read, write, exec, kick `[rwxk]` |
| `ADMIN`    | 2nd operator hierarchy | read, write, exec `[rwx-]`       |
| `OPERATOR` | 3rd operator hierarchy | read, exec `[r-x-]`              |
| `MEMBER`   | 4th operator hierarchy | read `[r---]`                    |

---

## Interface Modes

- **Web Panel** — Browser-based interface at `http://localhost:5000`. Includes session management, operator controls, broadcast, and live logs. Can be toggled at runtime via `webstart` / `webstop`.
- **CLI Mode** — Full-featured terminal interface started with `-C`. Supports all commands, session interaction, operator management, and profiles.
- **JavaFX GUI** — Desktop application started with `-G`. Features a sidebar-based navigation with Overview, Sessions, Command Center, Terminal, Logs, and Settings panels.
- **TeamServer** — Multi-operator mode (`-TSC`, `-TSW`, `-TSG`) that exposes a dedicated REST API on a separate port (default `5001`), allowing multiple clients to connect to the same C2 instance simultaneously.

---

## Security Features

- **AES-256-GCM** encryption for all agent communication
- **Mutual TLS (mTLS)** with PKCS12 keystores for agent authentication
- **Full certificate lifecycle** management — CA → Server → Agent
- **Role-based access control** with four operator permission levels
- **TLSv1.3** enforced for all encrypted transport

---

## Database Backends

RAVEN supports multiple storage backends, configured in `raven.properties` via `db.type`:

| Backend    | Value      | Notes                          |
| ---------- | ---------- | ------------------------------ |
| In-memory  | `none`     | Default, no persistence        |
| SQLite     | `sqlite`   | File-based, no server required |
| PostgreSQL | `postgres` | Production-grade relational DB |
| MongoDB    | `mongo`    | Document-oriented store        |

---

## Documentation

- **Documentation:** [Open](https://matrixtm26.github.io/RAVEN)
- **Wiki:** [Open](https://github.com/MatrixTM26/RAVEN/wiki)

---

<p align="center">
    &copy;
    Copyright 2023-2026 
    <a href="https://github.com/matrixtm26">@MatrixTM26</a>
    &nbsp;
    &middot;
    &nbsp;
    All right reserved.
    <br>
    Licensed under
    &nbsp;
    <a href="./LICENSE">AGPL-V3</a>
</p>
