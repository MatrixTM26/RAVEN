package com.raven.interfaces.APP.api;

import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.output.Logger;
import com.raven.interfaces.APP.shared.HttpHelper;
import com.sun.net.httpserver.HttpExchange;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthApi {

    private static final long TokenTtlMs = 8L * 60 * 60 * 1000;

    public record TokenInfo(String Username, OperatorRole Role, long ExpiresAt) {
        public boolean Valid() {
            return System.currentTimeMillis() < ExpiresAt;
        }
    }

    private final TeamDatabase Database;
    private final ConcurrentHashMap<String, TokenInfo> Tokens;

    public AuthApi(TeamDatabase Database) {
        this.Database = Database;
        this.Tokens = new ConcurrentHashMap<>();
    }

    public String Login(HttpExchange Exchange) throws Exception {
        Map<String, Object> Body = HttpHelper.Body(Exchange);
        String Username = HttpHelper.Str(Body, "Username", "");
        String Password = HttpHelper.Str(Body, "Password", "");
        if (Username.isEmpty() || Password.isEmpty()) return HttpHelper.Json(Map.of("Error", "Username and Password required"));
        if (!Database.ValidateOperator(Username, Password)) return HttpHelper.Json(Map.of("Error", "Invalid credentials"));
        OperatorRole Role = Database.GetOperatorRole(Username);
        String Token = GenerateToken();
        Tokens.put(Token, new TokenInfo(Username, Role, System.currentTimeMillis() + TokenTtlMs));
        Database.UpdateLastSeen(Username);
        Logger.Info("Operator login: " + Username + " [" + Role + "]");
        return HttpHelper.Json(Map.of("Token", Token, "Role", Role.name(), "Username", Username, "ExpiresIn", TokenTtlMs / 1000));
    }

    public String Logout(HttpExchange Exchange) throws Exception {
        String Auth = Exchange.getRequestHeaders().getFirst("Authorization");
        if (Auth != null && Auth.startsWith("Bearer ")) Tokens.remove(Auth.substring(7));
        return HttpHelper.Json(Map.of("Success", true));
    }

    public TokenInfo Validate(HttpExchange Exchange) {
        String Auth = Exchange.getRequestHeaders().getFirst("Authorization");
        if (Auth == null || !Auth.startsWith("Bearer ")) return null;
        TokenInfo Token = Tokens.get(Auth.substring(7));
        return Token != null && Token.Valid() ? Token : null;
    }

    public void InvalidateTokensFor(String Username) {
        Tokens.entrySet().removeIf(Entry -> Entry.getValue().Username().equals(Username));
    }

    private static String GenerateToken() {
        byte[] Bytes = new byte[32];
        new SecureRandom().nextBytes(Bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Bytes);
    }
}
