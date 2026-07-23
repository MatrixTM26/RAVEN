package com.raven.interfaces.CLI;

import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import com.raven.interfaces.APP.WebApp;
import com.raven.interfaces.banner.CLIBanner;
import com.raven.utils.AnsiColor;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CLI {

    private static final Object SharedServerLock = new Object();
    private static volatile RavenServer SharedServer = null;
    private static volatile Instant SharedServerStartTime = null;
    private static volatile List<String> SharedLogs = new CopyOnWriteArrayList<>();
    private final ServerConfig Config;
    private final TeamDatabase Database;
    private RavenServer Server;
    private Instant ServerStartTime;
    private final List<String> Logs = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> ChatMessages = new CopyOnWriteArrayList<>();
    private static final int MaxChat = 500;
    private final int MaxLogs;
    private volatile boolean Running = true;
    private int CurrentSession = -1;
    private ListenerMode ActiveMode = ListenerMode.MULTI;
    private String OperatorName = null;
    private OperatorRole OperatorRoles = null;
    private boolean IsTeamServerMode = false;

    public CLI(ServerConfig Config) {
        this.Config = Config;
        this.MaxLogs = Config.GetMaxLogEntries();
        this.Database = TeamDatabase.Connect(Config);
    }

    private volatile int CachedWidth = -1;
    private final AtomicInteger TermWidth = new AtomicInteger(-1);
    private Thread WidthPoller;

    private void StartWidthPoller() {
        WidthPoller = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int Width = DetectTermWidth();
                TermWidth.set(Width);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "term-width-poller");
        WidthPoller.setDaemon(true);
        WidthPoller.start();
    }

    private int TermWidth() {
        String Colums = System.getenv("COLUMNS");
        if (Colums != null && !Colums.isBlank()) {
            try {
                return Math.max(40, Integer.parseInt(Colums.trim()));
            } catch (NumberFormatException Ignored) {}
        }

        if (WidthPoller == null || !WidthPoller.isAlive()) StartWidthPoller();

        int Cached = TermWidth.get();
        if (Cached > 0) return Cached;

        int Detected = DetectTermWidth();
        TermWidth.set(Detected);
        return Detected;
    }

    private int DetectTermWidth() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            Integer Width = WinTermWidth();
            return Width != null ? Width : 80;
        }
        Integer UnixTermWidth = UnixTermWidth();
        return UnixTermWidth != null ? UnixTermWidth : 80;
    }

    private Integer UnixTermWidth() {
        Integer Width = UnixTermWidthidthStty();
        if (Width != null) return Width;
        return UnixTermWidthidthTput();
    }

    private Integer UnixTermWidthidthStty() {
        try {
            ProcessBuilder ExecuteProcess = new ProcessBuilder("sh", "-c", "stty size < /dev/tty");
            ExecuteProcess.redirectErrorStream(true);
            Process ExecuteThread = ExecuteProcess.start();
            String Output = new String(ExecuteThread.getInputStream().readAllBytes()).trim();
            ExecuteThread.waitFor();
            String[] Parts = Output.split("\\s+");
            if (Parts.length >= 2) return Math.max(40, Integer.parseInt(Parts[1]));
        } catch (Exception Ignored) {}
        return null;
    }

    private Integer UnixTermWidthidthTput() {
        try {
            ProcessBuilder ExecuteProcess = new ProcessBuilder("sh", "-c", "tput cols < /dev/tty");
            ExecuteProcess.redirectErrorStream(true);
            Process ExecuteThread = ExecuteProcess.start();
            String Output = new String(ExecuteThread.getInputStream().readAllBytes()).trim();
            ExecuteThread.waitFor();
            if (!Output.isBlank()) return Math.max(40, Integer.parseInt(Output));
        } catch (Exception Ignored) {}
        return null;
    }

    private Integer WinTermWidth() {
        Integer Width = WinWidthPowerShell();
        if (Width != null) return Width;
        return WinWidthModeCon();
    }

    private Integer WinWidthPowerShell() {
        try {
            ProcessBuilder ExecuteProcess = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "[Console]::WindowWidth");
            ExecuteProcess.redirectErrorStream(true);
            ExecuteProcess.redirectInput(ProcessBuilder.Redirect.INHERIT);
            Process ExecuteThread = ExecuteProcess.start();
            String Output = new String(ExecuteThread.getInputStream().readAllBytes()).trim();
            boolean Finished = ExecuteThread.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!Finished) {
                ExecuteThread.destroyForcibly();
                return null;
            }
            if (!Output.isBlank()) {
                String Digits = Output.replaceAll("\\D+", "");
                if (!Digits.isBlank()) {
                    int Value = Integer.parseInt(Digits);
                    if (Value >= 20 && Value <= 1000) return Math.max(40, Value);
                }
            }
        } catch (Exception Ignored) {}
        return null;
    }

    private Integer WinWidthModeCon() {
        try {
            ProcessBuilder ExecuteProcess = new ProcessBuilder("cmd.exe", "/c", "mode con");
            ExecuteProcess.redirectErrorStream(true);
            Process ExecuteThread = ExecuteProcess.start();
            String Output = new String(ExecuteThread.getInputStream().readAllBytes());
            boolean Finished = ExecuteThread.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!Finished) {
                ExecuteThread.destroyForcibly();
                return null;
            }
            for (String Line : Output.split("\\r?\\n")) {
                String Lower = Line.toLowerCase();
                if (Lower.contains("column") || Lower.contains("kolom")) {
                    String Digits = Line.replaceAll("[^0-9]", "");
                    if (!Digits.isBlank()) {
                        int Value = Integer.parseInt(Digits);
                        if (Value >= 20 && Value <= 1000) return Math.max(40, Value);
                    }
                }
            }
        } catch (Exception Ignored) {}
        return null;
    }

    private static final String FrameIndent = "  ";

    private int ContentWidth() {
        return Math.max(36, TermWidth() - FrameIndent.length() - 2);
    }

    private String Indent(String Text) {
        return FrameIndent + Text;
    }

    private String Box(String Title) {
        int Width = ContentWidth();
        int Inner = Math.max(0, Width - 2);
        int PaddingLeft = Math.max(0, (Inner - Title.length()) / 2);
        int PaddingRight = Math.max(0, Inner - PaddingLeft - Title.length());
        String HLine = "─";
        String VLine = "|";
        String ULLine = "┌";
        String URLine = "┐";
        String BLLine = "└";
        String BRLine = "┘";
        String Spaces = " ";
        String NLine = "\n";
        String Top = AnsiColor.White + ULLine + HLine.repeat(Inner) + URLine + AnsiColor.Reset;
        String Middle = AnsiColor.White + VLine + Spaces.repeat(PaddingLeft) + AnsiColor.Green + Title + Spaces.repeat(PaddingRight) + AnsiColor.White + VLine + AnsiColor.Reset;
        String Bottom = AnsiColor.White + BLLine + HLine.repeat(Inner) + BRLine + AnsiColor.Reset;
        return NLine + Indent(Top) + NLine + Indent(Middle) + NLine + Indent(Bottom);
    }

    private String OutputBox(String Output) {
        int Width = Math.max(34, ContentWidth());
        int Inner = Math.max(0, Width - 2);
        int LineWidth = Math.max(1, Inner - 2);
        String Label = "─ Output ";
        String HLine = "─";
        String VLine = "|";
        String ULLine = "┌";
        String URLine = "┐";
        String BLLine = "└";
        String BRLine = "┘";
        String Spaces = " ";
        String NLine = "\n";
        String Top = AnsiColor.Green + ULLine + Label + HLine.repeat(Math.max(0, Inner - Label.length())) + URLine + AnsiColor.Reset;
        String Bottom = AnsiColor.Green + BLLine + HLine.repeat(Inner) + BRLine + AnsiColor.Reset;
        StringBuilder StrBuilder = new StringBuilder(Indent(Top) + NLine);
        for (String Line : Output.split("\n", -1)) {
            String Stripped = Line.replaceAll("\u001B\\[[;\\d?]*[A-Za-z]|\u001B[=>]|\r", "");
            while (Stripped.length() > LineWidth) {
                String chunk = Stripped.substring(0, LineWidth);
                StrBuilder.append(Indent(AnsiColor.Green))
                    .append(VLine + Spaces)
                    .append(AnsiColor.White)
                    .append(chunk)
                    .append(AnsiColor.Green)
                    .append(Spaces + VLine)
                    .append(AnsiColor.Reset)
                    .append(NLine);
                Stripped = Stripped.substring(LineWidth);
            }
            int Padding = Math.max(0, LineWidth - Stripped.length());
            StrBuilder.append(Indent(AnsiColor.Green))
                .append(VLine + Spaces)
                .append(AnsiColor.White)
                .append(Stripped)
                .append(Spaces.repeat(Padding))
                .append(AnsiColor.Green)
                .append(Spaces + VLine)
                .append(AnsiColor.Reset)
                .append(NLine);
        }
        return StrBuilder.append(Indent(Bottom)).toString();
    }

    private String Divider() {
        String HLine = "─";
        return Indent(AnsiColor.White + HLine.repeat(ContentWidth()) + AnsiColor.Reset);
    }

    private void ShowHelp() {
        System.out.println(Box("COMMAND REFERENCE"));
        System.out.println();
        CLIBanner.Print();
        if (IsTeamServerMode && OperatorName != null) {
            System.out.println();
            Logger.Custom("  %s[TEAMSERVER MODE]%s  OPERATOR: %s%s%s  Role: %s%s%s%n", AnsiColor.Red, AnsiColor.Reset, AnsiColor.White, OperatorName, AnsiColor.Reset, AnsiColor.White, OperatorRoles != null ? OperatorRoles.name() : "?", AnsiColor.Reset);
            if (OperatorRoles != null) Logger.Custom("  %sPermissions:%s %s%n", AnsiColor.Red, AnsiColor.White, OperatorRoles.PermissionString());
        }
        System.out.println();
    }

    private boolean TeamLogin(BufferedReader Reader) throws IOException {
        System.out.println(Box("TEAMSERVER LOGIN"));
        System.out.println();
        Logger.Custom("  %sDefault credentials: admin / admin (change after first login)%s%n%n", AnsiColor.White, AnsiColor.Reset);
        for (int Attempt = 1; Attempt <= 3; Attempt++) {
            Logger.Custom("  %sUsername:%s ", AnsiColor.Red, AnsiColor.Reset);
            System.out.flush();
            String Username = Reader.readLine();
            if (Username == null) return false;
            Username = Username.trim();
            Logger.Custom("  %sPassword:%s ", AnsiColor.Red, AnsiColor.Reset);
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
            if (!Database.ValidateOperator(Username, TeamDatabase.HashPassword(Pass))) {
                Logger.Custom("  %sInvalid credentials - Attempt %d/3%s%n%n", AnsiColor.Red, Attempt, AnsiColor.Reset);
                continue;
            }
            OperatorName = Username;
            OperatorRoles = Database.GetOperatorRole(Username);
            Database.UpdateLastSeen(Username);
            Logger.Info("Operator login: " + Username + " [" + OperatorRoles + "]");
            Logger.Custom("  %n%sWelcome, %s [%s]%s%n", AnsiColor.Green, Username, OperatorRoles, AnsiColor.Reset);
            Logger.Custom("  %sPermissions:%s %s%n%n", AnsiColor.Red, AnsiColor.White, OperatorRoles.PermissionString());
            return true;
        }
        Logger.Error("authentication failed - exit.");
        return false;
    }

    private void ShowSessions() {
        if (Server == null) {
            System.out.println(Box("ACTIVE SESSIONS"));
            System.out.println();
            Logger.Info("running in cross-process mode - session list unavailable.");
            Logger.Info("use the primary operator terminal or webstart to view sessions.\n");
            return;
        }
        List<Session> Sessions = Server.GetSessions().GetAll();
        System.out.println(Box("ACTIVE SESSIONS"));
        System.out.println();
        if (Sessions.isEmpty()) {
            Logger.Info("no active sessions");
            return;
        }
        Logger.Custom("  %s%-5s %-14s %-14s %-16s %-10s %-10s %s%s%n", AnsiColor.Blue, "ID", "NAME/CERT", "TYPE", "IP", "OS", "USER", "SESSION-KEY", AnsiColor.Reset);
        System.out.println(Divider());
        for (Session S : Sessions) Logger.Custom("  %s#%-4d %-14s %-14s %-16s %-10s %-10s %s%s%n", AnsiColor.White, S.GetId(), S.GetDisplayName().length() > 14 ? S.GetDisplayName().substring(0, 13) + "-" : S.GetDisplayName(), S.GetSessionType().name(), S.GetAgentIp(), S.GetOs().length() > 10 ? S.GetOs().substring(0, 9) + "-" : S.GetOs(), S.GetUser(), S.GetSessionKey(), AnsiColor.Reset);
        System.out.println();
    }

    private void ShowStatus() {
        long Up = ServerStartTime != null ? Duration.between(ServerStartTime, Instant.now()).getSeconds() : 0;
        System.out.println(Box("SERVER STATUS"));
        System.out.println();
        if (Server == null || !Server.IsRunning()) {
            if (IsTeamServerMode && ServerStartTime != null) {
                Logger.Custom("  %sStatus      %sONLINE (cross-process)%n", AnsiColor.Red, AnsiColor.Green);
                Logger.Custom("  %sMode        %s%s%n", AnsiColor.Red, AnsiColor.White, ActiveMode.name());
                Logger.Custom("  %sAddress     %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Config.GetServerHost(), Config.GetServerPort());
                Logger.Custom("  %sUptime      %s%s (local session)%n", AnsiColor.Red, AnsiColor.White, SystemHelper.FormatUptime(Up));
                Logger.Custom("  %sSessions    %s(N/A - cross-process)%n", AnsiColor.Red, AnsiColor.White);
            } else {
                Logger.Custom("  %sStatus    %sOFFLINE%n", AnsiColor.Red, AnsiColor.Red);
            }
        } else {
            Logger.Custom("  %sStatus      %sONLINE%n", AnsiColor.Red, AnsiColor.Green);
            Logger.Custom("  %sMode        %s%s%n", AnsiColor.Red, AnsiColor.White, ActiveMode.name());
            Logger.Custom("  %sAddress     %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Server.GetHost(), Server.GetPort());
            Logger.Custom("  %sUptime      %s%s%n", AnsiColor.Red, AnsiColor.White, SystemHelper.FormatUptime(Up));
            Logger.Custom("  %sSessions    %s%d%n", AnsiColor.Red, AnsiColor.White, Server.GetSessions().Count());
        }
        Logger.Custom("  %sDB          %s%s (%s)%n", AnsiColor.Red, AnsiColor.White, Database.IsConnected() ? "connected" : "memory", Config.GetDatabaseType());
        if (IsTeamServerMode && OperatorName != null) Logger.Custom("  %sOperator    %s%s [%s]%n", AnsiColor.Red, AnsiColor.White, OperatorName, OperatorRoles);
        System.out.println();
    }

    private void ShowStats() {
        System.out.println(Box("SESSION STATISTICS"));
        System.out.println();
        if (Server == null) {
            Logger.Custom("  %sServer %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Config.GetServerHost(), Config.GetServerPort());
            Logger.Custom("  %sTotal  %s(N/A - cross-process mode)%n", AnsiColor.Red, AnsiColor.White);
            System.out.println();
            return;
        }
        Map<String, Integer> Stats = Server.GetSessions().GetStats();
        Logger.Custom("  %sServer    %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Server.GetHost(), Server.GetPort());
        Logger.Custom("  %sTOTAL     %s%d%n", AnsiColor.Red, AnsiColor.White, Stats.get("Total"));
        Logger.Custom("  %sRAVEN     %s%d%n", AnsiColor.Red, AnsiColor.White, Stats.get("RAVEN"));
        Logger.Custom("  %sRAW       %s%d%n", AnsiColor.Red, AnsiColor.White, Stats.get("REVERSE_SHELL"));
        System.out.println();
    }

    private void ShowLogs() {
        System.out.println(Box("RECENT LOGS"));
        System.out.println();
        if (Logs.isEmpty()) {
            Logger.Info(Indent("  no logs") + "\n");
            return;
        }
        int Start = Math.max(0, Logs.size() - 25);
        for (int I = Start; I < Logs.size(); I++) Logger.Info(Indent(Logs.get(I)));
        System.out.println();
    }

    private void ShowOperators() {
        List<Map<String, Object>> Ops = Database.GetOperators();
        System.out.println(Box("OPERATORS (" + Ops.size() + ")"));
        System.out.println();
        Logger.Custom("  %s%-18s %-14s %-24s %-20s%s%n", AnsiColor.Green, "USERNAME", "ROLE", "PERMISSIONS", "LAST SEEN", AnsiColor.Reset);
        System.out.println(Divider());
        for (Map<String, Object> Op : Ops) {
            OperatorRole R = OperatorRole.FromString(Op.get("Role").toString());
            boolean IsSelf = Op.get("Username").toString().equals(OperatorName);
            String Mark = IsSelf ? AnsiColor.Green + " < YOU" + AnsiColor.White : "";
            Logger.Custom("  %s%-18s %-14s %-24s %-20s%s%s%n", AnsiColor.White, Op.get("Username"), R.name(), "[" + R.ShortPerm() + "] " + R.PermissionString().replaceAll("^\\[.*?\\]\\s*", ""), Op.getOrDefault("LastSeen", "Never"), Mark, AnsiColor.Reset);
        }
        System.out.println();
        Logger.Info(AnsiColor.Red + "Roles:" + AnsiColor.Reset);
        for (OperatorRole R : OperatorRole.values()) Logger.Custom("    %s%-14s%s %s%n", AnsiColor.White, R.name(), AnsiColor.Reset, R.PermissionString());
        System.out.println();
    }

    private void ShowChat() {
        System.out.println(Box("CHAT MESSAGES"));
        System.out.println();
        if (ChatMessages.isEmpty()) {
            Logger.Info("no messages\n");
            return;
        }
        String MyName = OperatorName != null ? OperatorName : "";
        for (Map<String, Object> M : ChatMessages) {
            String From = M.getOrDefault("From", "?").toString();
            String To = M.getOrDefault("To", "all").toString();
            String Msg = M.getOrDefault("Message", "").toString();
            String Time = M.getOrDefault("Time", "").toString();
            boolean IsMine = From.equals(MyName);
            String ToLabel = To.equals("all") ? "all" : "> " + To;
            Logger.Custom("  %s[%s] %s%s%s [%s]: %s%s%n", IsMine ? AnsiColor.Green : AnsiColor.White, Time, IsMine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, ToLabel, Msg, AnsiColor.Reset);
        }
        System.out.println();
    }

    private void SendChat(String To, String Message) {
        if (OperatorName == null) {
            Logger.Info("not in team mode");
            return;
        }
        Map<String, Object> Entry = new LinkedHashMap<>();
        Entry.put("From", OperatorName);
        Entry.put("To", To);
        Entry.put("Message", Message);
        Entry.put("Time", java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        ChatMessages.add(Entry);
        if (ChatMessages.size() > MaxChat) ChatMessages.remove(0);
        Database.SaveChatLog(OperatorName, To, Message);
        Logger.Custom("Message sent to [%s]%s%n", AnsiColor.Green, To, AnsiColor.Reset);
    }

    private void ShowChatHistory() {
        List<Map<String, Object>> Logs = Database.GetChatLogs(100);
        System.out.println(Box("CHAT HISTORY (DB - last 100)"));
        System.out.println();
        if (Logs.isEmpty()) {
            Logger.Info("no chat history in database\n");
            return;
        }
        List<Map<String, Object>> Ordered = new ArrayList<>(Logs);
        Collections.reverse(Ordered);
        String MyName = OperatorName != null ? OperatorName : "";
        for (Map<String, Object> M : Ordered) {
            String From = M.getOrDefault("from_operator", "?").toString();
            String To = M.getOrDefault("to_operators", "all").toString();
            String Msg = M.getOrDefault("message", "").toString();
            String Time = M.getOrDefault("timestamp", "").toString();
            if (Time.length() > 8) Time = Time.substring(11, Math.min(19, Time.length()));
            boolean IsMine = From.equals(MyName);
            String ToLabel = "all".equals(To) ? "all" : "> " + To;
            Logger.Custom("  %s[%s] %s%s%s [%s]: %s%s%n", IsMine ? AnsiColor.Green : AnsiColor.White, Time, IsMine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, ToLabel, Msg, AnsiColor.Reset);
        }
        System.out.println();
    }

    private void DoExec(int SessionId, String Command) {
        if (Server == null) {
            Logger.Warn("session commands unavailable in cross-process mode.");
            Logger.Info("use the primary operator terminal to exec commands.");
            return;
        }
        String Op = OperatorName != null ? OperatorName : "operator";
        String[] Result = Server.ExecuteCommand(SessionId, Command);
        boolean Ok = Boolean.parseBoolean(Result[0]);
        if (Ok) {
            System.out.println(OutputBox(Result[1]));
            AddLog(AnsiColor.Green + "session-" + SessionId + " OK" + AnsiColor.Reset, false);
        } else {
            Logger.Info("" + Result[1]);
            AddLog(AnsiColor.Red + "session-" + SessionId + " FAIL: " + Result[1] + AnsiColor.Reset, false);
        }
        Database.SaveCommandLog(SessionId, Op, Command, Result[1], Ok);
    }

    private void DoBroadcast(List<Integer> Ids, String Command) {
        if (Server == null) {
            Logger.Info("broadcast unavailable in cross-process mode.");
            return;
        }
        String Op = OperatorName != null ? OperatorName : "operator";
        Logger.Custom("Broadcasting to %d session(s): %s%n", Ids.size(), Command);
        Map<Integer, String[]> Results = Server.BroadcastCommand(Ids, Command);
        System.out.println(Box("BROADCAST RESULTS - " + Results.size() + " sessions"));
        System.out.println();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            Logger.Custom("  %ssession-%-3d %s%s%n", Ok ? AnsiColor.Green : AnsiColor.Red, En.getKey(), Ok ? "" : "", AnsiColor.Reset);
            System.out.println(OutputBox(En.getValue()[1]));
            Database.SaveCommandLog(En.getKey(), Op, Command, En.getValue()[1], Ok);
        }
    }

    private void DoBroadcastAll(String Command) {
        if (Server == null) {
            Logger.Warn("unavailable in cross-process mode - use primary operator terminal");
            return;
        }
        int Total = Server.GetSessions().Count();
        if (Total == 0) {
            Logger.Info("no active sessions");
            return;
        }
        String Op = OperatorName != null ? OperatorName : "operator";
        Logger.Custom("  broadcasting to all %d session(s): %s%n", Total, Command);
        Map<Integer, String[]> Results = Server.BroadcastAll(Command);
        System.out.println(Box("BROADCAST-ALL RESULTS"));
        System.out.println();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            Logger.Custom("  %ssession-%-3d %s%s%n", Ok ? AnsiColor.Green : AnsiColor.Red, En.getKey(), Ok ? "" : "", AnsiColor.Reset);
            System.out.println(OutputBox(En.getValue()[1]));
            Database.SaveCommandLog(En.getKey(), Op, Command, En.getValue()[1], Ok);
        }
    }

    private void InteractiveSession(int SessionId) {
        Optional<Session> Opt = Server.GetSessions().Get(SessionId);
        if (Opt.isEmpty()) {
            Logger.Warn("session not found");
            return;
        }
        Session S = Opt.get();
        System.out.println(Box("INTERACTIVE SESSION"));
        String AgentInfo = "%n  %s[%s%s%s] %sID: %s%d %sUSER: %s%s@%s %sOS: %s%s %sARCH: %s%s %sIP: %s%s" + " %sTYPE: %s%s %sKEY: %s%s%s%n";
        Logger.Custom(AgentInfo, AnsiColor.Blue, AnsiColor.White, S.GetDisplayName(), AnsiColor.Blue, AnsiColor.Blue, AnsiColor.White, SessionId, AnsiColor.Blue, AnsiColor.White, S.GetUser(), S.GetHostname(), AnsiColor.Blue, AnsiColor.White, S.GetOs(), AnsiColor.Blue, AnsiColor.White, S.GetArch(), AnsiColor.Blue, AnsiColor.White, S.GetAgentIp(), AnsiColor.Blue, AnsiColor.White, S.GetSessionType(), AnsiColor.Blue, AnsiColor.White, S.GetSessionKey(), AnsiColor.Reset);
        Logger.Info("type 'back' to return");
        AddLog(AnsiColor.Blue + "entered [" + S.GetDisplayName() + "] session-" + SessionId + AnsiColor.Reset, false);
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        CurrentSession = SessionId;
        while (CurrentSession == SessionId) {
            try {
                String Prompt = "%n  %s[%s%s#%d%s%s]%s ~$ %s";
                Logger.Custom(Prompt, AnsiColor.Blue, AnsiColor.White, S.GetDisplayName(), SessionId, AnsiColor.Blue, AnsiColor.White, AnsiColor.Blue, AnsiColor.Reset);
                String Cmd = Reader.readLine();
                if (Cmd == null || Cmd.trim().isEmpty()) continue;
                Cmd = Cmd.trim();
                if (Cmd.equalsIgnoreCase("back")) {
                    CurrentSession = -1;
                    Logger.Info("returned to main console");
                    break;
                }
                if (Cmd.equalsIgnoreCase("clear")) {
                    SystemHelper.ClearScreen();
                    continue;
                }
                AddLog(AnsiColor.Blue + "[" + S.GetDisplayName() + "]: " + Cmd + AnsiColor.Reset, false);
                DoExec(SessionId, Cmd);
            } catch (IOException E) {
                break;
            }
        }
    }

    private void KillSession(int SessionId) {
        Optional<Session> Opt = Server.GetSessions().Get(SessionId);
        if (Opt.isEmpty()) {
            Logger.Warn("session not found");
            return;
        }
        String Name = Opt.get().GetDisplayName();
        Server.RemoveSession(SessionId);
        Logger.Custom("  session-%d [%s] terminated%n", SessionId, Name);
        AddLog(AnsiColor.Green + "session-" + SessionId + " [" + Name + "] killed" + AnsiColor.Reset, false);
    }

    private boolean CanManage() {
        return !IsTeamServerMode || (OperatorRoles != null && OperatorRoles.CanManage());
    }

    private boolean CanKick() {
        return !IsTeamServerMode || (OperatorRoles != null && OperatorRoles.CanKickOperator());
    }

    private boolean CanExec() {
        return !IsTeamServerMode || (OperatorRoles != null && OperatorRoles.CanExecute());
    }

    private void ServerEventHandler(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case ServerStarted -> AddLog(AnsiColor.White + " server listening on " + Data.get("Host") + ":" + Data.get("Port") + AnsiColor.Reset, true);
            case AgentConnected -> AddLog(AnsiColor.Green + "  [" + Data.get("AgentName") + "] session-" + Data.get("ID") + " key: " + Data.get("SessionKey") + " (" + Data.get("OS") + ")" + AnsiColor.Reset, true);
            case AgentDisconnected -> AddLog(AnsiColor.Red + "  session-" + Data.get("ID") + " disconnected: " + Data.get("Reason") + AnsiColor.Reset, true);
            case AgentRemoved -> AddLog(AnsiColor.White + "  session-" + Data.get("ID") + " removed" + AnsiColor.Reset, false);
            case Error -> AddLog(AnsiColor.Red + "  Error: " + Data.get("Message") + AnsiColor.Reset, true);
        }
    }

    private boolean StartServer(String Host, int Port, ListenerMode Mode) {
        if (IsTeamServerMode) {
            synchronized (SharedServerLock) {
                if (SharedServer != null && SharedServer.IsRunning()) {
                    Server = SharedServer;
                    ServerStartTime = SharedServerStartTime;
                    Logger.Info("Operator " + OperatorName + " joined existing server on " + Host + ":" + Port);
                    return true;
                }
                RavenServer S = new RavenServer(Host, Port, Mode, Config);
                S.AddEventListener(this::ServerEventHandler);
                boolean[] Result = S.StartServer();
                if (!Result[0]) {
                    if (IsPortBound(Host, Port)) {
                        Logger.Info("Operator " + OperatorName + " attached to existing listener on " + Host + ":" + Port + " (cross-process, DB-only mode)");
                        ServerStartTime = Instant.now();
                        AddLog(AnsiColor.White + "  attached to listener on " + Host + ":" + Port + " (session commands limited in cross-process mode)" + AnsiColor.Reset, true);
                        return true;
                    }
                    AddLog(AnsiColor.Red + "  failed to start server" + AnsiColor.Reset, true);
                    return false;
                }
                SharedServer = S;
                SharedServerStartTime = Instant.now();
                Server = SharedServer;
                ServerStartTime = SharedServerStartTime;
                Thread T = new Thread(SharedServer::AcceptConnections, "AcceptConnections");
                T.setDaemon(true);
                T.start();
                return true;
            }
        }
        Server = new RavenServer(Host, Port, Mode, Config);
        Server.AddEventListener(this::ServerEventHandler);
        boolean[] Result = Server.StartServer();
        if (!Result[0]) {
            AddLog(AnsiColor.Red + "  Failed to start server" + AnsiColor.Reset, true);
            return false;
        }
        ServerStartTime = Instant.now();
        Thread T = new Thread(Server::AcceptConnections, "AcceptConnections");
        T.setDaemon(true);
        T.start();
        return true;
    }

    private static boolean IsPortBound(String Host, int Port) {
        try (java.net.ServerSocket Test = new java.net.ServerSocket()) {
            Test.setReuseAddress(false);
            Test.bind(new java.net.InetSocketAddress(Port));
            return false;
        } catch (java.io.IOException E) {
            return true;
        }
    }

    private void AddLog(String Msg, boolean PrintNow) {
        String Times = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String Entry = "[" + Times + "]" + Msg;
        Logs.add(Entry);
        if (Logs.size() > MaxLogs) Logs.remove(0);
        if (PrintNow) Logger.Info(Entry);
    }

    private void RunLoop() {
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        int LastCnt = Logs.size();
        while (Running) {
            try {
                int Cur = Logs.size();
                String Prompt = "  %n%s┌──{%sRAVEN@C2%s}%n%s└─%s>>%s ";
                if (Cur > LastCnt) {
                    Logger.Info(Cur - LastCnt + " " + "new event(s) - type 'logs' to view");
                    LastCnt = Cur;
                }
                Logger.Custom(Prompt, AnsiColor.Red, AnsiColor.White, AnsiColor.Red, AnsiColor.Red, AnsiColor.White, AnsiColor.Reset);
                String Input = Reader.readLine();
                if (Input == null || Input.trim().isEmpty()) continue;
                String[] Parts = Input.trim().split("\\s+", 3);
                String Cmd = Parts[0].toLowerCase();
                switch (Cmd) {
                    case "exit", "quit" -> {
                        Logger.Debug("shutting down server");
                        Running = false;
                        if (IsTeamServerMode && SharedServer != null && SharedServer == Server) {
                            Logger.Info("teamserver session ended - server remains active");
                        } else if (Server != null && Server.IsRunning()) {
                            Server.StopServer();
                        }
                        if (WebPanel != null) {
                            WebPanel.Stop();
                            WebPanel = null;
                        }
                        Database.Close();
                        Logger.Shutdown();
                    }
                    case "help" -> ShowHelp();
                    case "clear" -> {
                        SystemHelper.ClearScreen();
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
                            Logger.Info("usage: use <id>");
                            continue;
                        }
                        try {
                            InteractiveSession(Integer.parseInt(Parts[1]));
                            LastCnt = Logs.size();
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "exec" -> {
                        if (!CanExec()) {
                            Logger.Warn("insufficient permissions");
                            continue;
                        }
                        if (Parts.length < 3) {
                            Logger.Info("usage: exec <id> <command>");
                            continue;
                        }
                        try {
                            DoExec(Integer.parseInt(Parts[1]), Parts[2]);
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "broadcast" -> {
                        if (!CanExec()) {
                            Logger.Warn("insufficient permissions");
                            continue;
                        }
                        if (Parts.length < 3) {
                            Logger.Info("usage: broadcast <id,id,...|all> <command>");
                            continue;
                        }
                        String IdsOrAll = Parts[1].toLowerCase();
                        String BCmd = Parts[2];
                        if (IdsOrAll.equals("all")) {
                            DoBroadcastAll(BCmd);
                        } else {
                            List<Integer> Ids = new ArrayList<>();
                            for (String S : IdsOrAll.split(","))
                                try {
                                    Ids.add(Integer.parseInt(S.trim()));
                                } catch (Exception Ign) {}
                            if (Ids.isEmpty()) Logger.Info("No valid session IDs");
                            else DoBroadcast(Ids, BCmd);
                        }
                    }
                    case "kill" -> {
                        if (Parts.length < 2) {
                            Logger.Info("usage: kill <id>");
                            continue;
                        }
                        try {
                            KillSession(Integer.parseInt(Parts[1]));
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "addopt", "addoperator" -> {
                        if (!CanManage()) {
                            Logger.Warn("ADMIN/SUPER required");
                            continue;
                        }
                        if (Parts.length < 3) {
                            Logger.Info("usage: addopt <user> <pass> [SUPER|ADMIN|OPERATOR|MEMBER]");
                            continue;
                        }
                        String[] Tok = Parts[2].split("\\s+", 2);
                        String OpPass = Tok[0];
                        String OperatorRoles = Tok.length > 1 ? Tok[1] : "OPERATOR";
                        if (Parts.length > 2 && Parts[2].contains(" ")) {
                            String[] Split = Parts[2].split(" ", 2);
                            OpPass = Split[0];
                            OperatorRoles = Split[1];
                        }
                        if (OpPass.length() < 8) {
                            Logger.Warn("password must be ≥ 8 chars");
                            continue;
                        }
                        OperatorRole R = OperatorRole.FromString(OperatorRoles);
                        if (R == OperatorRole.SUPER && (this.OperatorRoles == null || !this.OperatorRoles.IsSuperAdmin())) {
                            Logger.Warn("only SUPER can create SUPER accounts");
                            continue;
                        }
                        if (Database.CreateOperator(Parts[1], TeamDatabase.HashPassword(OpPass), R)) Logger.Custom("  Operator created: %s [%s]  %s%n", Parts[1], R, R.PermissionString());
                        else Logger.Warn("username already exists");
                    }
                    case "delopt", "deleteoperator" -> {
                        if (!CanManage()) {
                            Logger.Warn("ADMIN/SUPER required");
                            continue;
                        }
                        if (Parts.length < 2) {
                            Logger.Info("usage: delopt <username>");
                            continue;
                        }
                        if (Parts[1].equalsIgnoreCase(Config.GetAdminUsername())) {
                            Logger.Warn("Cannot delete admin");
                            continue;
                        }
                        if (Database.DeleteOperator(Parts[1])) Logger.Custom("  Deleted: %s%n", Parts[1]);
                        else Logger.Warn("operator not found");
                    }
                    case "kick", "kickopt" -> {
                        if (!CanKick()) {
                            Logger.Warn("SUPER role required to kick operators");
                            continue;
                        }
                        if (Parts.length < 2) {
                            Logger.Info("usage: kick <username>");
                            continue;
                        }
                        if (Parts[1].equalsIgnoreCase(Config.GetAdminUsername()) || Parts[1].equals(OperatorName)) {
                            Logger.Warn("Cannot kick admin or yourself");
                            continue;
                        }
                        if (Database.DeleteOperator(Parts[1])) Logger.Custom("  Kicked (removed): %s%n", Parts[1]);
                        else Logger.Warn("operator not found");
                    }
                    case "setrole", "changerole" -> {
                        if (!CanManage()) {
                            Logger.Warn("ADMIN/SUPER required");
                            continue;
                        }
                        if (Parts.length < 3) {
                            Logger.Info("usage: setrole <user> <SUPER|ADMIN|OPERATOR|MEMBER>");
                            continue;
                        }
                        if (Parts[1].equalsIgnoreCase(Config.GetAdminUsername())) {
                            Logger.Warn("Cannot change admin role");
                            continue;
                        }
                        OperatorRole NR = OperatorRole.FromString(Parts[2]);
                        if (NR == OperatorRole.SUPER && (this.OperatorRoles == null || !this.OperatorRoles.IsSuperAdmin())) {
                            Logger.Warn("only SUPER can promote to SUPER");
                            continue;
                        }
                        if (Database.UpdateOperatorRole(Parts[1], NR)) Logger.Custom("  Role updated: %s > %s  %s%n", Parts[1], NR, NR.PermissionString());
                        else Logger.Warn("operator not found");
                    }
                    case "passwd", "changepassword" -> {
                        if (!CanManage()) {
                            Logger.Warn("ADMIN/SUPER required");
                            continue;
                        }
                        if (Parts.length < 3) {
                            Logger.Info("usage: passwd <user> <newpass>");
                            continue;
                        }
                        if (Parts[2].length() < 8) {
                            Logger.Warn("password must be ≥ 8 chars");
                            continue;
                        }
                        if (Database.UpdateOperatorPassword(Parts[1], TeamDatabase.HashPassword(Parts[2]))) Logger.Custom("  Password updated: %s%n", Parts[1]);
                        else Logger.Warn("operator not found");
                    }
                    case "listopt", "listoperators" -> ShowOperators();
                    case "chat" -> ShowChat();
                    case "chathistory", "chatlog" -> ShowChatHistory();
                    case "ch" -> {
                        if (!IsTeamServerMode) {
                            Logger.Warn("not in team mode");
                            continue;
                        }
                        if (Parts.length < 3) {
                            Logger.Info("usage: ch <name> <message>");
                            continue;
                        }
                        SendChat(Parts[1], Parts[2]);
                    }
                    case "gc" -> {
                        if (!IsTeamServerMode) {
                            Logger.Warn("not in team mode");
                            continue;
                        }
                        if (Parts.length < 3) {
                            Logger.Info("usage: gc <all|id1,id2,...> <message>");
                            continue;
                        }
                        String GcTarget = Parts[1].toLowerCase();
                        String GcMsg = Parts[2];
                        if (GcTarget.equals("all")) {
                            SendChat("all", GcMsg);
                        } else {
                            for (String Name : GcTarget.split(",")) {
                                String N = Name.trim();
                                if (!N.isEmpty()) SendChat(N, GcMsg);
                            }
                        }
                    }
                    case "webstart" -> {
                        String WHost = Parts.length > 1 ? Parts[1] : Config.GetWebHost();
                        int WPort = Parts.length > 2 ? parseIntSafe(Parts[2], Config.GetWebPort()) : Config.GetWebPort();
                        StartWebPanel(WHost, WPort);
                    }
                    case "webstop" -> StopWebPanel();
                    case "webstatus" -> ShowWebStatus();
                    case "note" -> {
                        if (Parts.length < 3) {
                            Logger.Info("usage: note <session-id> <text>");
                            continue;
                        }
                        try {
                            int Sid = Integer.parseInt(Parts[1]);
                            Database.SetAgentNote(Sid, Parts[2]);
                            Logger.Info("note saved for session-" + Sid);
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "getnote" -> {
                        if (Parts.length < 2) {
                            Logger.Info("usage: getnote <session-id>");
                            continue;
                        }
                        try {
                            int Sid = Integer.parseInt(Parts[1]);
                            String N = Database.GetAgentNote(Sid);
                            Logger.Custom("  Note [session-%d]: %s%s%s%n", Sid, AnsiColor.White, N.isEmpty() ? "(empty)" : N, AnsiColor.Reset);
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "history" -> {
                        int Sid = Parts.length > 1 ? parseIntSafe(Parts[1], 0) : 0;
                        int Lim = Parts.length > 2 ? parseIntSafe(Parts[2], 50) : 50;
                        ShowCmdHistory(Sid, Lim);
                    }
                    case "sysinfo", "info" -> {
                        if (Parts.length < 2) {
                            Logger.Info("usage: sysinfo <session-id>");
                            continue;
                        }
                        try {
                            ShowSessionInfo(Integer.parseInt(Parts[1]));
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "whoami" -> {
                        if (Server == null) {
                            Logger.Info("unavailable in cross-process mode - use primary operator" + " terminal");
                            continue;
                        }
                        if (CurrentSession > 0) {
                            DoExec(CurrentSession, "whoami");
                        } else if (Parts.length > 1) {
                            try {
                                DoExec(Integer.parseInt(Parts[1]), "whoami");
                            } catch (NumberFormatException E) {
                                Logger.Warn("invalid session ID");
                            }
                        } else Logger.Info("usage: whoami <session-id>  (or use inside an interactive" + " session)");
                    }
                    case "sleep" -> {
                        if (Parts.length < 3) {
                            Logger.Info("usage: sleep <session-id> <seconds>");
                            continue;
                        }
                        try {
                            int Sid = Integer.parseInt(Parts[1]);
                            DoExec(Sid, "sleep " + Parts[2]);
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "tasks" -> {
                        if (Server == null) {
                            Logger.Warn("unavailable in cross-process mode - use primary operator" + " terminal");
                            continue;
                        }
                        ShowTasksQueue();
                    }
                    case "pivot" -> {
                        if (Parts.length < 3) {
                            Logger.Info("usage: pivot <session-id> <host:port>");
                            continue;
                        }
                        try {
                            int Sid = Integer.parseInt(Parts[1]);
                            String Target = Parts[2];
                            if (!Target.contains(":")) {
                                Logger.Error("invalid format - use host:port (e.g." + " 192.168.1.10:4445)");
                                continue;
                            }
                            String PivotHost = Target.split(":")[0];
                            int PivotPort;
                            try {
                                PivotPort = Integer.parseInt(Target.split(":")[1]);
                            } catch (NumberFormatException Ex) {
                                Logger.Warn("invalid port");
                                continue;
                            }

                            String PivotNote = "[PIVOT] " + Target;
                            Database.SetAgentNote(Sid, PivotNote);
                            Database.SaveCommandLog(Sid, OperatorName != null ? OperatorName : "cli", "pivot " + Target, "Pivot route set: " + Target, true);
                            Logger.Custom("  %sPivot route registered: session-%d > %s%s%n", AnsiColor.Green, Sid, Target, AnsiColor.Reset);
                            Logger.Custom("  %s  ℹ  Use your agent to initiate connection to %s:%d%s%n", AnsiColor.White, PivotHost, PivotPort, AnsiColor.Reset);
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "screenshot" -> {
                        if (Parts.length < 2) {
                            Logger.Info("usage: screenshot <session-id>");
                            continue;
                        }
                        try {
                            DoExec(Integer.parseInt(Parts[1]), "screenshot");
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "download" -> {
                        if (Parts.length < 3) {
                            Logger.Info("usage: download <session-id> <remote-path>");
                            continue;
                        }
                        try {
                            DoExec(Integer.parseInt(Parts[1]), "download " + Parts[2]);
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    case "upload" -> {
                        if (Parts.length < 4) {
                            Logger.Info("usage: upload <session-id> <local-path> <remote-path>");
                            continue;
                        }
                        try {
                            String RemotePath = Parts.length > 3 ? Parts[3] : "";
                            DoExec(Integer.parseInt(Parts[1]), "upload " + Parts[2] + " " + RemotePath);
                        } catch (NumberFormatException E) {
                            Logger.Warn("invalid session ID");
                        }
                    }
                    default -> {
                        Logger.Error("Unknown command: " + Cmd);
                        Logger.Info("Type 'help' for available commands");
                    }
                }
            } catch (IOException E) {
                break;
            }
        }
    }

    private WebApp WebPanel = null;
    private Thread WebPanelThread = null;

    private void ShowSessionInfo(int SessionId) {
        if (Server == null) {
            Logger.Info("unavailable in cross-process mode - use primary operator terminal");
            return;
        }
        Optional<Session> Opt = Server.GetSessions().Get(SessionId);
        if (Opt.isEmpty()) {
            Logger.Warn("Session not found");
            return;
        }
        Session S = Opt.get();
        System.out.println(Box("SESSION INFO - #" + SessionId));
        System.out.println();
        Logger.Custom("  %sID         %s%d%n", AnsiColor.Red, AnsiColor.White, S.GetId());
        Logger.Custom("  %sName       %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetDisplayName());
        Logger.Custom("  %sType       %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetSessionType().name());
        Logger.Custom("  %sHostname   %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetHostname());
        Logger.Custom("  %sUser       %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetUser());
        Logger.Custom("  %sOS         %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetOs());
        Logger.Custom("  %sArch       %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetArch());
        Logger.Custom("  %sAgent IP   %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetAgentIp());
        Logger.Custom("  %sKey        %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetSessionKey());
        Logger.Custom("  %sEncrypted  %s%b%n", AnsiColor.Red, AnsiColor.White, S.IsEncrypted());
        Logger.Custom("  %smTLS       %s%b%n", AnsiColor.Red, AnsiColor.White, S.IsMtlsEnabled());
        Logger.Custom("  %sCert CN    %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetCertCn());
        Logger.Custom("  %sShell Mode %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetShellMode());
        String Note = Database.GetAgentNote(SessionId);
        Logger.Custom("  %sNote       %s%s%n", AnsiColor.Red, AnsiColor.White, Note.isEmpty() ? "(none)" : Note);
        System.out.println();
    }

    private void ShowCmdHistory(int SessionId, int Limit) {
        List<Map<String, Object>> Hist = Database.GetCommandHistory(SessionId, Limit);
        String Title = SessionId == 0 ? "COMMAND HISTORY (all sessions, last " + Limit + ")" : "COMMAND HISTORY - session-" + SessionId + " (last " + Limit + ")";
        System.out.println(Box(Title));
        System.out.println();
        if (Hist.isEmpty()) {
            Logger.Info("No command history\n");
            return;
        }
        Logger.Custom("  %s%-5s %-12s %-14s %-36s %s%s%n", AnsiColor.Red, "SID", "OPERATOR", "STATUS", "COMMAND", "TIMESTAMP", AnsiColor.Reset);
        System.out.println(Divider());
        for (Map<String, Object> R : Hist) {
            boolean Ok = Boolean.parseBoolean(R.getOrDefault("Success", "false").toString());
            String Cmd = R.getOrDefault("Command", "").toString();
            if (Cmd.length() > 36) Cmd = Cmd.substring(0, 35) + "…";
            Logger.Custom("  %s%-5s %-12s %s%-14s%s %-36s %s%s%n", AnsiColor.White, R.getOrDefault("AgentId", "?"), R.getOrDefault("Operator", "?"), Ok ? AnsiColor.Green : AnsiColor.Red, Ok ? "ok" : "fail", AnsiColor.White, Cmd, R.getOrDefault("Timestamp", ""), AnsiColor.Reset);
        }
        System.out.println();
    }

    private void ShowTasksQueue() {
        System.out.println(Box("PENDING TASKS"));
        System.out.println();
        int Total = Server != null ? Server.GetSessions().Count() : 0;
        Logger.Custom("  %sActive sessions: %s%d%s%n", AnsiColor.Red, AnsiColor.White, Total, AnsiColor.Reset);
        Logger.Info("Use 'broadcast' or 'exec' to queue commands to sessions");
        System.out.println();
    }

    private void StartWebPanel(String Host, int Port) {
        if (WebPanel != null) {
            Logger.Custom("  %sWeb panel already running%s%n", AnsiColor.Red, AnsiColor.Reset);
            return;
        }
        try {
            WebPanel = new WebApp(Config, ActiveMode);
            if (Server != null && Server.IsRunning()) {
                WebPanel.AttachServer(Server, ServerStartTime);
            }
            WebPanel.Run(Host, Port);
            Logger.Custom("  %sWeb panel started > http://%s:%d/%s%n", AnsiColor.Green, Host, Port, AnsiColor.Reset);
        } catch (Exception E) {
            Logger.Error("Web panel start failed: " + E.getMessage());
            Logger.Custom("  %sWeb panel failed: %s%s%n", AnsiColor.Red, E.getMessage(), AnsiColor.Reset);
            WebPanel = null;
        }
    }

    private void StopWebPanel() {
        if (WebPanel == null) {
            Logger.Custom("  %sWeb panel not running%s%n", AnsiColor.Red, AnsiColor.Reset);
            return;
        }
        WebPanel.Stop();
        WebPanel = null;
        Logger.Custom("  %sWeb panel stopped%s%n", AnsiColor.Green, AnsiColor.Reset);
    }

    private void ShowWebStatus() {
        if (WebPanel == null) {
            Logger.Info("Web Panel  : " + AnsiColor.Red + "Offline" + AnsiColor.Reset);
        } else {
            Logger.Info("Web Panel  : " + AnsiColor.Green + "Online" + AnsiColor.Reset);
        }
    }

    private static int parseIntSafe(String S, int Def) {
        try {
            return Integer.parseInt(S.trim());
        } catch (Exception E) {
            return Def;
        }
    }

    public void Run(String Host, int Port, ListenerMode Mode) {
        this.ActiveMode = Mode;
        this.IsTeamServerMode = false;
        if (!StartServer(Host, Port, Mode)) return;
        try {
            Thread.sleep(300);
        } catch (InterruptedException Ign) {}
        RunLoop();
        System.exit(0);
    }

    public void RunTeamServer(String Host, int Port, ListenerMode Mode) throws IOException {
        this.ActiveMode = Mode;
        this.IsTeamServerMode = true;
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        if (!TeamLogin(Reader)) return;
        Logger.Custom("  %n%sStarting listener on %s:%d%s%n%n", AnsiColor.Green, Host, Port, AnsiColor.Reset);
        if (!StartServer(Host, Port, Mode)) return;
        try {
            Thread.sleep(300);
        } catch (InterruptedException Ign) {}
        RunLoop();
        System.exit(0);
    }
}
