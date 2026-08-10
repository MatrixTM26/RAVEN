package com.raven.interfaces.banner;

import com.raven.core.output.Logger;
import com.raven.utils.AnsiColor;

public final class CLIBanner {

    private CLIBanner() {}

    private static final String Indent = "    ";

    public static void Print() {
        String Red   = AnsiColor.Red;
        String White = AnsiColor.White;
        String Reset = AnsiColor.Reset;

        Logger.Custom("");

        Logger.Custom(Indent + White + "SESSION COMMANDS" + X);
        Logger.Custom(Indent + "  " + Red + "sessions / agents               " + Reset + "List active sessions");
        Logger.Custom(Indent + "  " + Red + "use <id>                        " + Reset + "Enter interactive session");
        Logger.Custom(Indent + "  " + Red + "exec <id> <cmd>                 " + Reset + "Execute command on session");
        Logger.Custom(Indent + "  " + Red + "broadcast <id,id,...|all> <cmd> " + Reset + "Execute on specific/all sessions");
        Logger.Custom(Indent + "  " + Red + "kill <id>                       " + Reset + "Terminate a session");
        Logger.Custom(Indent + "  " + Red + "sysinfo <id>                    " + Reset + "Full session info");
        Logger.Custom(Indent + "  " + Red + "whoami <id>                     " + Reset + "Run whoami on session");
        Logger.Custom(Indent + "  " + Red + "sleep <id> <secs>               " + Reset + "Set agent sleep interval");
        Logger.Custom(Indent + "  " + Red + "screenshot <id>                 " + Reset + "Request screenshot");
        Logger.Custom(Indent + "  " + Red + "download <id> <remote-path>     " + Reset + "Download file from agent");
        Logger.Custom(Indent + "  " + Red + "upload <id> <local> <remote>    " + Reset + "Upload file to agent");
        Logger.Custom(Indent + "  " + Red + "pivot <id> <host:port>          " + Reset + "Register pivot route (server-side)");
        Logger.Custom(Indent + "  " + Red + "note <id> <text>                " + Reset + "Set session note");
        Logger.Custom(Indent + "  " + Red + "getnote <id>                    " + Reset + "Get session note");
        Logger.Custom(Indent + "  " + Red + "history [id] [limit]            " + Reset + "Command history from DB");
        Logger.Custom(Indent + "  " + Red + "tasks                           " + Reset + "Pending task queue");

        Logger.Custom("");

        Logger.Custom(Indent + White + "SERVER / WEB" + X);
        Logger.Custom(Indent + "  " + Red + "status                          " + Reset + "Server status");
        Logger.Custom(Indent + "  " + Red + "stats                           " + Reset + "Session statistics");
        Logger.Custom(Indent + "  " + Red + "logs                            " + Reset + "Recent log entries");
        Logger.Custom(Indent + "  " + Red + "webstart [host] [port]          " + Reset + "Start web panel");
        Logger.Custom(Indent + "  " + Red + "webstop                         " + Reset + "Stop web panel");
        Logger.Custom(Indent + "  " + Red + "webstatus                       " + Reset + "Web panel status");
        Logger.Custom(Indent + "  " + Red + "clear                           " + Reset + "Clear screen");
        Logger.Custom(Indent + "  " + Red + "exit / quit                     " + Reset + "Shutdown & exit");

        Logger.Custom("");

        Logger.Custom(Indent + White + "TEAM OPERATOR MANAGEMENT" + X);
        Logger.Custom(Indent + "  " + Red + "listopt                         " + Reset + "List operators & roles");
        Logger.Custom(Indent + "  " + Red + "addopt <user> <pass> [ROLE]     " + Reset + "Add operator        [ADMIN+]");
        Logger.Custom(Indent + "  " + Red + "delopt <user>                   " + Reset + "Delete operator     [ADMIN+]");
        Logger.Custom(Indent + "  " + Red + "setrole <user> <ROLE>           " + Reset + "Change role         [SUPER]");
        Logger.Custom(Indent + "  " + Red + "passwd <user> <newpass>         " + Reset + "Change password     [ADMIN+]");
        Logger.Custom(Indent + "  " + Red + "kick <user>                     " + Reset + "Kick operator       [SUPER]");
        Logger.Custom(Indent + "  " + Red + "chat                            " + Reset + "Show in-memory chat");
        Logger.Custom(Indent + "  " + Red + "chathistory                     " + Reset + "Chat history from DB");
        Logger.Custom(Indent + "  " + Red + "ch <name> <msg>                 " + Reset + "DM an operator");
        Logger.Custom(Indent + "  " + Red + "gc all <msg>                    " + Reset + "Broadcast to all operators");

        Logger.Custom("");

        Logger.Custom(Indent + White + "EXAMPLES" + X);
        Logger.Custom(Indent + "  " + Red + "use 1                           " + Reset + "Enter session 1");
        Logger.Custom(Indent + "  " + Red + "exec 2 whoami                   " + Reset + "Run whoami on session 2");
        Logger.Custom(Indent + "  " + Red + "broadcast 1,2,3 id              " + Reset + "Run id on sessions 1,2,3");
        Logger.Custom(Indent + "  " + Red + "broadcast all uname -a          " + Reset + "Run uname -a on all");
        Logger.Custom(Indent + "  " + Red + "sysinfo 3                       " + Reset + "Full info on session 3");
        Logger.Custom(Indent + "  " + Red + "note 1 'dev-box initial access' " + Reset + "Set note for session 1");
        Logger.Custom(Indent + "  " + Red + "history 1 20                    " + Reset + "Last 20 cmds on session 1");
        Logger.Custom(Indent + "  " + Red + "addopt alice P@ss! OPERATOR     " + Reset + "Add operator");
        Logger.Custom(Indent + "  " + Red + "webstart 0.0.0.0 9926           " + Reset + "Launch web panel on all interfaces, port 9926");

        Logger.Custom("");

        Logger.Custom(Indent + White + "ROLES" + X);
        Logger.Custom(Indent + "  " + Red + "SUPER                           " + Reset + "[RWXK] read, write, execute, kick/delete operator");
        Logger.Custom(Indent + "  " + Red + "ADMIN                           " + Reset + "[RWX]  read, write, execute");
        Logger.Custom(Indent + "  " + Red + "OPERATOR                        " + Reset + "[RX]   read, execute");
        Logger.Custom(Indent + "  " + Red + "MEMBER                          " + Reset + "[R]    read only");

        Logger.Custom("");
    }
}
