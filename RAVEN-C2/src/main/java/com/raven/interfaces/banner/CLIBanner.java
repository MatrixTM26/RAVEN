package com.raven.interfaces.banner;

import com.raven.core.output.Logger;
import com.raven.utils.AnsiColor;

public final class CLIBanner {

    private CLIBanner() {}

    private static final String I = "    ";

    public static void Print() {
        String R = AnsiColor.Red;
        String W = AnsiColor.White;
        String X = AnsiColor.Reset;

        Logger.Custom("");

        Logger.Custom(I + W + "SESSION COMMANDS" + X);
        Logger.Custom(I + "  " + R + "sessions / agents               " + X + "List active sessions");
        Logger.Custom(I + "  " + R + "use <id>                        " + X + "Enter interactive session");
        Logger.Custom(I + "  " + R + "exec <id> <cmd>                 " + X + "Execute command on session");
        Logger.Custom(I + "  " + R + "broadcast <id,id,...|all> <cmd> " + X + "Execute on specific/all sessions");
        Logger.Custom(I + "  " + R + "kill <id>                       " + X + "Terminate a session");
        Logger.Custom(I + "  " + R + "sysinfo <id>                    " + X + "Full session info");
        Logger.Custom(I + "  " + R + "whoami <id>                     " + X + "Run whoami on session");
        Logger.Custom(I + "  " + R + "sleep <id> <secs>               " + X + "Set agent sleep interval");
        Logger.Custom(I + "  " + R + "screenshot <id>                 " + X + "Request screenshot");
        Logger.Custom(I + "  " + R + "download <id> <remote-path>     " + X + "Download file from agent");
        Logger.Custom(I + "  " + R + "upload <id> <local> <remote>    " + X + "Upload file to agent");
        Logger.Custom(I + "  " + R + "pivot <id> <host:port>          " + X + "Register pivot route (server-side)");
        Logger.Custom(I + "  " + R + "note <id> <text>                " + X + "Set session note");
        Logger.Custom(I + "  " + R + "getnote <id>                    " + X + "Get session note");
        Logger.Custom(I + "  " + R + "history [id] [limit]            " + X + "Command history from DB");
        Logger.Custom(I + "  " + R + "tasks                           " + X + "Pending task queue");

        Logger.Custom("");

        Logger.Custom(I + W + "SERVER / WEB" + X);
        Logger.Custom(I + "  " + R + "status                          " + X + "Server status");
        Logger.Custom(I + "  " + R + "stats                           " + X + "Session statistics");
        Logger.Custom(I + "  " + R + "logs                            " + X + "Recent log entries");
        Logger.Custom(I + "  " + R + "webstart [host] [port]          " + X + "Start web panel");
        Logger.Custom(I + "  " + R + "webstop                         " + X + "Stop web panel");
        Logger.Custom(I + "  " + R + "webstatus                       " + X + "Web panel status");
        Logger.Custom(I + "  " + R + "clear                           " + X + "Clear screen");
        Logger.Custom(I + "  " + R + "exit / quit                     " + X + "Shutdown & exit");

        Logger.Custom("");

        Logger.Custom(I + W + "TEAM OPERATOR MANAGEMENT" + X);
        Logger.Custom(I + "  " + R + "listopt                         " + X + "List operators & roles");
        Logger.Custom(I + "  " + R + "addopt <user> <pass> [ROLE]     " + X + "Add operator        [ADMIN+]");
        Logger.Custom(I + "  " + R + "delopt <user>                   " + X + "Delete operator     [ADMIN+]");
        Logger.Custom(I + "  " + R + "setrole <user> <ROLE>           " + X + "Change role         [SUPER]");
        Logger.Custom(I + "  " + R + "passwd <user> <newpass>         " + X + "Change password     [ADMIN+]");
        Logger.Custom(I + "  " + R + "kick <user>                     " + X + "Kick operator       [SUPER]");
        Logger.Custom(I + "  " + R + "chat                            " + X + "Show in-memory chat");
        Logger.Custom(I + "  " + R + "chathistory                     " + X + "Chat history from DB");
        Logger.Custom(I + "  " + R + "ch <name> <msg>                 " + X + "DM an operator");
        Logger.Custom(I + "  " + R + "gc all <msg>                    " + X + "Broadcast to all operators");

        Logger.Custom("");

        Logger.Custom(I + W + "EXAMPLES" + X);
        Logger.Custom(I + "  " + R + "use 1                           " + X + "Enter session 1");
        Logger.Custom(I + "  " + R + "exec 2 whoami                   " + X + "Run whoami on session 2");
        Logger.Custom(I + "  " + R + "broadcast 1,2,3 id              " + X + "Run id on sessions 1,2,3");
        Logger.Custom(I + "  " + R + "broadcast all uname -a          " + X + "Run uname -a on all");
        Logger.Custom(I + "  " + R + "sysinfo 3                       " + X + "Full info on session 3");
        Logger.Custom(I + "  " + R + "note 1 'dev-box initial access' " + X + "Set note for session 1");
        Logger.Custom(I + "  " + R + "history 1 20                    " + X + "Last 20 cmds on session 1");
        Logger.Custom(I + "  " + R + "addopt alice P@ss! OPERATOR     " + X + "Add operator");
        Logger.Custom(I + "  " + R + "webstart 0.0.0.0 9926           " + X + "Launch web panel on all interfaces, port 9926");

        Logger.Custom("");

        Logger.Custom(I + W + "ROLES" + X);
        Logger.Custom(I + "  " + R + "SUPER                           " + X + "[RWXK] read, write, execute, kick/delete operator");
        Logger.Custom(I + "  " + R + "ADMIN                           " + X + "[RWX]  read, write, execute");
        Logger.Custom(I + "  " + R + "OPERATOR                        " + X + "[RX]   read, execute");
        Logger.Custom(I + "  " + R + "MEMBER                          " + X + "[R]    read only");

        Logger.Custom("");
    }
}
