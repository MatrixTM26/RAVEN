package com.raven.interfaces.APP.api;

import com.raven.interfaces.APP.core.WebServerManager;
import com.raven.interfaces.APP.shared.HttpHelper;
import com.raven.utils.ServerConfig;
import com.sun.net.httpserver.HttpExchange;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerApi {

    private final ServerConfig Config;
    private final WebServerManager ServerManager;

    public ServerApi(ServerConfig Config, WebServerManager ServerManager) {
        this.Config = Config;
        this.ServerManager = ServerManager;
    }

    public String Status(HttpExchange Exchange) {
        boolean Up = ServerManager.IsRunning();
        Map<String, Object> Response = new LinkedHashMap<>();
        Response.put("Status", Up ? "Online" : "Offline");
        Response.put("Mode", Config.GetServerMode());
        Response.put("Host", Up ? ServerManager.GetServer().GetHost() : Config.GetServerHost());
        Response.put("Port", Up ? ServerManager.GetServer().GetPort() : Config.GetServerPort());
        Response.put("StartedAt", ServerManager.GetServerStartTime() != null ? ServerManager.GetServerStartTime().getEpochSecond() : 0);
        Response.put("Uptime", ServerManager.GetUptime());
        Response.put("Agents", Up ? ServerManager.GetServer().GetSessions().Count() : 0);
        if (Up) Response.put("Key", ServerManager.GetServer().GetKeyBase64());
        String DbType   = Config.GetDatabaseType();
        boolean DbOnline = !DbType.isBlank() && !DbType.equals("none");
        Response.put("DbOnline", DbOnline);
        Response.put("DbType",   DbOnline ? DbType : "none");
        return HttpHelper.Json(Response);
    }

    public String Start(HttpExchange Exchange) throws Exception {
        if (ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Already running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Host = HttpHelper.Str(Body, "Host", Config.GetServerHost());
        int Port = HttpHelper.Num(Body, "Port", Config.GetServerPort());
        ServerManager.StartManual(Host, Port, (Type, Data) -> {});
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Failed to start server"));
        return HttpHelper.Json(Map.of("Success", true, "Host", Host, "Port", Port, "StartedAt", ServerManager.GetServerStartTime().getEpochSecond()));
    }

    public String Stop(HttpExchange Exchange) {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Not running"));
        ServerManager.Stop();
        return HttpHelper.Json(Map.of("Success", true));
    }
}
