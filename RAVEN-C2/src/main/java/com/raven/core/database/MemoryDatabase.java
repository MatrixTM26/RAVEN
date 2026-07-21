package com.raven.core.database;

import com.raven.utils.ServerConfig;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class MemoryDatabase extends TeamDatabase {

    private static final DateTimeFormatter Fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MaxEntries = 5000;

    private final List<String> LogEntries = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> CommandLogs = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> SessionEvents = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, String> AgentNotes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> Operators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> LastSeenMap = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> ChatLogs = new CopyOnWriteArrayList<>();

    public MemoryDatabase(ServerConfig Config) {
        String AdminUser = Config != null ? Config.GetAdminUsername() : "admin";
        String AdminPass = Config != null ? Config.GetAdminPassword() : "admin";
        String AdminRole = Config != null ? Config.GetAdminRole() : "SUPER";
        Map<String, Object> Admin = new LinkedHashMap<>();
        Admin.put("Username", AdminUser);
        Admin.put("PasswordHash", HashPassword(AdminPass));
        Admin.put("Role", OperatorRole.FromString(AdminRole).name());
        Admin.put("CreatedAt", LocalDateTime.now().format(Fmt));
        Operators.put(AdminUser, Admin);
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
        Row.put("Timestamp", LocalDateTime.now().format(Fmt));
        CommandLogs.add(Row);
        if (CommandLogs.size() > MaxEntries) CommandLogs.remove(0);
    }

    @Override
    public void SaveSessionEvent(Map<String, Object> Data, String Event) {
        Map<String, Object> Row = new LinkedHashMap<>(Data);
        Row.put("Event", Event);
        Row.put("Timestamp", LocalDateTime.now().format(Fmt));
        SessionEvents.add(Row);
        if (SessionEvents.size() > MaxEntries) SessionEvents.remove(0);
    }

    @Override
    public List<Map<String, Object>> GetCommandHistory(int AgentId, int Limit) {
        return CommandLogs.stream()
            .filter(R -> AgentId == 0 || Objects.equals(R.get("AgentId"), AgentId))
            .sorted((A, B) -> B.get("Timestamp").toString().compareTo(A.get("Timestamp").toString()))
            .limit(Limit)
            .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> GetSessionHistory(int Limit) {
        return SessionEvents.stream()
            .sorted((A, B) -> B.get("Timestamp").toString().compareTo(A.get("Timestamp").toString()))
            .limit(Limit)
            .collect(Collectors.toList());
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
    public boolean CreateOperator(String Username, String PasswordHash, OperatorRole Role) {
        if (Operators.containsKey(Username)) return false;
        Map<String, Object> Op = new LinkedHashMap<>();
        Op.put("Username", Username);
        Op.put("PasswordHash", PasswordHash);
        Op.put("Role", Role.name());
        Op.put("CreatedAt", LocalDateTime.now().format(Fmt));
        Operators.put(Username, Op);
        return true;
    }

    @Override
    public boolean ValidateOperator(String Username, String PasswordHash) {
        Map<String, Object> Op = Operators.get(Username);
        if (Op == null) return false;
        Object Hash = Op.get("PasswordHash");
        return Hash != null && PasswordHash.equals(Hash.toString());
    }

    @Override
    public OperatorRole GetOperatorRole(String Username) {
        Map<String, Object> Op = Operators.get(Username);
        if (Op == null) return OperatorRole.MEMBER;
        Object R = Op.get("Role");
        return R != null ? OperatorRole.FromString(R.toString()) : OperatorRole.MEMBER;
    }

    @Override
    public List<Map<String, Object>> GetOperators() {
        return Operators.values()
            .stream()
            .map(Op -> {
                Map<String, Object> Safe = new LinkedHashMap<>();
                Safe.put("Username", Op.getOrDefault("Username", ""));
                Safe.put("Role", Op.getOrDefault("Role", "MEMBER"));
                Safe.put("CreatedAt", Op.getOrDefault("CreatedAt", ""));
                Safe.put("LastSeen", LastSeenMap.getOrDefault(Op.getOrDefault("Username", "").toString(), "Never"));
                return Safe;
            })
            .collect(Collectors.toList());
    }

    @Override
    public boolean UpdateOperatorRole(String Username, OperatorRole Role) {
        Map<String, Object> Op = Operators.get(Username);
        if (Op == null) return false;
        Op.put("Role", Role.name());
        return true;
    }

    @Override
    public boolean UpdateOperatorPassword(String Username, String PasswordHash) {
        Map<String, Object> Op = Operators.get(Username);
        if (Op == null) return false;
        Op.put("PasswordHash", PasswordHash);
        return true;
    }

    @Override
    public boolean DeleteOperator(String Username) {
        if ("admin".equalsIgnoreCase(Username)) return false;
        return Operators.remove(Username) != null;
    }

    @Override
    public void UpdateLastSeen(String Username) {
        LastSeenMap.put(Username, LocalDateTime.now().format(Fmt));
    }

    @Override
    public String GetLastSeen(String Username) {
        return LastSeenMap.getOrDefault(Username, "Never");
    }

    @Override
    public void SaveChatLog(String FromOperator, String ToOperators, String Message) {
        Map<String, Object> Entry = new LinkedHashMap<>();
        Entry.put("from_operator", FromOperator);
        Entry.put("to_operators", ToOperators);
        Entry.put("message", Message);
        Entry.put("timestamp", LocalDateTime.now().format(Fmt));
        ChatLogs.add(Entry);
        if (ChatLogs.size() > 500) ChatLogs.remove(0);
    }

    @Override
    public List<Map<String, Object>> GetChatLogs(int Limit) {
        int Start = Math.max(0, ChatLogs.size() - Limit);
        return new ArrayList<>(ChatLogs.subList(Start, ChatLogs.size()));
    }

    @Override
    public void Close() {}
}
