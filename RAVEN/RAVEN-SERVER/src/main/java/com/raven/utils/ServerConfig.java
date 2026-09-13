package com.raven.utils;

import com.raven.core.output.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ServerConfig {

    private static final String DefaultsResource  = "/config/raven-defaults.properties";
    private static final String UserConfigPath    = "config/server/raven.properties";
    private static final String OperatorConfigDir = "config/operator/operator.properties";

    private final Properties Properties = new Properties();
    private final OperatorConfig OperatorCfg;

    public ServerConfig() {
        this(UserConfigPath);
    }

    public ServerConfig(String UserPath) {
        LoadFromResource(DefaultsResource);
        LoadFromFile(UserPath);
        LoadFromFile(OperatorConfigDir);
        OperatorCfg = new OperatorConfig();
    }

    private void LoadFromResource(String ResourcePath) {
        try (InputStream ResourceStream = ServerConfig.class.getResourceAsStream(ResourcePath)) {
            if (ResourceStream == null) {
                Logger.Warn("Embedded defaults not found at " + ResourcePath + " — using empty config");
                return;
            }
            Properties.load(ResourceStream);
        } catch (IOException Exception) {
            Logger.Warn("Could not load embedded defaults: " + Exception.getMessage());
        }
    }

    private void LoadFromFile(String Path) {
        File ConfigFile = new File(Path);
        if (!ConfigFile.exists()) return;
        try (InputStream FileStream = new FileInputStream(ConfigFile)) {
            Properties.load(FileStream);
        } catch (IOException Exception) {
            Logger.Error("Config load failed [" + Path + "]: " + Exception.getMessage());
        }
    }

    private String  Str(String Key)  { return Properties.getProperty(Key, ""); }
    private int     Num(String Key)  { try { return Integer.parseInt(Properties.getProperty(Key, "0").trim()); } catch (NumberFormatException Ignored) { return 0; } }
    private long    Long(String Key) { try { return java.lang.Long.parseLong(Properties.getProperty(Key, "0").trim()); } catch (NumberFormatException Ignored) { return 0L; } }
    private boolean Bool(String Key) { return Boolean.parseBoolean(Properties.getProperty(Key, "false").trim()); }

    public String Get(String Key)                  { return Properties.getProperty(Key); }
    public String Get(String Key, String Default)  { return Properties.getProperty(Key, Default); }

    public String  GetServerHost()           { return Str("server.host"); }
    public int     GetServerPort()           { return Num("server.port"); }
    public String  GetServerMode()           { return Str("server.mode").toLowerCase(); }
    public String  GetWebHost()              { return Str("web.host"); }
    public int     GetWebPort()              { return Num("web.port"); }
    public String  GetTemplateDir()          { return Str("web.template.dir"); }
    public String  GetStaticDir()            { return Str("web.static.dir"); }
    public int     GetBeaconPort()           { return Num("web.beacon.port"); }
    public int     GetTeamServerPort()       { return Num("teamserver.port"); }
    public String  GetTeamServerApiPrefix()  { String Value = Str("teamserver.api.prefix"); return Value.isEmpty() ? "/api" : Value; }
    public String  GetDatabaseType()         { return Str("db.type").toLowerCase(); }
    public String  GetDatabaseUrl()          { return Str("db.url"); }
    public String  GetDatabaseName()         { return Str("db.name"); }
    public String  GetDatabaseUser()         { return Str("db.user"); }
    public String  GetDatabasePath()         { return Str("db.path"); }
    public String  GetDatabasePassword()     { return Str("db.password"); }
    public String  GetMongoUri()             { return Str("db.mongo.uri"); }
    public String  GetMongoDbName()          { return Str("db.mongo.name"); }
    public long    GetSleepIntervalMs()      { return Long("agent.sleep.interval"); }
    public long    GetJitterMs()             { return Long("agent.jitter.ms"); }
    public int     GetConnectionTimeout()    { return Num("agent.connection.timeout"); }
    public int     GetCommandTimeout()       { return Num("agent.command.timeout"); }
    public int     GetMaxConnections()       { return Num("agent.max.connections"); }
    public int     GetBufferSize()           { return Num("agent.buffer.size"); }
    public String  GetKeystorePath()         { return Str("cert.keystore.path"); }
    public String  GetKeystoreType()         { return Str("cert.keystore.type"); }
    public String  GetKeystorePassword()     { return Str("cert.keystore.password"); }
    public String  GetTruststorePath()       { return Str("cert.truststore.path"); }
    public String  GetTruststoreType()       { return Str("cert.truststore.type"); }
    public String  GetTruststorePassword()   { return Str("cert.truststore.password"); }
    public String  GetCaPath()               { return Str("cert.ca.path"); }
    public String  GetCaType()               { return Str("cert.ca.type"); }
    public String  GetCaPassword()           { return Str("cert.ca.password"); }
    public String  GetAgentCertDir()         { return Str("cert.agent.dir"); }
    public String  GetTlsProtocol()          { return Str("cert.tls.protocol"); }
    public int     GetServerValidityDays()   { return Num("cert.server.validity.days"); }
    public int     GetAgentValidityDays()    { return Num("cert.agent.validity.days"); }
    public int     GetCaValidityDays()       { return Num("cert.ca.validity.days"); }
    public String  GetDnCn()                 { return Str("cert.dn.cn"); }
    public String  GetDnO()                  { return Str("cert.dn.o"); }
    public String  GetDnOu()                 { return Str("cert.dn.ou"); }
    public String  GetDnL()                  { return Str("cert.dn.l"); }
    public String  GetDnSt()                 { return Str("cert.dn.st"); }
    public String  GetDnC()                  { return Str("cert.dn.c"); }
    public String  GetCaDnCn()               { return Str("cert.ca.dn.cn"); }
    public String  GetCaDnO()                { return Str("cert.ca.dn.o"); }
    public String  GetCaDnOu()               { return Str("cert.ca.dn.ou"); }
    public String  GetCaDnL()                { return Str("cert.ca.dn.l"); }
    public String  GetCaDnSt()               { return Str("cert.ca.dn.st"); }
    public String  GetCaDnC()                { return Str("cert.ca.dn.c"); }
    public String  GetLoggingLevel()         { return Str("logging.level").toUpperCase(); }
    public boolean IsVerbose()               { return Bool("logging.verbose"); }
    public int     GetMaxLogEntries()        { return Num("logging.max.entries"); }
    public String  GetLogFile()              { return Str("logging.file"); }
    public boolean IsFileLoggingEnabled()    { return Bool("logging.file.enabled"); }
    public String  GetInterfaceMode()        { return Str("mode.interface").toLowerCase(); }
    public String  GetExportDir()            { return Str("export.dir"); }
    public boolean IsMtlsEnabled()           { String Mode = GetServerMode(); return Mode.equals("mtls") || Mode.equals("fmtls"); }
    public String  GetAdminUsername()        { return OperatorCfg.GetAdminUsername(); }
    public String  GetAdminPassword()        { return OperatorCfg.GetAdminPassword(); }
    public String  GetAdminRole()            { return OperatorCfg.GetAdminRole(); }
}
