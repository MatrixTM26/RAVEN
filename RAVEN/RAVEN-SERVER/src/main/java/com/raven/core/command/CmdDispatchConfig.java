package com.raven.core.command;

import com.raven.core.output.Logger;
import com.raven.utils.RavenConstants;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public final class CmdDispatchConfig {

    private static final Properties Props = new Properties();

    static {
        LoadFromFile();
    }

    private CmdDispatchConfig() {}

    private static void LoadFromFile() {
        if (!Files.exists(Paths.get(RavenConstants.CmdDispatchPath))) {
            Logger.Warn("CmdDispatchConfig: " + RavenConstants.CmdDispatchPath + " not found — command dispatch will be unavailable");
            return;
        }
        try (FileInputStream InputStream = new FileInputStream(RavenConstants.CmdDispatchPath)) {
            Props.load(InputStream);
        } catch (IOException Exception) {
            Logger.Error("CmdDispatchConfig: failed to load " + RavenConstants.CmdDispatchPath + " — " + Exception.getMessage());
        }
    }

    public static String Get(boolean IsWindows, String Command) {
        String Platform = IsWindows ? "windows" : "linux";
        String Value = Props.getProperty(Platform + "." + Command);
        if (Value != null) return Value;
        return Props.getProperty("common." + Command);
    }

    public static String Resolve(boolean IsWindows, String Command, String... KeyValuePairs) {
        String Template = Get(IsWindows, Command);
        if (Template == null) return null;
        for (int Index = 0; Index + 1 < KeyValuePairs.length; Index += 2)
            Template = Template.replace("{" + KeyValuePairs[Index] + "}", KeyValuePairs[Index + 1]);
        return Template;
    }
}
