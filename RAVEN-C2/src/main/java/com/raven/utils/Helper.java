package com.raven.utils;

import java.util.List;

public final class Helper {

    private Helper() {}

    public static void PrintHelp() {
        String NL = "\n";
        System.out.println(
            String.join(
                NL,
                "",
                "    RAVEN C2 Framework",
                "",
                "    Usage: java -jar raven.jar [flags]",
                "",
                "    LISTENER MODE",
                "        -M      -multi                   Accept all: raw shell, RAVEN agent, HTTP" + " beacon  [default]",
                "        -R      -raw                     Plain TCP / raw reverse shell only",
                "                -http                    HTTP beacon only",
                "                -https                   HTTPS beacon (TLS, no client cert)",
                "                -tls                     RAVEN agent over TLS",
                "        -T      -mtls                    RAVEN agent over mTLS  (client cert" + " required)",
                "                -fmtls                   Full mTLS on both TCP and HTTPS beacon",
                "",
                "    SERVER",
                "        -s      -host    <host>          Bind host (default: server.host in" + " server.properties)",
                "        -p      -port    <port>          Agent listen port (default: server.port)",
                "",
                "    INTERFACE",
                "        -C      -cli-mode                CLI solo - no auth, direct",
                "        -G      -gui-mode                GUI solo - no auth",
                "        -W      -web-mode                Web panel - auth",
                "        -TSC    -teamserver-cli          CLI + TeamServer login - single-operator team" + " mode",
                "        -TSG    -teamserver-gui          GUI + TeamServer login",
                "        -TSW    -teamserver-web          TeamServer web panel - multi-operator via" + " HTTP (port: teamserver.port)",
                "        -TC     -teamclient              TeamClient CLI - connect to -TSW" + " server as operator 2,3,...",
                "        -ts     -teamserver-host <host>  TeamServer host (default:" + " web.host)",
                "        -tp     -teamserver-port <port>  TeamServer port (default:" + " teamserver.port)",
                "",
                "    AGENT GENERATION",
                "        -a      <id>                    Generate agent cert + source code",
                "        -m                              Generate multiple agents",
                "        -c      <n>                     Count   (default: 10)",
                "        -u      <prefix>                Prefix  (default: agent)",
                "        -ah     <host>                  C2 host to embed in agent",
                "        -ap     <port>                  C2 port to embed in agent",
                "        -am                             Enable mTLS in generated agent",
                "        -ps                             Enable persistence  (reconnect loop)",
                "        -hc                             Hide console  (Windows)",
                "        -al    <lang>                   Agent language: java  python  go  bash " + " (default: java)",
                "        -l                              List generated agents",
                "        -r     <id>                     Revoke agent cert",
                "",
                "    OTHER",
                "        -i     -init-certs              Initialize CA + server certificate",
                "        -h     -help                    Show this help",
                "",
                "    ROLES",
                "        SUPER     [RWXK]                read, write, execute, kick/delete operator",
                "        ADMIN     [RWX]                 read, write, execute",
                "        OPERATOR  [RX]                  read, execute",
                "        MEMBER    [R]                   read only",
                "",
                "    OPERATOR MANAGEMENT  (standalone, no server start)",
                "        -AO  -u <user> -pw <pass> [-r ROLE]     Add operator",
                "        -RO  -u <user>                          Remove operator",
                "        -OP  -u <user> -r <ROLE>                Change operator role",
                "        -OP                                     List roles",
                "",
                "    EXAMPLES",
                "        # Solo CLI - raw shell listener",
                "        java -jar raven.jar -C -multi -p 4444",
                "",
                "        # TeamServer Web - operator 1 (starts agent listener + HTTP" + " panel)",
                "        java -jar raven.jar -TSW -multi -p 4444 -tp 5001",
                "",
                "        # TeamClient CLI - operator 2+ (requires -TSW to be running" + " first)",
                "        java -jar raven.jar -TC -ts 127.0.0.1 -tp 5001",
                "",
                "        # TeamServer CLI - single-operator team mode (login, but no HTTP" + " API)",
                "        java -jar raven.jar -TSC -multi -p 4444",
                "",
                "        # NOTE: -TC (TeamClient) requires -TSW, NOT -TSC",
                "        #       -TSC has no HTTP server - -TC cannot connect to it",
                "",
                "        # Generate Java mTLS agent",
                "        java -jar raven.jar -a agent01 -ah 10.0.0.1 -ap 4444 -am -ps -al" + " java",
                "",
                "        # Generate Python agent (no mTLS)",
                "        java -jar raven.jar -a agent02 -ah 10.0.0.1 -ap 4444 -al python",
                "",
                "        # Init certificates",
                "        java -jar raven.jar -i",
                ""
            )
        );
    }

    public static String Arg(List<String> Args, String Short, String Long) {
        return Arg(Args, Short, Long, null);
    }

    public static String Arg(List<String> Args, String Short, String Long, String Default) {
        for (int I = 0; I < Args.size() - 1; I++) {
            if (Args.get(I).equals(Short) || Args.get(I).equals(Long)) return Args.get(I + 1);
        }
        return Default;
    }

    public static int ParseInt(String S, int Default) {
        try {
            return Integer.parseInt(S.trim());
        } catch (Exception E) {
            return Default;
        }
    }
}
