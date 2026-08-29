package com.raven.core.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.raven.core.database.TeamDatabase;
import com.raven.core.output.EventLog;
import com.raven.core.output.Logger;
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

    private static final DateTimeFormatter LogFmt = com.raven.utils.RavenConstants.TimestampFmt;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path OUT_DIR = Paths.get("exports");

    public enum Target {
        ALL,
        LOGS,
        CHAT,
        HISTORY,
        SESSIONS,
        OPERATORS,
        NOTES;

        public static Target From(String S) {
            return switch (S.trim().toLowerCase()) {
                case "all" -> ALL;
                case "logs" -> LOGS;
                case "chat" -> CHAT;
                case "history" -> HISTORY;
                case "sessions" -> SESSIONS;
                case "operators" -> OPERATORS;
                case "notes" -> NOTES;
                default -> null;
            };
        }
    }

    public enum Format {
        TXT,
        JSON;

        public static Format From(String S) {
            return switch (S.trim().toLowerCase()) {
                case "json" -> JSON;
                case "txt" -> TXT;
                default -> null;
            };
        }
    }

    private final TeamDatabase Db;
    private final EventLog Log;

    public ExportCommand(TeamDatabase Db, EventLog Log) {
        this.Db = Db;
        this.Log = Log;
    }

    public void Run(String TargetStr, String FormatStr) {
        Target T = Target.From(TargetStr);
        Format F = Format.From(FormatStr);

        if (T == null) {
            Logger.Warn("unknown export target: " + TargetStr + "  (all, logs, chat, history, sessions, operators, notes)");
            return;
        }
        if (F == null) {
            Logger.Warn("unknown format: " + FormatStr + "  (txt, json)");
            return;
        }

        try {
            Files.createDirectories(OUT_DIR);
        } catch (IOException Ex) {
            Logger.Error("cannot create exports dir: " + Ex.getMessage());
            return;
        }

        switch (T) {
            case ALL -> ExportAll(F);
            case LOGS -> Write("logs", F, CollectLogs());
            case CHAT -> Write("chat", F, CollectChat());
            case HISTORY -> Write("history", F, CollectHistory());
            case SESSIONS -> Write("sessions", F, CollectSessions());
            case OPERATORS -> Write("operators", F, CollectOperators());
            case NOTES -> Write("notes", F, CollectNotes());
        }
    }

    private void ExportAll(Format F) {
        Map<String, Object> Bundle = new LinkedHashMap<>();
        Bundle.put("exported_at", LocalDateTime.now().format(LogFmt));
        Bundle.put("logs", CollectLogs());
        Bundle.put("chat", CollectChat());
        Bundle.put("history", CollectHistory());
        Bundle.put("sessions", CollectSessions());
        Bundle.put("operators", CollectOperators());
        Bundle.put("notes", CollectNotes());

        if (F == Format.JSON) {
            WriteRaw("export_all", "json", GSON.toJson(Bundle));
        } else {
            StringBuilder Sb = new StringBuilder();
            Sb.append("RAVEN FULL EXPORT — ").append(LocalDateTime.now().format(LogFmt)).append("\n\n");
            AppendSection(Sb, "SERVER LOGS", CollectLogs());
            AppendSection(Sb, "CHAT HISTORY", CollectChat());
            AppendSection(Sb, "COMMAND HISTORY", CollectHistory());
            AppendSection(Sb, "SESSION HISTORY", CollectSessions());
            AppendSection(Sb, "OPERATORS", CollectOperators());
            AppendSection(Sb, "AGENT NOTES", CollectNotes());
            WriteRaw("export_all", "txt", Sb.toString());
        }
    }

    private void Write(String Name, Format F, List<Map<String, Object>> Data) {
        if (F == Format.JSON) {
            WriteRaw(Name, "json", GSON.toJson(Data));
        } else {
            StringBuilder Sb = new StringBuilder();
            Sb.append(Name.toUpperCase()).append(" — ").append(LocalDateTime.now().format(LogFmt)).append("\n\n");
            for (Map<String, Object> Row : Data) {
                for (Map.Entry<String, Object> E : Row.entrySet()) Sb.append(E.getKey()).append(": ").append(E.getValue()).append("\n");
                Sb.append("---\n");
            }
            WriteRaw(Name, "txt", Sb.toString());
        }
    }

    private void WriteRaw(String Name, String Ext, String Content) {
        String Filename = Name + "_" + LocalDateTime.now().format(com.raven.utils.RavenConstants.FilenameFmt) + "." + Ext;
        Path File = OUT_DIR.resolve(Filename);
        try {
            Files.writeString(File, Content);
            Logger.Info("exported → " + File.toAbsolutePath());
        } catch (IOException Ex) {
            Logger.Error("export failed: " + Ex.getMessage());
        }
    }

    private void AppendSection(StringBuilder Sb, String Title, List<Map<String, Object>> Rows) {
        Sb.append("══════════════════════════════════════\n");
        Sb.append(Title).append("\n");
        Sb.append("══════════════════════════════════════\n");
        if (Rows.isEmpty()) {
            Sb.append("  (no data)\n\n");
            return;
        }
        for (Map<String, Object> Row : Rows) {
            for (Map.Entry<String, Object> E : Row.entrySet()) Sb.append("  ").append(E.getKey()).append(": ").append(E.getValue()).append("\n");
            Sb.append("---\n");
        }
        Sb.append("\n");
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

    private List<Map<String, Object>> CollectChat() {
        return Db.GetChatLogs(5000);
    }

    private List<Map<String, Object>> CollectHistory() {
        return Db.GetCommandHistory(0, 5000);
    }

    private List<Map<String, Object>> CollectSessions() {
        return Db.GetSessionHistory(5000);
    }

    private List<Map<String, Object>> CollectOperators() {
        return Db.GetOperators();
    }

    private List<Map<String, Object>> CollectNotes() {
        return Db.GetAllAgentNotes();
    }
}
