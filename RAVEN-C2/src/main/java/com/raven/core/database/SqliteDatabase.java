package com.raven.core.database;

import com.raven.core.output.Logger;
import com.raven.utils.ServerConfig;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class SqliteDatabase extends TeamDatabase {

    private static final DateTimeFormatter Fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Connection Conn;
    private final ServerConfig Config;
    private boolean Connected = false;

    public SqliteDatabase(ServerConfig Config) throws Exception {
        this.Config = Config;
        String DbDir = Config.GetDatabasePath();
        String DbFile = DbDir + "/raven.db";
        Files.createDirectories(Paths.get(DbDir));
        Class.forName("org.sqlite.JDBC");
        Conn = DriverManager.getConnection("jdbc:sqlite:" + DbFile);
        Connected = true;
        CreateTables();
        Migrate();
        SeedAdmin();
        Logger.Info("SQLite database: " + Paths.get(DbFile).toAbsolutePath());
    }

    private void CreateTables() throws Exception {
        try (Statement St = Conn.createStatement()) {
            St.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS tclogs (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    entry     TEXT    NOT NULL,
                    createdat TEXT    NOT NULL
                )"""
            );
            St.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS tccommands (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    agentid   INTEGER NOT NULL,
                    operator  TEXT    NOT NULL,
                    command   TEXT    NOT NULL,
                    output    TEXT,
                    success   INTEGER NOT NULL DEFAULT 0,
                    timestamp TEXT    NOT NULL
                )"""
            );
            St.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS tcsessions (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    agentid   TEXT,
                    hostname  TEXT,
                    os        TEXT,
                    username  TEXT,
                    agentip   TEXT,
                    event     TEXT    NOT NULL,
                    timestamp TEXT    NOT NULL
                )"""
            );
            St.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS tcnotes (
                    agentid   INTEGER PRIMARY KEY,
                    note      TEXT    NOT NULL DEFAULT ''
                )"""
            );
            St.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS tcoperators (
                    username     TEXT PRIMARY KEY,
                    passwordhash TEXT NOT NULL,
                    role         TEXT NOT NULL DEFAULT 'MEMBER',
                    createdat    TEXT NOT NULL,
                    lastseen     TEXT NOT NULL DEFAULT 'Never'
                )"""
            );
            St.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS tcchatlog (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    fromoperator TEXT    NOT NULL,
                    tooperators  TEXT,
                    message      TEXT    NOT NULL,
                    timestamp    TEXT    NOT NULL
                )"""
            );
        }
    }

    private void Migrate() {
        try (Statement St = Conn.createStatement()) {
            try {
                St.executeUpdate("ALTER TABLE tcoperators ADD COLUMN lastseen TEXT NOT NULL DEFAULT 'Never'");
            } catch (Exception Ignored) {}
        } catch (Exception Ignored) {}
    }

    private void SeedAdmin() throws Exception {
        String AdminUser = Config.GetAdminUsername();
        String AdminPass = Config.GetAdminPassword();
        String AdminRole = Config.GetAdminRole();
        try (PreparedStatement Ps = Conn.prepareStatement("INSERT OR IGNORE INTO tcoperators (username,passwordhash,role,createdat,lastseen) VALUES (?,?,?,?,?)")) {
            Ps.setString(1, AdminUser);
            Ps.setString(2, HashPassword(AdminPass));
            Ps.setString(3, AdminRole);
            Ps.setString(4, LocalDateTime.now().format(Fmt));
            Ps.setString(5, "Never");
            Ps.executeUpdate();
        }
    }

    @Override
    public boolean IsConnected() {
        try {
            return Connected && Conn != null && !Conn.isClosed();
        } catch (Exception E) {
            return false;
        }
    }

    @Override
    public void SaveLog(String Entry) {
        try (PreparedStatement Ps = Conn.prepareStatement("INSERT INTO tclogs (entry,createdat) VALUES (?,?)")) {
            Ps.setString(1, Entry);
            Ps.setString(2, LocalDateTime.now().format(Fmt));
            Ps.executeUpdate();
        } catch (Exception E) {
            Logger.Verbose("SQLite SaveLog: " + E.getMessage());
        }
    }

    @Override
    public void SaveCommandLog(int AgentId, String Operator, String Command, String Output, boolean Success) {
        try (PreparedStatement Ps = Conn.prepareStatement("INSERT INTO tccommands (agentid,operator,command,output,success,timestamp) VALUES (?,?,?,?,?,?)")) {
            Ps.setInt(1, AgentId);
            Ps.setString(2, Operator);
            Ps.setString(3, Command);
            Ps.setString(4, Output);
            Ps.setInt(5, Success ? 1 : 0);
            Ps.setString(6, LocalDateTime.now().format(Fmt));
            Ps.executeUpdate();
        } catch (Exception E) {
            Logger.Verbose("SQLite SaveCommandLog: " + E.getMessage());
        }
    }

    @Override
    public void SaveSessionEvent(Map<String, Object> Data, String Event) {
        try (PreparedStatement Ps = Conn.prepareStatement("INSERT INTO tcsessions (agentid,hostname,os,username,agentip,event,timestamp) VALUES (?,?,?,?,?,?,?)")) {
            Ps.setString(1, Str(Data, "ID"));
            Ps.setString(2, Str(Data, "Hostname"));
            Ps.setString(3, Str(Data, "OS"));
            Ps.setString(4, Str(Data, "User"));
            Ps.setString(5, Str(Data, "AgentIP"));
            Ps.setString(6, Event);
            Ps.setString(7, LocalDateTime.now().format(Fmt));
            Ps.executeUpdate();
        } catch (Exception E) {
            Logger.Verbose("SQLite SaveSessionEvent: " + E.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> GetCommandHistory(int AgentId, int Limit) {
        List<Map<String, Object>> List = new ArrayList<>();
        String Sql = AgentId == 0 ? "SELECT * FROM tccommands ORDER BY id DESC LIMIT ?" : "SELECT * FROM tccommands WHERE agentid=? ORDER BY id DESC LIMIT ?";
        try (PreparedStatement Ps = Conn.prepareStatement(Sql)) {
            if (AgentId == 0) {
                Ps.setInt(1, Limit);
            } else {
                Ps.setInt(1, AgentId);
                Ps.setInt(2, Limit);
            }
            ResultSet Rs = Ps.executeQuery();
            while (Rs.next()) {
                Map<String, Object> Row = new LinkedHashMap<>();
                Row.put("AgentId", Rs.getInt("agentid"));
                Row.put("Operator", Rs.getString("operator"));
                Row.put("Command", Rs.getString("command"));
                Row.put("Output", Rs.getString("output"));
                Row.put("Success", Rs.getInt("success") == 1);
                Row.put("Timestamp", Rs.getString("timestamp"));
                List.add(Row);
            }
        } catch (Exception E) {
            Logger.Verbose("SQLite GetCommandHistory: " + E.getMessage());
        }
        return List;
    }

    @Override
    public List<Map<String, Object>> GetSessionHistory(int Limit) {
        List<Map<String, Object>> List = new ArrayList<>();
        try (PreparedStatement Ps = Conn.prepareStatement("SELECT * FROM tcsessions ORDER BY id DESC LIMIT ?")) {
            Ps.setInt(1, Limit);
            ResultSet Rs = Ps.executeQuery();
            while (Rs.next()) {
                Map<String, Object> Row = new LinkedHashMap<>();
                Row.put("ID", Rs.getString("agentid"));
                Row.put("Hostname", Rs.getString("hostname"));
                Row.put("OS", Rs.getString("os"));
                Row.put("User", Rs.getString("username"));
                Row.put("AgentIP", Rs.getString("agentip"));
                Row.put("Event", Rs.getString("event"));
                Row.put("Timestamp", Rs.getString("timestamp"));
                List.add(Row);
            }
        } catch (Exception E) {
            Logger.Verbose("SQLite GetSessionHistory: " + E.getMessage());
        }
        return List;
    }

    @Override
    public void SetAgentNote(int AgentId, String Note) {
        try (PreparedStatement Ps = Conn.prepareStatement("INSERT OR REPLACE INTO tcnotes (agentid,note) VALUES (?,?)")) {
            Ps.setInt(1, AgentId);
            Ps.setString(2, Note);
            Ps.executeUpdate();
        } catch (Exception E) {
            Logger.Verbose("SQLite SetAgentNote: " + E.getMessage());
        }
    }

    @Override
    public String GetAgentNote(int AgentId) {
        try (PreparedStatement Ps = Conn.prepareStatement("SELECT note FROM tcnotes WHERE agentid=?")) {
            Ps.setInt(1, AgentId);
            ResultSet Rs = Ps.executeQuery();
            if (Rs.next()) return Rs.getString("note");
        } catch (Exception E) {
            Logger.Verbose("SQLite GetAgentNote: " + E.getMessage());
        }
        return "";
    }

    @Override
    public boolean CreateOperator(String Username, String PasswordHash, OperatorRole Role) {
        try (PreparedStatement Ps = Conn.prepareStatement("INSERT OR IGNORE INTO tcoperators (username,passwordhash,role,createdat,lastseen) VALUES (?,?,?,?,?)")) {
            Ps.setString(1, Username);
            Ps.setString(2, PasswordHash);
            Ps.setString(3, Role.name());
            Ps.setString(4, LocalDateTime.now().format(Fmt));
            Ps.setString(5, "Never");
            return Ps.executeUpdate() > 0;
        } catch (Exception E) {
            Logger.Verbose("SQLite CreateOperator: " + E.getMessage());
            return false;
        }
    }

    @Override
    public boolean ValidateOperator(String Username, String PasswordHash) {
        try (PreparedStatement Ps = Conn.prepareStatement("SELECT 1 FROM tcoperators WHERE username=? AND passwordhash=?")) {
            Ps.setString(1, Username);
            Ps.setString(2, PasswordHash);
            return Ps.executeQuery().next();
        } catch (Exception E) {
            return false;
        }
    }

    @Override
    public OperatorRole GetOperatorRole(String Username) {
        try (PreparedStatement Ps = Conn.prepareStatement("SELECT role FROM tcoperators WHERE username=?")) {
            Ps.setString(1, Username);
            ResultSet Rs = Ps.executeQuery();
            if (Rs.next()) return OperatorRole.FromString(Rs.getString("role"));
        } catch (Exception E) {
            Logger.Verbose("SQLite GetOperatorRole: " + E.getMessage());
        }
        return OperatorRole.MEMBER;
    }

    @Override
    public List<Map<String, Object>> GetOperators() {
        List<Map<String, Object>> List = new ArrayList<>();
        try (Statement St = Conn.createStatement(); ResultSet Rs = St.executeQuery("SELECT username,role,createdat,lastseen FROM tcoperators ORDER BY username")) {
            while (Rs.next()) {
                Map<String, Object> Row = new LinkedHashMap<>();
                Row.put("Username", Rs.getString("username"));
                Row.put("Role", Rs.getString("role"));
                Row.put("CreatedAt", Rs.getString("createdat"));
                Row.put("LastSeen", Rs.getString("lastseen") != null ? Rs.getString("lastseen") : "Never");
                List.add(Row);
            }
        } catch (Exception E) {
            Logger.Verbose("SQLite GetOperators: " + E.getMessage());
        }
        return List;
    }

    @Override
    public boolean UpdateOperatorRole(String Username, OperatorRole Role) {
        try (PreparedStatement Ps = Conn.prepareStatement("UPDATE tcoperators SET role=? WHERE username=?")) {
            Ps.setString(1, Role.name());
            Ps.setString(2, Username);
            return Ps.executeUpdate() > 0;
        } catch (Exception E) {
            Logger.Verbose("SQLite UpdateOperatorRole: " + E.getMessage());
            return false;
        }
    }

    @Override
    public boolean UpdateOperatorPassword(String Username, String PasswordHash) {
        try (PreparedStatement Ps = Conn.prepareStatement("UPDATE tcoperators SET passwordhash=? WHERE username=?")) {
            Ps.setString(1, PasswordHash);
            Ps.setString(2, Username);
            return Ps.executeUpdate() > 0;
        } catch (Exception E) {
            Logger.Verbose("SQLite UpdateOperatorPassword: " + E.getMessage());
            return false;
        }
    }

    @Override
    public boolean DeleteOperator(String Username) {
        if ("admin".equalsIgnoreCase(Username)) return false;
        try (PreparedStatement Ps = Conn.prepareStatement("DELETE FROM tcoperators WHERE username=?")) {
            Ps.setString(1, Username);
            return Ps.executeUpdate() > 0;
        } catch (Exception E) {
            Logger.Verbose("SQLite DeleteOperator: " + E.getMessage());
            return false;
        }
    }

    @Override
    public void UpdateLastSeen(String Username) {
        try (PreparedStatement Ps = Conn.prepareStatement("UPDATE tcoperators SET lastseen=? WHERE username=?")) {
            Ps.setString(1, LocalDateTime.now().format(Fmt));
            Ps.setString(2, Username);
            Ps.executeUpdate();
        } catch (Exception E) {
            Logger.Verbose("SQLite UpdateLastSeen: " + E.getMessage());
        }
    }

    @Override
    public String GetLastSeen(String Username) {
        try (PreparedStatement Ps = Conn.prepareStatement("SELECT lastseen FROM tcoperators WHERE username=?")) {
            Ps.setString(1, Username);
            ResultSet Rs = Ps.executeQuery();
            if (Rs.next()) return Rs.getString("lastseen") != null ? Rs.getString("lastseen") : "Never";
        } catch (Exception E) {
            Logger.Verbose("SQLite GetLastSeen: " + E.getMessage());
        }
        return "Never";
    }

    @Override
    public void SaveChatLog(String FromOperator, String ToOperators, String Message) {
        try (PreparedStatement Ps = Conn.prepareStatement("INSERT INTO tcchatlog (fromoperator,tooperators,message,timestamp) VALUES (?,?,?,?)")) {
            Ps.setString(1, FromOperator);
            Ps.setString(2, ToOperators);
            Ps.setString(3, Message);
            Ps.setString(4, LocalDateTime.now().format(Fmt));
            Ps.executeUpdate();
        } catch (Exception E) {
            Logger.Verbose("SQLite SaveChatLog: " + E.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> GetChatLogs(int Limit) {
        List<Map<String, Object>> List = new ArrayList<>();
        try (PreparedStatement Ps = Conn.prepareStatement("SELECT * FROM tcchatlog ORDER BY id DESC LIMIT ?")) {
            Ps.setInt(1, Limit);
            ResultSet Rs = Ps.executeQuery();
            while (Rs.next()) {
                Map<String, Object> Row = new LinkedHashMap<>();
                Row.put("from_operator", Rs.getString("fromoperator"));
                Row.put("to_operators", Rs.getString("tooperators"));
                Row.put("message", Rs.getString("message"));
                Row.put("timestamp", Rs.getString("timestamp"));
                List.add(Row);
            }
        } catch (Exception E) {
            Logger.Verbose("SQLite GetChatLogs: " + E.getMessage());
        }
        return List;
    }

    @Override
    public void Close() {
        try {
            if (Conn != null && !Conn.isClosed()) Conn.close();
        } catch (Exception Ignored) {}
        Connected = false;
    }

    private static String Str(Map<String, Object> M, String K) {
        Object V = M.get(K);
        return V != null ? V.toString() : "";
    }
}
