package com.raven.core.database;

import com.raven.utils.ServerConfig;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class MemoryDatabase extends TeamDatabase {

    private static final DateTimeFormatter TimestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MaxEntries = 5000;

    private final List<String> LogEntries = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> CommandLogs = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> SessionEvents = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, String> AgentNotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> Operators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> LastSeenTable = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> ChatLogs = new CopyOnWriteArrayList<>();

    public MemoryDatabase(ServerConfig Configuration) {
        String AdminUsername = Configuration != null ? Configuration.GetAdminUsername() : "admin";
        String AdminPassword = Configuration != null ? Configuration.GetAdminPassword() : "admin";
        String AdminRoleName = Configuration != null ? Configuration.GetAdminRole() : "SUPER";
        Map<String, Object> AdminEntry = new LinkedHashMap<>();
        AdminEntry.put("Username", AdminUsername);
        AdminEntry.put("PasswordHash", HashPassword(AdminPassword));
        AdminEntry.put("Role", OperatorRole.FromString(AdminRoleName).name());
        AdminEntry.put("CreatedAt", LocalDateTime.now().format(TimestampFormat));
        Operators.put(AdminUsername, AdminEntry);
    }

    @Override
    public boolean IsConnected() {
        return true;
    }

    @Override
    public void SaveLog(String Entry) {
        LogEntries.add(Entry);
        if (LogEntries.size() > MaxEntries) LogEntries.remove(0);
    }

    @Override
    public void SaveCommandLog(int AgentId, String Operator, String Command, String Output, boolean Success) {
        Map<String, Object> Row = new LinkedHashMap<>();
        Row.put("AgentId", AgentId);
        Row.put("Operator", Operator);
        Row.put("Command", Command);
        Row.put("Output", Output);
        Row.put("Success", Success);
        Row.put("Timestamp", LocalDateTime.now().format(TimestampFormat));
        CommandLogs.add(Row);
        if (CommandLogs.size() > MaxEntries) CommandLogs.remove(0);
    }

    @Override
    public List<Map<String, Object>> GetCommandHistory(int AgentId, int Limit) {
        return CommandLogs.stream()
            .filter(Row -> AgentId == 0 || AgentId == ((Number) Row.getOrDefault("AgentId", 0)).intValue())
            .skip(Math.max(0, CommandLogs.size() - Limit))
            .collect(Collectors.toList());
    }

    @Override
    public void SaveSessionEvent(Map<String, Object> Data, String EventType) {
        Map<String, Object> Row = new LinkedHashMap<>(Data);
        Row.put("EventType", EventType);
        Row.put("Timestamp", LocalDateTime.now().format(TimestampFormat));
        SessionEvents.add(Row);
        if (SessionEvents.size() > MaxEntries) SessionEvents.remove(0);
    }

    @Override
    public List<Map<String, Object>> GetSessionHistory(int Limit) {
        int StartIndex = Math.max(0, SessionEvents.size() - Limit);
        return new ArrayList<>(SessionEvents.subList(StartIndex, SessionEvents.size()));
    }

    @Override
    public void SetAgentNote(int AgentId, String Note) {
        AgentNotes.put(AgentId, Note);
    }

    @Override
    public String GetAgentNote(int AgentId) {
        return AgentNotes.getOrDefault(AgentId, "");
    }

    @Override
    public List<Map<String, Object>> GetAllAgentNotes() {
        List<Map<String, Object>> Result = new ArrayList<>();
        AgentNotes.forEach((AgentId, Note) -> {
            Map<String, Object> Row = new LinkedHashMap<>();
            Row.put("AgentId", AgentId);
            Row.put("Note", Note);
            Result.add(Row);
        });
        return Result;
    }

    @Override
    public boolean CreateOperator(String Username, String PasswordHash, OperatorRole Role) {
        if (Operators.containsKey(Username)) return false;
        Map<String, Object> Entry = new LinkedHashMap<>();
        Entry.put("Username", Username);
        Entry.put("PasswordHash", PasswordHash);
        Entry.put("Role", Role.name());
        Entry.put("CreatedAt", LocalDateTime.now().format(TimestampFormat));
        Operators.put(Username, Entry);
        return true;
    }

    @Override
    public boolean DeleteOperator(String Username) {
        if ("admin".equalsIgnoreCase(Username)) return false;
        return Operators.remove(Username) != null;
    }

    @Override
    public boolean ValidateOperator(String Username, String PasswordHash) {
        Map<String, Object> Operator = Operators.get(Username);
        if (Operator == null) return false;
        Object StoredHash = Operator.get("PasswordHash");
        return StoredHash != null && PasswordHash.equals(StoredHash.toString());
    }

    @Override
    public OperatorRole GetOperatorRole(String Username) {
        Map<String, Object> Operator = Operators.get(Username);
        if (Operator == null) return OperatorRole.MEMBER;
        Object Role = Operator.get("Role");
        return Role != null ? OperatorRole.FromString(Role.toString()) : OperatorRole.MEMBER;
    }

    @Override
    public List<Map<String, Object>> GetOperators() {
        return Operators.values()
            .stream()
            .map(Operator -> {
                Map<String, Object> SafeView = new LinkedHashMap<>();
                SafeView.put("Username", Operator.getOrDefault("Username", ""));
                SafeView.put("Role", Operator.getOrDefault("Role", "MEMBER"));
                SafeView.put("CreatedAt", Operator.getOrDefault("CreatedAt", ""));
                SafeView.put("LastSeen", LastSeenTable.getOrDefault(Operator.getOrDefault("Username", "").toString(), "Never"));
                return SafeView;
            })
            .collect(Collectors.toList());
    }

    @Override
    public boolean UpdateOperatorRole(String Username, OperatorRole Role) {
        Map<String, Object> Operator = Operators.get(Username);
        if (Operator == null) return false;
        Operator.put("Role", Role.name());
        return true;
    }

    @Override
    public boolean UpdateOperatorPassword(String Username, String PasswordHash) {
        Map<String, Object> Operator = Operators.get(Username);
        if (Operator == null) return false;
        Operator.put("PasswordHash", PasswordHash);
        return true;
    }

    @Override
    public void UpdateLastSeen(String Username) {
        LastSeenTable.put(Username, LocalDateTime.now().format(TimestampFormat));
    }

    @Override
    public String GetLastSeen(String Username) {
        return LastSeenTable.getOrDefault(Username, "Never");
    }

    @Override
    public void SaveChatLog(String FromOperator, String ToOperators, String Message) {
        Map<String, Object> Entry = new LinkedHashMap<>();
        Entry.put("from_operator", FromOperator);
        Entry.put("to_operators", ToOperators);
        Entry.put("message", Message);
        Entry.put("timestamp", LocalDateTime.now().format(TimestampFormat));
        ChatLogs.add(Entry);
        if (ChatLogs.size() > 500) ChatLogs.remove(0);
    }

    @Override
    public List<Map<String, Object>> GetChatLogs(int Limit) {
        int StartIndex = Math.max(0, ChatLogs.size() - Limit);
        return new ArrayList<>(ChatLogs.subList(StartIndex, ChatLogs.size()));
    }

    @Override
    public void Close() {}
}
