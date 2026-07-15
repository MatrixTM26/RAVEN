package com.raven.interfaces.GUI.module.core.session;

import com.raven.core.db.TeamDatabase;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;

import java.util.Optional;
import java.util.concurrent.Executors;

public class SessionManager {

    private final RavenServer Server;
    private final TeamDatabase Db;
    private final ObservableList<SessionRow> SessionRows;
    private final Label SessionCountLabel;

    public SessionManager(RavenServer server, TeamDatabase db,
                          ObservableList<SessionRow> rows, Label countLabel) {
        this.Server = server;
        this.Db = db;
        this.SessionRows = rows;
        this.SessionCountLabel = countLabel;
    }

    public void Refresh() {
        if (Server == null) return;
        Platform.runLater(() -> {
            SessionRows.clear();
            for (Session S : Server.GetSessions().GetAll()) SessionRows.add(new SessionRow(S));
            int N = SessionRows.size();
            SessionCountLabel.setText("⊟ " + N + " Session" + (N != 1 ? "s" : ""));
        });
    }

    public void Kill(int Sid) {
        Server.RemoveSession(Sid);
        Refresh();
    }

    public void RunAgentCommand(int Sid, String Cmd, String OperatorName, java.util.function.Consumer<String> LogSink) {
        if (Server == null || !Server.IsRunning()) {
            LogSink.accept("[!] Server not running");
            return;
        }
        LogSink.accept("[>] SESSION-" + Sid + " → " + Cmd);
        Executors.newSingleThreadExecutor().submit(() -> {
            String[] Result = Server.ExecuteCommand(Sid, Cmd);
            boolean Ok = Boolean.parseBoolean(Result[0]);
            Db.SaveCommandLog(Sid, OperatorName != null ? OperatorName : "gui", Cmd, Result[1], Ok);
            Platform.runLater(() -> LogSink.accept((Ok ? "[+]" : "[!]") + " SESSION-" + Sid + ": " + Result[1]));
        });
    }

    public Optional<Session> Get(int Sid) {
        return Server.GetSessions().Get(Sid);
    }
}
