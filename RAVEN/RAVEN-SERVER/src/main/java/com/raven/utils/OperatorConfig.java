package com.raven.utils;

import com.raven.core.output.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class OperatorConfig {

    private final Properties Props    = new Properties();
    private final String     FilePath;

    public OperatorConfig() {
        this(RavenConstants.OperatorConfigPath);
    }

    public OperatorConfig(String Path) {
        this.FilePath = Path;
        LoadFromResource();
        LoadFromFile(Path);
    }

    OperatorConfig(Properties Merged) {
        this.FilePath = RavenConstants.OperatorConfigPath;
        LoadFromResource();
        Merged.forEach((Key, Value) -> Props.setProperty(Key.toString(), Value.toString()));
    }

    private void LoadFromResource() {
        try (InputStream Stream = OperatorConfig.class.getResourceAsStream(RavenConstants.OperatorDefaultsResource)) {
            if (Stream != null) Props.load(Stream);
        } catch (IOException Exception) {
            Logger.Warn("OperatorConfig: could not load embedded defaults — " + Exception.getMessage());
        }
    }

    private void LoadFromFile(String Path) {
        File F = new File(Path);
        if (!F.exists()) return;
        try (InputStream In = new FileInputStream(F)) {
            Props.load(In);
        } catch (IOException E) {
            Logger.Error("OperatorConfig: failed to load " + Path + " — " + E.getMessage());
        }
    }

    public boolean Save() {
        return Save(FilePath);
    }

    public boolean Save(String Path) {
        try {
            File F = new File(Path);
            F.getParentFile().mkdirs();
            try (FileOutputStream Out = new FileOutputStream(F)) {
                Props.store(Out, "RAVEN Operator Configuration");
            }
            return true;
        } catch (IOException E) {
            Logger.Error("OperatorConfig: failed to save " + Path + " — " + E.getMessage());
            return false;
        }
    }

    public String GetFilePath()              { return FilePath; }
    public String GetOperatorName()          { return Str("operator.name"); }
    public String GetOperatorRole()          { return Str("operator.role").toUpperCase(); }
    public String GetTheme()                 { return Str("operator.theme"); }
    public String GetPromptColor()           { return Str("operator.prompt.color"); }
    public boolean IsOutputBoxEnabled()      { return Bool("operator.output.box"); }
    public boolean IsTimestampEnabled()      { return Bool("operator.output.timestamp"); }
    public int GetSessionLogLimit()          { return Num("operator.session.log.limit"); }
    public int GetHistoryLimit()             { return Num("operator.history.limit"); }
    public boolean IsAutoReconnect()         { return Bool("operator.auto.reconnect"); }
    public boolean IsChatNotifyEnabled()     { return Bool("operator.chat.notify"); }
    public String GetTimezone()              { return Str("operator.timezone"); }
    public String GetDateFormat()            { return Str("operator.date.format"); }
    public String GetInteractiveExitCmd()    { return Str("operator.interactive.exit"); }
    public boolean IsBroadcastConfirm()      { return Bool("operator.broadcast.confirm"); }
    public boolean IsSelfDestructConfirm()   { return Bool("operator.selfdestruct.confirm"); }

    public void SetOperatorName(String Name) { Props.setProperty("operator.name", Name); }
    public void SetOperatorRole(String Role) { Props.setProperty("operator.role", Role.toUpperCase()); }
    public void SetTheme(String Theme)       { Props.setProperty("operator.theme", Theme); }

    public String  Get(String Key, String Default) { return Props.getProperty(Key, Default); }
    public void Put(String Key, String Value)    { Props.setProperty(Key, Value); }

    public String GetAdminUsername()  { return Str("admin.username"); }
    public String GetAdminPassword()  { return Str("admin.password"); }
    public String GetAdminRole()      { String Role = Str("admin.role").toUpperCase(); return Role.isEmpty() ? "SUPER" : Role; }

    public Map<String, String> ToMap() {
        Map<String, String> M = new LinkedHashMap<>();
        Props.forEach((K, V) -> M.put(K.toString(), V.toString()));
        return M;
    }

    private String Str(String Key)  { return Props.getProperty(Key, ""); }
    private int    Num(String Key)  {
        try { return Integer.parseInt(Props.getProperty(Key, "0").trim()); }
        catch (NumberFormatException Ignored) { return 0; }
    }
    private boolean Bool(String Key) {
        return Boolean.parseBoolean(Props.getProperty(Key, "false").trim());
    }
}
