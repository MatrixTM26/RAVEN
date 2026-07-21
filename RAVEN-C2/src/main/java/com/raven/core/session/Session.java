package com.raven.core.session;

import java.net.Socket;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class Session {

    public enum Type {
        RAVEN,
        METERPRETER,
        REVERSE_SHELL,
        UNKNOWN,
    }

    private static final DateTimeFormatter Fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int Id;
    private Socket Socket;
    private String RemoteAddress;
    private Type SessionType;
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
    private String CertCn;
    private boolean RawMode;
    private boolean Encrypted;
    private String Status;

    public Session() {
        JoinedAt = LocalDateTime.now().format(Fmt);
        SessionType = Type.RAVEN;
        Status = "Online";
        Encrypted = true;
        ShellMode = "Standard";
        Os = "Unknown";
        Hostname = "Unknown";
        User = "Unknown";
        Arch = "Unknown";
        AgentIp = "Unknown";
        CertCn = "N/A";
        AgentId = GenerateAgentId();
        SessionKey = GenerateSessionKey();
    }

    private static String GenerateAgentId() {
        byte[] B = new byte[6];
        new SecureRandom().nextBytes(B);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(B).toUpperCase().substring(0, 8);
    }

    private static String GenerateSessionKey() {
        byte[] B = new byte[18];
        new SecureRandom().nextBytes(B);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(B);
    }

    public int GetId() {
        return Id;
    }

    public void SetId(int V) {
        Id = V;
    }

    public java.net.Socket GetSocket() {
        return Socket;
    }

    public void SetSocket(java.net.Socket V) {
        Socket = V;
    }

    public String GetRemoteAddress() {
        return RemoteAddress;
    }

    public void SetRemoteAddress(String V) {
        RemoteAddress = V;
    }

    public Type GetSessionType() {
        return SessionType;
    }

    public void SetSessionType(Type V) {
        SessionType = V;
    }

    public String GetAgentName() {
        return AgentName;
    }

    public void SetAgentName(String V) {
        AgentName = V;
    }

    public String GetAgentId() {
        return AgentId;
    }

    public void SetAgentId(String V) {
        AgentId = V;
    }

    public String GetSessionKey() {
        return SessionKey;
    }

    public void SetSessionKey(String V) {
        SessionKey = V;
    }

    public String GetOs() {
        return Os;
    }

    public void SetOs(String V) {
        Os = V;
    }

    public String GetHostname() {
        return Hostname;
    }

    public void SetHostname(String V) {
        Hostname = V;
    }

    public String GetUser() {
        return User;
    }

    public void SetUser(String V) {
        User = V;
    }

    public String GetArch() {
        return Arch;
    }

    public void SetArch(String V) {
        Arch = V;
    }

    public String GetAgentIp() {
        return AgentIp;
    }

    public void SetAgentIp(String V) {
        AgentIp = V;
    }

    public String GetJoinedAt() {
        return JoinedAt;
    }

    public void SetJoinedAt(String V) {
        JoinedAt = V;
    }

    public String GetShellMode() {
        return ShellMode;
    }

    public void SetShellMode(String V) {
        ShellMode = V;
    }

    public boolean IsMtlsEnabled() {
        return MtlsEnabled;
    }

    public void SetMtlsEnabled(boolean V) {
        MtlsEnabled = V;
    }

    public String GetCertCn() {
        return CertCn;
    }

    public void SetCertCn(String V) {
        CertCn = V;
    }

    public boolean IsRawMode() {
        return RawMode;
    }

    public void SetRawMode(boolean V) {
        RawMode = V;
    }

    public boolean IsEncrypted() {
        return Encrypted;
    }

    public void SetEncrypted(boolean V) {
        Encrypted = V;
    }

    public String GetStatus() {
        return Status;
    }

    public void SetStatus(String V) {
        Status = V;
    }

    public String GetDisplayName() {
        if (CertCn != null && !CertCn.equals("N/A") && !CertCn.isBlank()) return CertCn.toUpperCase();
        if (AgentName != null && !AgentName.isBlank() && !AgentName.equals("Unknown")) return AgentName.toUpperCase();
        return "AGENT-" + Id;
    }
}
