package com.raven.core.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.raven.core.database.TeamDatabase;
import com.raven.core.output.EventLog;
import com.raven.core.output.Logger;
import com.raven.utils.RavenConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExportCommand {

    private static final DateTimeFormatter LogFmt = RavenConstants.TimestampFmt;
    private static final Gson Gson = new GsonBuilder().setPrettyPrinting().create();

    public enum Target {
        ALL, LOGS, CHAT, HISTORY, SESSIONS, OPERATORS, NOTES;

        public static Target From(String Source) {
            if (Source == null) return null;
            return switch (Source.trim().toLowerCase()) {
                case "all"       -> ALL;
                case "logs"      -> LOGS;
                case "chat"      -> CHAT;
                case "history"   -> HISTORY;
                case "sessions"  -> SESSIONS;
                case "operators" -> OPERATORS;
                case "notes"     -> NOTES;
                default          -> null;
            };
        }
    }

    public enum Format {
        TXT, JSON;

        public static Format From(String Source) {
            if (Source == null) return null;
            return switch (Source.trim().toLowerCase()) {
                case "json" -> JSON;
                case "txt"  -> TXT;
                default     -> null;
            };
        }
    }

    private final TeamDatabase Database;
    private final EventLog Log;
    private final Path OutputDir;

    public ExportCommand(TeamDatabase Database, EventLog Log, String ExportDir) {
        this.Database  = Database;
        this.Log       = Log;
        this.OutputDir = Paths.get(ExportDir != null && !ExportDir.isBlank() ? ExportDir : "exports");
    }

    public void Run(String TargetStr, String FormatStr) {
        Target ExportTarget = Target.From(TargetStr);
        Format ExportFormat = Format.From(FormatStr);
        if (ExportTarget == null) {
            Logger.Warn("unknown export target: " + TargetStr + "  (all, logs, chat, history, sessions, operators, notes)");
            return;
        }
        if (ExportFormat == null) {
            Logger.Warn("unknown format: " + FormatStr + "  (txt, json)");
            return;
        }
        try {
            Files.createDirectories(OutputDir);
        } catch (IOException Exception) {
            Logger.Error("cannot create export dir [" + OutputDir + "]: " + Exception.getMessage());
            return;
        }
        switch (ExportTarget) {
            case ALL       -> ExportAll(ExportFormat);
            case LOGS      -> Write("logs",      ExportFormat, CollectLogs());
            case CHAT      -> Write("chat",      ExportFormat, CollectChat());
            case HISTORY   -> Write("history",   ExportFormat, CollectHistory());
            case SESSIONS  -> Write("sessions",  ExportFormat, CollectSessions());
            case OPERATORS -> Write("operators", ExportFormat, CollectOperators());
            case NOTES     -> Write("notes",     ExportFormat, CollectNotes());
        }
    }

    private void ExportAll(Format ExportFormat) {
        Map<String, Object> Bundle = new LinkedHashMap<>();
        Bundle.put("exported_at", LocalDateTime.now().format(LogFmt));
        Bundle.put("logs",      CollectLogs());
        Bundle.put("chat",      CollectChat());
        Bundle.put("history",   CollectHistory());
        Bundle.put("sessions",  CollectSessions());
        Bundle.put("operators", CollectOperators());
        Bundle.put("notes",     CollectNotes());
        if (ExportFormat == Format.JSON) {
            WriteRaw("export_all", "json", Gson.toJson(Bundle));
        } else {
            StringBuilder Builder = new StringBuilder();
            Builder.append("RAVEN FULL EXPORT — ").append(LocalDateTime.now().format(LogFmt)).append("\n\n");
            AppendSection(Builder, "SERVER LOGS",      CollectLogs());
            AppendSection(Builder, "CHAT HISTORY",     CollectChat());
            AppendSection(Builder, "COMMAND HISTORY",  CollectHistory());
            AppendSection(Builder, "SESSION HISTORY",  CollectSessions());
            AppendSection(Builder, "OPERATORS",        CollectOperators());
            AppendSection(Builder, "AGENT NOTES",      CollectNotes());
            WriteRaw("export_all", "txt", Builder.toString());
        }
    }

    private void Write(String Name, Format ExportFormat, List<Map<String, Object>> Data) {
        if (ExportFormat == Format.JSON) {
            WriteRaw(Name, "json", Gson.toJson(Data));
        } else {
            StringBuilder Builder = new StringBuilder();
            Builder.append(Name.toUpperCase()).append(" — ").append(LocalDateTime.now().format(LogFmt)).append("\n\n");
            for (Map<String, Object> Row : Data) {
                for (Map.Entry<String, Object> Entry : Row.entrySet())
                    Builder.append(Entry.getKey()).append(": ").append(Entry.getValue()).append("\n");
                Builder.append("---\n");
            }
            WriteRaw(Name, "txt", Builder.toString());
        }
    }

    private void WriteRaw(String Name, String Extension, String Content) {
        String Filename = Name + "_" + LocalDateTime.now().format(RavenConstants.FilenameFmt) + "." + Extension;
        Path OutputFile = OutputDir.resolve(Filename);
        try {
            Files.writeString(OutputFile, Content);
            Logger.Info("exported → " + OutputFile.toAbsolutePath());
        } catch (IOException Exception) {
            Logger.Error("export failed [" + OutputFile + "]: " + Exception.getMessage());
        }
    }

    private void AppendSection(StringBuilder Builder, String Title, List<Map<String, Object>> Rows) {
        Builder.append("══════════════════════════════════════\n");
        Builder.append(Title).append("\n");
        Builder.append("══════════════════════════════════════\n");
        if (Rows.isEmpty()) { Builder.append("  (no data)\n\n"); return; }
        for (Map<String, Object> Row : Rows) {
            for (Map.Entry<String, Object> Entry : Row.entrySet())
                Builder.append("  ").append(Entry.getKey()).append(": ").append(Entry.getValue()).append("\n");
            Builder.append("---\n");
        }
        Builder.append("\n");
    }

    private List<Map<String, Object>> CollectLogs() {
        List<Map<String, Object>> Result = new ArrayList<>();
        for (String Entry : Log.GetAll()) {
            Map<String, Object> Row = new LinkedHashMap<>();
            Row.put("entry", Entry);
            Result.add(Row);
        }
        return Result;
    }

    private List<Map<String, Object>> CollectChat()      { return Database.GetChatLogs(5000); }
    private List<Map<String, Object>> CollectHistory()   { return Database.GetCommandHistory(0, 5000); }
    private List<Map<String, Object>> CollectSessions()  { return Database.GetSessionHistory(5000); }
    private List<Map<String, Object>> CollectOperators() { return Database.GetOperators(); }
    private List<Map<String, Object>> CollectNotes()     { return Database.GetAllAgentNotes(); }
}
