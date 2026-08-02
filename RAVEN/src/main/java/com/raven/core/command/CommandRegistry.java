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

    public enum Platform {
        ALL,
        LINUX,
        WINDOWS
    }

    public record CommandDef(String Name, String Usage, String Description, Category Category, Platform Platform, boolean RequireTeamMode) {}

    private static final Map<String, CommandDef> REGISTRY = new LinkedHashMap<>();

    static {
        // ── SYSTEM ─────────────────────────────────────────────────────────
        Register("help", "help", "Show this command reference", Category.SYSTEM, Platform.ALL, false);
        Register("clear", "clear", "Clear operator terminal display (local — does NOT affect agent)", Category.SYSTEM, Platform.ALL, false);
        Register("exit", "exit", "Shutdown server and exit", Category.SYSTEM, Platform.ALL, false);
        Register("quit", "quit", "Alias for exit", Category.SYSTEM, Platform.ALL, false);

        // ── SERVER ─────────────────────────────────────────────────────────
        Register("status", "status", "Show server, mode, uptime, and database status", Category.SERVER, Platform.ALL, false);
        Register("logs", "logs", "Show recent server event logs", Category.SERVER, Platform.ALL, false);

        // ── SESSION ────────────────────────────────────────────────────────
        Register("sessions", "sessions", "List all active agent sessions", Category.SESSION, Platform.ALL, false);
        Register("agents", "agents", "Alias for sessions", Category.SESSION, Platform.ALL, false);
        Register("stats", "stats", "Show session type statistics", Category.SESSION, Platform.ALL, false);
        Register("use", "use <id>", "Enter interactive shell with agent", Category.SESSION, Platform.ALL, false);
        Register("sysinfo", "sysinfo <id>", "Show full system info for agent", Category.SESSION, Platform.ALL, false);
        Register("info", "info <id>", "Alias for sysinfo", Category.SESSION, Platform.ALL, false);
        Register("exec", "exec <id> <command>", "Execute arbitrary command on agent", Category.SESSION, Platform.ALL, false);
        Register("shell", "shell <id> <command>", "Execute via shell interpreter (sh / cmd.exe)", Category.SESSION, Platform.ALL, false);
        Register("broadcast", "broadcast <id,id,...|all> <command>", "Broadcast command to selected or all agents", Category.SESSION, Platform.ALL, false);
        Register("kill", "kill <id>", "Terminate an agent session", Category.SESSION, Platform.ALL, false);
        Register("ping", "ping <id>", "Ping agent to verify liveness", Category.SESSION, Platform.ALL, false);
        Register("reconnect", "reconnect <id>", "Ask agent to reconnect to server (RAVEN agent only)", Category.SESSION, Platform.ALL, false);
        Register("self-destruct", "self-destruct <id>", "Wipe agent and terminate session (ADMIN+)", Category.SESSION, Platform.ALL, false);
        Register("sleep", "sleep <id> <seconds>", "Set agent check-in interval", Category.SESSION, Platform.ALL, false);
        Register("jitter", "jitter <id> <ms>", "Set agent jitter delay in milliseconds", Category.SESSION, Platform.ALL, false);

        // ── RECON — cross-platform ─────────────────────────────────────────
        Register("whoami", "whoami <id>", "Show current user on agent", Category.RECON, Platform.ALL, false);
        Register("id", "id <id>", "Show UID/GID info (Linux only)", Category.RECON, Platform.LINUX, false);
        Register("hostname", "hostname <id>", "Show agent hostname", Category.RECON, Platform.ALL, false);
        Register("uname", "uname <id>", "Show OS kernel version (Linux only)", Category.RECON, Platform.LINUX, false);
        Register("systeminfo", "systeminfo <id>", "Show full OS/hardware info (Windows only)", Category.RECON, Platform.WINDOWS, false);
        Register("screenshot", "screenshot <id>", "Capture screenshot from agent desktop", Category.RECON, Platform.ALL, false);
        Register("ps", "ps <id>", "List running processes (Linux: ps aux, Windows: tasklist)", Category.RECON, Platform.ALL, false);
        Register("tasklist", "tasklist <id>", "List running processes (Windows only — alias for ps on Windows)", Category.RECON, Platform.WINDOWS, false);
        Register("env", "env <id>", "Dump environment variables (Linux: env, Windows: set)", Category.RECON, Platform.ALL, false);
        Register("set", "set <id>", "Dump environment variables (Windows only — alias for env)", Category.RECON, Platform.WINDOWS, false);
        Register("ifconfig", "ifconfig <id>", "Show network interfaces (Linux: ifconfig/ip a)", Category.RECON, Platform.LINUX, false);
        Register("ipconfig", "ipconfig <id>", "Show network interfaces (Windows only: ipconfig /all)", Category.RECON, Platform.WINDOWS, false);
        Register("netstat", "netstat <id>", "Show network connections (Linux: ss -tulpn, Windows: netstat -an)", Category.RECON, Platform.ALL, false);
        Register("arp", "arp <id>", "Show ARP table (Linux: arp -n, Windows: arp -a)", Category.RECON, Platform.ALL, false);
        Register("route", "route <id>", "Show routing table (Linux: ip route, Windows: route print)", Category.RECON, Platform.ALL, false);
        Register("users", "users <id>", "List local users (Linux: /etc/passwd, Windows: net user)", Category.RECON, Platform.ALL, false);
        Register("groups", "groups <id>", "List groups (Linux: groups / id, Windows: net localgroup)", Category.RECON, Platform.ALL, false);
        Register("localadmins", "localadmins <id>", "List local admin accounts (Windows only)", Category.RECON, Platform.WINDOWS, false);
        Register("domaininfo", "domaininfo <id>", "Show domain info (Windows: net user /domain)", Category.RECON, Platform.WINDOWS, false);
        Register("crontab", "crontab <id>", "List cron jobs (Linux only)", Category.RECON, Platform.LINUX, false);
        Register("schtasks", "schtasks <id>", "List scheduled tasks (Windows only)", Category.RECON, Platform.WINDOWS, false);
        Register("services", "services <id>", "List running services (Linux: systemctl, Windows: sc query)", Category.RECON, Platform.ALL, false);
        Register("antivirus", "antivirus <id>", "Detect AV/EDR products (Windows: wmic, Linux: ps-based)", Category.RECON, Platform.ALL, false);
        Register("privcheck", "privcheck <id>", "Check current user privileges and sudo rights", Category.RECON, Platform.ALL, false);
        Register("clipboard", "clipboard <id>", "Read clipboard content (Windows: PowerShell, Linux: xclip/xsel)", Category.RECON, Platform.ALL, false);
        Register("keystroke", "keystroke <id> <on|off>", "Start or stop keylogger on agent", Category.RECON, Platform.ALL, false);
        Register("searchfiles", "searchfiles <id> <pattern>", "Search for files matching pattern on agent", Category.RECON, Platform.ALL, false);
        Register("hashdump", "hashdump <id>", "Dump credential hashes (Linux: /etc/shadow, Windows: SAM/LSASS)", Category.RECON, Platform.ALL, false);
        Register("dumpbrowsers", "dumpbrowsers <id>", "Dump saved browser credentials", Category.RECON, Platform.ALL, false);
        Register("wifidump", "wifidump <id>", "Dump saved WiFi credentials (Windows: netsh, Linux: nm-cli)", Category.RECON, Platform.ALL, false);
        Register("lastlog", "lastlog <id>", "Show recent logins (Linux only: lastlog / last)", Category.RECON, Platform.LINUX, false);
        Register("osquery", "osquery <id> <sql>", "Run osquery SQL on agent (if osquery installed)", Category.RECON, Platform.ALL, false);

        // ── FILESYSTEM ─────────────────────────────────────────────────────
        Register("ls", "ls <id> [path]", "List directory (Linux: ls -la, Windows: dir)", Category.FILESYSTEM, Platform.ALL, false);
        Register("dir", "dir <id> [path]", "List directory (Windows only — alias for ls on Windows)", Category.FILESYSTEM, Platform.WINDOWS, false);
        Register("pwd", "pwd <id>", "Print working directory (Linux: pwd, Windows: cd)", Category.FILESYSTEM, Platform.ALL, false);
        Register("cd", "cd <id> <path>", "Change working directory on agent", Category.FILESYSTEM, Platform.ALL, false);
        Register("cat", "cat <id> <file>", "Read file contents (Linux: cat, Windows: type)", Category.FILESYSTEM, Platform.ALL, false);
        Register("type", "type <id> <file>", "Read file contents (Windows only — alias for cat)", Category.FILESYSTEM, Platform.WINDOWS, false);
        Register("head", "head <id> <file> [n]", "Read first N lines of file (Linux only)", Category.FILESYSTEM, Platform.LINUX, false);
        Register("tail", "tail <id> <file> [n]", "Read last N lines of file (Linux only)", Category.FILESYSTEM, Platform.LINUX, false);
        Register("rm", "rm <id> <path>", "Delete file or directory on agent", Category.FILESYSTEM, Platform.ALL, false);
        Register("del", "del <id> <path>", "Delete file (Windows only — alias for rm)", Category.FILESYSTEM, Platform.WINDOWS, false);
        Register("mkdir", "mkdir <id> <path>", "Create directory on agent", Category.FILESYSTEM, Platform.ALL, false);
        Register("cp", "cp <id> <src> <dst>", "Copy file on agent (Linux: cp, Windows: copy)", Category.FILESYSTEM, Platform.ALL, false);
        Register("mv", "mv <id> <src> <dst>", "Move file on agent (Linux: mv, Windows: move)", Category.FILESYSTEM, Platform.ALL, false);
        Register("chmod", "chmod <id> <mode> <file>", "Change file permissions (Linux only)", Category.FILESYSTEM, Platform.LINUX, false);
        Register("chown", "chown <id> <user:group> <file>", "Change file ownership (Linux only)", Category.FILESYSTEM, Platform.LINUX, false);
        Register("find", "find <id> <path> <name>", "Find files by name (Linux: find, Windows: where)", Category.FILESYSTEM, Platform.ALL, false);
        Register("grep", "grep <id> <pattern> <file>", "Search text in file (Linux: grep, Windows: findstr)", Category.FILESYSTEM, Platform.ALL, false);
        Register("hash", "hash <id> <file>", "Get MD5/SHA256 hash of file (Linux: sha256sum, Windows: certutil)", Category.FILESYSTEM, Platform.ALL, false);
        Register("download", "download <id> <remote-path>", "Download file from agent to operator", Category.FILESYSTEM, Platform.ALL, false);
        Register("upload", "upload <id> <local-path> [remote-path]", "Upload file from operator to agent", Category.FILESYSTEM, Platform.ALL, false);

        // ── TASK ───────────────────────────────────────────────────────────
        Register("tasks", "tasks", "Show pending task queue", Category.TASK, Platform.ALL, false);
        Register("history", "history [id] [limit]", "Show command history (all or per agent)", Category.TASK, Platform.ALL, false);
        Register("sesshistory", "sesshistory [limit]", "Show session connection history", Category.TASK, Platform.ALL, false);
        Register("sessions-history", "sessions-history [limit]", "Alias for sesshistory", Category.TASK, Platform.ALL, false);
        Register("note", "note <id> <text>", "Set a note for an agent", Category.TASK, Platform.ALL, false);
        Register("getnote", "getnote <id>", "Get the note for an agent", Category.TASK, Platform.ALL, false);

        // ── LATERAL MOVEMENT ───────────────────────────────────────────────
        Register("pivot", "pivot <id> <host:port>", "Register a pivot route through agent", Category.LATERAL, Platform.ALL, false);
        Register("portfwd", "portfwd <id> <lport> <rhost> <rport>", "Forward local port through agent", Category.LATERAL, Platform.ALL, false);
        Register("socks", "socks <id> <lport>", "Start SOCKS5 proxy through agent", Category.LATERAL, Platform.ALL, false);
        Register("spawn", "spawn <id>", "Spawn a new agent process on target", Category.LATERAL, Platform.ALL, false);
        Register("migrate", "migrate <id> <pid>", "Migrate agent into another process (Windows: inject, Linux: ptrace)", Category.LATERAL, Platform.ALL, false);
        Register("runas", "runas <id> <user> <pass> <cmd>", "Run command as different user (Windows: runas, Linux: su/sudo)", Category.LATERAL, Platform.ALL, false);
        Register("pth", "pth <id> <hash> <domain\\user>", "Pass-the-Hash authentication (Windows only)", Category.LATERAL, Platform.WINDOWS, false);
        Register("shellcode", "shellcode <id> <hex-payload>", "Inject shellcode into agent process", Category.LATERAL, Platform.ALL, false);
        Register("persist", "persist <id> <method>", "Install persistence (Linux: cron/rc.local, Windows: registry/schtask)", Category.LATERAL, Platform.ALL, false);
        Register("unpersist", "unpersist <id> <method>", "Remove persistence entry from agent", Category.LATERAL, Platform.ALL, false);

        // ── OPERATOR ───────────────────────────────────────────────────────
        Register("listopt", "listopt", "List all operators and their roles", Category.OPERATOR, Platform.ALL, false);
        Register("listoperators", "listoperators", "Alias for listopt", Category.OPERATOR, Platform.ALL, false);
        Register("addopt", "addopt <user> <pass> [SUPER|ADMIN|OPERATOR|MEMBER]", "Add a new operator account", Category.OPERATOR, Platform.ALL, false);
        Register("addoperator", "addoperator <user> <pass> [role]", "Alias for addopt", Category.OPERATOR, Platform.ALL, false);
        Register("delopt", "delopt <username>", "Delete an operator account", Category.OPERATOR, Platform.ALL, false);
        Register("deleteoperator", "deleteoperator <username>", "Alias for delopt", Category.OPERATOR, Platform.ALL, false);
        Register("kick", "kick <username>", "Kick and remove operator token (SUPER only)", Category.OPERATOR, Platform.ALL, false);
        Register("kickopt", "kickopt <username>", "Alias for kick", Category.OPERATOR, Platform.ALL, false);
        Register("setrole", "setrole <user> <SUPER|ADMIN|OPERATOR|MEMBER>", "Change operator role", Category.OPERATOR, Platform.ALL, false);
        Register("changerole", "changerole <user> <role>", "Alias for setrole", Category.OPERATOR, Platform.ALL, false);
        Register("passwd", "passwd <user> <newpass>", "Change operator password", Category.OPERATOR, Platform.ALL, false);
        Register("changepassword", "changepassword <user> <newpass>", "Alias for passwd", Category.OPERATOR, Platform.ALL, false);

        // ── CHAT ───────────────────────────────────────────────────────────
        Register("chat", "chat", "Show in-memory chat messages", Category.CHAT, Platform.ALL, false);
        Register("chathistory", "chathistory [limit]", "Show chat history from database", Category.CHAT, Platform.ALL, false);
        Register("chatlog", "chatlog [limit]", "Alias for chathistory", Category.CHAT, Platform.ALL, false);
        Register("ch", "ch <recipient> <message>", "Send direct message to operator", Category.CHAT, Platform.ALL, true);
        Register("gc", "gc <all|name1,name2,...> <message>", "Send group or broadcast chat message", Category.CHAT, Platform.ALL, true);

        // ── EXPORT ─────────────────────────────────────────────────────────
        Register("export", "export <target> <format>", "Export data to file  |  targets: all, logs, chat, history, sessions, operators, notes  |  formats: txt, json", Category.EXPORT, Platform.ALL, false);

        // ── WEB PANEL ──────────────────────────────────────────────────────
        Register("webstart", "webstart [host] [port]", "Start the web panel server", Category.WEB, Platform.ALL, false);
        Register("webstop", "webstop", "Stop the web panel server", Category.WEB, Platform.ALL, false);
        Register("webstatus", "webstatus", "Show web panel current status", Category.WEB, Platform.ALL, false);
    }

    private static void Register(String Name, String Usage, String Description, Category Category, Platform Platform, boolean RequireTeamMode) {
        REGISTRY.put(Name, new CommandDef(Name, Usage, Description, Category, Platform, RequireTeamMode));
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

    public static List<CommandDef> ByCategory(Category Category) {
        return REGISTRY.values()
            .stream()
            .filter(Command -> Command.Category() == Category)
            .collect(Collectors.toList());
    }

    public static List<CommandDef> ByPlatform(Platform Platform) {
        return REGISTRY.values()
            .stream()
            .filter(Command -> Command.Platform() == Platform.ALL || Command.Platform() == Platform)
            .collect(Collectors.toList());
    }
}
