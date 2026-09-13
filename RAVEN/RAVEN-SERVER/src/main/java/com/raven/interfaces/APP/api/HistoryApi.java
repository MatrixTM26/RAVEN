package com.raven.interfaces.APP.api;

import com.raven.core.database.TeamDatabase;
import com.raven.interfaces.APP.shared.HttpHelper;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;

public final class HistoryApi {

    private final TeamDatabase Database;

    public HistoryApi(TeamDatabase Database) {
        this.Database = Database;
    }

    public String CommandHistory(HttpExchange Exchange) throws Exception {
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        int AgentId = HttpHelper.Num(Body, "AgentId", 0);
        int Limit = HttpHelper.Num(Body, "Limit", 100);
        return HttpHelper.Json(Map.of("History", Database.GetCommandHistory(AgentId, Limit)));
    }

    public String SessionHistory(HttpExchange Exchange) throws Exception {
        int Limit = HttpHelper.Num(HttpHelper.Body(Exchange), "Limit", 100);
        return HttpHelper.Json(Map.of("Sessions", Database.GetSessionHistory(Limit)));
    }
}
