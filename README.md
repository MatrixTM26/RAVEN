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

## Installation

### 1. Clone the Repository

```bash
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
mvn clean package -q
```

Output: `target/raven-3.0.0.jar`

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

## Interface Modes

- **Web Panel** — Browser-based interface at `http://localhost:5000`. Includes session management, operator controls, broadcast, and live logs. Can be toggled at runtime via `webstart` / `webstop`.
- **CLI Mode** — Full-featured terminal interface. Supports all commands, session interaction, operator management, and profiles.
- **JavaFX GUI** — Desktop application with sidebar-based navigation. Features Overview, Sessions, Command Center, Terminal, Logs, and Settings panels.
- **TeamServer** — Multi-operator mode that exposes a dedicated REST API on a separate port (default `5001`), allowing multiple clients to connect to the same C2 instance simultaneously.

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

## Operator Roles

| Role       | Description            | Permissions                      |
| ---------- | ---------------------- | -------------------------------- |
| `SUPER`    | Top operator hierarchy | read, write, exec, kick `[rwxk]` |
| `ADMIN`    | 2nd operator hierarchy | read, write, exec `[rwx-]`       |
| `OPERATOR` | 3rd operator hierarchy | read, exec `[r-x-]`              |
| `MEMBER`   | 4th operator hierarchy | read `[r---]`                    |

---

## Documentation

- **Quick Start Guide:** [QUICKSTART.md](./QUICKSTART.md)
- **Documentation:** [Open](https://matrixtm26.github.io/RAVEN)
- **Wiki:** [Open](https://github.com/MatrixTM26/RAVEN/wiki)
- **Security Policy:** [SECURITY.md](./SECURITY.md)
- **Contributing:** [CONTRIBUTING.md](./.github/CONTRIBUTING.md)

---

<p align="center">
    &copy;
    Copyright 2023-2026 
    <a href="https://github.com/MatrixTM26">@MatrixTM26</a>
    &nbsp;
    &middot;
    &nbsp;
    All right reserved.
    <br>
    Licensed under
    &nbsp;
    <a href="./LICENSE">AGPL-V3</a>
</p>
