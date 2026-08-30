package com.raven.interfaces.APP.api;

import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.interfaces.APP.core.WebLogger;
import com.raven.interfaces.APP.shared.HttpHelper;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Map;

public final class OperatorApi {

    private final TeamDatabase Database;
    private final WebLogger Logger;
    private final AuthApi Auth;

    public OperatorApi(TeamDatabase Database, WebLogger Logger, AuthApi Auth) {
        this.Database = Database;
        this.Logger = Logger;
        this.Auth = Auth;
    }

    public String GetOperators(HttpExchange Exchange) {
        return HttpHelper.Json(Map.of("Operators", Database.GetOperators()));
    }

    public String GetRoles(HttpExchange Exchange) {
        List<Map<String, Object>> Roles = new java.util.ArrayList<>();
        for (OperatorRole Role : OperatorRole.values()) {
            Map<String, Object> Entry = new java.util.LinkedHashMap<>();
            Entry.put("Name", Role.name());
            Entry.put("Permissions", Role.PermissionString());
            Entry.put("CanExecute", Role.CanExecute());
            Entry.put("CanWrite", Role.CanWrite());
            Entry.put("CanRead", Role.CanRead());
            Entry.put("CanKill", Role.CanKillSession());
            Entry.put("CanManage", Role.CanManage());
            Entry.put("CanKick", Role.CanKickOperator());
            Roles.add(Entry);
        }
        return HttpHelper.Json(Map.of("Roles", Roles));
    }

    public String Create(HttpExchange Exchange) throws Exception {
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Username = HttpHelper.Str(Body, "Username", "");
        String Password = HttpHelper.Str(Body, "Password", "");
        String RoleName = HttpHelper.Str(Body, "Role", "MEMBER");
        if (Username.isEmpty() || Password.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        if (Password.length() < 8) return HttpHelper.Json(Map.of("Error", "Password must be at least 8 characters"));
        OperatorRole Role = OperatorRole.FromString(RoleName);
        if (!Database.CreateOperator(Username, Password, Role)) return HttpHelper.Json(Map.of("Error", "Username already exists"));
        Logger.Add("[TEAM] Operator created: " + Username + " [" + Role + "]");
        return HttpHelper.Json(Map.of("Success", true, "Username", Username, "Role", Role.name()));
    }

    public String UpdateRole(HttpExchange Exchange) throws Exception {
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Username = HttpHelper.Str(Body, "Username", "");
        String RoleName = HttpHelper.Str(Body, "Role", "");
        if (Username.isEmpty() || RoleName.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Role required"));
        if (Username.equals("admin")) return HttpHelper.Json(Map.of("Error", "Cannot change admin role"));
        OperatorRole Role = OperatorRole.FromString(RoleName);
        Database.UpdateOperatorRole(Username, Role);
        Logger.Add("[TEAM] Role updated: " + Username + " > " + Role);
        return HttpHelper.Json(Map.of("Success", true));
    }

    public String UpdatePassword(HttpExchange Exchange) throws Exception {
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Username = HttpHelper.Str(Body, "Username", "");
        String NewPassword = HttpHelper.Str(Body, "Password", "");
        if (Username.isEmpty() || NewPassword.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        if (NewPassword.length() < 8) return HttpHelper.Json(Map.of("Error", "Password must be at least 8 characters"));
        boolean Success = Database.UpdateOperatorPassword(Username, NewPassword);
        if (Success) Logger.Add("[TEAM] Password changed: " + Username);
        return HttpHelper.Json(Map.of("Success", Success));
    }

    public String Delete(HttpExchange Exchange) throws Exception {
        String Username = HttpHelper.Str(HttpHelper.Body(Exchange), "Username", "");
        if (Username.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username required"));
        if (Username.equals("admin")) return HttpHelper.Json(Map.of("Error", "Cannot delete admin"));
        boolean Success = Database.DeleteOperator(Username);
        if (Success) Logger.Add("[TEAM] Operator deleted: " + Username);
        return HttpHelper.Json(Map.of("Success", Success));
    }

    public String Kick(HttpExchange Exchange) throws Exception {
        String Username = HttpHelper.Str(HttpHelper.Body(Exchange), "Username", "");
        if (Username.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username required"));
        if (Username.equals("admin")) return HttpHelper.Json(Map.of("Error", "Cannot kick admin"));
        Auth.InvalidateTokensFor(Username);
        Logger.Add("[TEAM] Operator kicked: " + Username);
        return HttpHelper.Json(Map.of("Success", true));
    }
}
