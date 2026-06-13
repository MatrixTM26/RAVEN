package com.tomcat.iface;

import com.tomcat.core.db.TeamDatabase;
import com.tomcat.core.db.TeamDatabase.OperatorRole;
import com.tomcat.core.event.EventManager.EventType;
import com.tomcat.core.output.Logger;
import com.tomcat.core.server.ListenerMode;
import com.tomcat.core.server.TomcatServer;
import com.tomcat.core.session.Session;
import com.tomcat.iface.banner.AUTHBanner;
import com.tomcat.iface.banner.CLIBanner;
import com.tomcat.iface.banner.EndBanner;
import com.tomcat.iface.banner.TBanner;
import com.tomcat.utils.AnsiColor;
import com.tomcat.utils.ServerConfig;
import com.tomcat.utils.SystemHelper;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class CLI {

    private final ServerConfig Config;
    private final TeamDatabase Db;
    private TomcatServer Server;
    private Instant ServerStartTime;
    private final List<String> Logs = new CopyOnWriteArrayList<>();
    private final int MaxLogs;
    private volatile boolean Running = true;
    private int CurrentSession = -1;
    private ListenerMode ActiveMode = ListenerMode.MULTI;
    private String OperatorName = null;
    private OperatorRole OpRole = null;
    private boolean IsTeamMode = false;

    public CLI(ServerConfig Config) {
        this.Config = Config;
        this.MaxLogs = Config.GetMaxLogEntries();
        this.Db = TeamDatabase.Connect(Config);
    }

    private int TermWidth() {
        try {
            String Cols = System.getenv("COLUMNS");
            if (Cols != null && !Cols.isBlank()) return Math.max(40, Integer.parseInt(Cols.trim()));
            ProcessBuilder Pb = new ProcessBuilder("tput", "cols");
            Pb.redirectErrorStream(true);
            Process P = Pb.start();
            String Out = new String(P.getInputStream().readAllBytes()).trim();
            P.waitFor();
            if (!Out.isBlank()) return Math.max(40, Integer.parseInt(Out));
        } catch (Exception Ignored) {}
        return 80;
    }

    private int ContentWidth() {
        return Math.max(36, TermWidth() - 4);
    }

    private String Box(String Title) {
        int W = ContentWidth();
        int Pad = Math.max(0, (W - Title.length()) / 2);
        String T = "  " + AnsiColor.Red + "┌" + "─".repeat(W) + "┐" + AnsiColor.Reset;
        String M =
            "  " +
            AnsiColor.Red +
            "│" +
            " ".repeat(Pad) +
            AnsiColor.White +
            Title +
            " ".repeat(Math.max(0, W - Pad - Title.length())) +
            AnsiColor.Red +
            "│" +
            AnsiColor.Reset;
        String B = "  " + AnsiColor.Red + "└" + "─".repeat(W) + "┘" + AnsiColor.Reset;
        return "\n" + T + "\n" + M + "\n" + B;
    }

    private String OutputBox(String Output) {
        int W = Math.max(34, ContentWidth() - 2);
        String Label = "─ Output ";
        String T =
            "  " + AnsiColor.Green + "┌" + Label + "─".repeat(Math.max(0, W - Label.length())) + "┐" + AnsiColor.Reset;
        String Bot = "  " + AnsiColor.Green + "└" + "─".repeat(W) + "┘" + AnsiColor.Reset;
        StringBuilder Sb = new StringBuilder(T + "\n");
        for (String Line : Output.split("\n")) {
            String Stripped = Line.replaceAll("\u001B\\[[;\\d?]*[A-Za-z]|\u001B[=>]|\r", "");
            while (Stripped.length() > W) {
                String Chunk = Stripped.substring(0, W);
                Sb.append("  ")
                    .append(AnsiColor.Green)
                    .append("│ ")
                    .append(AnsiColor.White)
                    .append(Chunk)
                    .append(AnsiColor.Green)
                    .append(" │")
                    .append(AnsiColor.Reset)
                    .append("\n");
                Stripped = Stripped.substring(W);
            }
            int Pad = Math.max(0, W - Stripped.length());
            Sb.append("  ")
                .append(AnsiColor.Green)
                .append("│ ")
                .append(AnsiColor.White)
                .append(Stripped)
                .append(" ".repeat(Pad))
                .append(" ")
                .append(AnsiColor.Green)
                .append("│")
                .append(AnsiColor.Reset)
                .append("\n");
        }
        return Sb + Bot;
    }

    private String Divider() {
        return "  " + AnsiColor.Red + "─".repeat(ContentWidth()) + AnsiColor.Reset;
    }

    private void ShowHelp() {
        SystemHelper.ClearScreen();
        TBanner.Logo();
        AUTHBanner.Logo();
        System.out.println(Box("COMMAND REFERENCE"));
        System.out.println();
        CLIBanner.Logo();
        if (IsTeamMode && OperatorName != null) {
            System.out.println();
            System.out.printf(
                "  %s[TEAMSERVER MODE]%s  Operator: %s%s%s  Role: %s%s%s%n",
                AnsiColor.Red,
                AnsiColor.Reset,
                AnsiColor.White,
                OperatorName,
                AnsiColor.Reset,
                AnsiColor.White,
                OpRole != null ? OpRole.name() : "?",
                AnsiColor.Reset
            );
            if (OpRole != null) System.out.printf(
                "  %sPermissions:%s %s%n",
                AnsiColor.Red,
                AnsiColor.White,
                OpRole.PermissionString()
            );
        }
        System.out.println();
    }

    private boolean TeamLogin(BufferedReader Reader) throws IOException {
        SystemHelper.ClearScreen();
        TBanner.Logo();
        AUTHBanner.Logo();
        System.out.println(Box("TEAMSERVER LOGIN"));
        System.out.println();
        System.out.printf(
            "  %sDefault credentials: admin / admin (change after first login)%s%n%n",
            AnsiColor.White,
            AnsiColor.Reset
        );

        for (int Attempt = 1; Attempt <= 3; Attempt++) {
            System.out.printf("  %sUsername:%s ", AnsiColor.Red, AnsiColor.Reset);
            System.out.flush();
            String User = Reader.readLine();
            if (User == null) return false;
            User = User.trim();

            System.out.printf("  %sPassword:%s ", AnsiColor.Red, AnsiColor.Reset);
            System.out.flush();
            String Pass;
            Console Cons = System.console();
            if (Cons != null) {
                char[] Pw = Cons.readPassword();
                Pass = Pw != null ? new String(Pw) : "";
            } else {
                Pass = Reader.readLine();
            }
            if (Pass == null) return false;

            if (!Db.ValidateOperator(User, TeamDatabase.HashPassword(Pass))) {
                System.out.printf(
                    "  %s✘ Invalid credentials (attempt %d/3)%s%n%n",
                    AnsiColor.Red,
                    Attempt,
                    AnsiColor.Reset
                );
                continue;
            }

            OperatorName = User;
            OpRole = Db.GetOperatorRole(User);
            Db.UpdateLastSeen(User);
            Logger.Info("Operator login: " + User + " [" + OpRole + "]");
            System.out.printf("%n  %s✔ Welcome, %s [%s]%s%n", AnsiColor.Green, User, OpRole, AnsiColor.Reset);
            System.out.printf("  %sPermissions:%s %s%n%n", AnsiColor.Red, AnsiColor.White, OpRole.PermissionString());
            return true;
        }
        System.out.printf("  %s✘ Authentication failed — exiting%s%n", AnsiColor.Red, AnsiColor.Reset);
        return false;
    }

    private void ShowSessions() {
        if (Server == null) {
            System.out.println("  ✘ Server not running");
            return;
        }
        List<Session> Sessions = Server.GetSessions().GetAll();
        System.out.println(Box("ACTIVE SESSIONS"));
        System.out.println();
        if (Sessions.isEmpty()) {
            System.out.println("  ⚠ No active sessions\n");
            return;
        }
        int W = ContentWidth();
        System.out.printf(
            "  %s%-5s %-14s %-14s %-16s %-10s %-10s %s%s%n",
            AnsiColor.Red,
            "ID",
            "NAME/CERT",
            "TYPE",
            "IP",
            "OS",
            "USER",
            "SESSION-KEY",
            AnsiColor.Reset
        );
        System.out.println(Divider());
        for (Session S : Sessions) System.out.printf(
            "  %s#%-4d %-14s %-14s %-16s %-10s %-10s %s%s%n",
            AnsiColor.White,
            S.GetId(),
            S.GetDisplayName().length() > 14 ? S.GetDisplayName().substring(0, 13) + "…" : S.GetDisplayName(),
            S.GetSessionType().name(),
            S.GetAgentIp(),
            S.GetOs().length() > 10 ? S.GetOs().substring(0, 9) + "…" : S.GetOs(),
            S.GetUser(),
            S.GetSessionKey(),
            AnsiColor.Reset
        );
        System.out.println();
    }

    private void ShowStatus() {
        if (Server == null || !Server.IsRunning()) {
            System.out.println("  ✘ Server not running");
            return;
        }
        long Up = ServerStartTime != null ? Duration.between(ServerStartTime, Instant.now()).getSeconds() : 0;
        System.out.println(Box("SERVER STATUS"));
        System.out.println();
        System.out.printf("  %sStatus    %s● ONLINE%n", AnsiColor.Red, AnsiColor.Green);
        System.out.printf("  %sMode      %s%s%n", AnsiColor.Red, AnsiColor.White, ActiveMode.name());
        System.out.printf(
            "  %sAddress   %s%s:%d%n",
            AnsiColor.Red,
            AnsiColor.White,
            Server.GetHost(),
            Server.GetPort()
        );
        System.out.printf("  %sUptime    %s%s%n", AnsiColor.Red, AnsiColor.White, SystemHelper.FormatUptime(Up));
        System.out.printf("  %sSessions  %s%d%n", AnsiColor.Red, AnsiColor.White, Server.GetSessions().Count());
        System.out.printf(
            "  %sDB        %s%s (%s)%n",
            AnsiColor.Red,
            AnsiColor.White,
            Db.IsConnected() ? "Connected" : "Memory",
            Config.GetDbType()
        );
        if (IsTeamMode && OperatorName != null) System.out.printf(
            "  %sOperator  %s%s [%s]%n",
            AnsiColor.Red,
            AnsiColor.White,
            OperatorName,
            OpRole
        );
        System.out.println();
    }

    private void ShowStats() {
        if (Server == null) {
            System.out.println("  ✘ Server not running");
            return;
        }
        Map<String, Integer> Stats = Server.GetSessions().GetStats();
        System.out.println(Box("SESSION STATISTICS"));
        System.out.println();
        System.out.printf(
            "  %sServer     %s%s:%d%n",
            AnsiColor.Red,
            AnsiColor.White,
            Server.GetHost(),
            Server.GetPort()
        );
        System.out.printf("  %sTotal      %s%d%n", AnsiColor.Red, AnsiColor.White, Stats.get("Total"));
        System.out.printf("  %sTOMCAT     %s%d%n", AnsiColor.Red, AnsiColor.White, Stats.get("TOMCAT"));
        System.out.printf("  %sRaw Shell  %s%d%n", AnsiColor.Red, AnsiColor.White, Stats.get("REVERSE_SHELL"));
        System.out.println();
    }

    private void ShowLogs() {
        System.out.println(Box("RECENT LOGS"));
        System.out.println();
        if (Logs.isEmpty()) {
            System.out.println("  ⚠ No logs\n");
            return;
        }
        int Start = Math.max(0, Logs.size() - 25);
        for (int I = Start; I < Logs.size(); I++) System.out.println(Logs.get(I));
        System.out.println();
    }

    private void ShowOperators() {
        List<Map<String, Object>> Ops = Db.GetOperators();
        System.out.println(Box("OPERATORS (" + Ops.size() + ")"));
        System.out.println();
        System.out.printf(
            "  %s%-18s %-14s %-24s %-20s%s%n",
            AnsiColor.Red,
            "USERNAME",
            "ROLE",
            "PERMISSIONS",
            "LAST SEEN",
            AnsiColor.Reset
        );
        System.out.println(Divider());
        for (Map<String, Object> Op : Ops) {
            OperatorRole R = OperatorRole.FromString(Op.get("Role").toString());
            boolean IsSelf = Op.get("Username").toString().equals(OperatorName);
            String Mark = IsSelf ? AnsiColor.Green + " ◀ YOU" + AnsiColor.White : "";
            System.out.printf(
                "  %s%-18s %-14s %-24s %-20s%s%s%n",
                AnsiColor.White,
                Op.get("Username"),
                R.name(),
                R.PermissionString().split("\\[")[0].trim(),
                Op.getOrDefault("LastSeen", "Never"),
                Mark,
                AnsiColor.Reset
            );
        }
        System.out.println();
        System.out.println("  " + AnsiColor.Red + "Roles:" + AnsiColor.Reset);
        for (OperatorRole R : OperatorRole.values()) System.out.printf(
            "  %s%-14s%s %s%n",
            AnsiColor.White,
            R.name(),
            AnsiColor.Reset,
            R.PermissionString()
        );
        System.out.println();
    }

    private void DoExec(int SessionId, String Command) {
        if (Server == null) {
            System.out.println("  ✘ Server not running");
            return;
        }
        String Op = OperatorName != null ? OperatorName : "operator";
        String[] Result = Server.ExecuteCommand(SessionId, Command);
        boolean Ok = Boolean.parseBoolean(Result[0]);
        if (Ok) {
            System.out.println(OutputBox(Result[1]));
            AddLog(AnsiColor.Green + "◀ SESSION-" + SessionId + " OK" + AnsiColor.Reset, false);
        } else {
            System.out.println("  ✘ " + Result[1]);
            AddLog(AnsiColor.Red + "✘ SESSION-" + SessionId + " FAIL: " + Result[1] + AnsiColor.Reset, false);
        }
        Db.SaveCommandLog(SessionId, Op, Command, Result[1], Ok);
    }

    private void DoBroadcast(List<Integer> Ids, String Command) {
        if (Server == null) {
            System.out.println("  ✘ Server not running");
            return;
        }
        String Op = OperatorName != null ? OperatorName : "operator";
        System.out.printf("  ⟳ Broadcasting to %d session(s): %s%n", Ids.size(), Command);
        Map<Integer, String[]> Results = Server.BroadcastCommand(Ids, Command);
        System.out.println(Box("BROADCAST RESULTS — " + Results.size() + " sessions"));
        System.out.println();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            System.out.printf(
                "  %sSESSION-%-3d %s%s%n",
                Ok ? AnsiColor.Green : AnsiColor.Red,
                En.getKey(),
                Ok ? "✔ " : "✘ ",
                AnsiColor.Reset
            );
            System.out.println(OutputBox(En.getValue()[1]));
            Db.SaveCommandLog(En.getKey(), Op, Command, En.getValue()[1], Ok);
        }
    }

    private void DoBroadcastAll(String Command) {
        if (Server == null) {
            System.out.println("  ✘ Server not running");
            return;
        }
        int Total = Server.GetSessions().Count();
        if (Total == 0) {
            System.out.println("  ⚠ No active sessions");
            return;
        }
        String Op = OperatorName != null ? OperatorName : "operator";
        System.out.printf("  ⟳ Broadcasting to all %d session(s): %s%n", Total, Command);
        Map<Integer, String[]> Results = Server.BroadcastAll(Command);
        System.out.println(Box("BROADCAST-ALL RESULTS"));
        System.out.println();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            System.out.printf(
                "  %sSESSION-%-3d %s%s%n",
                Ok ? AnsiColor.Green : AnsiColor.Red,
                En.getKey(),
                Ok ? "✔ " : "✘ ",
                AnsiColor.Reset
            );
            System.out.println(OutputBox(En.getValue()[1]));
            Db.SaveCommandLog(En.getKey(), Op, Command, En.getValue()[1], Ok);
        }
    }

    private void InteractiveSession(int SessionId) {
        Optional<Session> Opt = Server.GetSessions().Get(SessionId);
        if (Opt.isEmpty()) {
            System.out.println("  ✘ Session not found");
            return;
        }
        Session S = Opt.get();
        SystemHelper.ClearScreen();
        TBanner.Logo();
        System.out.println(Box("INTERACTIVE SESSION"));
        System.out.printf(
            "%n  %s[%s]%s  #%d  %s@%s  (%s)%n",
            AnsiColor.Red,
            S.GetDisplayName(),
            AnsiColor.Reset,
            SessionId,
            S.GetUser(),
            S.GetHostname(),
            S.GetOs()
        );
        System.out.printf("  IP: %s  Key: %s%n%n", S.GetAgentIp(), S.GetSessionKey());
        System.out.println("  → Type 'back' to return");
        AddLog(AnsiColor.Red + "↳ Entered [" + S.GetDisplayName() + "] SESSION-" + SessionId + AnsiColor.Reset, false);
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        CurrentSession = SessionId;
        while (CurrentSession == SessionId) {
            try {
                System.out.printf(
                    "%n%s(%s%s#%d%s%s)%s ≫ %s",
                    AnsiColor.Red,
                    AnsiColor.White,
                    S.GetDisplayName(),
                    SessionId,
                    AnsiColor.Red,
                    AnsiColor.White,
                    AnsiColor.Red,
                    AnsiColor.Reset
                );
                String Cmd = Reader.readLine();
                if (Cmd == null || Cmd.trim().isEmpty()) continue;
                Cmd = Cmd.trim();
                if (Cmd.equalsIgnoreCase("back")) {
                    CurrentSession = -1;
                    System.out.printf("  %s◀ Returned%s%n", AnsiColor.Red, AnsiColor.Reset);
                    break;
                }
                if (Cmd.equalsIgnoreCase("clear")) {
                    SystemHelper.ClearScreen();
                    TBanner.Logo();
                    continue;
                }
                AddLog(AnsiColor.Red + "▶ [" + S.GetDisplayName() + "]: " + Cmd + AnsiColor.Reset, false);
                DoExec(SessionId, Cmd);
            } catch (IOException E) {
                break;
            }
        }
    }

    private void KillSession(int SessionId) {
        Optional<Session> Opt = Server.GetSessions().Get(SessionId);
        if (Opt.isEmpty()) {
            System.out.println("  ✘ Session not found");
            return;
        }
        String Name = Opt.get().GetDisplayName();
        Server.RemoveSession(SessionId);
        System.out.printf("  ✔ SESSION-%d [%s] terminated%n", SessionId, Name);
        AddLog(AnsiColor.Green + "✔ SESSION-" + SessionId + " [" + Name + "] killed" + AnsiColor.Reset, false);
    }

    private boolean CanManage() {
        return !IsTeamMode || (OpRole != null && OpRole.CanManage());
    }

    private boolean CanKick() {
        return !IsTeamMode || (OpRole != null && OpRole.CanKickOperator());
    }

    private boolean CanExec() {
        return !IsTeamMode || (OpRole != null && OpRole.CanExecute());
    }

    private void ServerEventHandler(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case ServerStarted -> AddLog(
                AnsiColor.White +
                "✔ Server listening on " +
                Data.get("Host") +
                ":" +
                Data.get("Port") +
                AnsiColor.Reset,
                true
            );
            case AgentConnected -> AddLog(
                AnsiColor.Green +
                "★ [" +
                Data.get("AgentName") +
                "] SESSION-" +
                Data.get("ID") +
                " key=" +
                Data.get("SessionKey") +
                " (" +
                Data.get("OS") +
                ")" +
                AnsiColor.Reset,
                true
            );
            case AgentDisconnected -> AddLog(
                AnsiColor.Red +
                "✖ SESSION-" +
                Data.get("ID") +
                " disconnected: " +
                Data.get("Reason") +
                AnsiColor.Reset,
                true
            );
            case AgentRemoved -> AddLog(
                AnsiColor.White + "⊘ SESSION-" + Data.get("ID") + " removed" + AnsiColor.Reset,
                false
            );
            case Error -> AddLog(AnsiColor.Red + "✘ Error: " + Data.get("Message") + AnsiColor.Reset, true);
        }
    }

    private boolean StartServer(String Host, int Port, ListenerMode Mode) {
        Server = new TomcatServer(Host, Port, Mode, Config);
        Server.AddEventListener(this::ServerEventHandler);
        boolean[] Result = Server.StartServer();
        if (!Result[0]) {
            AddLog(AnsiColor.Red + "✘ Failed to start server" + AnsiColor.Reset, true);
            return false;
        }
        ServerStartTime = Instant.now();
        Thread T = new Thread(Server::AcceptConnections, "AcceptConnections");
        T.setDaemon(true);
        T.start();
        return true;
    }

    private void AddLog(String Msg, boolean PrintNow) {
        String Ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String Entry = "  " + AnsiColor.Red + "[" + Ts + "]" + AnsiColor.Reset + " " + Msg;
        Logs.add(Entry);
        if (Logs.size() > MaxLogs) Logs.remove(0);
        if (PrintNow) System.out.println(Entry);
    }

    private void RunLoop() {
        ShowHelp();
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        int LastCnt = Logs.size();
        while (Running) {
            try {
                int Cur = Logs.size();
                if (Cur > LastCnt) {
                    System.out.printf(
                        "  %s● %d new event(s) — type 'logs' to view%s%n",
                        AnsiColor.White,
                        Cur - LastCnt,
                        AnsiColor.Reset
                    );
                    LastCnt = Cur;
                }
                System.out.printf(
                    "%n%s┌──(%sTOMCAT@C2%s)%n%s└─%s≫ %s",
                    AnsiColor.Red,
                    AnsiColor.White,
                    AnsiColor.Red,
                    AnsiColor.Red,
                    AnsiColor.White,
                    AnsiColor.Reset
                );
                String Input = Reader.readLine();
                if (Input == null || Input.trim().isEmpty()) continue;
                String[] Parts = Input.trim().split("\\s+", 3);
                String Cmd = Parts[0].toLowerCase();
                switch (Cmd) {
                    case "exit", "quit" -> {
                        System.out.printf("  %s⏻ Shutting down%s%n", AnsiColor.White, AnsiColor.Reset);
                        if (Server != null) Server.StopServer();
                        Db.Close();
                        Running = false;
                    }
                    case "help" -> ShowHelp();
                    case "clear" -> {
                        SystemHelper.ClearScreen();
                        TBanner.Logo();
                        LastCnt = Logs.size();
                    }
                    case "sessions", "agents" -> ShowSessions();
                    case "status" -> ShowStatus();
                    case "stats" -> ShowStats();
                    case "logs" -> {
                        ShowLogs();
                        LastCnt = Logs.size();
                    }
                    case "use" -> {
                        if (Parts.length < 2) {
                            System.out.println("  ⚠ Usage: use <id>");
                            continue;
                        }
                        try {
                            InteractiveSession(Integer.parseInt(Parts[1]));
                            LastCnt = Logs.size();
                        } catch (NumberFormatException E) {
                            System.out.println("  ✘ Invalid session ID");
                        }
                    }
                    case "exec" -> {
                        if (!CanExec()) {
                            System.out.println("  ✘ Insufficient permissions");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  ⚠ Usage: exec <id> <command>");
                            continue;
                        }
                        try {
                            DoExec(Integer.parseInt(Parts[1]), Parts[2]);
                        } catch (NumberFormatException E) {
                            System.out.println("  ✘ Invalid session ID");
                        }
                    }
                    case "broadcast" -> {
                        if (!CanExec()) {
                            System.out.println("  ✘ Insufficient permissions");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  ⚠ Usage: broadcast <id,id,...|all> <command>");
                            continue;
                        }
                        String IdsOrAll = Parts[1].toLowerCase();
                        String BCmd = Parts[2];
                        if (IdsOrAll.equals("all")) {
                            DoBroadcastAll(BCmd);
                        } else {
                            List<Integer> Ids = new ArrayList<>();
                            for (String S : IdsOrAll.split(",")) try {
                                Ids.add(Integer.parseInt(S.trim()));
                            } catch (Exception Ign) {}
                            if (Ids.isEmpty()) System.out.println("  ✘ No valid session IDs");
                            else DoBroadcast(Ids, BCmd);
                        }
                    }
                    case "kill" -> {
                        if (Parts.length < 2) {
                            System.out.println("  ⚠ Usage: kill <id>");
                            continue;
                        }
                        try {
                            KillSession(Integer.parseInt(Parts[1]));
                        } catch (NumberFormatException E) {
                            System.out.println("  ✘ Invalid session ID");
                        }
                    }
                    case "listop", "listoperators" -> ShowOperators();
                    case "addop", "addoperator" -> {
                        if (!CanManage()) {
                            System.out.println("  ✘ ADMIN/SUPER_ADMIN required");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  ⚠ Usage: addop <user> <pass> [SUPER_ADMIN|ADMIN|OPERATOR|MEMBER]");
                            continue;
                        }
                        String[] Tok = Parts[2].split("\\s+", 2);
                        String OpPass = Tok[0];
                        String OpRole = Tok.length > 1 ? Tok[1] : "OPERATOR";
                        if (Parts.length > 2 && Parts[2].contains(" ")) {
                            String[] Split = Parts[2].split(" ", 2);
                            OpPass = Split[0];
                            OpRole = Split[1];
                        }
                        if (OpPass.length() < 8) {
                            System.out.println("  ✘ Password must be ≥ 8 chars");
                            continue;
                        }
                        OperatorRole R = OperatorRole.FromString(OpRole);
                        if (R == OperatorRole.SUPER_ADMIN && (OpRole == null || !OpRole.IsSuperAdmin())) {
                            System.out.println("  ✘ Only SUPER_ADMIN can create SUPER_ADMIN accounts");
                            continue;
                        }
                        if (Db.CreateOperator(Parts[1], TeamDatabase.HashPassword(OpPass), R)) System.out.printf(
                            "  ✔ Operator created: %s [%s]  %s%n",
                            Parts[1],
                            R,
                            R.PermissionString()
                        );
                        else System.out.println("  ✘ Username already exists");
                    }
                    case "delop", "deleteoperator" -> {
                        if (!CanManage()) {
                            System.out.println("  ✘ ADMIN/SUPER_ADMIN required");
                            continue;
                        }
                        if (Parts.length < 2) {
                            System.out.println("  ⚠ Usage: delop <username>");
                            continue;
                        }
                        if (Parts[1].equals("admin")) {
                            System.out.println("  ✘ Cannot delete admin");
                            continue;
                        }
                        if (Db.DeleteOperator(Parts[1])) System.out.printf("  ✔ Deleted: %s%n", Parts[1]);
                        else System.out.println("  ✘ Operator not found");
                    }
                    case "kick", "kickop" -> {
                        if (!CanKick()) {
                            System.out.println("  ✘ SUPER_ADMIN role required to kick operators");
                            continue;
                        }
                        if (Parts.length < 2) {
                            System.out.println("  ⚠ Usage: kick <username>");
                            continue;
                        }
                        if (Parts[1].equals("admin") || Parts[1].equals(OperatorName)) {
                            System.out.println("  ✘ Cannot kick admin or yourself");
                            continue;
                        }
                        if (Db.DeleteOperator(Parts[1])) System.out.printf("  ✔ Kicked (removed): %s%n", Parts[1]);
                        else System.out.println("  ✘ Operator not found");
                    }
                    case "setrole", "changerole" -> {
                        if (!CanManage()) {
                            System.out.println("  ✘ ADMIN/SUPER_ADMIN required");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  ⚠ Usage: setrole <user> <SUPER_ADMIN|ADMIN|OPERATOR|MEMBER>");
                            continue;
                        }
                        if (Parts[1].equals("admin")) {
                            System.out.println("  ✘ Cannot change admin role");
                            continue;
                        }
                        OperatorRole NR = OperatorRole.FromString(Parts[2]);
                        if (NR == OperatorRole.SUPER_ADMIN && (OpRole == null || !OpRole.IsSuperAdmin())) {
                            System.out.println("  ✘ Only SUPER_ADMIN can promote to SUPER_ADMIN");
                            continue;
                        }
                        if (Db.UpdateOperatorRole(Parts[1], NR)) System.out.printf(
                            "  ✔ Role updated: %s → %s  %s%n",
                            Parts[1],
                            NR,
                            NR.PermissionString()
                        );
                        else System.out.println("  ✘ Operator not found");
                    }
                    case "passwd", "changepassword" -> {
                        if (!CanManage()) {
                            System.out.println("  ✘ ADMIN/SUPER_ADMIN required");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  ⚠ Usage: passwd <user> <newpass>");
                            continue;
                        }
                        if (Parts[2].length() < 8) {
                            System.out.println("  ✘ Password must be ≥ 8 chars");
                            continue;
                        }
                        if (Db.UpdateOperatorPassword(Parts[1], TeamDatabase.HashPassword(Parts[2]))) System.out.printf(
                            "  ✔ Password updated: %s%n",
                            Parts[1]
                        );
                        else System.out.println("  ✘ Operator not found");
                    }
                    default -> {
                        System.out.printf("  %s✘ Unknown command: %s%s%n", AnsiColor.Red, Cmd, AnsiColor.Reset);
                        System.out.println("  → Type 'help' for available commands");
                    }
                }
            } catch (IOException E) {
                break;
            }
        }
        EndBanner.EndLogo();
    }

    public void Run(String Host, int Port, ListenerMode Mode) {
        this.ActiveMode = Mode;
        this.IsTeamMode = false;
        if (!StartServer(Host, Port, Mode)) return;
        try {
            Thread.sleep(500);
        } catch (InterruptedException Ign) {}
        RunLoop();
    }

    public void RunTeamServer(String Host, int Port, ListenerMode Mode) throws IOException {
        this.ActiveMode = Mode;
        this.IsTeamMode = true;
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        if (!TeamLogin(Reader)) return;
        if (!StartServer(Host, Port, Mode)) return;
        try {
            Thread.sleep(500);
        } catch (InterruptedException Ign) {}
        RunLoop();
    }
}
