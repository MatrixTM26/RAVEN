package com.raven.utils;

import java.util.List;

public final class Helper {

    private Helper() {}

    public static void PrintHelp() {
        String Newline = "\n";
        System.out.println(
            String.join(
                Newline,
                "",
                "RAVEN C2 Framework",
                "Author   :   MatrixTM26",
                "Version  :   1.0.0",
                "GitHub   :   https://github.com/MatrxitTM26/RAVEN",
                "License  :   AGPL-V3.0",
                "",
                "LEGAL NOTICE",
                "RAVEN is an offensive security tool designed exclusively for:",
                "   - Authorized penetration testing and red team engagements",
                "   - Controlled lab and research environments",
                "   - Cybersecurity education under supervised conditions",
                "",
                "You MUST have explicit written authorization before deployment.",
                "",
                "    Usage: java -jar raven.jar [flags]",
                "",
                "    LISTENER MODE",
                "        -M   -multi    Accept raw shell, RAVEN agent, HTTP beacon  [default]",
                "        -R   -raw      Plain TCP / raw reverse shell only",
                "             -http     HTTP beacon only",
                "             -https    HTTPS beacon (TLS, no client cert)",
                "             -tls      RAVEN agent over TLS",
                "        -T   -mtls     RAVEN agent over mTLS (client cert required)",
                "        -F   -fmtls    Full mTLS on both TCP and HTTPS beacon",
                "",
                "    SERVER",
                "        -s   -host   <host>   Bind host (default: server.host)",
                "        -p   -port   <port>   Agent listen port (default: server.port)",
                "",
                "    INTERFACE",
                "        -C   -cli              CLI solo — no auth, direct access",
                "        -G   -gui              GUI solo — no auth",
                "        -W   -web              Web panel standalone — auth required",
                "        -wp  -web-port <port>  Web panel port for -W and -TSW (default: web.port)",
                "        -TS  -teamserver       Backend only — agent listener + API, no web UI",
                "        -TSC -teamserver-cli   TeamServer CLI client — connect to -TS backend",
                "        -TSW -teamserver-web   TeamServer + Web UI (agent + API + web frontend)",
                "        -TSG -teamserver-gui   TeamServer GUI client",
                "        -ts  -thost  <host>    TeamServer host to connect to (default: 127.0.0.1)",
                "        -tp  -tport  <port>    TeamServer API port (default: teamserver.port)",
                "",
                "    AGENT GENERATION",
                "        -a   -agent  <id>     Generate agent cert + source code",
                "        -ma  -magent          Generate multiple agents",
                "        -c   -count  <n>      Agent count for -ma  (default: 10)",
                "        -px  -prefix <pfx>    Agent ID prefix for -ma  (default: agent)",
                "        -ah  -ahost  <host>   C2 callback host to embed in agent",
                "        -ap  -aport  <port>   C2 callback port to embed in agent",
                "        -al  -lang   <lang>   Language: java | python | go | bash (default: java)",
                "        -am  -amtls           Enable mTLS in generated agent",
                "        -ps  -persist         Enable persistence (reconnect loop)",
                "        -hc  -hide            Hide console (Windows agents)",
                "        -l   -list            List generated agents",
                "        -rv  -revoke  <id>    Revoke agent certificate",
                "",
                "    SETUP",
                "        -i   -init            Initialize CA + server certificate",
                "        -h   -help            Show this help",
                "",
                "    OPERATOR MANAGEMENT  (standalone — does not start server)",
                "        -AO  -addop   -u <user>  -pw <pass>  [-ro ROLE]    Add operator",
                "        -RO  -rmop    -u <user>                             Remove operator",
                "        -OP  -setperm -u <user>  -ro <ROLE>                Set operator role",
                "        -OP                                                 List all roles",
                "",
                "    ROLES",
                "        SUPER     [RWXK]   read, write, execute, kick / delete operator",
                "        ADMIN     [RWX]    read, write, execute",
                "        OPERATOR  [RX]     read, execute",
                "        MEMBER    [R]      read only",
                "",
                "    EXAMPLES",
                "        java -jar raven.jar -C -M -p 4444",
                "        java -jar raven.jar -TS -M -p 4444 -tp 5000",
                "        java -jar raven.jar -TSC -ts 0.0.0.0 -tp 5000",
                "        java -jar raven.jar -TSW -M -p 4444 -tp 5000 -wp 8080",
                "        java -jar raven.jar -W -wp 8080",
                "        java -jar raven.jar -i -s 10.0.0.1",
                "        java -jar raven.jar -a agent01 -ah 10.0.0.1 -ap 4444 -am -ps -al java",
                "        java -jar raven.jar -ma -magent -c 5 -px team -ah 10.0.0.1 -ap 4444",
                "        java -jar raven.jar -rv agent01",
                "        java -jar raven.jar -AO -addop -u alice -pw S3cr3t! -ro OPERATOR",
                "        java -jar raven.jar -OP -setperm -u alice -ro ADMIN",
                ""
            )
        );
    }

    public static String Arg(List<String> Args, String Short, String Long) {
        return Arg(Args, Short, Long, null);
    }

    public static String Arg(List<String> Args, String Short, String Long, String Default) {
        for (int Index = 0; Index < Args.size() - 1; Index++) {
            String Flag = Args.get(Index);
            if (Flag.equals(Short) || Flag.equals(Long)) return Args.get(Index + 1);
        }
        return Default;
    }

    public static int ParseInt(String Value) {
        return ParseInt(Value, -1);
    }

    public static int ParseInt(String Value, int Default) {
        try {
            return Integer.parseInt(Value.trim());
        } catch (Exception Ignored) {
            return Default;
        }
    }
}
