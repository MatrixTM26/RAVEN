package com.raven.interfaces.APP.core;

import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.utils.ServerConfig;
import java.time.Instant;
import java.util.Map;

public final class WebServerManager {

    private final ServerConfig Config;
    private final ListenerMode ActiveMode;
    private final WebLogger WebLogger;

    private RavenServer Server;
    private Instant ServerStartTime;
    private boolean ServerOwned;

    public WebServerManager(ServerConfig Config, ListenerMode ActiveMode, WebLogger WebLogger) {
        this.Config = Config;
        this.ActiveMode = ActiveMode;
        this.WebLogger = WebLogger;
    }

    public void AttachServer(RavenServer ExistingServer, Instant StartTime) {
        this.Server = ExistingServer;
        this.ServerStartTime = StartTime;
        this.ServerOwned = false;
        Logger.Info("WebApp attached to existing RavenServer on " + ExistingServer.GetHost() + ":" + ExistingServer.GetPort());
    }

    public void StartIfNeeded(EventHandler EventHandler) {
        if (Server != null) return;
        Server = new RavenServer(Config.GetServerHost(), Config.GetServerPort(), ActiveMode, Config);
        Server.AddEventListener((Type, Data) -> EventHandler.OnEvent(Type, Data));
        boolean[] Result = Server.StartServer();
        if (Result[0]) {
            ServerOwned = true;
            ServerStartTime = Instant.now();
            Thread AcceptThread = new Thread(Server::AcceptConnections, "AcceptConnections");
            AcceptThread.setDaemon(true);
            AcceptThread.start();
        } else {
            Logger.Warn("Agent server failed to start — web panel running in monitor-only mode");
        }
    }

    public void StartManual(String Host, int Port, EventHandler EventHandler) {
        if (Server != null && Server.IsRunning()) return;
        Server = new RavenServer(Host, Port, ActiveMode, Config);
        Server.AddEventListener((Type, Data) -> EventHandler.OnEvent(Type, Data));
        boolean[] Result = Server.StartServer();
        if (Result[0]) {
            ServerOwned = true;
            ServerStartTime = Instant.now();
            WebLogger.Add("Server started on " + Host + ":" + Port);
            new Thread(Server::AcceptConnections, "AcceptConnections").start();
        } else {
            WebLogger.Add("Failed to start server");
        }
    }

    public void Stop() {
        if (ServerOwned && Server != null && Server.IsRunning()) {
            Server.StopServer();
        }
        Server = null;
        ServerOwned = false;
        ServerStartTime = null;
    }

    public RavenServer GetServer() {
        return Server;
    }

    public Instant GetServerStartTime() {
        return ServerStartTime;
    }

    public boolean IsRunning() {
        return Server != null && Server.IsRunning();
    }

    public String GetUptime() {
        if (ServerStartTime == null) return "00:00:00";
        long Seconds = java.time.Duration.between(ServerStartTime, Instant.now()).getSeconds();
        return String.format("%02d:%02d:%02d", Seconds / 3600, (Seconds % 3600) / 60, Seconds % 60);
    }

    @FunctionalInterface
    public interface EventHandler {
        void OnEvent(EventType Type, Map<String, Object> Data);
    }
}
