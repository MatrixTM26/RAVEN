package com.raven.core.database;

import com.google.gson.Gson;
import com.raven.core.output.Logger;
import com.raven.utils.ServerConfig;
import java.sql.*;
import java.util.*;

public final class PostgresDatabase extends TeamDatabase {

    private final Connection Conn;
    private final ServerConfig Config;
    private static final Gson GsonInst = new Gson();

    public PostgresDatabase(ServerConfig Config) throws Exception {
        this.Config = Config;
        String Url = Config.GetDbUrl();
        String User = Config.GetDbUser();
        String Pass = Config.GetDbPassword();
        if (!Url.startsWith("jdbc:postgresql://") && !Url.startsWith("jdbc:postgres://")) {
            throw new Exception("Invalid PostgreSQL URL — must start with jdbc:postgresql://");
        }
        try {
            Class.forName("org.postgresql.Driver");
            Conn = DriverManager.getConnection(Url, User, Pass);
            Conn.setAutoCommit(true);
            Logger.Info("PostgreSQL connected: " + Url);
            InitSchema();
        } catch (ClassNotFoundException E) {
            throw new Exception("PostgreSQL driver not found — add postgresql jar to classpath");
        } catch (SQLException E) {
            throw new Exception("PostgreSQL connect failed [" + Url + "]: " + E.getMessage());
        }
    }

    private void InitSchema() throws SQLException {
        try (Statement St = Conn.createStatement()) {
            St.execute(
                """
                CREATE TABLE IF NOT EXISTS tclogs (
                    id      BIGSERIAL PRIMARY KEY,
                    entry   TEXT NOT NULL,
                    createdat TIMESTAMP DEFAULT NOW()
                )"""
            );
            St.execute(
                """
                CREATE TABLE IF NOT EXISTS tccommands (
                    id        BIGSERIAL PRIMARY KEY,
                    agentid   INTEGER NOT NULL,
                    operator  VARCHAR(128),
                    command   TEXT NOT NULL,
                    output    TEXT,
                    success   BOOLEAN,
                    createdat TIMESTAMP DEFAULT NOW()
                )"""
            );
            St.execute(
                """
                CREATE TABLE IF NOT EXISTS tcsessions (
                    id        BIGSERIAL PRIMARY KEY,
                    agentid   VARCHAR(64),
                    hostname  VARCHAR(256),
                    os        VARCHAR(128),
                    username  VARCHAR(128),
                    agentip   VARCHAR(64),
                    event     VARCHAR(32),
                    data      TEXT,
                    createdat TIMESTAMP DEFAULT NOW()
                )"""
            );
            St.execute(
                """
                CREATE TABLE IF NOT EXISTS tcnotes (
                    agentid   INTEGER PRIMARY KEY,
                    note      TEXT,
                    updatedat TIMESTAMP DEFAULT NOW()
                )"""
            );
            St.execute(
                """
                CREATE TABLE IF NOT EXISTS tcoperators (
                    username     VARCHAR(128) PRIMARY KEY,
                    passwordhash VARCHAR(64)  NOT NULL,
                    role         VARCHAR(32)  NOT NULL DEFAULT 'MEMBER',
                    lastseen     VARCHAR(32),
                    createdat    TIMESTAMP DEFAULT NOW()
                )"""
            );
            try {
                St.execute("ALTER TABLE tcoperators ADD COLUMN IF NOT EXISTS lastseen VARCHAR(32)");
            } catch (Exception Ignored) {}
            St.execute(
                """
                CREATE TABLE IF NOT EXISTS tcchatlog (
                    id           BIGSERIAL PRIMARY KEY,
                    fromoperator VARCHAR(128) NOT NULL,
                    tooperators  VARCHAR(256),
                    message      TEXT NOT NULL,
                    timestamp    TIMESTAMP DEFAULT NOW()
                )"""
            );
            St.execute("INSERT INTO tcoperators (username,passwordhash,role) VALUES ('" + Config.GetAdminUsername() + "','" + HashPassword(Config.GetAdminPassword()) + "','" + Config.GetAdminRole() + "') ON CONFLICT (username) DO NOTHING");
            Logger.Verbose("PostgreSQL schema ready");
        }
    }

    @Override
    public boolean IsConnected() {
        try {
            return Conn != null && !Conn.isClosed() && Conn.isValid(2);
        } catch (Exception E) {
            return false;
        }
    }

    @Override
    public void SaveLog(String Entry) {
        exec("INSERT INTO tclogs (entry) VALUES (?)", Entry);
    }

    @Override
    public void SaveCommandLog(int AgentId, String Operator, String Command, String Output, boolean Success) {
        exec("INSERT INTO tccommands (agentid,operator,command,output,success) VALUES (?,?,?,?,?)", AgentId, Operator, Command, Output, Success);
    }

    @Override
    public void SaveSessionEvent(Map<String, Object> Data, String Event) {
        exec("INSERT INTO tcsessions (agentid,hostname,os,username,agentip,event,data) VALUES (?,?,?,?,?,?,?)", str(Data, "ID"), str(Data, "Hostname"), str(Data, "OS"), str(Data, "User"), str(Data, "AgentIP"), Event, GsonInst.toJson(Data));
    }

    @Override
    public List<Map<String, Object>> GetCommandHistory(int AgentId, int Limit) {
        return AgentId == 0 ? query("SELECT * FROM tccommands ORDER BY createdat DESC LIMIT ?", Limit) : query("SELECT * FROM tccommands WHERE agentid=? ORDER BY createdat DESC LIMIT ?", AgentId, Limit);
    }

    @Override
    public List<Map<String, Object>> GetSessionHistory(int Limit) {
        return query("SELECT * FROM tcsessions ORDER BY createdat DESC LIMIT ?", Limit);
    }

    @Override
    public void SetAgentNote(int AgentId, String Note) {
        exec("INSERT INTO tcnotes (agentid,note) VALUES (?,?) ON CONFLICT (agentid) DO UPDATE SET note=?,updatedat=NOW()", AgentId, Note, Note);
    }

    @Override
    public String GetAgentNote(int AgentId) {
        List<Map<String, Object>> Rows = query("SELECT note FROM tcnotes WHERE agentid=?", AgentId);
        if (Rows.isEmpty()) return "";
        Object V = Rows.get(0).get("note");
        return V != null ? V.toString() : "";
    }

    @Override
    public boolean CreateOperator(String Username, String PasswordHash, OperatorRole Role) {
        try {
            exec("INSERT INTO tcoperators (username,passwordhash,role) VALUES (?,?,?)", Username, PasswordHash, Role.name());
            return true;
        } catch (Exception E) {
            Logger.Error("CreateOperator failed: " + E.getMessage());
            return false;
        }
    }

    @Override
    public boolean ValidateOperator(String Username, String PasswordHash) {
        List<Map<String, Object>> Rows = query("SELECT 1 FROM tcoperators WHERE username=? AND passwordhash=?", Username, PasswordHash);
        return !Rows.isEmpty();
    }

    @Override
    public OperatorRole GetOperatorRole(String Username) {
        List<Map<String, Object>> Rows = query("SELECT role FROM tcoperators WHERE username=?", Username);
        if (Rows.isEmpty()) return OperatorRole.MEMBER;
        Object V = Rows.get(0).get("role");
        return OperatorRole.FromString(V != null ? V.toString() : "MEMBER");
    }

    @Override
    public List<Map<String, Object>> GetOperators() {
        List<Map<String, Object>> Raw = query("SELECT username,role,lastseen,createdat FROM tcoperators ORDER BY createdat");
        List<Map<String, Object>> Result = new ArrayList<>();
        for (Map<String, Object> Row : Raw) {
            Map<String, Object> M = new LinkedHashMap<>();
            M.put("Username", Row.getOrDefault("username", ""));
            M.put("Role", Row.getOrDefault("role", "MEMBER"));
            M.put("LastSeen", Row.getOrDefault("lastseen", "Never") != null ? Row.getOrDefault("lastseen", "Never") : "Never");
            M.put("CreatedAt", Row.getOrDefault("createdat", ""));
            Result.add(M);
        }
        return Result;
    }

    @Override
    public boolean UpdateOperatorRole(String Username, OperatorRole Role) {
        exec("UPDATE tcoperators SET role=? WHERE username=?", Role.name(), Username);
        return true;
    }

    @Override
    public boolean UpdateOperatorPassword(String Username, String PasswordHash) {
        exec("UPDATE tcoperators SET passwordhash=? WHERE username=?", PasswordHash, Username);
        return true;
    }

    @Override
    public boolean DeleteOperator(String Username) {
        if ("admin".equalsIgnoreCase(Username)) return false;
        exec("DELETE FROM tcoperators WHERE username=?", Username);
        return true;
    }

    @Override
    public void UpdateLastSeen(String Username) {
        exec("UPDATE tcoperators SET lastseen=? WHERE username=?", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), Username);
    }

    @Override
    public String GetLastSeen(String Username) {
        try {
            List<Map<String, Object>> Rows = query("SELECT lastseen FROM tcoperators WHERE username=?", Username);
            if (Rows.isEmpty()) return "Never";
            Object Val = Rows.get(0).get("lastseen");
            return Val != null ? Val.toString() : "Never";
        } catch (Exception E) {
            return "Never";
        }
    }

    @Override
    public void SaveChatLog(String FromOperator, String ToOperators, String Message) {
        exec("INSERT INTO tcchatlog (fromoperator,tooperators,message) VALUES (?,?,?)", FromOperator, ToOperators, Message);
    }

    @Override
    public List<Map<String, Object>> GetChatLogs(int Limit) {
        try {
            List<Map<String, Object>> Raw = query("SELECT * FROM tcchatlog ORDER BY timestamp DESC LIMIT ?", Limit);
            List<Map<String, Object>> Result = new ArrayList<>();
            for (Map<String, Object> Row : Raw) {
                Map<String, Object> M = new LinkedHashMap<>();
                M.put("from_operator", Row.getOrDefault("fromoperator", ""));
                M.put("to_operators", Row.getOrDefault("tooperators", ""));
                M.put("message", Row.getOrDefault("message", ""));
                M.put("timestamp", Row.getOrDefault("timestamp", ""));
                Result.add(M);
            }
            return Result;
        } catch (Exception E) {
            Logger.Error("GetChatLogs: " + E.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void Close() {
        try {
            if (Conn != null && !Conn.isClosed()) Conn.close();
        } catch (Exception Ignored) {}
    }

    private void exec(String Sql, Object... Params) {
        try (PreparedStatement Ps = Conn.prepareStatement(Sql)) {
            for (int I = 0; I < Params.length; I++) Ps.setObject(I + 1, Params[I]);
            Ps.executeUpdate();
        } catch (SQLException E) {
            Logger.Error("DB exec failed: " + E.getMessage());
        }
    }

    private List<Map<String, Object>> query(String Sql, Object... Params) {
        List<Map<String, Object>> Rows = new ArrayList<>();
        try (PreparedStatement Ps = Conn.prepareStatement(Sql)) {
            for (int I = 0; I < Params.length; I++) Ps.setObject(I + 1, Params[I]);
            try (ResultSet Rs = Ps.executeQuery()) {
                ResultSetMetaData Meta = Rs.getMetaData();
                int Cols = Meta.getColumnCount();
                while (Rs.next()) {
                    Map<String, Object> Row = new LinkedHashMap<>();
                    for (int I = 1; I <= Cols; I++) Row.put(Meta.getColumnName(I), Rs.getObject(I));
                    Rows.add(Row);
                }
            }
        } catch (SQLException E) {
            Logger.Error("DB query failed: " + E.getMessage());
        }
        return Rows;
    }

    private static String str(Map<String, Object> M, String K) {
        Object V = M.get(K);
        return V != null ? V.toString() : "";
    }
}
