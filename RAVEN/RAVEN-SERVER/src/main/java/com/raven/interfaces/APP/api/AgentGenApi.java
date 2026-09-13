package com.raven.interfaces.APP.api;

import com.raven.interfaces.APP.shared.HttpHelper;
import com.raven.utils.AgentSourceGen;
import com.raven.utils.ServerConfig;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Map;

public final class AgentGenApi {

    private final ServerConfig Config;

    public AgentGenApi(ServerConfig Config) {
        this.Config = Config;
    }

    public String Generate(HttpExchange Exchange) throws Exception {
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Language = HttpHelper.Str(Body, "Language", "python").toLowerCase();
        String Host = HttpHelper.Str(Body, "Host", Config.GetServerHost());
        int Port = HttpHelper.Num(Body, "Port", Config.GetServerPort());
        boolean Mtls = Boolean.parseBoolean(HttpHelper.Str(Body, "UseMtls", "false"));
        boolean Persist = Boolean.parseBoolean(HttpHelper.Str(Body, "Persist", "false"));
        boolean Hide = Boolean.parseBoolean(HttpHelper.Str(Body, "Hide", "false"));
        String AgentId = HttpHelper.Str(Body, "AgentId", "");
        if (AgentId.isEmpty()) return HttpHelper.Json(Map.of("Error", "AgentId required"));
        try {
            String Source = AgentSourceGen.Generate(Language, AgentId, Host, Port, Mtls, Persist, Hide);
            String Filename = AgentSourceGen.Filename(Language);
            return HttpHelper.Json(Map.of("Success", true, "Language", Language, "Source", Source, "AgentId", AgentId, "Filename", Filename));
        } catch (Exception Ex) {
            return HttpHelper.Json(Map.of("Error", "Generation failed: " + Ex.getMessage()));
        }
    }

    public String ListAgents(HttpExchange Exchange) throws Exception {
        return HttpHelper.Json(Map.of("Languages", List.of("python", "java", "go", "bash")));
    }
}
