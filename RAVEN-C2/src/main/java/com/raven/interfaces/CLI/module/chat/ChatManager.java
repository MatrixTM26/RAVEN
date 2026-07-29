package com.raven.interfaces.CLI.module.chat;

import com.raven.core.database.TeamDatabase;
import com.raven.core.output.Logger;
import com.raven.interfaces.CLI.module.terminal.TerminalRenderer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ChatManager {

    private static final DateTimeFormatter TimeFormat  = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int               MaxMessages = 500;

    private final List<Map<String, Object>> MessageHistory = new CopyOnWriteArrayList<>();
    private final TeamDatabase              Database;
    private final TerminalRenderer          Renderer;

    private String OperatorName;

    public ChatManager(TeamDatabase Database, TerminalRenderer Renderer) {
        this.Database = Database;
        this.Renderer = Renderer;
    }

    public void SetOperator(String OperatorName) {
        this.OperatorName = OperatorName;
    }

    public void Send(String Recipient, String Message) {
        if (OperatorName == null) { Logger.Info("not in team mode"); return; }
        Map<String, Object> Entry = new LinkedHashMap<>();
        Entry.put("From",    OperatorName);
        Entry.put("To",      Recipient);
        Entry.put("Message", Message);
        Entry.put("Time",    LocalTime.now().format(TimeFormat));
        MessageHistory.add(Entry);
        if (MessageHistory.size() > MaxMessages) MessageHistory.remove(0);
        Database.SaveChatLog(OperatorName, Recipient, Message);
        Logger.Custom("  Message sent to [%s%s%s]%n",
            com.raven.utils.AnsiColor.Green, Recipient, com.raven.utils.AnsiColor.Reset);
    }

    public void ShowLocalMessages() {
        System.out.println(Renderer.Box("CHAT MESSAGES"));
        System.out.println();
        if (MessageHistory.isEmpty()) { Logger.Info("no messages\n"); return; }
        String CurrentOperator = OperatorName != null ? OperatorName : "";
        for (Map<String, Object> Message : MessageHistory) {
            String  From      = Message.getOrDefault("From", "?").toString();
            String  To        = Message.getOrDefault("To", "all").toString();
            String  Content   = Message.getOrDefault("Message", "").toString();
            String  Timestamp = Message.getOrDefault("Time", "").toString();
            boolean IsMine    = From.equals(CurrentOperator);
            String  ToLabel   = To.equals("all") ? "all" : "> " + To;
            Logger.Custom("  %s[%s] %s%s%s [%s]: %s%s%n",
                IsMine ? com.raven.utils.AnsiColor.Green : com.raven.utils.AnsiColor.White,
                Timestamp,
                IsMine ? com.raven.utils.AnsiColor.Green : com.raven.utils.AnsiColor.Red,
                From, com.raven.utils.AnsiColor.Reset,
                ToLabel, Content, com.raven.utils.AnsiColor.Reset);
        }
        System.out.println();
    }

    public void ShowDatabaseHistory() {
        List<Map<String, Object>> Records = Database.GetChatLogs(100);
        System.out.println(Renderer.Box("CHAT HISTORY (Database - last 100)"));
        System.out.println();
        if (Records.isEmpty()) { Logger.Info("no chat history in database\n"); return; }
        List<Map<String, Object>> Ordered = new ArrayList<>(Records);
        Collections.reverse(Ordered);
        String CurrentOperator = OperatorName != null ? OperatorName : "";
        for (Map<String, Object> Record : Ordered) {
            String  From      = Record.getOrDefault("from_operator", "?").toString();
            String  To        = Record.getOrDefault("to_operators", "all").toString();
            String  Content   = Record.getOrDefault("message", "").toString();
            String  Timestamp = Record.getOrDefault("timestamp", "").toString();
            if (Timestamp.length() > 11) Timestamp = Timestamp.substring(11, Math.min(19, Timestamp.length()));
            boolean IsMine  = From.equals(CurrentOperator);
            String  ToLabel = "all".equals(To) ? "all" : "> " + To;
            Logger.Custom("  %s[%s] %s%s%s [%s]: %s%s%n",
                IsMine ? com.raven.utils.AnsiColor.Green : com.raven.utils.AnsiColor.White,
                Timestamp,
                IsMine ? com.raven.utils.AnsiColor.Green : com.raven.utils.AnsiColor.Red,
                From, com.raven.utils.AnsiColor.Reset,
                ToLabel, Content, com.raven.utils.AnsiColor.Reset);
        }
        System.out.println();
    }
}
