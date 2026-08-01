package com.raven.utils;

import com.raven.core.output.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ServerConfig {

    private static final String DefaultConfigPath = "config/server/server.properties";
    private final Properties Properties = new Properties();
    private final String FilePath;

    public ServerConfig() {
        this(DefaultConfigPath);
    }

    public ServerConfig(String Path) {
        this.FilePath = Path;
        LoadDefaults();
        LoadFromFile(Path);
    }

    private void LoadDefaults() {
        SetPropertyValue("server.host", "0.0.0.0");
        SetPropertyValue("server.port", "4444");
        SetPropertyValue("server.mode", "multi");
        SetPropertyValue("web.host", "0.0.0.0");
        SetPropertyValue("web.port", "5000");
        SetPropertyValue("web.template.dir", "config/interfaces/app/templates");
        SetPropertyValue("web.static.dir", "config/interfaces/app/static");
        SetPropertyValue("web.beacon.port", "-1");
        SetPropertyValue("cert.keystore.path", "certs/server.p12");
        SetPropertyValue("cert.keystore.type", "PKCS12");
        SetPropertyValue("cert.keystore.password", "raven");
        SetPropertyValue("cert.truststore.path", "certs/truststore.p12");
        SetPropertyValue("cert.truststore.type", "PKCS12");
        SetPropertyValue("cert.truststore.password", "raven");
        SetPropertyValue("cert.ca.path", "certs/ca.p12");
        SetPropertyValue("cert.ca.type", "PKCS12");
        SetPropertyValue("cert.ca.password", "raven");
        SetPropertyValue("cert.agent.dir", "certs/agents");
        SetPropertyValue("cert.dn.cn", "RAVEN Server");
        SetPropertyValue("cert.dn.o", "RAVEN C2 Frameworks");
        SetPropertyValue("cert.dn.ou", "ManInTheMatrix");
        SetPropertyValue("cert.dn.l", "DarkNet");
        SetPropertyValue("cert.dn.st", "Cybertron");
        SetPropertyValue("cert.dn.c", "US");
        SetPropertyValue("cert.ca.dn.cn", "RAVEN Root CA");
        SetPropertyValue("cert.ca.dn.o", "RAVEN Frameworks V3");
        SetPropertyValue("cert.ca.dn.ou", "ManInTheMatrix");
        SetPropertyValue("cert.ca.dn.l", "DarkNet");
        SetPropertyValue("cert.ca.dn.st", "Cybertron");
        SetPropertyValue("cert.ca.dn.c", "US");
        SetPropertyValue("cert.server.validity.days", "365");
        SetPropertyValue("cert.agent.validity.days", "90");
        SetPropertyValue("cert.ca.validity.days", "3650");
        SetPropertyValue("cert.tls.protocol", "TLSv1.3");
        SetPropertyValue("agent.connection.timeout", "10000");
        SetPropertyValue("agent.command.timeout", "120000");
        SetPropertyValue("agent.max.connections", "100");
        SetPropertyValue("agent.buffer.size", "8192");
        SetPropertyValue("logging.level", "INFO");
        SetPropertyValue("logging.verbose", "false");
        SetPropertyValue("logging.max.entries", "1000");
        SetPropertyValue("logging.file", "logs/raven.log");
        SetPropertyValue("logging.file.enabled", "false");
        SetPropertyValue("db.type", "none");
        SetPropertyValue("db.path", "database");
        SetPropertyValue("db.url", "");
        SetPropertyValue("db.name", "raven");
        SetPropertyValue("db.user", "raven");
        SetPropertyValue("db.password", "raven");
        SetPropertyValue("db.mongo.uri", "mongodb://localhost:27017");
        SetPropertyValue("db.mongo.name", "raven");
        SetPropertyValue("mode.interface", "cli");
        SetPropertyValue("teamserver.port", "5001");
    }

    private void SetPropertyValue(String Key, String Value) {
        Properties.setProperty(Key, Value);
    }

    private void LoadFromFile(String Path) {
        File FileName = new File(Path);
        if (!FileName.exists()) return;
        try (InputStream In = new FileInputStream(FileName)) {
            Properties.load(In);
        } catch (IOException E) {
            Logger.Error("server config failed to load " + Path + ": " + E.getMessage());
        }
    }

    public String GetFilePath() {
        return FilePath;
    }

    public String GetServerHost() {
        return StrObject("server.host");
    }

    public int GetServerPort() {
        return Number("server.port");
    }

    public String GetServerMode() {
        return StrObject("server.mode").toLowerCase();
    }

    public String GetWebHost() {
        return StrObject("web.host");
    }

    public int GetWebPort() {
        return Number("web.port");
    }

    public String GetTemplateDir() {
        return StrObject("web.template.dir");
    }

    public String GetStaticDir() {
        return StrObject("web.static.dir");
    }

    public int GetBeaconPort() {
        return Number("web.beacon.port");
    }

    public String GetKeystorePath() {
        return StrObject("cert.keystore.path");
    }

    public String GetKeystoreType() {
        return StrObject("cert.keystore.type");
    }

    public String GetKeystorePassword() {
        return StrObject("cert.keystore.password");
    }

    public String GetTruststorePath() {
        return StrObject("cert.truststore.path");
    }

    public String GetTruststoreType() {
        return StrObject("cert.truststore.type");
    }

    public String GetTruststorePassword() {
        return StrObject("cert.truststore.password");
    }

    public String GetCaPath() {
        return StrObject("cert.ca.path");
    }

    public String GetCaType() {
        return StrObject("cert.ca.type");
    }

    public String GetCaPassword() {
        return StrObject("cert.ca.password");
    }

    public String GetAgentCertDir() {
        return StrObject("cert.agent.dir");
    }

    public String GetDnCn() {
        return StrObject("cert.dn.cn");
    }

    public String GetDnO() {
        return StrObject("cert.dn.o");
    }

    public String GetDnOu() {
        return StrObject("cert.dn.ou");
    }

    public String GetDnL() {
        return StrObject("cert.dn.l");
    }

    public String GetDnSt() {
        return StrObject("cert.dn.st");
    }

    public String GetDnC() {
        return StrObject("cert.dn.c");
    }

    public String GetCaDnCn() {
        return StrObject("cert.ca.dn.cn");
    }

    public String GetCaDnO() {
        return StrObject("cert.ca.dn.o");
    }

    public String GetCaDnOu() {
        return StrObject("cert.ca.dn.ou");
    }

    public String GetCaDnL() {
        return StrObject("cert.ca.dn.l");
    }

    public String GetCaDnSt() {
        return StrObject("cert.ca.dn.st");
    }

    public String GetCaDnC() {
        return StrObject("cert.ca.dn.c");
    }

    public int GetServerValidityDays() {
        return Number("cert.server.validity.days");
    }

    public int GetAgentValidityDays() {
        return Number("cert.agent.validity.days");
    }

    public int GetCaValidityDays() {
        return Number("cert.ca.validity.days");
    }

    public String GetTlsProtocol() {
        return StrObject("cert.tls.protocol");
    }

    public int GetConnectionTimeout() {
        return Number("agent.connection.timeout");
    }

    public int GetCommandTimeout() {
        return Number("agent.command.timeout");
    }

    public int GetMaxConnections() {
        return Number("agent.max.connections");
    }

    public int GetBufferSize() {
        return Number("agent.buffer.size");
    }

    public String GetLoggingLevel() {
        return StrObject("logging.level").toUpperCase();
    }

    public boolean IsVerbose() {
        return bool("logging.verbose");
    }

    public int GetMaxLogEntries() {
        return Number("logging.max.entries");
    }

    public String GetLogFile() {
        return StrObject("logging.file");
    }

    public boolean IsFileLoggingEnabled() {
        return bool("logging.file.enabled");
    }

    public String GetInterfaceMode() {
        return StrObject("mode.interface").toLowerCase();
    }

    public boolean IsMtlsEnabled() {
        String M = GetServerMode();
        return M.equals("mtls") || M.equals("fmtls");
    }

    public boolean IsMeterpreterMode() {
        return GetServerMode().equals("multi");
    }

    public String Get(String Key) {
        return Properties.getProperty(Key);
    }

    public String Get(String Key, String Default) {
        return Properties.getProperty(Key, Default);
    }

    private String StrObject(String Key) {
        return Properties.getProperty(Key, "");
    }

    private int Number(String Key) {
        try {
            return Integer.parseInt(Properties.getProperty(Key, "0").trim());
        } catch (NumberFormatException E) {
            return 0;
        }
    }

    private boolean bool(String Key) {
        return Boolean.parseBoolean(Properties.getProperty(Key, "false").trim());
    }

    public String GetDatabaseType() {
        return StrObject("db.type").toLowerCase();
    }

    public String GetDatabaseUrl() {
        return StrObject("db.url");
    }

    public String GetDatabaseName() {
        return StrObject("db.name");
    }

    public String GetDatabaseUser() {
        return StrObject("db.user");
    }

    public String GetDatabasePath() {
        return StrObject("db.path");
    }

    public String GetDatabasePassword() {
        return StrObject("db.password");
    }

    public String GetMongoUri() {
        return StrObject("db.mongo.uri");
    }

    public String GetMongoDbName() {
        return StrObject("db.mongo.name");
    }

    public int GetTeamServerPort() {
        return Number("teamserver.port");
    }

    public String GetAdminUsername() {
        String V = StrObject("admin.username");
        return V.isEmpty() ? "admin" : V;
    }

    public String GetAdminPassword() {
        String V = StrObject("admin.password");
        return V.isEmpty() ? "admin" : V;
    }

    public String GetAdminRole() {
        String V = StrObject("admin.role").toUpperCase();
        return V.isEmpty() ? "SUPER" : V;
    }
}
