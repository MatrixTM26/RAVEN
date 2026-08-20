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
      <div style="font-family:var(--mono);font-size:8px;color:var(--text-muted);letter-spacing:1.5px;text-transform:uppercase;margin-bottom:8px;">Role Permissions</div>
      <table style="width:100%;border-collapse:collapse;font-size:11px;">
        ${roles
            .map(
                r => `<tr style="border-bottom:1px solid var(--border);">
          <td style="padding:6px 8px;color:var(--text);font-weight:600;font-family:var(--mono);width:120px;">${Esc(r.Name)}</td>
          <td style="padding:6px 8px;color:var(--text-dim);font-family:var(--mono);">${Esc(r.Permissions)}</td>
        </tr>`
            )
            .join("")}
      </table>
    </div>`
        : "";

    let opTable = `
    <table style="width:100%;border-collapse:collapse;font-size:12px;margin-bottom:14px;">
      <thead><tr style="border-bottom:1px solid var(--border-strong);">
        <th style="text-align:left;padding:7px 8px;color:var(--text-muted);font-family:var(--mono);font-size:9px;letter-spacing:1px;font-weight:500;text-transform:uppercase;">Username</th>
        <th style="text-align:left;padding:7px 8px;color:var(--text-muted);font-family:var(--mono);font-size:9px;letter-spacing:1px;font-weight:500;text-transform:uppercase;">Role</th>
        <th style="text-align:left;padding:7px 8px;color:var(--text-muted);font-family:var(--mono);font-size:9px;letter-spacing:1px;font-weight:500;text-transform:uppercase;">Last Seen</th>
        <th style="text-align:right;padding:7px 8px;color:var(--text-muted);font-family:var(--mono);font-size:9px;letter-spacing:1px;font-weight:500;text-transform:uppercase;">Actions</th>
      </tr></thead>
      <tbody>
        ${ops
            .map(op => {
                let isSelf = op.Username === State.operator;
                let isAdmin = op.Username === "admin";
                return `<tr style="border-bottom:1px solid var(--border);">
            <td style="padding:8px;color:var(--text);font-family:var(--mono);font-size:11px;">
              ${Esc(op.Username)}${isSelf ? ' <span style="color:var(--accent,#2563eb);font-size:9px;margin-left:4px;">[you]</span>' : ""}
            </td>
            <td style="padding:8px;">
              <span style="background:var(--accent-pale);color:var(--accent,#2563eb);padding:2px 8px;border:1px solid var(--accent-border);border-radius:3px;font-family:var(--mono);font-size:9px;font-weight:600;">${Esc(op.Role)}</span>
            </td>
            <td style="padding:8px;color:var(--text-muted);font-family:var(--mono);font-size:10px;">${Esc(op.LastSeen || "Never")}</td>
            <td style="padding:8px;text-align:right;">
              ${!isAdmin && !isSelf ? `<button class="btn btn-danger btn-sm" onclick="KickOp('${Esc(op.Username)}')" title="Remove operator"><i class="fas fa-user-times"></i></button>` : ""}
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

function getThemeColor(varName) {
    return getComputedStyle(document.documentElement)
        .getPropertyValue(varName)
        .trim();
}

function DrawTopology() {
    let svg = document.getElementById("topologySvg");
    if (!svg) return;
    svg.innerHTML = "";
    let W = svg.clientWidth || 600;
    let H = parseInt(svg.getAttribute("height")) || 300;
    svg.setAttribute("viewBox", "0 0 " + W + " " + H);

    let isDark = document.documentElement.getAttribute("data-theme") === "dark";
    let clrBg = isDark ? "#0d0f14" : "#f4f6fa";
    let clrGrid = isDark ? "rgba(255,255,255,0.035)" : "rgba(0,0,0,0.04)";
    let clrAccent = "#2563eb";
    let clrOk = "#16a34a";
    let clrWarn = "#d97706";
    let clrMuted = isDark ? "#4a4f65" : "#9096b0";
    let clrText = isDark ? "#8b90a4" : "#4a5068";
    let clrBorder = isDark ? "rgba(255,255,255,0.08)" : "rgba(0,0,0,0.09)";

    let defs = SvgEl("defs");

    let pat = SvgEl("pattern", {
        id: "tg",
        width: "24",
        height: "24",
        patternUnits: "userSpaceOnUse"
    });
    pat.appendChild(
        SvgEl("path", {
            d: "M 24 0 L 0 0 0 24",
            fill: "none",
            stroke: clrGrid,
            "stroke-width": "0.5"
        })
    );
    defs.appendChild(pat);

    let gradS = SvgEl("radialGradient", {
        id: "gsrv",
        cx: "50%",
        cy: "50%",
        r: "50%"
    });
    let s1 = SvgEl("stop");
    s1.setAttribute("offset", "0%");
    s1.setAttribute("stop-color", clrAccent);
    s1.setAttribute("stop-opacity", "0.18");
    gradS.appendChild(s1);
    let s2 = SvgEl("stop");
    s2.setAttribute("offset", "100%");
    s2.setAttribute("stop-color", clrAccent);
    s2.setAttribute("stop-opacity", "0.04");
    gradS.appendChild(s2);
    defs.appendChild(gradS);

    let gradA = SvgEl("radialGradient", {
        id: "gagt",
        cx: "50%",
        cy: "50%",
        r: "50%"
    });
    let a1 = SvgEl("stop");
    a1.setAttribute("offset", "0%");
    a1.setAttribute("stop-color", clrOk);
    a1.setAttribute("stop-opacity", "0.14");
    gradA.appendChild(a1);
    let a2 = SvgEl("stop");
    a2.setAttribute("offset", "100%");
    a2.setAttribute("stop-color", clrOk);
    a2.setAttribute("stop-opacity", "0.03");
    gradA.appendChild(a2);
    defs.appendChild(gradA);

    let gradW = SvgEl("radialGradient", {
        id: "gsel",
        cx: "50%",
        cy: "50%",
        r: "50%"
    });
    let w1 = SvgEl("stop");
    w1.setAttribute("offset", "0%");
    w1.setAttribute("stop-color", clrWarn);
    w1.setAttribute("stop-opacity", "0.14");
    gradW.appendChild(w1);
    let w2 = SvgEl("stop");
    w2.setAttribute("offset", "100%");
    w2.setAttribute("stop-color", clrWarn);
    w2.setAttribute("stop-opacity", "0.03");
    gradW.appendChild(w2);
    defs.appendChild(gradW);

    svg.appendChild(defs);
    svg.appendChild(SvgEl("rect", { width: W, height: H, fill: clrBg }));
    svg.appendChild(SvgEl("rect", { width: W, height: H, fill: "url(#tg)" }));

    if (!State.serverRunning && !State.agentList.length) {
        let emptyG = SvgEl("g");
        emptyG.appendChild(
            SvgEl("circle", {
                cx: W / 2,
                cy: H / 2,
                r: "40",
                fill: "none",
                stroke: clrBorder,
                "stroke-width": "1",
                "stroke-dasharray": "4 4"
            })
        );
        let et = SvgEl("text", {
            x: W / 2,
            y: H / 2 + 4,
            "text-anchor": "middle",
            fill: clrMuted,
            "font-family": "monospace",
            "font-size": "9",
            "letter-spacing": "2"
        });
        et.textContent = "NO CONNECTIONS";
        emptyG.appendChild(et);
        svg.appendChild(emptyG);
        return;
    }

    let cx = W / 2,
        cy = H / 2;
    let rad = Math.min(W, H) * 0.32;
    let n = State.agentList.length;

    State.agentList.forEach((a, i) => {
        let angle = (2 * Math.PI * i) / Math.max(n, 1) - Math.PI / 2;
        let ax = cx + rad * Math.cos(angle);
        let ay = cy + rad * Math.sin(angle);
        let sel = State.selectedId === a.ID;
        let pid = "tp" + i;
        let lineColor = sel ? clrWarn : clrAccent;

        svg.appendChild(
            SvgEl("line", {
                x1: cx,
                y1: cy,
                x2: ax,
                y2: ay,
                stroke: sel ? "rgba(217,119,6,0.3)" : "rgba(37,99,235,0.18)",
                "stroke-width": sel ? "1.5" : "1",
                "stroke-dasharray": "4 4"
            })
        );

        let pathEl = SvgEl("path", {
            id: pid,
            d: "M" + cx + "," + cy + " L" + ax + "," + ay,
            fill: "none"
        });
        svg.appendChild(pathEl);

        let pkt = SvgEl("circle", {
            r: "2.5",
            fill: sel ? clrWarn : clrAccent,
            opacity: "0.8"
        });
        let anim = SvgEl("animateMotion", {
            dur: 2.2 + i * 0.5 + "s",
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
            sel,
            { clrAccent, clrOk, clrWarn, clrMuted, clrText, clrBorder, isDark }
        );
    });

    DrawTopoNode(
        svg,
        cx,
        cy,
        "C2 SERVER",
        State.serverHost + ":" + State.serverPort,
        true,
        null,
        false,
        { clrAccent, clrOk, clrWarn, clrMuted, clrText, clrBorder, isDark }
    );
}

function DrawTopoNode(svg, x, y, label, sub, isServer, onClick, sel, colors) {
    let { clrAccent, clrOk, clrWarn, clrMuted, clrText, clrBorder, isDark } =
        colors;
    let g = SvgEl("g");
    if (onClick) {
        g.style.cursor = "pointer";
        g.addEventListener("click", onClick);
    }

    let r = isServer ? 26 : 18;
    let nodeColor = isServer ? clrAccent : sel ? clrWarn : clrOk;
    let fillGrad = isServer ? "url(#gsrv)" : sel ? "url(#gsel)" : "url(#gagt)";
    let bgFill = isDark ? "#141720" : "#ffffff";

    g.appendChild(
        SvgEl("circle", {
            cx: x,
            cy: y,
            r: r + 8,
            fill: "none",
            stroke: nodeColor,
            "stroke-width": "0.5",
            "stroke-dasharray": isServer ? "0" : "3 3",
            opacity: "0.3"
        })
    );
    g.appendChild(
        SvgEl("circle", {
            cx: x,
            cy: y,
            r: r,
            fill: bgFill,
            stroke: nodeColor,
            "stroke-width": "1.5"
        })
    );
    g.appendChild(SvgEl("circle", { cx: x, cy: y, r: r, fill: fillGrad }));

    let iconPath;
    if (isServer) {
        iconPath =
            "M-7,-6 L7,-6 L7,6 L-7,6 Z M-5,-4 L5,-4 M-5,-1 L5,-1 M-5,2 L3,2";
    } else {
        iconPath =
            "M-7,-5 L7,-5 L7,3 L-7,3 Z M-4,3 L4,3 L4,5 L-4,5 Z M-5,-3 L5,-3 M-5,-1 L3,-1";
    }

    let iconG = SvgEl("g");
    iconG.setAttribute("transform", "translate(" + x + "," + y + ")");
    let iconRect = SvgEl("path", {
        d: iconPath,
        fill: "none",
        stroke: nodeColor,
        "stroke-width": "1.2",
        "stroke-linecap": "round",
        "stroke-linejoin": "round"
    });
    iconG.appendChild(iconRect);
    g.appendChild(iconG);

    let lbl = SvgEl("text", {
        x,
        y: y + r + 13,
        "text-anchor": "middle",
        fill: nodeColor,
        "font-family": "monospace",
        "font-size": "8.5",
        "font-weight": "700",
        "letter-spacing": "0.8"
    });
    lbl.textContent = label.length > 11 ? label.substring(0, 10) + "…" : label;
    g.appendChild(lbl);

    if (sub) {
        let sub2 = SvgEl("text", {
            x,
            y: y + r + 24,
            "text-anchor": "middle",
            fill: clrMuted,
            "font-family": "monospace",
            "font-size": "7.5"
        });
        sub2.textContent = sub;
        g.appendChild(sub2);
    }

    svg.appendChild(g);
}

function ShowLogin(msg) {
    let old = document.getElementById("login-overlay");
    if (old) old.remove();
    let ov = document.createElement("div");
    ov.id = "login-overlay";
    ov.style.cssText =
        "position:fixed;inset:0;background:rgba(0,0,0,0.7);display:flex;align-items:center;justify-content:center;z-index:9999;backdrop-filter:blur(4px);";
    ov.innerHTML = `
    <div style="background:var(--bg1);border:1px solid var(--border-strong);border-radius:6px;padding:36px 32px;width:340px;max-width:94vw;text-align:center;box-shadow:0 8px 32px rgba(0,0,0,0.3);">
      <div style="font-family:'Syne',sans-serif;font-size:20px;font-weight:800;color:var(--text);letter-spacing:2px;margin-bottom:3px;">RAVEN</div>
      <div style="font-family:'JetBrains Mono',monospace;font-size:9px;color:var(--text-muted);letter-spacing:1.5px;margin-bottom:28px;">TEAMSERVER · OPERATOR AUTH</div>
      ${msg ? `<div style="color:var(--err,#dc2626);font-family:'JetBrains Mono',monospace;font-size:11px;margin-bottom:14px;padding:8px 10px;background:rgba(220,38,38,0.07);border:1px solid rgba(220,38,38,0.2);border-radius:4px;">${Esc(msg)}</div>` : ""}
      <div style="text-align:left;margin-bottom:10px;">
        <label style="display:block;font-family:'Inter',sans-serif;font-size:11px;font-weight:500;color:var(--text-dim);margin-bottom:4px;">Username</label>
        <input id="li-user" type="text" placeholder="username" autocomplete="off"
          style="width:100%;box-sizing:border-box;background:var(--bg0);border:1px solid var(--border-strong);border-radius:4px;padding:9px 11px;color:var(--text);font-family:'JetBrains Mono',monospace;font-size:12px;outline:none;transition:border-color 0.12s;">
      </div>
      <div style="text-align:left;margin-bottom:20px;">
        <label style="display:block;font-family:'Inter',sans-serif;font-size:11px;font-weight:500;color:var(--text-dim);margin-bottom:4px;">Password</label>
        <input id="li-pass" type="password" placeholder="••••••••"
          style="width:100%;box-sizing:border-box;background:var(--bg0);border:1px solid var(--border-strong);border-radius:4px;padding:9px 11px;color:var(--text);font-family:'JetBrains Mono',monospace;font-size:12px;outline:none;transition:border-color 0.12s;">
      </div>
      <button id="li-btn" onclick="DoLogin()"
        style="width:100%;background:#2563eb;color:#fff;border:none;border-radius:4px;padding:10px;font-weight:600;font-family:'Inter',sans-serif;font-size:13px;cursor:pointer;letter-spacing:0.3px;transition:background 0.12s;">
        Sign In</button>
      <div id="li-err" style="color:#dc2626;font-family:'JetBrains Mono',monospace;font-size:11px;margin-top:12px;min-height:16px;"></div>
    </div>`;
    document.body.appendChild(ov);
    let u = document.getElementById("li-user");
    let p = document.getElementById("li-pass");
    if (u) {
        u.focus();
        u.addEventListener("focus", () => (u.style.borderColor = "#2563eb"));
        u.addEventListener("blur", () => (u.style.borderColor = ""));
        u.onkeydown = e => {
            if (e.key === "Enter" && p) p.focus();
        };
    }
    if (p) {
        p.addEventListener("focus", () => (p.style.borderColor = "#2563eb"));
        p.addEventListener("blur", () => (p.style.borderColor = ""));
        p.onkeydown = e => {
            if (e.key === "Enter") DoLogin();
        };
    }
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
