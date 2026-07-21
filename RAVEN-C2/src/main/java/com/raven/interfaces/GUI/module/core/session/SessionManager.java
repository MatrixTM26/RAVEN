package com.raven.interfaces.GUI.module.core.session;

import com.raven.core.database.TeamDatabase;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SessionManager {

    private final RavenServer server;
    private final TeamDatabase db;
    private final ObservableList<SessionRow> rows;
    private final Label countLabel;

    public SessionManager(RavenServer server, TeamDatabase db,
                          ObservableList<SessionRow> rows, Label countLabel) {
        this.server     = server;
        this.db         = db;
        this.rows       = rows;
        this.countLabel = countLabel;
    }

    public void Refresh() {
        if (server == null) return;
        Platform.runLater(() -> {
            rows.clear();
            server.GetSessions().GetAll().forEach(s -> rows.add(new SessionRow(s)));
            int n = rows.size();
            countLabel.setText(n + " session" + (n != 1 ? "s" : ""));
        });
    }

    public void Kill(int sid) {
        server.RemoveSession(sid);
        Refresh();
    }

    public void RunAgentCommand(int sid, String cmd, String operator, Consumer<String> log) {
        if (server == null || !server.IsRunning()) { log.accept("[!] Server not running"); return; }
        log.accept("> SESSION-" + sid + " — " + cmd);
        Executors.newSingleThreadExecutor().submit(() -> {
            String[] result = server.ExecuteCommand(sid, cmd);
            boolean ok = Boolean.parseBoolean(result[0]);
            db.SaveCommandLog(sid, operator != null ? operator : "gui", cmd, result[1], ok);
            Platform.runLater(() -> log.accept(result[1]));
        });
    }

    public Optional<Session> Get(int sid) {
        return server.GetSessions().Get(sid);
    }
}
