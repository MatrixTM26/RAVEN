package com.raven.interfaces.CLI;

import com.raven.core.database.TeamDatabase;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.interfaces.CLI.core.command.CommandDispatcher;
import com.raven.interfaces.CLI.core.command.CommandDispatcher.DispatchResult;
import com.raven.interfaces.CLI.core.operator.OperatorCommands;
import com.raven.interfaces.CLI.core.server.ServerManager;
import com.raven.interfaces.CLI.core.session.SessionCommands;
import com.raven.interfaces.CLI.core.task.TaskCommands;
import com.raven.interfaces.CLI.core.web.WebPanelManager;
import com.raven.interfaces.CLI.module.chat.ChatManager;
import com.raven.interfaces.CLI.module.log.LogManager;
import com.raven.interfaces.CLI.module.terminal.TerminalRenderer;
import com.raven.interfaces.CLI.module.terminal.TerminalWidthDetector;
import com.raven.utils.AnsiColor;
import com.raven.utils.ServerConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CLI {

    private final ServerConfig       Config;
    private final TeamDatabase       Database;

    private final TerminalWidthDetector WidthDetector;
    private final TerminalRenderer      Renderer;
    private final LogManager            LogManager;
    private final ServerManager         ServerManager;
    private final SessionCommands       SessionCommands;
    private final OperatorCommands      OperatorCommands;
    private final ChatManager           ChatManager;
    private final WebPanelManager       WebPanelManager;
    private final TaskCommands          TaskCommands;
    private final CommandDispatcher     Dispatcher;

    private volatile boolean IsRunning       = true;
    private ListenerMode     ActiveMode       = ListenerMode.MULTI;
    private boolean          IsTeamServerMode = false;

    public CLI(ServerConfig Config) {
        this.Config   = Config;
        this.Database = TeamDatabase.Connect(Config);

        WidthDetector    = new TerminalWidthDetector();
        Renderer         = new TerminalRenderer(WidthDetector);
        LogManager       = new LogManager(Config.GetMaxLogEntries(), Renderer);
        ServerManager    = new ServerManager(Config, LogManager, Renderer);
        SessionCommands  = new SessionCommands(Renderer, LogManager, Database);
        OperatorCommands = new OperatorCommands(Database, Renderer);
        ChatManager      = new ChatManager(Database, Renderer);
        WebPanelManager  = new WebPanelManager(Config);
        TaskCommands     = new TaskCommands(Database, Renderer, SessionCommands);

        Dispatcher = new CommandDispatcher(
            Config,
            Database,
            LogManager,
            ServerManager,
            SessionCommands,
            OperatorCommands,
            ChatManager,
            WebPanelManager,
            TaskCommands
        );
    }

    private void SyncModules() {
        String OperatorName = OperatorCommands.GetOperatorName();
        SessionCommands.SetServer(ServerManager.GetServer());
        SessionCommands.SetOperator(OperatorName);
        ChatManager.SetOperator(OperatorName);
        TaskCommands.SetOperator(OperatorName);
        ServerManager.SetContext(IsTeamServerMode, OperatorName, ActiveMode);
        OperatorCommands.SetTeamServerMode(IsTeamServerMode);
        WebPanelManager.SetActiveMode(ActiveMode);
        Dispatcher.SetTeamServerMode(IsTeamServerMode);
    }

    private void RunLoop() {
        SyncModules();

        BufferedReader Reader    = new BufferedReader(new InputStreamReader(System.in));
        int            LastCount = LogManager.Count();

        while (IsRunning) {
            try {
                int CurrentCount = LogManager.Count();
                if (CurrentCount > LastCount) {
                    Logger.Info(CurrentCount - LastCount + " new event(s) - type 'logs' to view");
                    LastCount = CurrentCount;
                }

                Logger.Custom("  %n%s┌──{%sRAVEN@C2%s}%n%s└─%s>>%s ",
                    AnsiColor.Red, AnsiColor.White, AnsiColor.Red,
                    AnsiColor.Red, AnsiColor.White, AnsiColor.Reset);

                String Input = Reader.readLine();
                if (Input == null || Input.trim().isEmpty()) continue;

                String[] Parts   = Input.trim().split("\\s+", 3);
                String   Command = Parts[0].toLowerCase();

                DispatchResult Result = Dispatcher.Dispatch(Command, Parts);

                switch (Result) {
                    case Exit            -> IsRunning = false;
                    case UpdateLastCount -> LastCount = LogManager.Count();
                    case Handled         -> {}
                }

            } catch (IOException Exception) {
                break;
            }
        }
    }

    public void Run(String Host, int Port, ListenerMode Mode) {
        ActiveMode       = Mode;
        IsTeamServerMode = false;
        SyncModules();
        if (!ServerManager.Start(Host, Port, Mode)) return;
        try { Thread.sleep(300); } catch (InterruptedException Ignored) {}
        RunLoop();
        System.exit(0);
    }

    public void RunTeamServer(String Host, int Port, ListenerMode Mode) throws IOException {
        ActiveMode       = Mode;
        IsTeamServerMode = true;
        SyncModules();

        BufferedReader Reader = new BufferedReader(new InputStreamReader(System.in));
        if (!OperatorCommands.Login(Reader)) return;

        SyncModules();
        Logger.Custom("  %n%sStarting listener on %s:%d%s%n%n",
            AnsiColor.Green, Host, Port, AnsiColor.Reset);
        if (!ServerManager.Start(Host, Port, Mode)) return;
        try { Thread.sleep(300); } catch (InterruptedException Ignored) {}
        RunLoop();
        System.exit(0);
    }
}
