package com.raven.core.session;

import com.raven.utils.RavenConstants;
import java.net.Socket;
import java.security.SecureRandom;
import java.time.LocalDateTime;

public class Session {

    public enum Type {
        RAVEN,
        METERPRETER,
        ReverseShell,
        UNKNOWN
    }

    private static final String KeyAlphabet =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom Random = new SecureRandom();

    private int    Id;
    private Socket Socket;
    private String RemoteAddress;
    private Type   SessionType;
    private String AgentName;
    private String AgentId;
    private String SessionKey;
    private String Os;
    private String Hostname;
    private String User;
    private String Arch;
    private String AgentIp;
    private String JoinedAt;
    private String ShellMode;
    private boolean MtlsEnabled;
    private String  CertCn;
    private boolean RawMode;
    private boolean Encrypted;
    private String  Status;

    public Session() {
        JoinedAt    = LocalDateTime.now().format(RavenConstants.TimestampFmt);
        SessionType = Type.RAVEN;
        Status      = "Online";
        Encrypted   = true;
        ShellMode   = "Standard";
        Os          = "Unknown";
        Hostname    = "Unknown";
        User        = "Unknown";
        Arch        = "Unknown";
        AgentIp     = "Unknown";
        CertCn      = "N/A";
        AgentId     = GenerateAgentId();
        SessionKey  = GenerateSessionKey();
    }

    private static String GenerateAgentId() {
        StringBuilder Builder = new StringBuilder(8);
        for (int Index = 0; Index < 8; Index++)
            Builder.append(KeyAlphabet.charAt(Random.nextInt(KeyAlphabet.length())));
        return Builder.toString().toUpperCase();
    }

    private static String GenerateSessionKey() {
        StringBuilder Builder = new StringBuilder(24);
        for (int Index = 0; Index < 24; Index++)
            Builder.append(KeyAlphabet.charAt(Random.nextInt(KeyAlphabet.length())));
        return Builder.toString();
    }

    public int     GetId()              { return Id; }
    public void    SetId(int Value)     { Id = Value; }

    public Socket  GetSocket()             { return Socket; }
    public void    SetSocket(Socket Value) { Socket = Value; }

    public String  GetRemoteAddress()            { return RemoteAddress; }
    public void    SetRemoteAddress(String Value) { RemoteAddress = Value; }

    public Type    GetSessionType()          { return SessionType; }
    public void    SetSessionType(Type Value){ SessionType = Value; }

    public String  GetAgentName()            { return AgentName; }
    public void    SetAgentName(String Value){ AgentName = Value; }

    public String  GetAgentId()              { return AgentId; }
    public void    SetAgentId(String Value)  { AgentId = Value; }

    public String  GetSessionKey()             { return SessionKey; }
    public void    SetSessionKey(String Value) { SessionKey = Value; }

    public String  GetOs()               { return Os; }
    public void    SetOs(String Value)   { Os = Value; }

    public String  GetHostname()             { return Hostname; }
    public void    SetHostname(String Value) { Hostname = Value; }

    public String  GetUser()             { return User; }
    public void    SetUser(String Value) { User = Value; }

    public String  GetArch()             { return Arch; }
    public void    SetArch(String Value) { Arch = Value; }

    public String  GetAgentIp()              { return AgentIp; }
    public void    SetAgentIp(String Value)  { AgentIp = Value; }

    public String  GetJoinedAt()             { return JoinedAt; }
    public void    SetJoinedAt(String Value) { JoinedAt = Value; }

    public String  GetShellMode()             { return ShellMode; }
    public void    SetShellMode(String Value) { ShellMode = Value; }

    public boolean IsMtlsEnabled()               { return MtlsEnabled; }
    public void    SetMtlsEnabled(boolean Value) { MtlsEnabled = Value; }

    public String  GetCertCn()             { return CertCn; }
    public void    SetCertCn(String Value) { CertCn = Value; }

    public boolean IsRawMode()              { return RawMode; }
    public void    SetRawMode(boolean Value){ RawMode = Value; }

    public boolean IsEncrypted()               { return Encrypted; }
    public void    SetEncrypted(boolean Value) { Encrypted = Value; }

    public String  GetStatus()             { return Status; }
    public void    SetStatus(String Value) { Status = Value; }

    public String GetDisplayName() {
        if (CertCn != null && !CertCn.equals("N/A") && !CertCn.isBlank()) return CertCn.toUpperCase();
        if (AgentName != null && !AgentName.isBlank() && !AgentName.equals("Unknown")) return AgentName.toUpperCase();
        return "AGENT-" + Id;
    }
}
