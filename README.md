<div align="center">

```txt
    11111111111         11111   111         111   1111111111    1111      111
    111      111       111 111   111       111    111           111111    111
    111      111      111   111   111     111     111111111     111 111   111
    1111111111       111     111   111   111      111           111   111 111
    111      111    111       111   111 111       111           111     11111
    111      111   111         111   11111        1111111111    111      1111
```

</div>

# RAVEN Framework

![AGPL](https://img.shields.io/badge/AGPL-v3-000000?style=for-the-badge&logo=gnu&logoColor=ffffff&labelColor=000000&color=03001a)
![OpenJDK](https://img.shields.io/badge/OpenJDK-17+-000000?style=for-the-badge&logo=openjdk&logoColor=ffee1a&labelColor=000000&color=03001a)
![Red Teaming](https://img.shields.io/badge/RED%20TEAMING-000000?style=for-the-badge&logo=keepassxc&logoColor=ff0000&labelColor=000000&color=03001a)
![Cyber Security](https://img.shields.io/badge/CYBER%20SECURITY-000000?style=for-the-badge&logo=socket&logoColor=009ceb&labelColor=000000&color=03001a)
![Cryptography](https://img.shields.io/badge/CRYPTOGRAPHY-000000?style=for-the-badge&logo=letsencrypt&logoColor=0eff39&labelColor=000000&color=03001a)
![Maven](https://img.shields.io/badge/Maven-000000?style=for-the-badge&logo=apachemaven&logoColor=ee6a2a&labelColor=000000&color=03001a)
![Networking](https://img.shields.io/badge/Networking-000000?style=for-the-badge&logo=cloudflare&logoColor=26ff7d&labelColor=000000&color=03001a)

> **_Author:_** _MatrixTM26_ **_GitHub:_** _[MatrixTM26](https://github.com/MatrixTM26)_

---

## <img src="https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/solid/circle-info.svg" width="18"> Overview

> [!CAUTION]
> This tool is currently under development, in some release versions, you may encounter functional errors or logic flaws.

<details>
<summary>LEGAL DISCLAIMER</summary>

> [!CAUTION]
> **RAVEN Framework is a offensive security tool designed exclusively for:**
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
> By downloading, cloning, building, or executing TOMCAT C2 in any form, you acknowledge that:
>
> 1. You are a qualified security professional acting within legal and ethical boundaries
> 2. You hold valid written authorization for all target systems
> 3. You accept full legal and moral responsibility for your actions
> 4. Misuse of this tool is a violation of this license and applicable law
>
> **If you are unsure whether your use case is authorized — it is not. Stop and consult a legal professional.**

</details>

---

<!---

### UI OVERVIEW

<div align="center">
    <img src="doc/pages/public/w1.png" width="100%" height="auto" alt="RAVEN UI">
    <hr />
    <img src="doc/pages/public/w2.png" width="100%" height="auto" alt="RAVEN UI">
    <hr />
    <img src="doc/pages/public/w3.png" width="100%" height="auto" alt="RAVEN UI">
    <hr />
    <img src="doc/pages/public/w4.png" width="100%" height="auto" alt="RAVEN UI">
    <hr />
    <img src="doc/pages/public/w5.png" width="100%" height="auto" alt="RAVEN UI">
    <hr />
    <img src="doc/pages/public/w6.png" width="100%" height="auto" alt="RAVEN UI">
</div>

--->

## <img src="https://cdn.simpleicons.org/gnubash/ff0000" width="18"> Features

- **Multi-Interface Support** — Web Panel (HTTP), CLI, JavaFX GUI
- **AES-256-GCM Encryption** — All agent communication is encrypted end-to-end
- **Mutual TLS (mTLS)** — Agent authentication via PKCS12 certificates
- **Multi-Protocol Sessions** — TOMCAT agents, Meterpreter, Reverse Shells
- **Certificate Manager** — Full CA, server, and agent cert lifecycle management
- **File Transfer** — Upload and download files to/from agents
- **Session Management** — Thread-safe concurrent session handling
- **Event System** — Decoupled event-driven architecture
- **Cross-Platform** — Runs on Windows, Linux, macOS via JVM
- **Configurable** — All settings via `server.properties`

---

## <img src="https://cdn.simpleicons.org/gnubash/ff0000" width="18"> Installation & Usage

### 1. Clone the Repository

- MAIN
    > For normal usage, clone branch main

```bash
git clone --branch main https://github.com/MatrixTM26/RAVEN.git
cd RAVEN
```

<details>
    <summary>Other</summary>

- DEV
    > For contribution commit, pull request and development, push to branch dev

```bash
git clone --branch dev https://github.com/MatrixTM26/RAVEN.git
cd RAVEN
```

- MASTER
    > Only for owner/admin commit, pull request and upcoming version of development

</details>

### 2. Build the Project

> General Build

```bash
mvn clean package -q -X
```

### 3. Run the Server

```bash
# Web Panel Mode (Default)
java -jar target/raven-3.0.0.jar

# CLI Mode
java -jar target/raven-3.0.0.jar -C

# GUI Mode
java -jar target/raven-3.0.0.jar -G
```

---

## <img src="https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/solid/key.svg" width="18"> Certificate Management (MTLS)

### Initialize CA and Server Certificate

```bash
java -jar target/raven-3.0.0.jar --init-certs
```

### Generate Agent Certificates

```bash
# Single Agent
java -jar target/raven-3.0.0.jar -a myagent -ah 192.168.1.10 -ap 4444 -am

# Multiple Agents
java -jar target/raven-3.0.0.jar -m -c 10 -u team -ah 192.168.1.10 -ap 4444 -am
```

---

## <img src="https://cdn.simpleicons.org/gnubash/ff0000" width="18"> Command Line Arguments

- GENERAL

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

- LISTENER MODE

| Option | Long Option | Listener Mode              |
| ------ | ----------- | -------------------------- |
| `-A`   | `-multi`    | Multi-protocol auto-detect |
| `-R`   | `-raw`      | Raw TCP reverse shell only |
| `-b`   | `-http`     | HTTP beacon only           |
| `-B`   | `-https`    | HTTPS beacon only          |
| `-T`   | `-tls`      | TCP TLS — TOMCAT agent     |
| `-M`   | `-mtls`     | Mutual TLS — TOMCAT agent  |
| `-F`   | `-fmtls`    | Full mTLS + HTTPS beacon   |

```bash
java -jar target/raven-3.0.0.jar -A # multi-protocol
java -jar target/raven-3.0.0.jar -R # raw TCP
java -jar target/raven-3.0.0.jar -b # HTTP beacon
java -jar target/raven-3.0.0.jar -B # HTTPS beacon
java -jar target/raven-3.0.0.jar -T # TLS
java -jar target/raven-3.0.0.jar -M # mTLS
java -jar target/raven-3.0.0.jar -F # full mTLS + HTTPS beacon
```

- INTERFACE MODE

| Option       | Long Option               | Interface                                          |
| ------------ | ------------------------- | -------------------------------------------------- |
| `-C`         | `-cli-mode`               | Start in CLI mode                                  |
| `-G`         | `-gui-mode`               | Start in JavaFX GUI mode                           |
| `-W`         | `-web-mode`               | Start in Web Panel mode                            |
| `-TSC`       | `-teamserver-cli`         | TeamServer with CLI interface                      |
| `-TSW`       | `-teamserver-web`         | TeamServer with Web interface                      |
| `-TSG`       | `-teamserver-gui`         | TeamServer with GUI interface                      |
| `-tp <port>` | `-teamserver-port <port>` | TeamServer API port (default: `server.properties`) |

```bash
java -jar target/raven-3.0.0.jar -C            # CLI mode
java -jar target/raven-3.0.0.jar -G            # JavaFX GUI mode
java -jar target/raven-3.0.0.jar -W            # Web Panel mode
java -jar target/raven-3.0.0.jar -TSC          # TeamServer CLI
java -jar target/raven-3.0.0.jar -TSW -tp 5001 # TeamServer Web on port 5001
java -jar target/raven-3.0.0.jar -TSG          # TeamServer GUI

# Combine listener + interface
java -jar target/raven-3.0.0.jar -M -C -s 0.0.0.0 -p 4444 # mTLS + CLI
java -jar target/raven-3.0.0.jar -A -W -s 0.0.0.0 -p 4444 # multi + Web Panel
java -jar target/raven-3.0.0.jar -F -TSW -tp 5001         # fmTLS + TeamServer Web
```

- CLI COMMANDS

| Option                            | Description                         | Allowed     |
| --------------------------------- | ----------------------------------- | ----------- |
| `help`                            | Show help                           | all         |
| `clear`                           | Clear user interface                | all         |
| `stats`                           | Show server stats                   | all         |
| `status`                          | Show server status                  | all         |
| `logs`                            | Show server logs history            | all         |
| `sessions`                        | Show all current sessions           | all         |
| `use <id>`                        | Session live interaction            | all         |
| `exec <id> [command]`             | Execute command on specific session | all         |
| `kill <id>`                       | Kill specific session by id         | all         |
| `broadcast all [command]`         | Send command to all sessions        | all         |
| `broadcast <id,id,...> [command]` | Broadcast to specific sessions      | all         |
| `exit`                            | Exit and shutdown server            | all         |
| `listopt`                         | List all operators                  | all         |
| `addop <user> <pass> <ROLE>`      | Add new operator                    | ADMIN+      |
| `delop <user>`                    | Delete operator permanently         | ADMIN+      |
| `kick <user>`                     | Kick operator                       | SUPER ADMIN |
| `setrole <user> <ROLE>`           | Change operator role/permission     | SUPER ADMIN |
| `passwd <user> <new pass>`        | Change operator password            | ADMIN+      |

- LIVE SESSIONS

| Option      | Description            |
| ----------- | ---------------------- |
| `back`      | Return to main console |
| `<command>` | Run any system command |

- CERTIFICATE MANAGEMENT

| Option        | Long Option          | Description                                             |
| ------------- | -------------------- | ------------------------------------------------------- |
| `-i`          | `-init-certs`        | Initialize CA and server certificates                   |
| `-s <host>`   | `-server <host>`     | Server host used during cert init                       |
| `-a <id>`     | `-agent <id>`        | Generate a single agent certificate                     |
| `-m`          | `-multi`             | Generate multiple agent certificates                    |
| `-c <count>`  | `-count <count>`     | Number of agents to generate (with `-m`, default: `10`) |
| `-u <prefix>` | `-prefix <prefix>`   | Username prefix for bulk agent certs (default: `agent`) |
| `-ah <host>`  | `-agent-host <host>` | Agent callback host                                     |
| `-ap <port>`  | `-agent-port <port>` | Agent callback port                                     |
| `-am`         | `-agent-mtls`        | Enable mTLS in generated agent                          |
| `-ps`         | `-persistent`        | Enable persistence in generated agent                   |
| `-hc`         | `-hide-console`      | Hide console window in generated agent                  |
| `-l`          | `-list`              | List all generated agent certificates                   |
| `-r <id>`     | `-revoke <id>`       | Revoke an agent certificate by ID                       |

```bash
# Initialize CA + server certificate
java -jar target/raven-3.0.0.jar -i

# Initialize with specific server host
java -jar target/raven-3.0.0.jar -i -s 192.168.1.10

# Generate single agent cert
java -jar target/raven-3.0.0.jar -a myagent -ah 192.168.1.10 -ap 4444

# Generate single agent cert with mTLS + persistence + hidden console
java -jar target/raven-3.0.0.jar -a myagent -ah 192.168.1.10 -ap 4444 -am -ps -hc

# Generate 10 agent certs with prefix "agent"
java -jar target/raven-3.0.0.jar -m -c 10 -u agent -ah 192.168.1.10 -ap 4444 -am

# List all generated agents
java -jar target/raven-3.0.0.jar -l

# Revoke agent certificate
java -jar target/raven-3.0.0.jar -r myagent
```

- OPERATOR MANAGEMENT

| Option       | Long Option            | Description                                   |
| ------------ | ---------------------- | --------------------------------------------- |
| `-AO`        | `-add-operator`        | Add a new operator                            |
| `-RO`        | `-remove-operator`     | Remove an existing operator                   |
| `-OP`        | `-operator-permission` | View or update operator role                  |
| `-u <user>`  | `-username <user>`     | Operator username                             |
| `-pw <pass>` | `-password <pass>`     | Operator password (min 8 characters)          |
| `-r <role>`  | `-role <role>`         | Operator role — `ADMIN`, `OPERATOR`, `VIEWER` |

```bash
# Add operator with default role (OPERATOR)
java -jar target/raven-3.0.0.jar -AO -u op1 -pw securepass

# Add operator with specific role
java -jar target/raven-3.0.0.jar -AO -u op1 -pw securepass -r ADMIN

# Remove operator
java -jar target/raven-3.0.0.jar -RO -u op1

# Update operator role
java -jar target/raven-3.0.0.jar -OP -u op1 -r VIEWER

# List all available roles
java -jar target/raven-3.0.0.jar -OP
```

> OPERATOR ROLES
>
> | Role       | Description            | Permissions                      |
> | ---------- | ---------------------- | -------------------------------- |
> | `SUPER`    | Top operator hierarchy | read, write, exec, kick `[rwxk]` |
> | `ADMIN`    | 2nd operator hierarchy | read, write, exec `[rwx-]`       |
> | `OPERATOR` | 3rd operator hierarchy | read, exec `[r-x-]`              |
> | `MEMBER`   | 4th operator hierarchy | read `[r---]`                    |

---

## <img src="https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/solid/circle-info.svg" width="18"> Interface Modes

- **Web Panel** — Access via browser at `http://localhost:5000`
- **CLI Mode** — Powerful terminal interface (`-C`)
- **JavaFX GUI** — Full desktop application with sidebar navigation (`-G`)

---

## <img src="https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/solid/shield-halved.svg" width="18"> Security Features

- **AES-256-GCM** encryption for all agent communication
- **Mutual TLS (mTLS)** with PKCS12 keystores
- Full certificate lifecycle management (CA → Server → Agent)

---

## <img src="https://cdn.simpleicons.org/readme/ff0000" width="18"> Documentation

- **Documentation:** [Open](https://matrixtm26.github.io/RAVEN)
- **Wiki:** [Open](https://github.com/MatrixTM26/RAVEN/wiki)

## <img src="https://cdn.simpleicons.org/github/ff0000" width="18"> Credit

- **Author:** [MatrixTM26](https://github.com/MatrixTM26)
- **License:** [AGPL-V3](./LICENSE)

## <img src="https://cdn.simpleicons.org/githubsponsors/ff0000" width="18"> Support Me

[![Ko-fi](https://img.shields.io/badge/KO--FI-000000?style=for-the-badge&logo=kofi&logoColor=fff707)](https://ko-fi.com/MatrixTM26)
[![Trakteer](https://img.shields.io/badge/TRAKTEER-000000?style=for-the-badge&logo=buymeacoffee&logoColor=ff6a6a)](https://trakteer.id/MatrixTM26)
[![PayPal](https://img.shields.io/badge/PAYPAL-000000?style=for-the-badge&logo=paypal&logoColor=0000ff)](https://paypal.me/TeukuMaulana)

---

<p align="center">Copyright &copy;2023-2026 MatrixTM26 &middot; All Rights Reserved</p>
