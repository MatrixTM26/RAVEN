package com.raven.interfaces;

import com.google.gson.Gson;
import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import com.raven.interfaces.APP.api.AuthApi;
import com.raven.interfaces.APP.api.AuthApi.TokenInfo;
import com.raven.interfaces.APP.core.HttpRouter;
import com.raven.interfaces.APP.shared.HttpHelper;
import com.raven.interfaces.APP.shared.PathResolver;
import com.raven.utils.ServerConfig;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class TeamServer {

    private static final long TOKEN_TTL_MS = 8L * 60 * 60 * 1000;
    private static final int MAX_CHAT = 500;

    private final ServerConfig Config;
    private final ListenerMode Mode;
    private final TeamDatabase Db;
    private final int MaxLogs;
    private final PathResolver PathResolver;
    private final AuthApi Auth;
    private final Map<String, TokenInfo> Tokens = new ConcurrentHashMap<>();
    private final List<String> Logs = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> ChatMessages = new CopyOnWriteArrayList<>();

    private RavenServer Server;
    private HttpServer HttpSrv;
    private HttpRouter Router;
    private Instant ServerStartTime;

    @FunctionalInterface
    private interface RouteHandler {
        String handle(HttpExchange E, TokenInfo T) throws Exception;
    }

    public TeamServer(ServerConfig Config, ListenerMode Mode) {
        this.Config = Config;
        this.Mode = Mode;
        this.MaxLogs = Config.GetMaxLogEntries();
        this.Db = TeamDatabase.Connect(Config);
        this.Auth = new AuthApi(this.Db);
        this.PathResolver = new PathResolver(TeamServer.class);
    }

    public void Run(String Host, int Port) throws Exception {
        HttpSrv = HttpServer.create(new InetSocketAddress(Host, Port), 100);
        Router = new HttpRouter(HttpSrv, Config, PathResolver);
        RegisterRoutes();
        HttpSrv.setExecutor(Executors.newFixedThreadPool(20));
        HttpSrv.start();
        Logger.Info("TeamServer started  : http://" + Host + ":" + Port);
        Logger.Info("Web UI              : http://" + Host + ":" + Port + "/");
        Logger.Info("API base            : http://" + Host + ":" + Port + "/api/");
        AddLog("TeamServer initialized : " + Mode.name());
    }

    public void Stop() {
        if (Server != null) Server.StopServer();
        if (HttpSrv != null) HttpSrv.stop(0);
        Db.Close();
    }

    private void RegisterRoutes() {
        HttpSrv.createContext("/api/auth/login", E -> Route(E, this::AuthLogin, false));
        HttpSrv.createContext("/api/auth/logout", E -> Route(E, this::AuthLogout, true));
        HttpSrv.createContext("/api/server/status", E -> Route(E, this::ApiStatus, true));
        HttpSrv.createContext("/api/server/start", E -> Route(E, this::ApiStart, true));
        HttpSrv.createContext("/api/server/stop", E -> Route(E, this::ApiStop, true));
        HttpSrv.createContext("/api/agents", E -> Route(E, this::ApiAgents, true));
        HttpSrv.createContext("/api/agents/kill", E -> Route(E, this::ApiKill, true));
        HttpSrv.createContext("/api/agents/note", E -> Route(E, this::ApiNote, true));
        HttpSrv.createContext("/api/command/execute", E -> Route(E, this::ApiExec, true));
        HttpSrv.createContext("/api/command/broadcast", E -> Route(E, this::ApiBroadcast, true));
        HttpSrv.createContext("/api/command/broadcastall", E -> Route(E, this::ApiBroadcastAll, true));
        HttpSrv.createContext("/api/command/history", E -> Route(E, this::ApiCmdHist, true));
        HttpSrv.createContext("/api/sessions/history", E -> Route(E, this::ApiSessHist, true));
        HttpSrv.createContext("/api/logs", E -> Route(E, this::ApiLogs, true));
        HttpSrv.createContext("/api/team/operators", E -> Route(E, this::ApiOperators, true));
        HttpSrv.createContext("/api/team/operators/create", E -> Route(E, this::ApiOpCreate, true));
        HttpSrv.createContext("/api/team/operators/role", E -> Route(E, this::ApiOpRole, true));
        HttpSrv.createContext("/api/team/operators/delete", E -> Route(E, this::ApiOpDelete, true));
        HttpSrv.createContext("/api/team/operators/password", E -> Route(E, this::ApiOpPassword, true));
        HttpSrv.createContext("/api/team/operators/kick", E -> Route(E, this::ApiOpKick, true));
        HttpSrv.createContext("/api/team/operators/list", E -> Route(E, this::ApiOpList, true));
        HttpSrv.createContext("/api/team/operators/add", E -> Route(E, this::ApiOpAdd, true));
        HttpSrv.createContext("/api/team/roles", E -> Route(E, this::ApiRoles, true));
        HttpSrv.createContext("/api/team/chat/send", E -> Route(E, this::ApiChatSend, true));
        HttpSrv.createContext("/api/team/chat/messages", E -> Route(E, this::ApiChatMessages, true));
        HttpSrv.createContext("/api/team/chat/logs", E -> Route(E, this::ApiChatLogs, true));
        Router.RegisterStatic();
    }

    private void Route(HttpExchange E, RouteHandler H, boolean RequireAuth) {
        try {
            TokenInfo T = null;
            if (RequireAuth) {
                String Auth = E.getRequestHeaders().getFirst("Authorization");
                if (Auth == null || !Auth.startsWith("Bearer ")) {
                    WriteJson(E, 401, Map.of("Error", "Unauthorized"));
                    return;
                }
                T = Tokens.get(Auth.substring(7));
                if (T == null || T.ExpiresAt() < System.currentTimeMillis()) {
                    WriteJson(E, 401, Map.of("Error", "Token expired or invalid"));
                    return;
                }
            }
            String Result = H.handle(E, T);
            byte[] Bytes = Result.getBytes("UTF-8");
            E.getResponseHeaders().add("Content-Type", "application/json");
            E.sendResponseHeaders(200, Bytes.length);
            try (OutputStream Out = E.getResponseBody()) {
                Out.write(Bytes);
            }
        } catch (Exception Ex) {
            try {
                WriteJson(E, 500, Map.of("Error", Ex.getMessage()));
            } catch (Exception Ignored) {}
        }
    }

    private void WriteJson(HttpExchange E, int Status, Object Body) throws IOException {
        byte[] Bytes = HttpHelper.Json(Body).getBytes("UTF-8");
        E.getResponseHeaders().add("Content-Type", "application/json");
        E.sendResponseHeaders(Status, Bytes.length);
        try (OutputStream Out = E.getResponseBody()) {
            Out.write(Bytes);
        }
    }

    private Map<String, Object> Body(HttpExchange E) throws Exception {
        try (InputStream In = E.getRequestBody()) {
            byte[] Raw = In.readAllBytes();
            if (Raw.length == 0) return new HashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> M = new Gson().fromJson(new String(Raw, "UTF-8"), Map.class);
            return M != null ? M : new HashMap<>();
        }
    }

    private String Str(Map<String, Object> M, String K, String Def) {
        return HttpHelper.Str(M, K, Def);
    }

    private int Num(Map<String, Object> M, String K, int Def) {
        return HttpHelper.Num(M, K, Def);
    }

    private String AuthLogin(HttpExchange E, TokenInfo Ignored) throws Exception {
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Pass = Str(B, "Password", "");
        if (User.isEmpty() || Pass.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        if (!Db.ValidateOperator(User, TeamDatabase.HashPassword(Pass))) return HttpHelper.Json(Map.of("Error", "Invalid credentials"));
        OperatorRole Role = Db.GetOperatorRole(User);
        String Token = UUID.randomUUID().toString().replace("-", "");
        Tokens.put(Token, new TokenInfo(User, Role, System.currentTimeMillis() + TOKEN_TTL_MS));
        Logger.Info("Operator login: " + User + " [" + Role + "]");
        AddLog("[AUTH] Login: " + User + " [" + Role + "]");
        return HttpHelper.Json(Map.of("Token", Token, "Role", Role.name(), "Username", User, "ExpiresIn", TOKEN_TTL_MS / 1000));
    }

    private String AuthLogout(HttpExchange E, TokenInfo T) throws Exception {
        String Auth = E.getRequestHeaders().getFirst("Authorization");
        if (Auth != null && Auth.startsWith("Bearer ")) Tokens.remove(Auth.substring(7));
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiStatus(HttpExchange E, TokenInfo T) {
        boolean Up = Server != null && Server.IsRunning();
        Map<String, Object> R = new LinkedHashMap<>();
        R.put("Status", Up ? "Online" : "Offline");
        R.put("Mode", Mode.name());
        R.put("Host", Up ? Server.GetHost() : Config.GetServerHost());
        R.put("Port", Up ? Server.GetPort() : Config.GetServerPort());
        R.put("Agents", Up ? Server.GetSessions().Count() : 0);
        R.put("Uptime", Uptime());
        R.put("Operator", T.Username());
        R.put("Role", T.Role().name());
        R.put("DbType", Config.GetDatabaseType());
        R.put("DbOnline", Db.IsConnected());
        if (Up) R.put("Key", Server.GetKeyBase64());
        return HttpHelper.Json(R);
    }

    private String ApiStart(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        if (Server != null && Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server already running"));
        Map<String, Object> B = Body(E);
        String Host = Str(B, "Host", Config.GetServerHost());
        int Port = Num(B, "Port", Config.GetServerPort());
        Server = new RavenServer(Host, Port, Mode, Config);
        Server.AddEventListener(this::OnEvent);
        boolean[] Result = Server.StartServer();
        if (!Result[0]) return HttpHelper.Json(Map.of("Error", "Failed to start server"));
        ServerStartTime = Instant.now();
        new Thread(Server::AcceptConnections, "AcceptConnections").start();
        AddLog("Server started on " + Host + ":" + Port + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true, "Host", Host, "Port", Port));
    }

    private String ApiStop(HttpExchange E, TokenInfo T) {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Server.StopServer();
        Server = null;
        ServerStartTime = null;
        AddLog("Server stopped by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiAgents(HttpExchange E, TokenInfo T) {
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Agents", Collections.emptyList()));
        List<Map<String, Object>> Agents = new ArrayList<>();
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
            Agents.add(A);
        }
        return HttpHelper.Json(Map.of("Agents", Agents));
    }

    private String ApiExec(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanExecute()) return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        int AgentId = Num(B, "AgentId", 0);
        String Command = Str(B, "Command", "");
        if (AgentId == 0 || Command.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and Command required"));
        AddLog("[>] [" + T.Username() + "] Agent-" + AgentId + " » " + Command);
        String[] R = Server.ExecuteCommand(AgentId, Command);
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(AgentId, T.Username(), Command, R[1], Ok);
        AddLog(R[1]);
        return HttpHelper.Json(Map.of("Success", Ok, "Output", R[1], "Command", Command));
    }

    private String ApiBroadcast(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanBroadcast()) return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        String Command = Str(B, "Command", "");
        if (Command.isEmpty()) return HttpHelper.Json(Map.of("Error", "Command required"));
        @SuppressWarnings("unchecked")
        List<Object> Raw = (List<Object>) B.getOrDefault("AgentIds", new ArrayList<>());
        List<Integer> Ids = new ArrayList<>();
        for (Object O : Raw) {
            try {
                Ids.add((int) Double.parseDouble(O.toString()));
            } catch (Exception Ignored) {}
        }
        if (Ids.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentIds required"));
        AddLog("[BROADCAST] [" + T.Username() + "] > " + Ids.size() + " agents » " + Command);
        Map<Integer, String[]> Results = Server.BroadcastCommand(Ids, Command);
        Map<String, Object> Out = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            Out.put(String.valueOf(En.getKey()), Map.of("Success", Ok, "Output", En.getValue()[1]));
            Db.SaveCommandLog(En.getKey(), T.Username(), Command, En.getValue()[1], Ok);
        }
        return HttpHelper.Json(Map.of("Success", true, "Results", Out, "Count", Results.size()));
    }

    private String ApiBroadcastAll(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanBroadcast()) return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        String Command = Str(Body(E), "Command", "");
        if (Command.isEmpty()) return HttpHelper.Json(Map.of("Error", "Command required"));
        AddLog("[BROADCAST-ALL] [" + T.Username() + "] > " + Server.GetSessions().Count() + " agents » " + Command);
        Map<Integer, String[]> Results = Server.BroadcastAll(Command);
        Map<String, Object> Out = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            Out.put(String.valueOf(En.getKey()), Map.of("Success", Ok, "Output", En.getValue()[1]));
            Db.SaveCommandLog(En.getKey(), T.Username(), Command, En.getValue()[1], Ok);
        }
        return HttpHelper.Json(Map.of("Success", true, "Results", Out, "Count", Results.size()));
    }

    private String ApiCmdHist(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        return HttpHelper.Json(Map.of("History", Db.GetCommandHistory(Num(B, "AgentId", 0), Num(B, "Limit", 100))));
    }

    private String ApiSessHist(HttpExchange E, TokenInfo T) throws Exception {
        return HttpHelper.Json(Map.of("Sessions", Db.GetSessionHistory(Num(Body(E), "Limit", 100))));
    }

    private String ApiKill(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanKillSession()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        int Id = Num(Body(E), "AgentId", 0);
        if (Id == 0) return HttpHelper.Json(Map.of("Error", "AgentId required"));
        Server.RemoveSession(Id);
        AddLog("[KILL] [" + T.Username() + "] Agent-" + Id);
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiNote(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        Db.SetAgentNote(Num(B, "AgentId", 0), Str(B, "Note", ""));
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiLogs(HttpExchange E, TokenInfo T) {
        return HttpHelper.Json(Map.of("Logs", new ArrayList<>(Logs)));
    }

    private String ApiOperators(HttpExchange E, TokenInfo T) {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        return HttpHelper.Json(Map.of("Operators", Db.GetOperators()));
    }

    private String ApiOpCreate(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Pass = Str(B, "Password", "");
        String Role = Str(B, "Role", "OPERATOR");
        if (User.isEmpty() || Pass.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        if (Pass.length() < 8) return HttpHelper.Json(Map.of("Error", "Password must be at least 8 characters"));
        OperatorRole R = OperatorRole.FromString(Role);
        if (!Db.CreateOperator(User, TeamDatabase.HashPassword(Pass), R)) return HttpHelper.Json(Map.of("Error", "Username already exists"));
        AddLog("[TEAM] Created operator: " + User + " [" + R + "] by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true, "Username", User, "Role", R.name()));
    }

    private String ApiOpRole(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Role = Str(B, "Role", "");
        if (User.isEmpty() || Role.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Role required"));
        if (User.equals("admin")) return HttpHelper.Json(Map.of("Error", "Cannot change admin role"));
        OperatorRole R = OperatorRole.FromString(Role);
        Db.UpdateOperatorRole(User, R);
        AddLog("[TEAM] Role updated: " + User + " > " + R + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiOpDelete(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        String User = Str(Body(E), "Username", "");
        if (User.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username required"));
        if (User.equals("admin")) return HttpHelper.Json(Map.of("Error", "Cannot delete admin"));
        boolean Del = Db.DeleteOperator(User);
        if (Del) AddLog("[TEAM] Deleted operator: " + User + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", Del));
    }

    private String ApiOpPassword(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String NewPass = Str(B, "Password", "");
        if (User.isEmpty() || NewPass.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        if (NewPass.length() < 8) return HttpHelper.Json(Map.of("Error", "Password must be at least 8 characters"));
        boolean Ok = Db.UpdateOperatorPassword(User, TeamDatabase.HashPassword(NewPass));
        if (Ok) AddLog("[TEAM] Password changed: " + User + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", Ok));
    }

    private String ApiOpKick(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanKickOperator()) return HttpHelper.Json(Map.of("Error", "SUPER role required"));
        String User = Str(Body(E), "Username", "");
        if (User.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username required"));
        if (User.equals("admin")) return HttpHelper.Json(Map.of("Error", "Cannot kick admin"));
        if (User.equals(T.Username())) return HttpHelper.Json(Map.of("Error", "Cannot kick yourself"));
        boolean Del = Db.DeleteOperator(User);
        if (Del) AddLog("[TEAM] Kicked operator: " + User + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", Del));
    }

    private String ApiOpList(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN+ required"));
        return HttpHelper.Json(Map.of("Operators", Db.GetOperators()));
    }

    private String ApiOpAdd(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN+ required"));
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Pass = Str(B, "Password", "");
        String Role = Str(B, "Role", "OPERATOR");
        if (User.isEmpty() || Pass.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        if (Pass.length() < 8) return HttpHelper.Json(Map.of("Error", "Password must be 8+ chars"));
        OperatorRole R = OperatorRole.FromString(Role);
        if (R == OperatorRole.SUPER && !T.Role().IsSuperAdmin()) return HttpHelper.Json(Map.of("Error", "Only SUPER can create SUPER"));
        if (!Db.CreateOperator(User, TeamDatabase.HashPassword(Pass), R)) return HttpHelper.Json(Map.of("Error", "Username already exists"));
        AddLog("[TEAM] Operator added: " + User + " [" + R + "] by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true, "Message", "Operator created: " + User));
    }

    private String ApiRoles(HttpExchange E, TokenInfo T) {
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
        return HttpHelper.Json(Map.of("Roles", Roles));
    }

    private String ApiChatSend(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        String Msg = Str(B, "Message", "");
        String To = Str(B, "To", "all");
        if (Msg.isEmpty()) return HttpHelper.Json(Map.of("Error", "Message required"));
        Map<String, Object> Entry = new LinkedHashMap<>();
        Entry.put("From", T.Username());
        Entry.put("To", To);
        Entry.put("Message", Msg);
        Entry.put("Time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        ChatMessages.add(Entry);
        if (ChatMessages.size() > MAX_CHAT) ChatMessages.remove(0);
        Db.SaveChatLog(T.Username(), To, Msg);
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiChatMessages(HttpExchange E, TokenInfo T) throws Exception {
        String User = T.Username();
        List<Map<String, Object>> Visible = new ArrayList<>();
        for (Map<String, Object> M : ChatMessages) {
            String To = M.getOrDefault("To", "all").toString();
            String From = M.getOrDefault("From", "").toString();
            if (To.equals("all") || To.equals(User) || From.equals(User)) Visible.add(M);
        }
        return HttpHelper.Json(Map.of("Messages", Visible));
    }

    private String ApiChatLogs(HttpExchange E, TokenInfo T) throws Exception {
        int Limit = Num(Body(E), "Limit", 100);
        return HttpHelper.Json(Map.of("Logs", Db.GetChatLogs(Math.min(Limit, 500))));
    }

    private void OnEvent(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case AgentConnected -> {
                AddLog("SESSION-" + Data.get("ID") + " [" + Data.get("Type") + "] " + Data.get("User") + "@" + Data.get("Hostname") + " " + Data.get("OS"));
                Db.SaveSessionEvent(Data, "connected");
            }
            case AgentDisconnected -> {
                AddLog("SESSION-" + Data.get("ID") + " disconnected: " + Data.get("Reason"));
                Db.SaveSessionEvent(Data, "disconnected");
            }
            case Error -> AddLog("" + Data.get("Message"));
        }
    }

    private void AddLog(String Msg) {
        String Entry = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + Msg;
        Logs.add(Entry);
        if (Logs.size() > MaxLogs) Logs.remove(0);
        Db.SaveLog(Entry);
    }

    private String Uptime() {
        if (ServerStartTime == null) return "00:00:00";
        long S = Duration.between(ServerStartTime, Instant.now()).getSeconds();
        return String.format("%02d:%02d:%02d", S / 3600, (S % 3600) / 60, S % 60);
    }
}
