package com.raven.interfaces.APP.api;

import com.raven.core.database.TeamDatabase;
import com.raven.interfaces.APP.core.WebLogger;
import com.raven.interfaces.APP.core.WebServerManager;
import com.raven.interfaces.APP.shared.HttpHelper;
import com.sun.net.httpserver.HttpExchange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BroadcastApi {

    private final WebServerManager ServerManager;
    private final TeamDatabase Database;
    private final WebLogger Logger;

    public BroadcastApi(WebServerManager ServerManager, TeamDatabase Database, WebLogger Logger) {
        this.ServerManager = ServerManager;
        this.Database = Database;
        this.Logger = Logger;
    }

    public String Broadcast(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Command = HttpHelper.Str(Body, "Command", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        @SuppressWarnings("unchecked")
        List<Object> RawIds = (List<Object>) Body.getOrDefault("AgentIds", new ArrayList<>());
        if (Command.isEmpty()) return HttpHelper.Json(Map.of("Error", "Command required"));
        List<Integer> Ids = new ArrayList<>();
        for (Object IdObject : RawIds)
            try {
                Ids.add((int) Double.parseDouble(IdObject.toString()));
            } catch (Exception Ignored) {}
        if (Ids.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentIds required"));
        Logger.Add("[BROADCAST] [" + Operator + "] >> " + Ids.size() + " agents: " + Command);
        Map<Integer, String[]> Results = ServerManager.GetServer().BroadcastCommand(Ids, Command);
        Map<String, Object> OutputMap = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> Entry : Results.entrySet()) {
            boolean Success = Boolean.parseBoolean(Entry.getValue()[0]);
            OutputMap.put(String.valueOf(Entry.getKey()), Map.of("Success", Success, "Output", Entry.getValue()[1]));
            Database.SaveCommandLog(Entry.getKey(), Operator, Command, Entry.getValue()[1], Success);
        }
        return HttpHelper.Json(Map.of("Success", true, "Results", OutputMap, "Count", Results.size()));
    }

    public String BroadcastAll(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Command = HttpHelper.Str(Body, "Command", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (Command.isEmpty()) return HttpHelper.Json(Map.of("Error", "Command required"));
        Logger.Add("[BROADCAST-ALL] [" + Operator + "] >> " + ServerManager.GetServer().GetSessions().Count() + " agents: " + Command);
        Map<Integer, String[]> Results = ServerManager.GetServer().BroadcastAll(Command);
        Map<String, Object> OutputMap = new LinkedHashMap<>();
        for (Map.Entry<Integer, String[]> Entry : Results.entrySet()) {
            boolean Success = Boolean.parseBoolean(Entry.getValue()[0]);
            OutputMap.put(String.valueOf(Entry.getKey()), Map.of("Success", Success, "Output", Entry.getValue()[1]));
            Database.SaveCommandLog(Entry.getKey(), Operator, Command, Entry.getValue()[1], Success);
        }
        return HttpHelper.Json(Map.of("Success", true, "Results", OutputMap, "Count", Results.size()));
    }
}
