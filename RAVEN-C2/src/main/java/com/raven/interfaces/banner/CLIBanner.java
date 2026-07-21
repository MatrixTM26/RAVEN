package com.raven.interfaces.banner;

import com.raven.utils.AnsiColor;

public final class CLIBanner {

    private CLIBanner() {}

    private static final String I = "    ";

    public static void Print() {
        String R = AnsiColor.Red;
        String W = AnsiColor.White;
        String X = AnsiColor.Reset;
        String NL = "\n";

        System.out.println(
            NL +
                I +
                W +
                "SESSION COMMANDS" +
                X +
                NL +
                I +
                "  " +
                R +
                "sessions / agents               " +
                X +
                "List active sessions" +
                NL +
                I +
                "  " +
                R +
                "use <id>                        " +
                X +
                "Enter interactive session" +
                NL +
                I +
                "  " +
                R +
                "exec <id> <cmd>                 " +
                X +
                "Execute command on session" +
                NL +
                I +
                "  " +
                R +
                "broadcast <id,id,...|all> <cmd> " +
                X +
                "Execute on specific/all sessions" +
                NL +
                I +
                "  " +
                R +
                "kill <id>                       " +
                X +
                "Terminate a session" +
                NL +
                I +
                "  " +
                R +
                "sysinfo <id>                    " +
                X +
                "Full session info" +
                NL +
                I +
                "  " +
                R +
                "whoami <id>                     " +
                X +
                "Run whoami on session" +
                NL +
                I +
                "  " +
                R +
                "sleep <id> <secs>               " +
                X +
                "Set agent sleep interval" +
                NL +
                I +
                "  " +
                R +
                "screenshot <id>                 " +
                X +
                "Request screenshot" +
                NL +
                I +
                "  " +
                R +
                "download <id> <remote-path>     " +
                X +
                "Download file from agent" +
                NL +
                I +
                "  " +
                R +
                "upload <id> <local> <remote>    " +
                X +
                "Upload file to agent" +
                NL +
                I +
                "  " +
                R +
                "pivot <id> <host:port>          " +
                X +
                "Register pivot route (server-side)" +
                NL +
                I +
                "  " +
                R +
                "note <id> <text>                " +
                X +
                "Set session note" +
                NL +
                I +
                "  " +
                R +
                "getnote <id>                    " +
                X +
                "Get session note" +
                NL +
                I +
                "  " +
                R +
                "history [id] [limit]            " +
                X +
                "Command history from DB" +
                NL +
                I +
                "  " +
                R +
                "tasks                           " +
                X +
                "Pending task queue" +
                NL +
                NL +
                I +
                W +
                "SERVER / WEB" +
                X +
                NL +
                I +
                "  " +
                R +
                "status                          " +
                X +
                "Server status" +
                NL +
                I +
                "  " +
                R +
                "stats                           " +
                X +
                "Session statistics" +
                NL +
                I +
                "  " +
                R +
                "logs                            " +
                X +
                "Recent log entries" +
                NL +
                I +
                "  " +
                R +
                "webstart [host] [port]          " +
                X +
                "Start web panel" +
                NL +
                I +
                "  " +
                R +
                "webstop                         " +
                X +
                "Stop web panel" +
                NL +
                I +
                "  " +
                R +
                "webstatus                       " +
                X +
                "Web panel status" +
                NL +
                I +
                "  " +
                R +
                "clear                           " +
                X +
                "Clear screen" +
                NL +
                I +
                "  " +
                R +
                "exit / quit                     " +
                X +
                "Shutdown & exit" +
                NL +
                NL +
                I +
                W +
                "TEAM OPERATOR MANAGEMENT" +
                X +
                NL +
                I +
                "  " +
                R +
                "listopt                         " +
                X +
                "List operators & roles" +
                NL +
                I +
                "  " +
                R +
                "addop <user> <pass> [ROLE]      " +
                X +
                "Add operator        [ADMIN+]" +
                NL +
                I +
                "  " +
                R +
                "delop <user>                    " +
                X +
                "Delete operator     [ADMIN+]" +
                NL +
                I +
                "  " +
                R +
                "setrole <user> <ROLE>           " +
                X +
                "Change role         [SUPER]" +
                NL +
                I +
                "  " +
                R +
                "passwd <user> <newpass>         " +
                X +
                "Change password     [ADMIN+]" +
                NL +
                I +
                "  " +
                R +
                "kick <user>                     " +
                X +
                "Kick operator       [SUPER]" +
                NL +
                I +
                "  " +
                R +
                "chat                            " +
                X +
                "Show in-memory chat" +
                NL +
                I +
                "  " +
                R +
                "chathistory                     " +
                X +
                "Chat history from DB" +
                NL +
                I +
                "  " +
                R +
                "ch <name> <msg>                 " +
                X +
                "DM an operator" +
                NL +
                I +
                "  " +
                R +
                "gc all <msg>                    " +
                X +
                "Broadcast to all operators" +
                NL +
                NL +
                I +
                W +
                "ROLES" +
                X +
                NL +
                I +
                "  " +
                R +
                "SUPER    " +
                X +
                "[RWXK] read, write, execute, kick/delete operator" +
                NL +
                I +
                "  " +
                R +
                "ADMIN    " +
                X +
                "[RWX]  read, write, execute" +
                NL +
                I +
                "  " +
                R +
                "OPERATOR " +
                X +
                "[RX]   read, execute" +
                NL +
                I +
                "  " +
                R +
                "MEMBER   " +
                X +
                "[R]    read only"
        );

        System.out.println(NL + I + W + "EXAMPLES" + X + NL + I + "  " + R + "use 1" + X + "                           Enter session 1" + NL + I + "  " + R + "exec 2 whoami" + X + "                   Run whoami on session 2" + NL + I + "  " + R + "broadcast 1,2,3 id" + X + "              Run id on sessions 1,2,3" + NL + I + "  " + R + "broadcast all uname -a" + X + "          Run uname -a on all" + NL + I + "  " + R + "sysinfo 3" + X + "                       Full info on session 3" + NL + I + "  " + R + "note 1 \"dev-box initial access\"" + X + "" + NL + I + "  " + R + "history 1 20" + X + "                    Last 20 cmds on session 1" + NL + I + "  " + R + "addop alice P@ss! OPERATOR" + X + "       Add operator" + NL + I + "  " + R + "webstart 0.0.0.0 9926" + X + "           Launch web panel");
        System.out.println();
    }
}
