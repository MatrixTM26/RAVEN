package com.tomcat.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ServerConfig {

    private static final String DefaultPath = "server.properties";
    private final Properties Props = new Properties();

    public ServerConfig() {
        LoadDefaults();
        LoadFromFile(DefaultPath);
    }

    public ServerConfig(String Path) {
        LoadDefaults();
        LoadFromFile(Path);
    }

    private void LoadDefaults() {
        Props.setProperty("server.host", "0.0.0.0");
        Props.setProperty("server.port", "4444");
        Props.setProperty("web.host", "0.0.0.0");
        Props.setProperty("web.port", "5000");
        Props.setProperty("web.template.dir", "config/app/templates");
        Props.setProperty("web.static.dir", "config/app/static");
        Props.setProperty("security.mtls.enabled", "false");
        Props.setProperty("security.keystore.path", "certs/server.p12");
        Props.setProperty("security.keystore.password", "tomcat-c2");
        Props.setProperty("security.keystore.type", "PKCS12");
        Props.setProperty("security.truststore.path", "certs/truststore.p12");
        Props.setProperty("security.truststore.password", "tomcat-c2");
        Props.setProperty("security.tls.protocol", "TLSv1.3");
        Props.setProperty("agent.connection.timeout", "10000");
        Props.setProperty("agent.command.timeout", "120000");
        Props.setProperty("agent.max.connections", "100");
        Props.setProperty("agent.buffer.size", "8192");
        Props.setProperty("logging.level", "INFO");
        Props.setProperty("logging.max.entries", "1000");
        Props.setProperty("mode.meterpreter", "false");
        Props.setProperty("mode.interface", "web");
    }

    private void LoadFromFile(String Path) {
        try (InputStream In = new FileInputStream(Path)) {
            Props.load(In);
        } catch (IOException Ignored) {}
    }

    public String GetServerHost() {
        return Props.getProperty("server.host");
    }

    public int GetServerPort() {
        return Integer.parseInt(Props.getProperty("server.port"));
    }

    public String GetWebHost() {
        return Props.getProperty("web.host");
    }

    public int GetWebPort() {
        return Integer.parseInt(Props.getProperty("web.port"));
    }

    public String GetTemplateDir() {
        return Props.getProperty("web.template.dir");
    }

    public String GetStaticDir() {
        return Props.getProperty("web.static.dir");
    }

    public boolean IsMtlsEnabled() {
        return Boolean.parseBoolean(Props.getProperty("security.mtls.enabled"));
    }

    public String GetKeystorePath() {
        return Props.getProperty("security.keystore.path");
    }

    public String GetKeystorePassword() {
        return Props.getProperty("security.keystore.password");
    }

    public String GetKeystoreType() {
        return Props.getProperty("security.keystore.type");
    }

    public String GetTruststorePath() {
        return Props.getProperty("security.truststore.path");
    }

    public String GetTruststorePassword() {
        return Props.getProperty("security.truststore.password");
    }

    public String GetTlsProtocol() {
        return Props.getProperty("security.tls.protocol");
    }

    public int GetConnectionTimeout() {
        return Integer.parseInt(Props.getProperty("agent.connection.timeout"));
    }

    public int GetCommandTimeout() {
        return Integer.parseInt(Props.getProperty("agent.command.timeout"));
    }

    public int GetMaxConnections() {
        return Integer.parseInt(Props.getProperty("agent.max.connections"));
    }

    public int GetBufferSize() {
        return Integer.parseInt(Props.getProperty("agent.buffer.size"));
    }

    public int GetMaxLogEntries() {
        return Integer.parseInt(Props.getProperty("logging.max.entries"));
    }

    public boolean IsMeterpreterMode() {
        return Boolean.parseBoolean(Props.getProperty("mode.meterpreter"));
    }

    public String GetInterfaceMode() {
        return Props.getProperty("mode.interface");
    }

    public String Get(String Key) {
        return Props.getProperty(Key);
    }

    public String Get(String Key, String Default) {
        return Props.getProperty(Key, Default);
    }
}
