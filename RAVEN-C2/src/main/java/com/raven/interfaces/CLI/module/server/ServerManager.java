package com.raven.interfaces.CLI.module.server;

import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.interfaces.CLI.module.log.LogManager;
import com.raven.interfaces.CLI.module.terminal.TerminalRenderer;
import com.raven.utils.AnsiColor;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class ServerManager {

    private static final Object     SharedServerLock     = new Object();
    private static volatile RavenServer SharedServer     = null;
    private static volatile Instant SharedServerStart    = null;

    private final ServerConfig      Config;
    private final LogManager        LogManager;
    private final TerminalRenderer  Renderer;

    private RavenServer Server;
    private Instant     ServerStartTime;
    private boolean     IsTeamServerMode;
    private String      OperatorName;
    private ListenerMode ActiveMode;

    public ServerManager(ServerConfig Config, LogManager LogManager, TerminalRenderer Renderer) {
        this.Config      = Config;
        this.LogManager  = LogManager;
        this.Renderer    = Renderer;
    }

    public void SetContext(boolean IsTeamServerMode, String OperatorName, ListenerMode ActiveMode) {
        this.IsTeamServerMode = IsTeamServerMode;
        this.OperatorName     = OperatorName;
        this.ActiveMode       = ActiveMode;
    }

    public boolean Start(String Host, int Port, ListenerMode Mode) {
        this.ActiveMode = Mode;

        if (IsTeamServerMode) {
            synchronized (SharedServerLock) {
                if (SharedServer != null && SharedServer.IsRunning()) {
                    Server          = SharedServer;
                    ServerStartTime = SharedServerStart;
                    Logger.Info("Operator " + OperatorName + " joined existing server on " + Host + ":" + Port);
                    return true;
                }
                RavenServer NewServer = new RavenServer(Host, Port, Mode, Config);
                NewServer.AddEventListener(this::HandleEvent);
                boolean[] Result = NewServer.StartServer();
                if (!Result[0]) {
                    if (IsPortAlreadyBound(Host, Port)) {
                        Logger.Info("Operator " + OperatorName + " attached to existing listener on " + Host + ":" + Port + " (cross-process, Database-only mode)");
                        ServerStartTime = Instant.now();
                        LogManager.Add(AnsiColor.White + "  attached to listener on " + Host + ":" + Port + " (session commands limited in cross-process mode)" + AnsiColor.Reset, true);
                        return true;
                    }
                    LogManager.Add(AnsiColor.Red + "  failed to start server" + AnsiColor.Reset, true);
                    return false;
                }
                SharedServer  = NewServer;
                SharedServerStart = Instant.now();
                Server          = SharedServer;
                ServerStartTime = SharedServerStart;
                Thread AcceptThread = new Thread(SharedServer::AcceptConnections, "AcceptConnections");
                AcceptThread.setDaemon(true);
                AcceptThread.start();
                return true;
            }
        }

        Server = new RavenServer(Host, Port, Mode, Config);
        Server.AddEventListener(this::HandleEvent);
        boolean[] Result = Server.StartServer();
        if (!Result[0]) {
            LogManager.Add(AnsiColor.Red + "  Failed to start server" + AnsiColor.Reset, true);
            return false;
        }
        ServerStartTime = Instant.now();
        Thread AcceptThread = new Thread(Server::AcceptConnections, "AcceptConnections");
        AcceptThread.setDaemon(true);
        AcceptThread.start();
        return true;
    }

    public void Stop() {
        if (IsTeamServerMode && SharedServer != null && SharedServer == Server) {
            Logger.Info("teamserver session ended - server remains active");
        } else if (Server != null && Server.IsRunning()) {
            Server.StopServer();
        }
    }

    public void ShowStatus(String DatabaseState, String DatabaseType) {
        long UptimeSeconds = ServerStartTime != null
            ? Duration.between(ServerStartTime, Instant.now()).getSeconds() : 0;

        System.out.println(Renderer.Box("SERVER STATUS"));
        System.out.println();

        if (Server == null || !Server.IsRunning()) {
            if (IsTeamServerMode && ServerStartTime != null) {
                Logger.Custom("  %sStatus      %sONLINE (cross-process)%n", AnsiColor.Red, AnsiColor.Green);
                Logger.Custom("  %sMode        %s%s%n",   AnsiColor.Red, AnsiColor.White, ActiveMode.name());
                Logger.Custom("  %sAddress     %s%s:%d%n",AnsiColor.Red, AnsiColor.White, Config.GetServerHost(), Config.GetServerPort());
                Logger.Custom("  %sUptime      %s%s (local session)%n", AnsiColor.Red, AnsiColor.White, SystemHelper.FormatUptime(UptimeSeconds));
                Logger.Custom("  %sSessions    %s(N/A - cross-process)%n", AnsiColor.Red, AnsiColor.White);
            } else {
                Logger.Custom("  %sStatus    %sOFFLINE%n", AnsiColor.Red, AnsiColor.Red);
            }
        } else {
            Logger.Custom("  %sStatus      %sONLINE%n",   AnsiColor.Red, AnsiColor.Green);
            Logger.Custom("  %sMode        %s%s%n",       AnsiColor.Red, AnsiColor.White, ActiveMode.name());
            Logger.Custom("  %sAddress     %s%s:%d%n",    AnsiColor.Red, AnsiColor.White, Server.GetHost(), Server.GetPort());
            Logger.Custom("  %sUptime      %s%s%n",       AnsiColor.Red, AnsiColor.White, SystemHelper.FormatUptime(UptimeSeconds));
            Logger.Custom("  %sSessions    %s%d%n",       AnsiColor.Red, AnsiColor.White, Server.GetSessions().Count());
        }

        Logger.Custom("  %sDatabase    %s%s (%s)%n", AnsiColor.Red, AnsiColor.White, DatabaseState, DatabaseType);
        if (IsTeamServerMode && OperatorName != null) {
            Logger.Custom("  %sOperator    %s%s%n", AnsiColor.Red, AnsiColor.White, OperatorName);
        }
        System.out.println();
    }

    public RavenServer GetServer()          { return Server; }
    public Instant     GetServerStartTime() { return ServerStartTime; }
    public boolean     IsRunning()          { return Server != null && Server.IsRunning(); }

    private void HandleEvent(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case ServerStarted ->
                LogManager.Add(AnsiColor.White + " server listening on " + Data.get("Host") + ":" + Data.get("Port") + AnsiColor.Reset, true);
            case AgentConnected ->
                LogManager.Add(AnsiColor.Green + "  [" + Data.get("AgentName") + "] session-" + Data.get("ID") + " key: " + Data.get("SessionKey") + " (" + Data.get("OS") + ")" + AnsiColor.Reset, true);
            case AgentDisconnected ->
                LogManager.Add(AnsiColor.Red + "  session-" + Data.get("ID") + " disconnected: " + Data.get("Reason") + AnsiColor.Reset, true);
            case AgentRemoved ->
                LogManager.Add(AnsiColor.White + "  session-" + Data.get("ID") + " removed" + AnsiColor.Reset, false);
            case Error ->
                LogManager.Add(AnsiColor.Red + "  Error: " + Data.get("Message") + AnsiColor.Reset, true);
        }
    }

    private static boolean IsPortAlreadyBound(String Host, int Port) {
        try (ServerSocket TestSocket = new ServerSocket()) {
            TestSocket.setReuseAddress(false);
            TestSocket.bind(new InetSocketAddress(Port));
            return false;
        } catch (IOException Exception) {
            return true;
        }
    }
}
