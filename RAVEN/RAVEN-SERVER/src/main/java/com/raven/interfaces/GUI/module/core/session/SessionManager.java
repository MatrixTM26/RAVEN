package com.raven.interfaces.GUI.module.core.session;

import com.raven.core.database.TeamDatabase;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;

public class SessionManager {

    private final RavenServer Server;
    private final TeamDatabase Database;
    private final ObservableList<SessionRow> Rows;
    private final Label CountLabel;

    public SessionManager(RavenServer Server, TeamDatabase Database, ObservableList<SessionRow> Rows, Label CountLabel) {
        this.Server     = Server;
        this.Database   = Database;
        this.Rows       = Rows;
        this.CountLabel = CountLabel;
    }

    public void Refresh() {
        if (Server == null) return;
        Platform.runLater(() -> {
            Rows.clear();
            Server.GetSessions().GetAll().forEach(AgentSession -> Rows.add(new SessionRow(AgentSession)));
            int Count = Rows.size();
            CountLabel.setText(Count + " session" + (Count != 1 ? "s" : ""));
        });
    }

    public void Kill(int SessionId) {
        Server.RemoveSession(SessionId);
        Refresh();
    }

    public void RunAgentCommand(int SessionId, String Command, String Operator, Consumer<String> LogConsumer) {
        if (Server == null || !Server.IsRunning()) {
            LogConsumer.accept("[!] Server not running");
            return;
        }
        LogConsumer.accept("> SESSION-" + SessionId + " — " + Command);
        Executors.newSingleThreadExecutor().submit(() -> {
            String[] Result = Server.ExecuteCommand(SessionId, Command);
            boolean Success = Boolean.parseBoolean(Result[0]);
            Database.SaveCommandLog(SessionId, Operator != null ? Operator : "gui", Command, Result[1], Success);
            Platform.runLater(() -> LogConsumer.accept(Result[1]));
        });
    }

    public Optional<Session> Get(int SessionId) {
        return Server.GetSessions().Get(SessionId);
    }
}
