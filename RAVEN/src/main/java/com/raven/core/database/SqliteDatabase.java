package com.raven.core.database;

import com.raven.core.output.Logger;
import com.raven.utils.RavenConstants;
import com.raven.utils.ServerConfig;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SqliteDatabase extends TeamDatabase {

    private final Connection Conn;
    private final String AdminUsername;
    private boolean Connected = false;

    public SqliteDatabase(ServerConfig Config) throws Exception {
        this.AdminUsername = Config.GetAdminUsername();
        String DbDir  = Config.GetDatabasePath();
        String DbFile = DbDir + "/raven.db";
        Files.createDirectories(Paths.get(DbDir));
        Class.forName("org.sqlite.JDBC");
        Conn = DriverManager.getConnection("jdbc:sqlite:" + DbFile);
        Conn.setAutoCommit(true);
        try (Statement Statement = Conn.createStatement()) {
            Statement.execute("PRAGMA journal_mode=WAL");
            Statement.execute("PRAGMA synchronous=NORMAL");
            Statement.execute("PRAGMA busy_timeout=5000");
        }
        Connected = true;
        CreateTables();
        Migrate();
        SeedAdmin(Config);
        Logger.Info("SQLite database: " + Paths.get(DbFile).toAbsolutePath());
    }

    private void CreateTables() throws Exception {
        try (Statement Statement = Conn.createStatement()) {
            Statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tclogs (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    entry     TEXT    NOT NULL,
                    createdat TEXT    NOT NULL
                )""");
            Statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tccommands (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    agentid   INTEGER NOT NULL,
                    operator  TEXT    NOT NULL,
                    command   TEXT    NOT NULL,
                    output    TEXT,
                    success   INTEGER NOT NULL DEFAULT 0,
                    timestamp TEXT    NOT NULL
                )""");
            Statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tcsessions (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    agentid   TEXT,
                    hostname  TEXT,
                    os        TEXT,
                    username  TEXT,
                    agentip   TEXT,
                    event     TEXT    NOT NULL,
                    timestamp TEXT    NOT NULL
                )""");
            Statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tcnotes (
                    agentid   INTEGER PRIMARY KEY,
                    note      TEXT    NOT NULL DEFAULT ''
                )""");
            Statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tcoperators (
                    username     TEXT PRIMARY KEY,
                    passwordhash TEXT NOT NULL,
                    role         TEXT NOT NULL DEFAULT 'MEMBER',
                    createdat    TEXT NOT NULL,
                    lastseen     TEXT NOT NULL DEFAULT 'Never'
                )""");
            Statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tcchatlog (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    fromoperator TEXT    NOT NULL,
                    tooperators  TEXT,
                    message      TEXT    NOT NULL,
                    timestamp    TEXT    NOT NULL
                )""");
        }
    }

    private void Migrate() {
        try (Statement Statement = Conn.createStatement()) {
            try { Statement.executeUpdate("ALTER TABLE tcoperators ADD COLUMN lastseen TEXT NOT NULL DEFAULT 'Never'"); }
            catch (Exception Ignored) {}
        } catch (Exception Ignored) {}
    }

    private void SeedAdmin(ServerConfig Config) throws Exception {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "INSERT OR IGNORE INTO tcoperators (username,passwordhash,role,createdat,lastseen) VALUES (?,?,?,?,?)")) {
            PreparedStatement.setString(1, Config.GetAdminUsername());
            PreparedStatement.setString(2, HashPassword(Config.GetAdminPassword()));
            PreparedStatement.setString(3, Config.GetAdminRole());
            PreparedStatement.setString(4, LocalDateTime.now().format(RavenConstants.TimestampFmt));
            PreparedStatement.setString(5, "Never");
            PreparedStatement.executeUpdate();
        }
    }

    @Override
    public boolean IsConnected() {
        try { return Connected && Conn != null && !Conn.isClosed(); }
        catch (Exception Exception) { return false; }
    }

    @Override
    public synchronized void SaveLog(String Entry) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "INSERT INTO tclogs (entry,createdat) VALUES (?,?)")) {
            PreparedStatement.setString(1, Entry);
            PreparedStatement.setString(2, LocalDateTime.now().format(RavenConstants.TimestampFmt));
            PreparedStatement.executeUpdate();
        } catch (Exception Exception) {
            Logger.Verbose("SQLite SaveLog: " + Exception.getMessage());
        }
    }

    @Override
    public synchronized void SaveCommandLog(int AgentId, String Operator, String Command, String Output, boolean Success) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "INSERT INTO tccommands (agentid,operator,command,output,success,timestamp) VALUES (?,?,?,?,?,?)")) {
            PreparedStatement.setInt(1, AgentId);
            PreparedStatement.setString(2, Operator);
            PreparedStatement.setString(3, Command);
            PreparedStatement.setString(4, Output);
            PreparedStatement.setInt(5, Success ? 1 : 0);
            PreparedStatement.setString(6, LocalDateTime.now().format(RavenConstants.TimestampFmt));
            PreparedStatement.executeUpdate();
        } catch (Exception Exception) {
            Logger.Verbose("SQLite SaveCommandLog: " + Exception.getMessage());
        }
    }

    @Override
    public synchronized void SaveSessionEvent(Map<String, Object> Data, String Event) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "INSERT INTO tcsessions (agentid,hostname,os,username,agentip,event,timestamp) VALUES (?,?,?,?,?,?,?)")) {
            PreparedStatement.setString(1, Str(Data, "ID"));
            PreparedStatement.setString(2, Str(Data, "Hostname"));
            PreparedStatement.setString(3, Str(Data, "OS"));
            PreparedStatement.setString(4, Str(Data, "User"));
            PreparedStatement.setString(5, Str(Data, "AgentIP"));
            PreparedStatement.setString(6, Event);
            PreparedStatement.setString(7, LocalDateTime.now().format(RavenConstants.TimestampFmt));
            PreparedStatement.executeUpdate();
        } catch (Exception Exception) {
            Logger.Verbose("SQLite SaveSessionEvent: " + Exception.getMessage());
        }
    }

    @Override
    public synchronized List<Map<String, Object>> GetCommandHistory(int AgentId, int Limit) {
        List<Map<String, Object>> List = new ArrayList<>();
        String Sql = AgentId == 0
            ? "SELECT * FROM tccommands ORDER BY id DESC LIMIT ?"
            : "SELECT * FROM tccommands WHERE agentid=? ORDER BY id DESC LIMIT ?";
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(Sql)) {
            if (AgentId == 0) { PreparedStatement.setInt(1, Limit); }
            else { PreparedStatement.setInt(1, AgentId); PreparedStatement.setInt(2, Limit); }
            ResultSet ResultSet = PreparedStatement.executeQuery();
            while (ResultSet.next()) {
                Map<String, Object> Row = new LinkedHashMap<>();
                Row.put("AgentId",   ResultSet.getInt("agentid"));
                Row.put("Operator",  ResultSet.getString("operator"));
                Row.put("Command",   ResultSet.getString("command"));
                Row.put("Output",    ResultSet.getString("output"));
                Row.put("Success",   ResultSet.getInt("success") == 1);
                Row.put("Timestamp", ResultSet.getString("timestamp"));
                List.add(Row);
            }
        } catch (Exception Exception) {
            Logger.Verbose("SQLite GetCommandHistory: " + Exception.getMessage());
        }
        return List;
    }

    @Override
    public synchronized List<Map<String, Object>> GetSessionHistory(int Limit) {
        List<Map<String, Object>> List = new ArrayList<>();
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "SELECT * FROM tcsessions ORDER BY id DESC LIMIT ?")) {
            PreparedStatement.setInt(1, Limit);
            ResultSet ResultSet = PreparedStatement.executeQuery();
            while (ResultSet.next()) {
                Map<String, Object> Row = new LinkedHashMap<>();
                Row.put("ID",        ResultSet.getString("agentid"));
                Row.put("Hostname",  ResultSet.getString("hostname"));
                Row.put("OS",        ResultSet.getString("os"));
                Row.put("User",      ResultSet.getString("username"));
                Row.put("AgentIP",   ResultSet.getString("agentip"));
                Row.put("Event",     ResultSet.getString("event"));
                Row.put("Timestamp", ResultSet.getString("timestamp"));
                List.add(Row);
            }
        } catch (Exception Exception) {
            Logger.Verbose("SQLite GetSessionHistory: " + Exception.getMessage());
        }
        return List;
    }

    @Override
    public synchronized void SetAgentNote(int AgentId, String Note) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "INSERT OR REPLACE INTO tcnotes (agentid,note) VALUES (?,?)")) {
            PreparedStatement.setInt(1, AgentId);
            PreparedStatement.setString(2, Note);
            PreparedStatement.executeUpdate();
        } catch (Exception Exception) {
            Logger.Verbose("SQLite SetAgentNote: " + Exception.getMessage());
        }
    }

    @Override
    public synchronized String GetAgentNote(int AgentId) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "SELECT note FROM tcnotes WHERE agentid=?")) {
            PreparedStatement.setInt(1, AgentId);
            ResultSet ResultSet = PreparedStatement.executeQuery();
            if (ResultSet.next()) return ResultSet.getString("note");
        } catch (Exception Exception) {
            Logger.Verbose("SQLite GetAgentNote: " + Exception.getMessage());
        }
        return "";
    }

    @Override
    public synchronized List<Map<String, Object>> GetAllAgentNotes() {
        List<Map<String, Object>> Result = new ArrayList<>();
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "SELECT agentid, note FROM tcnotes ORDER BY agentid")) {
            ResultSet ResultSet = PreparedStatement.executeQuery();
            while (ResultSet.next()) {
                Map<String, Object> Row = new LinkedHashMap<>();
                Row.put("AgentId", ResultSet.getInt("agentid"));
                Row.put("Note",    ResultSet.getString("note"));
                Result.add(Row);
            }
        } catch (Exception Exception) {
            Logger.Verbose("SQLite GetAllAgentNotes: " + Exception.getMessage());
        }
        return Result;
    }

    @Override
    public synchronized boolean CreateOperator(String Username, String PlaintextPassword, OperatorRole Role) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "INSERT OR IGNORE INTO tcoperators (username,passwordhash,role,createdat,lastseen) VALUES (?,?,?,?,?)")) {
            PreparedStatement.setString(1, Username);
            PreparedStatement.setString(2, HashPassword(PlaintextPassword));
            PreparedStatement.setString(3, Role.name());
            PreparedStatement.setString(4, LocalDateTime.now().format(RavenConstants.TimestampFmt));
            PreparedStatement.setString(5, "Never");
            return PreparedStatement.executeUpdate() > 0;
        } catch (Exception Exception) {
            Logger.Verbose("SQLite CreateOperator: " + Exception.getMessage());
            return false;
        }
    }

    @Override
    public synchronized boolean ValidateOperator(String Username, String PlaintextPassword) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "SELECT passwordhash FROM tcoperators WHERE username=?")) {
            PreparedStatement.setString(1, Username);
            ResultSet ResultSet = PreparedStatement.executeQuery();
            if (!ResultSet.next()) return false;
            return VerifyPassword(PlaintextPassword, ResultSet.getString("passwordhash"));
        } catch (Exception Exception) {
            return false;
        }
    }

    @Override
    public synchronized OperatorRole GetOperatorRole(String Username) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "SELECT role FROM tcoperators WHERE username=?")) {
            PreparedStatement.setString(1, Username);
            ResultSet ResultSet = PreparedStatement.executeQuery();
            if (ResultSet.next()) return OperatorRole.FromString(ResultSet.getString("role"));
        } catch (Exception Exception) {
            Logger.Verbose("SQLite GetOperatorRole: " + Exception.getMessage());
        }
        return OperatorRole.MEMBER;
    }

    @Override
    public synchronized List<Map<String, Object>> GetOperators() {
        List<Map<String, Object>> List = new ArrayList<>();
        try (Statement Statement = Conn.createStatement();
             ResultSet ResultSet = Statement.executeQuery(
                 "SELECT username,role,createdat,lastseen FROM tcoperators ORDER BY username")) {
            while (ResultSet.next()) {
                Map<String, Object> Row = new LinkedHashMap<>();
                Row.put("Username",  ResultSet.getString("username"));
                Row.put("Role",      ResultSet.getString("role"));
                Row.put("CreatedAt", ResultSet.getString("createdat"));
                String LastSeen = ResultSet.getString("lastseen");
                Row.put("LastSeen",  LastSeen != null ? LastSeen : "Never");
                List.add(Row);
            }
        } catch (Exception Exception) {
            Logger.Verbose("SQLite GetOperators: " + Exception.getMessage());
        }
        return List;
    }

    @Override
    public synchronized boolean UpdateOperatorRole(String Username, OperatorRole Role) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "UPDATE tcoperators SET role=? WHERE username=?")) {
            PreparedStatement.setString(1, Role.name());
            PreparedStatement.setString(2, Username);
            return PreparedStatement.executeUpdate() > 0;
        } catch (Exception Exception) {
            Logger.Verbose("SQLite UpdateOperatorRole: " + Exception.getMessage());
            return false;
        }
    }

    @Override
    public synchronized boolean UpdateOperatorPassword(String Username, String PlaintextPassword) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "UPDATE tcoperators SET passwordhash=? WHERE username=?")) {
            PreparedStatement.setString(1, HashPassword(PlaintextPassword));
            PreparedStatement.setString(2, Username);
            return PreparedStatement.executeUpdate() > 0;
        } catch (Exception Exception) {
            Logger.Verbose("SQLite UpdateOperatorPassword: " + Exception.getMessage());
            return false;
        }
    }

    @Override
    public synchronized boolean DeleteOperator(String Username) {
        if (AdminUsername.equalsIgnoreCase(Username)) return false;
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "DELETE FROM tcoperators WHERE username=?")) {
            PreparedStatement.setString(1, Username);
            return PreparedStatement.executeUpdate() > 0;
        } catch (Exception Exception) {
            Logger.Verbose("SQLite DeleteOperator: " + Exception.getMessage());
            return false;
        }
    }

    @Override
    public synchronized void UpdateLastSeen(String Username) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "UPDATE tcoperators SET lastseen=? WHERE username=?")) {
            PreparedStatement.setString(1, LocalDateTime.now().format(RavenConstants.TimestampFmt));
            PreparedStatement.setString(2, Username);
            PreparedStatement.executeUpdate();
        } catch (Exception Exception) {
            Logger.Verbose("SQLite UpdateLastSeen: " + Exception.getMessage());
        }
    }

    @Override
    public synchronized String GetLastSeen(String Username) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "SELECT lastseen FROM tcoperators WHERE username=?")) {
            PreparedStatement.setString(1, Username);
            ResultSet ResultSet = PreparedStatement.executeQuery();
            if (ResultSet.next()) {
                String LastSeen = ResultSet.getString("lastseen");
                return LastSeen != null ? LastSeen : "Never";
            }
        } catch (Exception Exception) {
            Logger.Verbose("SQLite GetLastSeen: " + Exception.getMessage());
        }
        return "Never";
    }

    @Override
    public synchronized void SaveChatLog(String FromOperator, String ToOperators, String Message) {
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "INSERT INTO tcchatlog (fromoperator,tooperators,message,timestamp) VALUES (?,?,?,?)")) {
            PreparedStatement.setString(1, FromOperator);
            PreparedStatement.setString(2, ToOperators);
            PreparedStatement.setString(3, Message);
            PreparedStatement.setString(4, LocalDateTime.now().format(RavenConstants.TimestampFmt));
            PreparedStatement.executeUpdate();
        } catch (Exception Exception) {
            Logger.Verbose("SQLite SaveChatLog: " + Exception.getMessage());
        }
    }

    @Override
    public synchronized List<Map<String, Object>> GetChatLogs(int Limit) {
        List<Map<String, Object>> List = new ArrayList<>();
        try (PreparedStatement PreparedStatement = Conn.prepareStatement(
                "SELECT * FROM tcchatlog ORDER BY id ASC LIMIT ?")) {
            PreparedStatement.setInt(1, Limit);
            ResultSet ResultSet = PreparedStatement.executeQuery();
            while (ResultSet.next()) {
                Map<String, Object> Row = new LinkedHashMap<>();
                Row.put("From",      ResultSet.getString("fromoperator"));
                Row.put("To",        ResultSet.getString("tooperators"));
                Row.put("Message",   ResultSet.getString("message"));
                Row.put("Timestamp", ResultSet.getString("timestamp"));
                List.add(Row);
            }
        } catch (Exception Exception) {
            Logger.Verbose("SQLite GetChatLogs: " + Exception.getMessage());
        }
        return List;
    }

    @Override
    public void Close() {
        try { if (Conn != null && !Conn.isClosed()) Conn.close(); }
        catch (Exception Ignored) {}
        Connected = false;
    }

    private static String Str(Map<String, Object> Map, String Key) {
        Object Value = Map.get(Key);
        return Value != null ? Value.toString() : "";
    }
}
