package com.raven.interfaces.GUI.module.core.server;

import com.raven.core.event.EventManager.EventType;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.utils.ServerConfig;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

import java.time.Instant;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ServerController {

    private RavenServer Server;
    private Instant StartTime;
    private final ServerConfig Config;

    private final Label StatusLabel;
    private final Label ServerStatusLabel;
    private final Label ServerInfoLabel;
    private final Button StartBtn;
    private final Button StopBtn;

    private final Consumer<String> LogSink;
    private final BiConsumer<EventType, Map<String, Object>> EventHandler;
    private final Runnable OnStart;
    private final Runnable OnStop;

    private static final String RedColor = "#ff1f3d";
    private static final String TextColor = "#ffffff";

    public ServerController(ServerConfig config,
                             Label statusLabel,
                             Label serverStatusLabel,
                             Label serverInfoLabel,
                             Button startBtn,
                             Button stopBtn,
                             Consumer<String> logSink,
                             BiConsumer<EventType, Map<String, Object>> eventHandler,
                             Runnable onStart,
                             Runnable onStop) {
        this.Config = config;
        this.StatusLabel = statusLabel;
        this.ServerStatusLabel = serverStatusLabel;
        this.ServerInfoLabel = serverInfoLabel;
        this.StartBtn = startBtn;
        this.StopBtn = stopBtn;
        this.LogSink = logSink;
        this.EventHandler = eventHandler;
        this.OnStart = onStart;
        this.OnStop = onStop;
    }

    public void Start(String Host, int Port) {
        Server = new RavenServer(Host, Port, ListenerMode.FromString(Config.GetServerMode()), Config);
        Server.AddEventListener(EventHandler);
        boolean[] Result = Server.StartServer();
        if (!Result[0]) {
            LogSink.accept("[!] Failed to start server");
            return;
        }
        StartTime = Instant.now();
        Thread T = new Thread(Server::AcceptConnections, "AcceptConnections");
        T.setDaemon(true);
        T.start();
        Platform.runLater(() -> {
            ServerStatusLabel.setText("● ONLINE");
            ServerStatusLabel.setTextFill(Color.web(TextColor));
            ServerInfoLabel.setText(Host + ":" + Port);
            StatusLabel.setText("● Online");
            StatusLabel.setTextFill(Color.web(TextColor));
            StartBtn.setDisable(true);
            StopBtn.setDisable(false);
        });
        LogSink.accept("[+] Server started on " + Host + ":" + Port);
        LogSink.accept("[+] Session Key: " + Server.GetKeyBase64());
        if (OnStart != null) OnStart.run();
    }

    public void Stop() {
        if (Server == null) return;
        Server.StopServer();
        StartTime = null;
        Platform.runLater(() -> {
            ServerStatusLabel.setText("● OFFLINE");
            ServerStatusLabel.setTextFill(Color.web(RedColor));
            ServerInfoLabel.setText("Not running");
            StatusLabel.setText("● Offline");
            StatusLabel.setTextFill(Color.web(RedColor));
            StartBtn.setDisable(false);
            StopBtn.setDisable(true);
        });
        LogSink.accept("[!] Server stopped");
        if (OnStop != null) OnStop.run();
    }

    public RavenServer GetServer() { return Server; }
    public Instant GetStartTime() { return StartTime; }
    public boolean IsRunning() { return Server != null && Server.IsRunning(); }
}
