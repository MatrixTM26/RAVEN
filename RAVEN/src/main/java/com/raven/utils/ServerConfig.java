package com.raven.utils;

import com.raven.core.output.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ServerConfig {

    private static final String DefaultConfigPath = "config/server/raven.properties";
    private final Properties Properties = new Properties();
    private final String FilePath;
    private final OperatorConfig OperatorCfg = new OperatorConfig();

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
        SetPropertyValue("web.port", "8080");
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
        SetPropertyValue("agent.sleep.interval", "5000");
        SetPropertyValue("agent.jitter.ms", "1000");
        SetPropertyValue("logging.level", "INFO");
        SetPropertyValue("logging.verbose", "false");
        SetPropertyValue("logging.max.entries", "1000");
        SetPropertyValue("logging.file", "logs/raven.log");
        SetPropertyValue("logging.file.enabled", "false");
        SetPropertyValue("db.type", "sqlite");
        SetPropertyValue("db.path", "database");
        SetPropertyValue("db.url", "");
        SetPropertyValue("db.name", "raven");
        SetPropertyValue("db.user", "raven");
        SetPropertyValue("db.password", "raven");
        SetPropertyValue("db.mongo.uri", "mongodb://localhost:27017");
        SetPropertyValue("db.mongo.name", "raven");
        SetPropertyValue("mode.interface", "cli");
        SetPropertyValue("teamserver.port", "5001");
        SetPropertyValue("teamserver.api.prefix", "/api");
    }

    private void SetPropertyValue(String Key, String Value) {
        Properties.setProperty(Key, Value);
    }

    private void LoadFromFile(String Path) {
        File FileName = new File(Path);
        if (!FileName.exists()) return;
        try (InputStream In = new FileInputStream(FileName)) {
            Properties.load(In);
        } catch (IOException Exception) {
            Logger.Error("server config failed to load " + Path + ": " + Exception.getMessage());
        }
    }

    public String GetFilePath()             { return FilePath; }
    public String GetServerHost()           { return Str("server.host"); }
    public int    GetServerPort()           { return Num("server.port"); }
    public String GetServerMode()           { return Str("server.mode").toLowerCase(); }
    public String GetWebHost()              { return Str("web.host"); }
    public int    GetWebPort()              { return Num("web.port"); }
    public String GetTemplateDir()          { return Str("web.template.dir"); }
    public String GetStaticDir()            { return Str("web.static.dir"); }
    public int    GetBeaconPort()           { return Num("web.beacon.port"); }
    public String GetKeystorePath()         { return Str("cert.keystore.path"); }
    public String GetKeystoreType()         { return Str("cert.keystore.type"); }
    public String GetKeystorePassword()     { return Str("cert.keystore.password"); }
    public String GetTruststorePath()       { return Str("cert.truststore.path"); }
    public String GetTruststoreType()       { return Str("cert.truststore.type"); }
    public String GetTruststorePassword()   { return Str("cert.truststore.password"); }
    public String GetCaPath()               { return Str("cert.ca.path"); }
    public String GetCaType()              { return Str("cert.ca.type"); }
    public String GetCaPassword()           { return Str("cert.ca.password"); }
    public String GetAgentCertDir()         { return Str("cert.agent.dir"); }
    public String GetDnCn()                 { return Str("cert.dn.cn"); }
    public String GetDnO()                  { return Str("cert.dn.o"); }
    public String GetDnOu()                 { return Str("cert.dn.ou"); }
    public String GetDnL()                  { return Str("cert.dn.l"); }
    public String GetDnSt()                 { return Str("cert.dn.st"); }
    public String GetDnC()                  { return Str("cert.dn.c"); }
    public String GetCaDnCn()               { return Str("cert.ca.dn.cn"); }
    public String GetCaDnO()               { return Str("cert.ca.dn.o"); }
    public String GetCaDnOu()               { return Str("cert.ca.dn.ou"); }
    public String GetCaDnL()               { return Str("cert.ca.dn.l"); }
    public String GetCaDnSt()              { return Str("cert.ca.dn.st"); }
    public String GetCaDnC()               { return Str("cert.ca.dn.c"); }
    public int    GetServerValidityDays()   { return Num("cert.server.validity.days"); }
    public int    GetAgentValidityDays()    { return Num("cert.agent.validity.days"); }
    public int    GetCaValidityDays()       { return Num("cert.ca.validity.days"); }
    public String GetTlsProtocol()          { return Str("cert.tls.protocol"); }
    public int    GetConnectionTimeout()    { return Num("agent.connection.timeout"); }
    public int    GetCommandTimeout()       { return Num("agent.command.timeout"); }
    public int    GetMaxConnections()       { return Num("agent.max.connections"); }
    public int    GetBufferSize()           { return Num("agent.buffer.size"); }
    public long   GetSleepIntervalMs()      { return Long("agent.sleep.interval"); }
    public long   GetJitterMs()             { return Long("agent.jitter.ms"); }
    public String GetLoggingLevel()         { return Str("logging.level").toUpperCase(); }
    public boolean IsVerbose()              { return Bool("logging.verbose"); }
    public int    GetMaxLogEntries()        { return Num("logging.max.entries"); }
    public String GetLogFile()              { return Str("logging.file"); }
    public boolean IsFileLoggingEnabled()   { return Bool("logging.file.enabled"); }
    public String GetInterfaceMode()        { return Str("mode.interface").toLowerCase(); }
    public boolean IsMtlsEnabled()          { String Mode = GetServerMode(); return Mode.equals("mtls") || Mode.equals("fmtls"); }
    public boolean IsMeterpreterMode()      { return GetServerMode().equals("multi"); }
    public String GetDatabaseType()         { return Str("db.type").toLowerCase(); }
    public String GetDatabaseUrl()          { return Str("db.url"); }
    public String GetDatabaseName()         { return Str("db.name"); }
    public String GetDatabaseUser()         { return Str("db.user"); }
    public String GetDatabasePath()         { return Str("db.path"); }
    public String GetDatabasePassword()     { return Str("db.password"); }
    public String GetMongoUri()             { return Str("db.mongo.uri"); }
    public String GetMongoDbName()          { return Str("db.mongo.name"); }
    public int    GetTeamServerPort()       { return Num("teamserver.port"); }
    public String GetTeamServerApiPrefix()  { String Value = Str("teamserver.api.prefix"); return Value.isEmpty() ? "/api" : Value; }

    public String Get(String Key)                  { return Properties.getProperty(Key); }
    public String Get(String Key, String Default)  { return Properties.getProperty(Key, Default); }

    private String  Str(String Key)  { return Properties.getProperty(Key, ""); }
    private int     Num(String Key)  { try { return Integer.parseInt(Properties.getProperty(Key, "0").trim()); } catch (NumberFormatException Ignored) { return 0; } }
    private long    Long(String Key) { try { return java.lang.Long.parseLong(Properties.getProperty(Key, "0").trim()); } catch (NumberFormatException Ignored) { return 0L; } }
    private boolean Bool(String Key) { return Boolean.parseBoolean(Properties.getProperty(Key, "false").trim()); }

    public String GetAdminUsername() { return OperatorCfg.GetAdminUsername(); }
    public String GetAdminPassword() { return OperatorCfg.GetAdminPassword(); }
    public String GetAdminRole()     { return OperatorCfg.GetAdminRole(); }
}
