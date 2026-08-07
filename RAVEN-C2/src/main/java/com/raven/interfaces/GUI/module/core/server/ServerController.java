package com.raven.interfaces.GUI.module.core.server;

import com.raven.core.event.EventManager;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.utils.ServerConfig;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

public class ServerController {

    private static final String IconCircle = "\uEF4A";

    private RavenServer Server;
    private Instant StartTime;

    private final ServerConfig Configuration;
    private final Label StatusDot;
    private final Label ServerStatusLabel;
    private final Label ServerInfoLabel;
    private final Button StartButton;
    private final Button StopButton;
    private final Consumer<String> LogConsumer;
    private final EventManager.EventListener EventHandler;
    private final Runnable OnStartCallback;
    private final Runnable OnStopCallback;

    public ServerController(ServerConfig Configuration, Label StatusDot, Label ServerStatusLabel, Label ServerInfoLabel, Button StartButton, Button StopButton, Consumer<String> LogConsumer, EventManager.EventListener EventHandler, Runnable OnStartCallback, Runnable OnStopCallback) {
        this.Configuration = Configuration;
        this.StatusDot = StatusDot;
        this.ServerStatusLabel = ServerStatusLabel;
        this.ServerInfoLabel = ServerInfoLabel;
        this.StartButton = StartButton;
        this.StopButton = StopButton;
        this.LogConsumer = LogConsumer;
        this.EventHandler = EventHandler;
        this.OnStartCallback = OnStartCallback;
        this.OnStopCallback = OnStopCallback;
    }

    public void Start(String Host, int Port) {
        Server = new RavenServer(Host, Port, ListenerMode.FromString(Configuration.GetServerMode()), Configuration);
        Server.AddEventListener(EventHandler);
        boolean[] Result = Server.StartServer();
        if (!Result[0]) {
            LogConsumer.accept("[!] Failed to start server");
            return;
        }
        StartTime = Instant.now();
        Thread AcceptThread = new Thread(Server::AcceptConnections, "AcceptConnections");
        AcceptThread.setDaemon(true);
        AcceptThread.start();

        Platform.runLater(() -> {
            if (ServerStatusLabel != null) {
                ServerStatusLabel.setText("Online");
                ServerStatusLabel.setTextFill(Color.web(Palette.AccentGreen));
            }
            if (ServerInfoLabel != null) ServerInfoLabel.setText(Host + ":" + Port + "  |  " + Configuration.GetServerMode().toUpperCase());
            if (StatusDot != null) {
                StatusDot.setText(IconCircle + "  Online");
                StatusDot.setStyle("-fx-text-fill:" + Palette.AccentGreen + ";" + "-fx-font-size:11px;" + "-fx-font-family:'Material Icons','Segoe UI';");
            }
            if (StartButton != null) StartButton.setDisable(true);
            if (StopButton != null) StopButton.setDisable(false);
        });

        LogConsumer.accept("[+] Server started — " + Host + ":" + Port);
        LogConsumer.accept("[+] Session key: " + Server.GetKeyBase64());
        if (OnStartCallback != null) OnStartCallback.run();
    }

    public void Stop() {
        if (Server == null) return;
        Server.StopServer();
        StartTime = null;

        Platform.runLater(() -> {
            if (ServerStatusLabel != null) {
                ServerStatusLabel.setText("Offline");
                ServerStatusLabel.setTextFill(Color.web(Palette.AccentRed));
            }
            if (ServerInfoLabel != null) ServerInfoLabel.setText("Not running");
            if (StatusDot != null) {
                StatusDot.setText(IconCircle + "  Offline");
                StatusDot.setStyle("-fx-text-fill:" + Palette.AccentRed + ";" + "-fx-font-size:11px;" + "-fx-font-family:'Material Icons','Segoe UI';");
            }
            if (StartButton != null) StartButton.setDisable(false);
            if (StopButton != null) StopButton.setDisable(true);
        });

        LogConsumer.accept("[!] Server stopped");
        if (OnStopCallback != null) OnStopCallback.run();
    }

    public RavenServer GetServer() {
        return Server;
    }

    public Instant GetStartTime() {
        return StartTime;
    }

    public boolean IsRunning() {
        return Server != null && Server.IsRunning();
    }
}
