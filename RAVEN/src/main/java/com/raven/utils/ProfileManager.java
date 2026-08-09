package com.raven.utils;

import com.raven.core.output.Logger;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public final class ProfileManager {

    public record Profile(String Name, String Description, String CreatedAt, Properties Settings) {
        public String  Get(String Key, String Default) { return Settings.getProperty(Key, Default); }
        public String  Get(String Key)                 { return Settings.getProperty(Key, ""); }
        public int     GetInt(String Key, int Default) {
            try { return Integer.parseInt(Settings.getProperty(Key, String.valueOf(Default)).trim()); }
            catch (Exception Ignored) { return Default; }
        }
        public boolean GetBool(String Key, boolean Default) {
            String V = Settings.getProperty(Key);
            return V != null ? Boolean.parseBoolean(V.trim()) : Default;
        }
    }

    private ProfileManager() {}

    public static void Initialize() {
        try { Files.createDirectories(Paths.get(RavenConstants.ProfilesDir)); }
        catch (IOException E) { Logger.Error("ProfileManager: init failed — " + E.getMessage()); }
    }

    public static List<String> ListProfiles() {
        List<String> Result = new ArrayList<>();
        try {
            Files.list(Paths.get(RavenConstants.ProfilesDir))
                .filter(P -> P.toString().endsWith(RavenConstants.ProfileExt))
                .map(P -> P.getFileName().toString().replace(RavenConstants.ProfileExt, ""))
                .sorted()
                .forEach(Result::add);
        } catch (IOException Ignored) {}
        return Result;
    }

    public static Optional<Profile> Load(String Name) {
        Path P = Paths.get(RavenConstants.ProfilesDir, Name + RavenConstants.ProfileExt);
        if (!Files.exists(P)) return Optional.empty();
        try (InputStream In = new FileInputStream(P.toFile())) {
            Properties Settings = new Properties();
            Settings.load(In);
            return Optional.of(new Profile(
                Settings.getProperty("profile.name",        Name),
                Settings.getProperty("profile.description", ""),
                Settings.getProperty("profile.created_at",  ""),
                Settings
            ));
        } catch (IOException E) {
            Logger.Error("ProfileManager: failed to load '" + Name + "' — " + E.getMessage());
            return Optional.empty();
        }
    }

    public static boolean Save(String Name, Map<String, String> Settings, String Description) {
        try {
            Files.createDirectories(Paths.get(RavenConstants.ProfilesDir));
            Path P = Paths.get(RavenConstants.ProfilesDir, Name + RavenConstants.ProfileExt);
            Properties Props = new Properties();
            Props.setProperty("profile.name",        Name);
            Props.setProperty("profile.description", Description != null ? Description : "");
            Props.setProperty("profile.created_at",  LocalDateTime.now().format(RavenConstants.TimestampFmt));
            Settings.forEach(Props::setProperty);
            try (FileOutputStream Out = new FileOutputStream(P.toFile())) {
                Props.store(Out, "RAVEN Operator Profile — " + Name);
            }
            return true;
        } catch (IOException E) {
            Logger.Error("ProfileManager: failed to save '" + Name + "' — " + E.getMessage());
            return false;
        }
    }

    public static boolean Delete(String Name) {
        if (Name.equalsIgnoreCase(RavenConstants.DefaultProfile)) {
            Logger.Warn("Cannot delete the default profile");
            return false;
        }
        try {
            return Files.deleteIfExists(Paths.get(RavenConstants.ProfilesDir, Name + RavenConstants.ProfileExt));
        } catch (IOException E) {
            Logger.Error("ProfileManager: failed to delete '" + Name + "' — " + E.getMessage());
            return false;
        }
    }

    public static boolean Exists(String Name) {
        return Files.exists(Paths.get(RavenConstants.ProfilesDir, Name + RavenConstants.ProfileExt));
    }

    public static boolean Clone(String Source, String Target) {
        Optional<Profile> Src = Load(Source);
        if (Src.isEmpty()) return false;
        Map<String, String> Settings = new LinkedHashMap<>();
        Src.get().Settings().forEach((K, V) -> Settings.put(K.toString(), V.toString()));
        Settings.remove("profile.name");
        Settings.remove("profile.created_at");
        return Save(Target, Settings, "Cloned from " + Source);
    }

    public static OperatorConfig LoadAsOperatorConfig(String Name) {
        Optional<Profile> Opt = Load(Name);
        if (Opt.isEmpty()) {
            Logger.Warn("Profile '" + Name + "' not found — using default OperatorConfig");
            return new OperatorConfig();
        }
        Profile Active = Opt.get();
        Properties Base = new Properties();
        Path BasePath = Paths.get(RavenConstants.OperatorConfigPath);
        if (Files.exists(BasePath)) {
            try (InputStream In = new FileInputStream(BasePath.toFile())) {
                Base.load(In);
            } catch (IOException E) {
                Logger.Warn("ProfileManager: could not read base operator config — " + E.getMessage());
            }
        }
        Active.Settings().forEach((K, V) -> {
            String Key = K.toString();
            if (Key.startsWith("operator.") && !Key.startsWith("profile.")) {
                Base.setProperty(Key, V.toString());
            }
        });
        Logger.Info("Profile applied: " + Name);
        return new OperatorConfig(Base);
    }
}
