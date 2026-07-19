package com.raven.utils;

import com.raven.core.output.Logger;
import java.io.*;
import java.util.Properties;

public final class ServerConfig {

    private static final String DefaultPath = "config/server/server.properties";
    private final Properties Props = new Properties();
    private final String FilePath;

    public ServerConfig() {
        this(DefaultPath);
    }

    public ServerConfig(String Path) {
        this.FilePath = Path;
        LoadDefaults();
        LoadFromFile(Path);
    }

    private void LoadDefaults() {
        set("server.host", "0.0.0.0");
        set("server.port", "4444");
        set("server.mode", "multi");
        set("web.host", "0.0.0.0");
        set("web.port", "5000");
        set("web.template.dir", "config/interfaces/app/templates");
        set("web.static.dir", "config/interfaces/app/static");
        set("web.beacon.port", "-1");
        set("cert.keystore.path", "certs/server.p12");
        set("cert.keystore.type", "PKCS12");
        set("cert.keystore.password", "raven");
        set("cert.truststore.path", "certs/truststore.p12");
        set("cert.truststore.type", "PKCS12");
        set("cert.truststore.password", "raven");
        set("cert.ca.path", "certs/ca.p12");
        set("cert.ca.type", "PKCS12");
        set("cert.ca.password", "raven");
        set("cert.agent.dir", "certs/agents");
        set("cert.dn.cn", "RAVEN Server");
        set("cert.dn.o", "RAVEN C2 Frameworks");
        set("cert.dn.ou", "ManInTheMatrix");
        set("cert.dn.l", "DarkNet");
        set("cert.dn.st", "Cybertron");
        set("cert.dn.c", "US");
        set("cert.ca.dn.cn", "RAVEN Root CA");
        set("cert.ca.dn.o", "RAVEN Frameworks V3");
        set("cert.ca.dn.ou", "ManInTheMatrix");
        set("cert.ca.dn.l", "DarkNet");
        set("cert.ca.dn.st", "Cybertron");
        set("cert.ca.dn.c", "US");
        set("cert.server.validity.days", "365");
        set("cert.agent.validity.days", "90");
        set("cert.ca.validity.days", "3650");
        set("cert.tls.protocol", "TLSv1.3");
        set("agent.connection.timeout", "10000");
        set("agent.command.timeout", "120000");
        set("agent.max.connections", "100");
        set("agent.buffer.size", "8192");
        set("logging.level", "INFO");
        set("logging.verbose", "false");
        set("logging.max.entries", "1000");
        set("logging.file", "logs/raven.log");
        set("logging.file.enabled", "false");
        set("db.type", "none");
        set("db.path", "database");
        set("db.url", "");
        set("db.name", "raven");
        set("db.user", "raven");
        set("db.password", "raven");
        set("db.mongo.uri", "mongodb://localhost:27017");
        set("db.mongo.name", "raven");
        set("mode.interface", "cli");
        set("teamserver.port", "5001");
    }

    private void set(String Key, String Value) {
        Props.setProperty(Key, Value);
    }

    private void LoadFromFile(String Path) {
        File F = new File(Path);
        if (!F.exists()) return;
        try (InputStream In = new FileInputStream(F)) {
            Props.load(In);
        } catch (IOException E) {
            Logger.Error("server config failed to load " + Path + ": " + E.getMessage());
        }
    }

    public String GetFilePath() {
        return FilePath;
    }

    public String GetServerHost() {
        return str("server.host");
    }

    public int GetServerPort() {
        return num("server.port");
    }

    public String GetServerMode() {
        return str("server.mode").toLowerCase();
    }

    public String GetWebHost() {
        return str("web.host");
    }

    public int GetWebPort() {
        return num("web.port");
    }

    public String GetTemplateDir() {
        return str("web.template.dir");
    }

    public String GetStaticDir() {
        return str("web.static.dir");
    }

    public int GetBeaconPort() {
        return num("web.beacon.port");
    }

    public String GetKeystorePath() {
        return str("cert.keystore.path");
    }

    public String GetKeystoreType() {
        return str("cert.keystore.type");
    }

    public String GetKeystorePassword() {
        return str("cert.keystore.password");
    }

    public String GetTruststorePath() {
        return str("cert.truststore.path");
    }

    public String GetTruststoreType() {
        return str("cert.truststore.type");
    }

    public String GetTruststorePassword() {
        return str("cert.truststore.password");
    }

    public String GetCaPath() {
        return str("cert.ca.path");
    }

    public String GetCaType() {
        return str("cert.ca.type");
    }

    public String GetCaPassword() {
        return str("cert.ca.password");
    }

    public String GetAgentCertDir() {
        return str("cert.agent.dir");
    }

    public String GetDnCn() {
        return str("cert.dn.cn");
    }

    public String GetDnO() {
        return str("cert.dn.o");
    }

    public String GetDnOu() {
        return str("cert.dn.ou");
    }

    public String GetDnL() {
        return str("cert.dn.l");
    }

    public String GetDnSt() {
        return str("cert.dn.st");
    }

    public String GetDnC() {
        return str("cert.dn.c");
    }

    public String GetCaDnCn() {
        return str("cert.ca.dn.cn");
    }

    public String GetCaDnO() {
        return str("cert.ca.dn.o");
    }

    public String GetCaDnOu() {
        return str("cert.ca.dn.ou");
    }

    public String GetCaDnL() {
        return str("cert.ca.dn.l");
    }

    public String GetCaDnSt() {
        return str("cert.ca.dn.st");
    }

    public String GetCaDnC() {
        return str("cert.ca.dn.c");
    }

    public int GetServerValidityDays() {
        return num("cert.server.validity.days");
    }

    public int GetAgentValidityDays() {
        return num("cert.agent.validity.days");
    }

    public int GetCaValidityDays() {
        return num("cert.ca.validity.days");
    }

    public String GetTlsProtocol() {
        return str("cert.tls.protocol");
    }

    public int GetConnectionTimeout() {
        return num("agent.connection.timeout");
    }

    public int GetCommandTimeout() {
        return num("agent.command.timeout");
    }

    public int GetMaxConnections() {
        return num("agent.max.connections");
    }

    public int GetBufferSize() {
        return num("agent.buffer.size");
    }

    public String GetLoggingLevel() {
        return str("logging.level").toUpperCase();
    }

    public boolean IsVerbose() {
        return bool("logging.verbose");
    }

    public int GetMaxLogEntries() {
        return num("logging.max.entries");
    }

    public String GetLogFile() {
        return str("logging.file");
    }

    public boolean IsFileLoggingEnabled() {
        return bool("logging.file.enabled");
    }

    public String GetInterfaceMode() {
        return str("mode.interface").toLowerCase();
    }

    public boolean IsMtlsEnabled() {
        String M = GetServerMode();
        return M.equals("mtls") || M.equals("fmtls");
    }

    public boolean IsMeterpreterMode() {
        return GetServerMode().equals("multi");
    }

    public String Get(String Key) {
        return Props.getProperty(Key);
    }

    public String Get(String Key, String Default) {
        return Props.getProperty(Key, Default);
    }

    private String str(String K) {
        return Props.getProperty(K, "");
    }

    private int num(String K) {
        try {
            return Integer.parseInt(Props.getProperty(K, "0").trim());
        } catch (NumberFormatException E) {
            return 0;
        }
    }

    private boolean bool(String K) {
        return Boolean.parseBoolean(Props.getProperty(K, "false").trim());
    }

    public String GetDbType() {
        return str("db.type").toLowerCase();
    }

    public String GetDbUrl() {
        return str("db.url");
    }

    public String GetDbName() {
        return str("db.name");
    }

    public String GetDbUser() {
        return str("db.user");
    }

    public String GetDbPath() {
        return str("db.path");
    }

    public String GetDbPassword() {
        return str("db.password");
    }

    public String GetMongoUri() {
        return str("db.mongo.uri");
    }

    public String GetMongoDbName() {
        return str("db.mongo.name");
    }

    public int GetTeamServerPort() {
        return num("teamserver.port");
    }

    public String GetAdminUsername() {
        String V = str("admin.username");
        return V.isEmpty() ? "admin" : V;
    }

    public String GetAdminPassword() {
        String V = str("admin.password");
        return V.isEmpty() ? "admin" : V;
    }

    public String GetAdminRole() {
        String V = str("admin.role").toUpperCase();
        return V.isEmpty() ? "SUPER" : V;
    }
}
