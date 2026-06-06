package com.tomcat.core.db;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class MemoryDatabase extends TeamDatabase {

    private static final DateTimeFormatter Fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<String> LogEntries = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> CommandLogs = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> SessionEvents = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, String> AgentNotes = new ConcurrentHashMap<>();
    private static final int MaxEntries = 5000;

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
    public List<Map<String, Object>> GetOperators() {
        return CommandLogs.stream()
            .map(R -> R.get("Operator").toString())
            .distinct()
            .map(Op -> Map.of("Name", (Object) Op))
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
    public void Close() {}
}
