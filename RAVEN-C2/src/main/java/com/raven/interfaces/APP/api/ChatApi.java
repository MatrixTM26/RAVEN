package com.raven.interfaces.APP.api;

import com.raven.core.database.TeamDatabase;
import com.raven.interfaces.APP.shared.HttpHelper;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;

public final class ChatApi {

    private final TeamDatabase Database;

    public ChatApi(TeamDatabase Database) {
        this.Database = Database;
    }

    public String Send(HttpExchange Exchange) throws Exception {
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String From = HttpHelper.Str(Body, "From", "system");
        String To = HttpHelper.Str(Body, "To", "all");
        String Message = HttpHelper.Str(Body, "Message", "");
        if (Message.isEmpty()) return HttpHelper.Json(Map.of("Error", "Message required"));
        Database.SaveChatLog(From, To, Message);
        return HttpHelper.Json(Map.of("Success", true));
    }

    public String History(HttpExchange Exchange) throws Exception {
        int Limit = HttpHelper.Num(HttpHelper.Body(Exchange), "Limit", 100);
        return HttpHelper.Json(Map.of("Messages", Database.GetChatLogs(Limit)));
    }
}
