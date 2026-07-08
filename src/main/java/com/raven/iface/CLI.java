package com.raven.iface;

import com.raven.core.db.TeamDatabase;
import com.raven.core.db.TeamDatabase.OperatorRole;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import com.raven.iface.banner.CLIBanner;
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
    private final TeamDatabase Db;
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
    private OperatorRole OpRole = null;
    private boolean IsTeamMode = false;

    public CLI(ServerConfig Config) {
        this.Config = Config;
        this.MaxLogs = Config.GetMaxLogEntries();
        this.Db = TeamDatabase.Connect(Config);
    }

    private volatile int cachedWidth = -1;
    private final AtomicInteger termWidth = new AtomicInteger(-1);
    private Thread widthPoller;

    private void startWidthPoller() {
        widthPoller = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int w = detectTermWidth();
                termWidth.set(w);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "term-width-poller");
        widthPoller.setDaemon(true);
        widthPoller.start();
    }

    private int TermWidth() {
        String cols = System.getenv("COLUMNS");
        if (cols != null && !cols.isBlank()) {
            try {
                return Math.max(40, Integer.parseInt(cols.trim()));
            } catch (NumberFormatException ignored) {}
        }

        if (widthPoller == null || !widthPoller.isAlive()) startWidthPoller();

        int cached = termWidth.get();
        if (cached > 0) return cached;

        int detected = detectTermWidth();
        termWidth.set(detected);
        return detected;
    }

    private int detectTermWidth() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            Integer w = WinTermWidth();
            return w != null ? w : 80;
        }
        Integer unixW = UnixTermWidth();
        return unixW != null ? unixW : 80;
    }

    private Integer UnixTermWidth() {
        Integer w = UnixWidthStty();
        if (w != null) return w;
        return UnixWidthTput();
    }

    private Integer UnixWidthStty() {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "stty size < /dev/tty");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            String[] parts = out.split("\\s+");
            if (parts.length >= 2) return Math.max(40, Integer.parseInt(parts[1]));
        } catch (Exception ignored) {}
        return null;
    }

    private Integer UnixWidthTput() {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "tput cols < /dev/tty");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (!out.isBlank()) return Math.max(40, Integer.parseInt(out));
        } catch (Exception ignored) {}
        return null;
    }

    private Integer WinTermWidth() {
        Integer w = WinWidthPowerShell();
        if (w != null) return w;
        return WinWidthModeCon();
    }

    private Integer WinWidthPowerShell() {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "[Console]::WindowWidth");
            pb.redirectErrorStream(true);
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            boolean finished = p.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }
            if (!out.isBlank()) {
                String digits = out.replaceAll("\\D+", "");
                if (!digits.isBlank()) {
                    int val = Integer.parseInt(digits);
                    if (val >= 20 && val <= 1000) return Math.max(40, val);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Integer WinWidthModeCon() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "mode con");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean finished = p.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }
            for (String line : out.split("\\r?\\n")) {
                String lower = line.toLowerCase();
                if (lower.contains("column") || lower.contains("kolom")) {
                    String digits = line.replaceAll("[^0-9]", "");
                    if (!digits.isBlank()) {
                        int val = Integer.parseInt(digits);
                        if (val >= 20 && val <= 1000) return Math.max(40, val);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static final String FrameIndent = "  ";

    private int ContentWidth() {
        return Math.max(36, TermWidth() - FrameIndent.length() - 2);
    }

    private String Indent(String text) {
        return FrameIndent + text;
    }

    private String Box(String title) {
        int w = ContentWidth();
        int inner = Math.max(0, w - 2);
        int padLeft = Math.max(0, (inner - title.length()) / 2);
        int padRight = Math.max(0, inner - padLeft - title.length());
        String t = AnsiColor.White + "┌" + "─".repeat(inner) + "┐" + AnsiColor.Reset;
        String m = AnsiColor.White + "|" + " ".repeat(padLeft) + AnsiColor.Green + title + " ".repeat(padRight) + AnsiColor.White + "|" + AnsiColor.Reset;
        String b = AnsiColor.White + "└" + "─".repeat(inner) + "┘" + AnsiColor.Reset;
        return "\n" + Indent(t) + "\n" + Indent(m) + "\n" + Indent(b);
    }

    private String OutputBox(String output) {
        int w = Math.max(34, ContentWidth());
        int inner = Math.max(0, w - 2);
        int lineWidth = Math.max(1, inner - 2);
        String label = "─ Output ";
        String t = AnsiColor.Green + "┌" + label + "─".repeat(Math.max(0, inner - label.length())) + "┐" + AnsiColor.Reset;
        String bot = AnsiColor.Green + "└" + "─".repeat(inner) + "┘" + AnsiColor.Reset;
        StringBuilder sb = new StringBuilder(Indent(t) + "\n");
        for (String line : output.split("\n", -1)) {
            String stripped = line.replaceAll("\u001B\\[[;\\d?]*[A-Za-z]|\u001B[=>]|\r", "");
            while (stripped.length() > lineWidth) {
                String chunk = stripped.substring(0, lineWidth);
                sb.append(Indent(AnsiColor.Green)).append("| ").append(AnsiColor.White).append(chunk).append(AnsiColor.Green).append(" |").append(AnsiColor.Reset).append("\n");
                stripped = stripped.substring(lineWidth);
            }
            int pad = Math.max(0, lineWidth - stripped.length());
            sb.append(Indent(AnsiColor.Green)).append("| ").append(AnsiColor.White).append(stripped).append(" ".repeat(pad)).append(AnsiColor.Green).append(" |").append(AnsiColor.Reset).append("\n");
        }
        return sb.append(Indent(bot)).toString();
    }

    private String Divider() {
        return Indent(AnsiColor.White + "─".repeat(ContentWidth()) + AnsiColor.Reset);
    }

    private void ShowHelp() {
        System.out.println(Box("COMMAND REFERENCE"));
        System.out.println();
        CLIBanner.Print();
        if (IsTeamMode && OperatorName != null) {
            System.out.println();
            System.out.printf("  %s[TEAMSERVER MODE]%s  OPERATOR: %s%s%s  Role: %s%s%s%n", AnsiColor.Red, AnsiColor.Reset, AnsiColor.White, OperatorName, AnsiColor.Reset, AnsiColor.White, OpRole != null ? OpRole.name() : "?", AnsiColor.Reset);
            if (OpRole != null) System.out.printf("  %sPermissions:%s %s%n", AnsiColor.Red, AnsiColor.White, OpRole.PermissionString());
        }
        System.out.println();
    }

    private boolean TeamLogin(BufferedReader Reader) throws IOException {
        System.out.println(Box("TEAMSERVER LOGIN"));
        System.out.println();
        System.out.printf("  %sDefault credentials: admin / admin (change after first login)%s%n%n", AnsiColor.White, AnsiColor.Reset);
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
                System.out.printf("  %sInvalid credentials - Attempt %d/3%s%n%n", AnsiColor.Red, Attempt, AnsiColor.Reset);
                continue;
            }
            OperatorName = User;
            OpRole = Db.GetOperatorRole(User);
            Db.UpdateLastSeen(User);
            Logger.Info("  Operator login: " + User + " [" + OpRole + "]");
            System.out.printf("  %n%sWelcome, %s [%s]%s%n", AnsiColor.Green, User, OpRole, AnsiColor.Reset);
            System.out.printf("  %sPermissions:%s %s%n%n", AnsiColor.Red, AnsiColor.White, OpRole.PermissionString());
            return true;
        }
        System.out.printf("  %sAuthentication failed — exiting%s%n", AnsiColor.Red, AnsiColor.Reset);
        return false;
    }

    private void ShowSessions() {
        if (Server == null) {
            System.out.println(Box("ACTIVE SESSIONS"));
            System.out.println();
            System.out.println("  Running in cross-process mode — session list unavailable.");
            System.out.println("  Use the primary operator terminal or webstart to view sessions.\n");
            return;
        }
        List<Session> Sessions = Server.GetSessions().GetAll();
        System.out.println(Box("ACTIVE SESSIONS"));
        System.out.println();
        if (Sessions.isEmpty()) {
            System.out.println("  No active sessions\n");
            return;
        }
        System.out.printf("  %s%-5s %-14s %-14s %-16s %-10s %-10s %s%s%n", AnsiColor.Blue, "ID", "NAME/CERT", "TYPE", "IP", "OS", "USER", "SESSION-KEY", AnsiColor.Reset);
        System.out.println(Divider());
        for (Session S : Sessions) System.out.printf("  %s#%-4d %-14s %-14s %-16s %-10s %-10s %s%s%n", AnsiColor.White, S.GetId(), S.GetDisplayName().length() > 14 ? S.GetDisplayName().substring(0, 13) + "-" : S.GetDisplayName(), S.GetSessionType().name(), S.GetAgentIp(), S.GetOs().length() > 10 ? S.GetOs().substring(0, 9) + "-" : S.GetOs(), S.GetUser(), S.GetSessionKey(), AnsiColor.Reset);
        System.out.println();
    }

    private void ShowStatus() {
        long Up = ServerStartTime != null ? Duration.between(ServerStartTime, Instant.now()).getSeconds() : 0;
        System.out.println(Box("SERVER STATUS"));
        System.out.println();
        if (Server == null || !Server.IsRunning()) {
            if (IsTeamMode && ServerStartTime != null) {
                System.out.printf("  %sStatus    %sONLINE (cross-process)%n", AnsiColor.Red, AnsiColor.Green);
                System.out.printf("  %sMode      %s%s%n", AnsiColor.Red, AnsiColor.White, ActiveMode.name());
                System.out.printf("  %sAddress   %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Config.GetServerHost(), Config.GetServerPort());
                System.out.printf("  %sUptime    %s%s (local session)%n", AnsiColor.Red, AnsiColor.White, SystemHelper.FormatUptime(Up));
                System.out.printf("  %sSessions  %s(N/A — cross-process)%n", AnsiColor.Red, AnsiColor.White);
            } else {
                System.out.printf("  %sStatus    %sOFFLINE%n", AnsiColor.Red, AnsiColor.Red);
            }
        } else {
            System.out.printf("  %sStatus    %sONLINE%n", AnsiColor.Red, AnsiColor.Green);
            System.out.printf("  %sMode      %s%s%n", AnsiColor.Red, AnsiColor.White, ActiveMode.name());
            System.out.printf("  %sAddress   %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Server.GetHost(), Server.GetPort());
            System.out.printf("  %sUptime    %s%s%n", AnsiColor.Red, AnsiColor.White, SystemHelper.FormatUptime(Up));
            System.out.printf("  %sSessions  %s%d%n", AnsiColor.Red, AnsiColor.White, Server.GetSessions().Count());
        }
        System.out.printf("  %sDB %s%s (%s)%n", AnsiColor.Red, AnsiColor.White, Db.IsConnected() ? "Connected" : "Memory", Config.GetDbType());
        if (IsTeamMode && OperatorName != null) System.out.printf("  %sOperator  %s%s [%s]%n", AnsiColor.Red, AnsiColor.White, OperatorName, OpRole);
        System.out.println();
    }

    private void ShowStats() {
        System.out.println(Box("SESSION STATISTICS"));
        System.out.println();
        if (Server == null) {
            System.out.printf("  %sServer %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Config.GetServerHost(), Config.GetServerPort());
            System.out.printf("  %sTotal  %s(N/A — cross-process mode)%n", AnsiColor.Red, AnsiColor.White);
            System.out.println();
            return;
        }
        Map<String, Integer> Stats = Server.GetSessions().GetStats();
        System.out.printf("  %sServer %s%s:%d%n", AnsiColor.Red, AnsiColor.White, Server.GetHost(), Server.GetPort());
        System.out.printf("  %sTOTAL    %s%d%n", AnsiColor.Red, AnsiColor.White, Stats.get("Total"));
        System.out.printf("  %sRAVEN    %s%d%n", AnsiColor.Red, AnsiColor.White, Stats.get("RAVEN"));
        System.out.printf("  %sRAW      %s%d%n", AnsiColor.Red, AnsiColor.White, Stats.get("REVERSE_SHELL"));
        System.out.println();
    }

    private void ShowLogs() {
        System.out.println(Box("RECENT LOGS"));
        System.out.println();
        if (Logs.isEmpty()) {
            System.out.println(Indent("  No logs") + "\n");
            return;
        }
        int Start = Math.max(0, Logs.size() - 25);
        for (int I = Start; I < Logs.size(); I++) System.out.println(Indent(Logs.get(I)));
        System.out.println();
    }

    private void ShowOperators() {
        List<Map<String, Object>> Ops = Db.GetOperators();
        System.out.println(Box("OPERATORS (" + Ops.size() + ")"));
        System.out.println();
        System.out.printf("  %s%-18s %-14s %-24s %-20s%s%n", AnsiColor.Green, "USERNAME", "ROLE", "PERMISSIONS", "LAST SEEN", AnsiColor.Reset);
        System.out.println(Divider());
        for (Map<String, Object> Op : Ops) {
            OperatorRole R = OperatorRole.FromString(Op.get("Role").toString());
            boolean IsSelf = Op.get("Username").toString().equals(OperatorName);
            String Mark = IsSelf ? AnsiColor.Green + " < YOU" + AnsiColor.White : "";
            System.out.printf("  %s%-18s %-14s %-24s %-20s%s%s%n", AnsiColor.White, Op.get("Username"), R.name(), "[" + R.ShortPerm() + "] " + R.PermissionString().replaceAll("^\\[.*?\\]\\s*", ""), Op.getOrDefault("LastSeen", "Never"), Mark, AnsiColor.Reset);
        }
        System.out.println();
        System.out.println("  " + AnsiColor.Red + "Roles:" + AnsiColor.Reset);
        for (OperatorRole R : OperatorRole.values()) System.out.printf("    %s%-14s%s %s%n", AnsiColor.White, R.name(), AnsiColor.Reset, R.PermissionString());
        System.out.println();
    }

    private void ShowChat() {
        System.out.println(Box("CHAT MESSAGES"));
        System.out.println();
        if (ChatMessages.isEmpty()) {
            System.out.println("  No messages\n");
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
            System.out.printf("  %s[%s] %s%s%s [%s]: %s%s%n", IsMine ? AnsiColor.Green : AnsiColor.White, Time, IsMine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, ToLabel, Msg, AnsiColor.Reset);
        }
        System.out.println();
    }

    private void SendChat(String To, String Message) {
        if (OperatorName == null) {
            System.out.println("Not in team mode");
            return;
        }
        Map<String, Object> Entry = new LinkedHashMap<>();
        Entry.put("From", OperatorName);
        Entry.put("To", To);
        Entry.put("Message", Message);
        Entry.put("Time", java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        ChatMessages.add(Entry);
        if (ChatMessages.size() > MaxChat) ChatMessages.remove(0);
        Db.SaveChatLog(OperatorName, To, Message);
        System.out.printf("Message sent to [%s]%s%n", AnsiColor.Green, To, AnsiColor.Reset);
    }

    private void ShowChatHistory() {
        List<Map<String, Object>> Logs = Db.GetChatLogs(100);
        System.out.println(Box("CHAT HISTORY (DB — last 100)"));
        System.out.println();
        if (Logs.isEmpty()) {
            System.out.println("  No chat history in database\n");
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
            System.out.printf("  %s[%s] %s%s%s [%s]: %s%s%n", IsMine ? AnsiColor.Green : AnsiColor.White, Time, IsMine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, ToLabel, Msg, AnsiColor.Reset);
        }
        System.out.println();
    }

    private void DoExec(int SessionId, String Command) {
        if (Server == null) {
            System.out.println("  Session commands unavailable in cross-process mode.");
            System.out.println("  Use the primary operator terminal to exec commands.");
            return;
        }
        String Op = OperatorName != null ? OperatorName : "operator";
        String[] Result = Server.ExecuteCommand(SessionId, Command);
        boolean Ok = Boolean.parseBoolean(Result[0]);
        if (Ok) {
            System.out.println(OutputBox(Result[1]));
            AddLog(AnsiColor.Green + "SESSION-" + SessionId + " OK" + AnsiColor.Reset, false);
        } else {
            System.out.println("" + Result[1]);
            AddLog(AnsiColor.Red + "SESSION-" + SessionId + " FAIL: " + Result[1] + AnsiColor.Reset, false);
        }
        Db.SaveCommandLog(SessionId, Op, Command, Result[1], Ok);
    }

    private void DoBroadcast(List<Integer> Ids, String Command) {
        if (Server == null) {
            System.out.println("  Broadcast unavailable in cross-process mode.");
            return;
        }
        String Op = OperatorName != null ? OperatorName : "operator";
        System.out.printf("Broadcasting to %d session(s): %s%n", Ids.size(), Command);
        Map<Integer, String[]> Results = Server.BroadcastCommand(Ids, Command);
        System.out.println(Box("BROADCAST RESULTS — " + Results.size() + " sessions"));
        System.out.println();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            System.out.printf("  %sSESSION-%-3d %s%s%n", Ok ? AnsiColor.Green : AnsiColor.Red, En.getKey(), Ok ? "" : "", AnsiColor.Reset);
            System.out.println(OutputBox(En.getValue()[1]));
            Db.SaveCommandLog(En.getKey(), Op, Command, En.getValue()[1], Ok);
        }
    }

    private void DoBroadcastAll(String Command) {
        if (Server == null) {
            System.out.println("  Unavailable in cross-process mode — use primary operator terminal");
            return;
        }
        int Total = Server.GetSessions().Count();
        if (Total == 0) {
            System.out.println("  No active sessions");
            return;
        }
        String Op = OperatorName != null ? OperatorName : "operator";
        System.out.printf("  Broadcasting to all %d session(s): %s%n", Total, Command);
        Map<Integer, String[]> Results = Server.BroadcastAll(Command);
        System.out.println(Box("BROADCAST-ALL RESULTS"));
        System.out.println();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            System.out.printf("  %sSESSION-%-3d %s%s%n", Ok ? AnsiColor.Green : AnsiColor.Red, En.getKey(), Ok ? "" : "", AnsiColor.Reset);
            System.out.println(OutputBox(En.getValue()[1]));
            Db.SaveCommandLog(En.getKey(), Op, Command, En.getValue()[1], Ok);
        }
    }

    private void InteractiveSession(int SessionId) {
        Optional<Session> Opt = Server.GetSessions().Get(SessionId);
        if (Opt.isEmpty()) {
            System.out.println("  Session not found");
            return;
        }
        Session S = Opt.get();
        System.out.println(Box("INTERACTIVE SESSION"));
        String AgentInfo = "%n  %s[%s%s%s] %sID: %s%d %sUSER: %s%s@%s %sOS: %s%s %sARCH: %s%s %sIP: %s%s" + " %sTYPE: %s%s %sKEY: %s%s%s%n";
        System.out.printf(AgentInfo, AnsiColor.Blue, AnsiColor.White, S.GetDisplayName(), AnsiColor.Blue, AnsiColor.Blue, AnsiColor.White, SessionId, AnsiColor.Blue, AnsiColor.White, S.GetUser(), S.GetHostname(), AnsiColor.Blue, AnsiColor.White, S.GetOs(), AnsiColor.Blue, AnsiColor.White, S.GetArch(), AnsiColor.Blue, AnsiColor.White, S.GetAgentIp(), AnsiColor.Blue, AnsiColor.White, S.GetSessionType(), AnsiColor.Blue, AnsiColor.White, S.GetSessionKey(), AnsiColor.Reset);
        System.out.println("  Type 'back' to return");
        AddLog(AnsiColor.Blue + "Entered [" + S.GetDisplayName() + "] SESSION-" + SessionId + AnsiColor.Reset, false);
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        CurrentSession = SessionId;
        while (CurrentSession == SessionId) {
            try {
                String Prompt = "%n  %s[%s%s#%d%s%s]%s ~$ %s";
                System.out.printf(Prompt, AnsiColor.Blue, AnsiColor.White, S.GetDisplayName(), SessionId, AnsiColor.Blue, AnsiColor.White, AnsiColor.Blue, AnsiColor.Reset);
                String Cmd = Reader.readLine();
                if (Cmd == null || Cmd.trim().isEmpty()) continue;
                Cmd = Cmd.trim();
                if (Cmd.equalsIgnoreCase("back")) {
                    CurrentSession = -1;
                    System.out.printf("  %sReturned%s%n", AnsiColor.Red, AnsiColor.Reset);
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
            System.out.println("  Session not found");
            return;
        }
        String Name = Opt.get().GetDisplayName();
        Server.RemoveSession(SessionId);
        System.out.printf("  SESSION-%d [%s] terminated%n", SessionId, Name);
        AddLog(AnsiColor.Green + "SESSION-" + SessionId + " [" + Name + "] killed" + AnsiColor.Reset, false);
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
            case ServerStarted -> AddLog(AnsiColor.White + "  Server listening on " + Data.get("Host") + ":" + Data.get("Port") + AnsiColor.Reset, true);
            case AgentConnected -> AddLog(AnsiColor.Green + "  [" + Data.get("AgentName") + "] SESSION-" + Data.get("ID") + " key: " + Data.get("SessionKey") + " (" + Data.get("OS") + ")" + AnsiColor.Reset, true);
            case AgentDisconnected -> AddLog(AnsiColor.Red + "  SESSION-" + Data.get("ID") + " disconnected: " + Data.get("Reason") + AnsiColor.Reset, true);
            case AgentRemoved -> AddLog(AnsiColor.White + "  SESSION-" + Data.get("ID") + " removed" + AnsiColor.Reset, false);
            case Error -> AddLog(AnsiColor.Red + "  Error: " + Data.get("Message") + AnsiColor.Reset, true);
        }
    }

    private boolean StartServer(String Host, int Port, ListenerMode Mode) {
        if (IsTeamMode) {
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
                        AddLog(AnsiColor.White + "  Attached to listener on " + Host + ":" + Port + " (session commands limited in cross-process mode)" + AnsiColor.Reset, true);
                        return true;
                    }
                    AddLog(AnsiColor.Red + "  Failed to start server" + AnsiColor.Reset, true);
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
        String Ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String Entry = "" + AnsiColor.Red + "[" + Ts + "]" + AnsiColor.Reset + " " + Msg;
        Logs.add(Entry);
        if (Logs.size() > MaxLogs) Logs.remove(0);
        if (PrintNow) System.out.println(Entry);
    }

    private void RunLoop() {
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        int LastCnt = Logs.size();
        while (Running) {
            try {
                int Cur = Logs.size();
                String Prompt = "  %n%s┌──(%sRAVEN@C2%s)%n%s└─%s>>%s ";
                if (Cur > LastCnt) {
                    System.out.printf("  %s* %d New event(s) — type 'logs' to view%s%n", AnsiColor.White, Cur - LastCnt, AnsiColor.Reset);
                    LastCnt = Cur;
                }
                System.out.printf(Prompt, AnsiColor.Red, AnsiColor.White, AnsiColor.Red, AnsiColor.Red, AnsiColor.White, AnsiColor.Reset);
                String Input = Reader.readLine();
                if (Input == null || Input.trim().isEmpty()) continue;
                String[] Parts = Input.trim().split("\\s+", 3);
                String Cmd = Parts[0].toLowerCase();
                switch (Cmd) {
                    case "exit", "quit" -> {
                        System.out.printf("  %s* Shutting down...%s%n", AnsiColor.White, AnsiColor.Reset);
                        Running = false;
                        if (IsTeamMode && SharedServer != null && SharedServer == Server) {
                            Logger.Info("TeamServer session ended — server remains active");
                        } else if (Server != null && Server.IsRunning()) {
                            Server.StopServer();
                        }
                        if (WebPanel != null) {
                            WebPanel.Stop();
                            WebPanel = null;
                        }
                        Db.Close();
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
                            System.out.println("  Usage: use <id>");
                            continue;
                        }
                        try {
                            InteractiveSession(Integer.parseInt(Parts[1]));
                            LastCnt = Logs.size();
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "exec" -> {
                        if (!CanExec()) {
                            System.out.println("  Insufficient permissions");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  Usage: exec <id> <command>");
                            continue;
                        }
                        try {
                            DoExec(Integer.parseInt(Parts[1]), Parts[2]);
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "broadcast" -> {
                        if (!CanExec()) {
                            System.out.println("  Insufficient permissions");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  Usage: broadcast <id,id,...|all> <command>");
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
                            if (Ids.isEmpty()) System.out.println("  No valid session IDs");
                            else DoBroadcast(Ids, BCmd);
                        }
                    }
                    case "kill" -> {
                        if (Parts.length < 2) {
                            System.out.println("  Usage: kill <id>");
                            continue;
                        }
                        try {
                            KillSession(Integer.parseInt(Parts[1]));
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "addopt", "addoperator" -> {
                        if (!CanManage()) {
                            System.out.println("ADMIN/SUPER required");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  Usage: addopt <user> <pass> [SUPER|ADMIN|OPERATOR|MEMBER]");
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
                            System.out.println("  Password must be ≥ 8 chars");
                            continue;
                        }
                        OperatorRole R = OperatorRole.FromString(OpRole);
                        if (R == OperatorRole.SUPER && (this.OpRole == null || !this.OpRole.IsSuperAdmin())) {
                            System.out.println("  Only SUPER can create SUPER accounts");
                            continue;
                        }
                        if (Db.CreateOperator(Parts[1], TeamDatabase.HashPassword(OpPass), R)) System.out.printf("  Operator created: %s [%s]  %s%n", Parts[1], R, R.PermissionString());
                        else System.out.println("  Username already exists");
                    }
                    case "delopt", "deleteoperator" -> {
                        if (!CanManage()) {
                            System.out.println("  ADMIN/SUPER required");
                            continue;
                        }
                        if (Parts.length < 2) {
                            System.out.println("  Usage: delopt <username>");
                            continue;
                        }
                        if (Parts[1].equalsIgnoreCase(Config.GetAdminUsername())) {
                            System.out.println("  Cannot delete admin");
                            continue;
                        }
                        if (Db.DeleteOperator(Parts[1])) System.out.printf("  Deleted: %s%n", Parts[1]);
                        else System.out.println("  Operator not found");
                    }
                    case "kick", "kickopt" -> {
                        if (!CanKick()) {
                            System.out.println("  SUPER role required to kick operators");
                            continue;
                        }
                        if (Parts.length < 2) {
                            System.out.println("  Usage: kick <username>");
                            continue;
                        }
                        if (Parts[1].equalsIgnoreCase(Config.GetAdminUsername()) || Parts[1].equals(OperatorName)) {
                            System.out.println("  Cannot kick admin or yourself");
                            continue;
                        }
                        if (Db.DeleteOperator(Parts[1])) System.out.printf("  Kicked (removed): %s%n", Parts[1]);
                        else System.out.println("  Operator not found");
                    }
                    case "setrole", "changerole" -> {
                        if (!CanManage()) {
                            System.out.println("  ADMIN/SUPER required");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  Usage: setrole <user> <SUPER|ADMIN|OPERATOR|MEMBER>");
                            continue;
                        }
                        if (Parts[1].equalsIgnoreCase(Config.GetAdminUsername())) {
                            System.out.println("  Cannot change admin role");
                            continue;
                        }
                        OperatorRole NR = OperatorRole.FromString(Parts[2]);
                        if (NR == OperatorRole.SUPER && (this.OpRole == null || !this.OpRole.IsSuperAdmin())) {
                            System.out.println("  Only SUPER can promote to SUPER");
                            continue;
                        }
                        if (Db.UpdateOperatorRole(Parts[1], NR)) System.out.printf("  Role updated: %s > %s  %s%n", Parts[1], NR, NR.PermissionString());
                        else System.out.println("Operator not found");
                    }
                    case "passwd", "changepassword" -> {
                        if (!CanManage()) {
                            System.out.println("  ADMIN/SUPER required");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  Usage: passwd <user> <newpass>");
                            continue;
                        }
                        if (Parts[2].length() < 8) {
                            System.out.println("  Password must be ≥ 8 chars");
                            continue;
                        }
                        if (Db.UpdateOperatorPassword(Parts[1], TeamDatabase.HashPassword(Parts[2]))) System.out.printf("  Password updated: %s%n", Parts[1]);
                        else System.out.println("  Operator not found");
                    }
                    case "listopt", "listoperators" -> ShowOperators();
                    case "chat" -> ShowChat();
                    case "chathistory", "chatlog" -> ShowChatHistory();
                    case "ch" -> {
                        if (!IsTeamMode) {
                            System.out.println("  Not in team mode");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  Usage: ch <name> <message>");
                            continue;
                        }
                        SendChat(Parts[1], Parts[2]);
                    }
                    case "gc" -> {
                        if (!IsTeamMode) {
                            System.out.println("  Not in team mode");
                            continue;
                        }
                        if (Parts.length < 3) {
                            System.out.println("  Usage: gc <all|id1,id2,...> <message>");
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
                            System.out.println("  Usage: note <session-id> <text>");
                            continue;
                        }
                        try {
                            int Sid = Integer.parseInt(Parts[1]);
                            Db.SetAgentNote(Sid, Parts[2]);
                            System.out.printf("  %sNote saved for SESSION-%d%s%n", AnsiColor.Green, Sid, AnsiColor.Reset);
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "getnote" -> {
                        if (Parts.length < 2) {
                            System.out.println("  Usage: getnote <session-id>");
                            continue;
                        }
                        try {
                            int Sid = Integer.parseInt(Parts[1]);
                            String N = Db.GetAgentNote(Sid);
                            System.out.printf("  Note [SESSION-%d]: %s%s%s%n", Sid, AnsiColor.White, N.isEmpty() ? "(empty)" : N, AnsiColor.Reset);
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "history" -> {
                        int Sid = Parts.length > 1 ? parseIntSafe(Parts[1], 0) : 0;
                        int Lim = Parts.length > 2 ? parseIntSafe(Parts[2], 50) : 50;
                        ShowCmdHistory(Sid, Lim);
                    }
                    case "sysinfo", "info" -> {
                        if (Parts.length < 2) {
                            System.out.println("  Usage: sysinfo <session-id>");
                            continue;
                        }
                        try {
                            ShowSessionInfo(Integer.parseInt(Parts[1]));
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "whoami" -> {
                        if (Server == null) {
                            System.out.println("  Unavailable in cross-process mode — use primary operator" + " terminal");
                            continue;
                        }
                        if (CurrentSession > 0) {
                            DoExec(CurrentSession, "whoami");
                        } else if (Parts.length > 1) {
                            try {
                                DoExec(Integer.parseInt(Parts[1]), "whoami");
                            } catch (NumberFormatException E) {
                                System.out.println("  Invalid session ID");
                            }
                        } else System.out.println("  Usage: whoami <session-id>  (or use inside an interactive" + " session)");
                    }
                    case "sleep" -> {
                        if (Parts.length < 3) {
                            System.out.println("  Usage: sleep <session-id> <seconds>");
                            continue;
                        }
                        try {
                            int Sid = Integer.parseInt(Parts[1]);
                            DoExec(Sid, "sleep " + Parts[2]);
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "tasks" -> {
                        if (Server == null) {
                            System.out.println("  Unavailable in cross-process mode — use primary operator" + " terminal");
                            continue;
                        }
                        ShowTasksQueue();
                    }
                    case "pivot" -> {
                        if (Parts.length < 3) {
                            System.out.println("  Usage: pivot <session-id> <host:port>");
                            continue;
                        }
                        try {
                            int Sid = Integer.parseInt(Parts[1]);
                            String Target = Parts[2];
                            if (!Target.contains(":")) {
                                System.out.println("  Invalid format — use host:port (e.g." + " 192.168.1.10:4445)");
                                continue;
                            }
                            String PivotHost = Target.split(":")[0];
                            int PivotPort;
                            try {
                                PivotPort = Integer.parseInt(Target.split(":")[1]);
                            } catch (NumberFormatException Ex) {
                                System.out.println("  Invalid port");
                                continue;
                            }

                            String PivotNote = "[PIVOT] " + Target;
                            Db.SetAgentNote(Sid, PivotNote);
                            Db.SaveCommandLog(Sid, OperatorName != null ? OperatorName : "cli", "pivot " + Target, "Pivot route set: " + Target, true);
                            System.out.printf("  %sPivot route registered: SESSION-%d > %s%s%n", AnsiColor.Green, Sid, Target, AnsiColor.Reset);
                            System.out.printf("  %s  ℹ  Use your agent to initiate connection to %s:%d%s%n", AnsiColor.White, PivotHost, PivotPort, AnsiColor.Reset);
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "screenshot" -> {
                        if (Parts.length < 2) {
                            System.out.println("  Usage: screenshot <session-id>");
                            continue;
                        }
                        try {
                            DoExec(Integer.parseInt(Parts[1]), "screenshot");
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "download" -> {
                        if (Parts.length < 3) {
                            System.out.println("  Usage: download <session-id> <remote-path>");
                            continue;
                        }
                        try {
                            DoExec(Integer.parseInt(Parts[1]), "download " + Parts[2]);
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    case "upload" -> {
                        if (Parts.length < 4) {
                            System.out.println("  Usage: upload <session-id> <local-path> <remote-path>");
                            continue;
                        }
                        try {
                            String RemotePath = Parts.length > 3 ? Parts[3] : "";
                            DoExec(Integer.parseInt(Parts[1]), "upload " + Parts[2] + " " + RemotePath);
                        } catch (NumberFormatException E) {
                            System.out.println("  Invalid session ID");
                        }
                    }
                    default -> {
                        System.out.printf("  %sUnknown command: %s%s%n", AnsiColor.Red, Cmd, AnsiColor.Reset);
                        System.out.println("  * Type 'help' for available commands");
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
            System.out.println("  Unavailable in cross-process mode — use primary operator terminal");
            return;
        }
        Optional<Session> Opt = Server.GetSessions().Get(SessionId);
        if (Opt.isEmpty()) {
            System.out.println("  Session not found");
            return;
        }
        Session S = Opt.get();
        System.out.println(Box("SESSION INFO — #" + SessionId));
        System.out.println();
        System.out.printf("  %sID         %s%d%n", AnsiColor.Red, AnsiColor.White, S.GetId());
        System.out.printf("  %sName       %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetDisplayName());
        System.out.printf("  %sType       %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetSessionType().name());
        System.out.printf("  %sHostname   %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetHostname());
        System.out.printf("  %sUser       %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetUser());
        System.out.printf("  %sOS         %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetOs());
        System.out.printf("  %sArch       %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetArch());
        System.out.printf("  %sAgent IP   %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetAgentIp());
        System.out.printf("  %sKey        %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetSessionKey());
        System.out.printf("  %sEncrypted  %s%b%n", AnsiColor.Red, AnsiColor.White, S.IsEncrypted());
        System.out.printf("  %smTLS       %s%b%n", AnsiColor.Red, AnsiColor.White, S.IsMtlsEnabled());
        System.out.printf("  %sCert CN    %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetCertCn());
        System.out.printf("  %sShell Mode %s%s%n", AnsiColor.Red, AnsiColor.White, S.GetShellMode());
        String Note = Db.GetAgentNote(SessionId);
        System.out.printf("  %sNote       %s%s%n", AnsiColor.Red, AnsiColor.White, Note.isEmpty() ? "(none)" : Note);
        System.out.println();
    }

    private void ShowCmdHistory(int SessionId, int Limit) {
        List<Map<String, Object>> Hist = Db.GetCommandHistory(SessionId, Limit);
        String Title = SessionId == 0 ? "COMMAND HISTORY (all sessions, last " + Limit + ")" : "COMMAND HISTORY — SESSION-" + SessionId + " (last " + Limit + ")";
        System.out.println(Box(Title));
        System.out.println();
        if (Hist.isEmpty()) {
            System.out.println("  No command history\n");
            return;
        }
        System.out.printf("  %s%-5s %-12s %-14s %-36s %s%s%n", AnsiColor.Red, "SID", "OPERATOR", "STATUS", "COMMAND", "TIMESTAMP", AnsiColor.Reset);
        System.out.println(Divider());
        for (Map<String, Object> R : Hist) {
            boolean Ok = Boolean.parseBoolean(R.getOrDefault("Success", "false").toString());
            String Cmd = R.getOrDefault("Command", "").toString();
            if (Cmd.length() > 36) Cmd = Cmd.substring(0, 35) + "…";
            System.out.printf("  %s%-5s %-12s %s%-14s%s %-36s %s%s%n", AnsiColor.White, R.getOrDefault("AgentId", "?"), R.getOrDefault("Operator", "?"), Ok ? AnsiColor.Green : AnsiColor.Red, Ok ? "ok" : "fail", AnsiColor.White, Cmd, R.getOrDefault("Timestamp", ""), AnsiColor.Reset);
        }
        System.out.println();
    }

    private void ShowTasksQueue() {
        System.out.println(Box("PENDING TASKS"));
        System.out.println();
        int Total = Server != null ? Server.GetSessions().Count() : 0;
        System.out.printf("  %sActive sessions: %s%d%s%n", AnsiColor.Red, AnsiColor.White, Total, AnsiColor.Reset);
        System.out.println("  Use 'broadcast' or 'exec' to queue commands to sessions");
        System.out.println();
    }

    private void StartWebPanel(String Host, int Port) {
        if (WebPanel != null) {
            System.out.printf("  %sWeb panel already running%s%n", AnsiColor.Red, AnsiColor.Reset);
            return;
        }
        try {
            WebPanel = new WebApp(Config, ActiveMode);
            if (Server != null && Server.IsRunning()) {
                WebPanel.AttachServer(Server, ServerStartTime);
            }
            WebPanel.Run(Host, Port);
            System.out.printf("  %sWeb panel started > http://%s:%d/%s%n", AnsiColor.Green, Host, Port, AnsiColor.Reset);
        } catch (Exception E) {
            Logger.Error("  Web panel start failed: " + E.getMessage());
            System.out.printf("  %sWeb panel failed: %s%s%n", AnsiColor.Red, E.getMessage(), AnsiColor.Reset);
            WebPanel = null;
        }
    }

    private void StopWebPanel() {
        if (WebPanel == null) {
            System.out.printf("  %sWeb panel not running%s%n", AnsiColor.Red, AnsiColor.Reset);
            return;
        }
        WebPanel.Stop();
        WebPanel = null;
        System.out.printf("  %sWeb panel stopped%s%n", AnsiColor.Green, AnsiColor.Reset);
    }

    private void ShowWebStatus() {
        if (WebPanel == null) {
            System.out.println("  Web Panel  : " + AnsiColor.Red + "Offline" + AnsiColor.Reset);
        } else {
            System.out.println("  Web Panel  : " + AnsiColor.Green + "Online" + AnsiColor.Reset);
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
        this.IsTeamMode = false;
        if (!StartServer(Host, Port, Mode)) return;
        try {
            Thread.sleep(300);
        } catch (InterruptedException Ign) {}
        RunLoop();
        System.exit(0);
    }

    public void RunTeamServer(String Host, int Port, ListenerMode Mode) throws IOException {
        this.ActiveMode = Mode;
        this.IsTeamMode = true;
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        if (!TeamLogin(Reader)) return;
        System.out.printf("  %n%sStarting listener on %s:%d%s%n%n", AnsiColor.Green, Host, Port, AnsiColor.Reset);
        if (!StartServer(Host, Port, Mode)) return;
        try {
            Thread.sleep(300);
        } catch (InterruptedException Ign) {}
        RunLoop();
        System.exit(0);
    }
}
