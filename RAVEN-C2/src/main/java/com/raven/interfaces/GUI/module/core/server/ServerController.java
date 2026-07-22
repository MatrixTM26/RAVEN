package com.raven.interfaces.GUI.module.core.server;

import com.raven.core.event.EventManager;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.utils.ServerConfig;
import java.time.Instant;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

public class ServerController {

    private RavenServer server;
    private Instant startTime;

    private final ServerConfig config;
    private final Label statusDot;
    private final Label serverStatusLabel;
    private final Label serverInfoLabel;
    private final Button startBtn;
    private final Button stopBtn;
    private final Consumer<String> log;
    private final EventManager.EventListener eventHandler;
    private final Runnable onStart;
    private final Runnable onStop;

    public ServerController(ServerConfig config, Label statusDot, Label serverStatusLabel, Label serverInfoLabel, Button startBtn, Button stopBtn, Consumer<String> log, EventManager.EventListener eventHandler, Runnable onStart, Runnable onStop) {
        this.config = config;
        this.statusDot = statusDot;
        this.serverStatusLabel = serverStatusLabel;
        this.serverInfoLabel = serverInfoLabel;
        this.startBtn = startBtn;
        this.stopBtn = stopBtn;
        this.log = log;
        this.eventHandler = eventHandler;
        this.onStart = onStart;
        this.onStop = onStop;
    }

    public void Start(String host, int port) {
        server = new RavenServer(host, port, ListenerMode.FromString(config.GetServerMode()), config);
        server.AddEventListener(eventHandler);
        boolean[] result = server.StartServer();
        if (!result[0]) {
            log.accept("[!] Failed to start server");
            return;
        }
        startTime = Instant.now();
        Thread t = new Thread(server::AcceptConnections, "AcceptConnections");
        t.setDaemon(true);
        t.start();
        Platform.runLater(() -> {
            serverStatusLabel.setText("Online");
            serverStatusLabel.setTextFill(Color.web("#4caf50"));
            serverInfoLabel.setText(host + ":" + port + "  |  " + config.GetServerMode().toUpperCase());
            statusDot.setText("Online");
            statusDot.setTextFill(Color.web("#4caf50"));
            startBtn.setDisable(true);
            stopBtn.setDisable(false);
        });
        log.accept("[+] Server started — " + host + ":" + port);
        log.accept("[+] Session key: " + server.GetKeyBase64());
        if (onStart != null) onStart.run();
    }

    public void Stop() {
        if (server == null) return;
        server.StopServer();
        startTime = null;
        Platform.runLater(() -> {
            serverStatusLabel.setText("Offline");
            serverStatusLabel.setTextFill(Color.web("#f44336"));
            serverInfoLabel.setText("Not running");
            statusDot.setText("Offline");
            statusDot.setTextFill(Color.web("#f44336"));
            startBtn.setDisable(false);
            stopBtn.setDisable(true);
        });
        log.accept("[!] Server stopped");
        if (onStop != null) onStop.run();
    }

    public RavenServer GetServer() {
        return server;
    }

    public Instant GetStartTime() {
        return startTime;
    }

    public boolean IsRunning() {
        return server != null && server.IsRunning();
    }
}
