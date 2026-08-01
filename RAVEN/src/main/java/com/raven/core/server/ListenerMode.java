package com.raven.core.server;

public enum ListenerMode {
    MULTI,
    HTTP,
    HTTPS,
    TLS,
    MTLS,
    FMTLS,
    RAW;

    public static ListenerMode FromString(String Value) {
        if (Value == null) return MULTI;
        return switch (Value.trim().toLowerCase()) {
            case "multi", "meterpreter" -> MULTI;
            case "http" -> HTTP;
            case "https" -> HTTPS;
            case "tls" -> TLS;
            case "mtls" -> MTLS;
            case "fmtls", "full-mtls" -> FMTLS;
            case "raw", "plain", "tcp" -> RAW;
            default -> MULTI;
        };
    }

    public boolean RequiresTls() {
        return this == TLS || this == MTLS || this == FMTLS || this == HTTPS;
    }

    public boolean RequiresClientCert() {
        return this == MTLS || this == FMTLS;
    }

    public boolean AcceptsRawShell() {
        return this == MULTI || this == RAW;
    }

    public boolean AcceptsHttp() {
        return this == MULTI || this == HTTP || this == HTTPS || this == FMTLS;
    }

    public boolean AcceptsRavenAgent() {
        return this == MULTI || this == TLS || this == MTLS || this == FMTLS || this == RAW;
    }
}
