package com.raven.utils;

import com.raven.core.output.Logger;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public final class ConnectorConfig {

    private final Properties Props = new Properties();

    public ConnectorConfig(String FilePath) {
        if (!Files.exists(Paths.get(FilePath))) {
            Logger.Verbose("ConnectorConfig: " + FilePath + " not found — using defaults");
            return;
        }
        try (FileInputStream Input = new FileInputStream(FilePath)) {
            Props.load(Input);
        } catch (Exception Exception) {
            Logger.Error("ConnectorConfig: failed to load " + FilePath + " — " + Exception.getMessage());
        }
    }

    public String Get(String Key, String Default) {
        return Props.getProperty(Key, Default);
    }

    public int GetInt(String Key, int Default) {
        try {
            return Integer.parseInt(Props.getProperty(Key, String.valueOf(Default)).trim());
        } catch (NumberFormatException Ignored) {
            return Default;
        }
    }
}
