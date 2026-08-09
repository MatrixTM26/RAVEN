package com.raven.interfaces.APP.api;

import com.raven.interfaces.APP.core.WebLogger;
import com.raven.interfaces.APP.shared.HttpHelper;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;

public final class LogsApi {

    private final WebLogger Logger;

    public LogsApi(WebLogger Logger) {
        this.Logger = Logger;
    }

    public String GetLogs(HttpExchange Exchange) {
        return HttpHelper.Json(Map.of("Logs", Logger.GetAll()));
    }

    public String ClearLogs(HttpExchange Exchange) {
        Logger.Clear();
        return HttpHelper.Json(Map.of("Success", true));
    }
}
