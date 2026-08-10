package com.raven.interfaces.APP.shared;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class HttpHelper {

    private static final Gson GsonInstance = new Gson();

    private HttpHelper() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> Body(HttpExchange Exchange) throws Exception {
        try (InputStream InputStream = Exchange.getRequestBody()) {
            String Body = new String(InputStream.readAllBytes(), "UTF-8");
            if (Body.isEmpty()) return new HashMap<>();
            Map<String, Object> Result = GsonInstance.fromJson(Body, Map.class);
            return Result != null ? Result : new HashMap<>();
        }
    }

    public static String Str(Map<String, Object> Map, String Key, String Default) {
        Object Value = Map.get(Key);
        return Value != null ? Value.toString() : Default;
    }

    public static int Num(Map<String, Object> Map, String Key, int Default) {
        try {
            return (int) Double.parseDouble(Map.getOrDefault(Key, Default).toString());
        } catch (Exception Exception) {
            return Default;
        }
    }

    public static String Json(Object Value) {
        return GsonInstance.toJson(Value);
    }

    public static Gson Gson() {
        return GsonInstance;
    }
}
