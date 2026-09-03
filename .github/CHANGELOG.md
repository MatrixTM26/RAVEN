# Changelog

All notable changes to RAVEN C2 Framework are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Planned

- Additional agent payload formats
- Plugin/module loader system
- Extended osquery integration
- Enhanced lateral movement capabilities

---

## [3.0.0] — 2026-09-03

### Added

- TeamServer mode with dedicated REST API (`-TSC`, `-TSW`, `-TSG`)
- Full mTLS + HTTPS beacon listener mode (`-F`)
- Web Panel interface (browser-based at `http://localhost:5000`)
- JavaFX GUI interface with sidebar navigation
- AES-256-GCM end-to-end encryption for all agent communication
- Mutual TLS (mTLS) with PKCS12 certificate lifecycle management
- Role-based access control — SUPER, ADMIN, OPERATOR, MEMBER
- Operator profile system — save, load, clone, edit
- Multi-database backend support — SQLite, PostgreSQL, MongoDB, In-memory
- Session broadcast — run commands across all or selected agents
- Keylogger support (RAVEN protocol agents)
- Screenshot capture (RAVEN protocol agents)
- Browser credential dump (RAVEN protocol agents)
- SOCKS5 proxy through agent
- Port forwarding through agent
- Pivot route registration through agent
- Shellcode injection (ptrace / VirtualAllocEx)
- Persistence install and removal (cron / bashrc / systemd / reg / schtask)
- File upload and download (RAVEN protocol agents)
- Export command — sessions, logs, chat, history in `txt` or `json`
- In-memory chat and group messaging (TeamServer)
- `QUICKSTART.md` — step-by-step startup guide

### Changed

- README restructured — installation and overview only; usage moved to `QUICKSTART.md`
- Build output standardized to `target/raven-3.0.0.jar`
- Default TLS protocol enforced to TLSv1.3

### Security

- All agent transport encrypted with AES-256-GCM
- TLSv1.3 enforced across all encrypted listeners
- Operator authentication with role-gated command execution
- mTLS agent authentication via PKCS12 keystores

---

## [2.0.0] — 2025-01-01

### Added

- CLI interface with full command reference
- HTTP and HTTPS beacon listeners
- Raw TCP reverse shell listener
- Basic session management
- SQLite database backend

### Changed

- Core server architecture refactored
- Command dispatcher redesigned for extensibility

---

## [1.0.0] — 2024-01-01

### Added

- Initial release
- Basic TCP listener
- Single-operator session management
- In-memory storage only

---

[Unreleased]: https://github.com/MatrixTM26/RAVEN/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/MatrixTM26/RAVEN/compare/v2.0.0...v3.0.0
[2.0.0]: https://github.com/MatrixTM26/RAVEN/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/MatrixTM26/RAVEN/releases/tag/v1.0.0
