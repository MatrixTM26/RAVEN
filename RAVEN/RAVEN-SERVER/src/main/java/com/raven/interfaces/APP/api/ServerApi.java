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
    private final WebServerManager.EventHandler EventHandler;

    public ServerApi(ServerConfig Config, WebServerManager ServerManager, WebServerManager.EventHandler EventHandler) {
        this.Config        = Config;
        this.ServerManager = ServerManager;
        this.EventHandler  = EventHandler;
    }

    public String Status(HttpExchange Exchange) {
        boolean Up = ServerManager.IsRunning();
        Map<String, Object> Response = new LinkedHashMap<>();
        Response.put("Status",    Up ? "Online" : "Offline");
        Response.put("Mode",      Config.GetServerMode());
        Response.put("Host",      Up ? ServerManager.GetServer().GetHost() : Config.GetServerHost());
        Response.put("Port",      Up ? ServerManager.GetServer().GetPort() : Config.GetServerPort());
        Response.put("StartedAt", ServerManager.GetServerStartTime() != null ? ServerManager.GetServerStartTime().getEpochSecond() : 0);
        Response.put("Uptime",    ServerManager.GetUptime());
        Response.put("Agents",    Up ? ServerManager.GetServer().GetSessions().Count() : 0);
        if (Up) Response.put("Key", ServerManager.GetServer().GetKeyBase64());
        String DbType  = Config.GetDatabaseType();
        boolean DbUp   = !DbType.isBlank() && !DbType.equals("none");
        Response.put("DbOnline",  DbUp);
        Response.put("DbType",    DbUp ? DbType : "none");
        Response.put("SleepIntervalMs", Config.GetSleepIntervalMs());
        Response.put("JitterMs",        Config.GetJitterMs());
        return HttpHelper.Json(Response);
    }

    public String Start(HttpExchange Exchange) throws Exception {
        if (ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Already running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Host = HttpHelper.Str(Body, "Host", Config.GetServerHost());
        int Port    = HttpHelper.Num(Body, "Port", Config.GetServerPort());
        ServerManager.StartManual(Host, Port, EventHandler);
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Failed to start listener on " + Host + ":" + Port));
        return HttpHelper.Json(Map.of(
            "Success",   true,
            "Host",      Host,
            "Port",      Port,
            "StartedAt", ServerManager.GetServerStartTime().getEpochSecond()
        ));
    }

    public String Stop(HttpExchange Exchange) {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Not running"));
        ServerManager.Stop();
        return HttpHelper.Json(Map.of("Success", true));
    }
}
