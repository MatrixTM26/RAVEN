package com.raven.core.database;

import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.raven.core.output.Logger;
import com.raven.utils.ServerConfig;
import java.util.*;
import org.bson.Document;

public final class MongoDatabase extends TeamDatabase {

    private final MongoClient Client;
    private final com.mongodb.client.MongoDatabase MongoDatabaseRef;
    private final MongoCollection<Document> ColLogs;
    private final MongoCollection<Document> ColCommands;
    private final MongoCollection<Document> ColSessions;
    private final MongoCollection<Document> ColNotes;
    private final MongoCollection<Document> ColOperators;
    private final MongoCollection<Document> ColChatLogs;

    public MongoDatabase(ServerConfig Config) throws Exception {
        String Url = Config.GetMongoUri();
        if (!Url.startsWith("mongodb://") && !Url.startsWith("mongodb+srv://")) {
            throw new Exception("Invalid MongoDB URL — must start with mongodb:// or mongodb+srv://");
        }
        try {
            Client = MongoClients.create(Url);
            MongoDatabaseRef = Client.getDatabase(Config.GetMongoDbName());
            ColLogs = MongoDatabaseRef.getCollection("tclogs");
            ColCommands = MongoDatabaseRef.getCollection("tccommands");
            ColSessions = MongoDatabaseRef.getCollection("tcsessions");
            ColNotes = MongoDatabaseRef.getCollection("tcnotes");
            ColOperators = MongoDatabaseRef.getCollection("tcoperators");
            ColChatLogs = MongoDatabaseRef.getCollection("tcchatlog");
            ColCommands.createIndex(Indexes.descending("createdat"));
            ColSessions.createIndex(Indexes.descending("createdat"));
            ColOperators.createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
            SeedDefaultAdmin(Config);
            Logger.Info("MongoDB connected: " + Url + "/" + Config.GetMongoDbName());
        } catch (Exception E) {
            throw new Exception("MongoDB connect failed: " + E.getMessage());
        }
    }

    private void SeedDefaultAdmin(ServerConfig Config) {
        try {
            String AdminUser = Config.GetAdminUsername();
            if (ColOperators.find(Filters.eq("username", AdminUser)).first() == null) {
                ColOperators.insertOne(
                    new Document()
                        .append("username", AdminUser)
                        .append("passwordhash", HashPassword(Config.GetAdminPassword()))
                        .append("role", OperatorRole.FromString(Config.GetAdminRole()).name())
                        .append("lastseen", "Never")
                        .append("createdat", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                );
            }
        } catch (Exception Ignored) {}
    }

    @Override
    public boolean IsConnected() {
        try {
            Client.listDatabaseNames().first();
            return true;
        } catch (Exception E) {
            return false;
        }
    }

    @Override
    public void SaveLog(String Entry) {
        try {
            ColLogs.insertOne(new Document("entry", Entry).append("createdat", new java.util.Date()));
        } catch (Exception E) {
            Logger.Error("Mongo SaveLog: " + E.getMessage());
        }
    }

    @Override
    public void SaveCommandLog(int AgentId, String Operator, String Command, String Output, boolean Success) {
        try {
            ColCommands.insertOne(new Document().append("agentid", AgentId).append("operator", Operator).append("command", Command).append("output", Output).append("success", Success).append("createdat", new java.util.Date()));
        } catch (Exception E) {
            Logger.Error("Mongo SaveCommandLog: " + E.getMessage());
        }
    }

    @Override
    public void SaveSessionEvent(Map<String, Object> Data, String Event) {
        try {
            Document Doc = new Document(Data);
            Doc.append("event", Event).append("createdat", new java.util.Date());
            ColSessions.insertOne(Doc);
        } catch (Exception E) {
            Logger.Error("Mongo SaveSessionEvent: " + E.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> GetCommandHistory(int AgentId, int Limit) {
        try {
            FindIterable<Document> Cur = AgentId == 0 ? ColCommands.find() : ColCommands.find(Filters.eq("agentid", AgentId));
            return DocToList(Cur.sort(Sorts.descending("createdat")).limit(Limit));
        } catch (Exception E) {
            Logger.Error("Mongo GetCommandHistory: " + E.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> GetSessionHistory(int Limit) {
        try {
            return DocToList(ColSessions.find().sort(Sorts.descending("createdat")).limit(Limit));
        } catch (Exception E) {
            Logger.Error("Mongo GetSessionHistory: " + E.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void SetAgentNote(int AgentId, String Note) {
        try {
            ColNotes.replaceOne(Filters.eq("agentid", AgentId), new Document("agentid", AgentId).append("note", Note).append("updatedat", new java.util.Date()), new ReplaceOptions().upsert(true));
        } catch (Exception E) {
            Logger.Error("Mongo SetAgentNote: " + E.getMessage());
        }
    }

    @Override
    public String GetAgentNote(int AgentId) {
        try {
            Document Doc = ColNotes.find(Filters.eq("agentid", AgentId)).first();
            if (Doc == null) return "";
            Object V = Doc.get("note");
            return V != null ? V.toString() : "";
        } catch (Exception E) {
            Logger.Error("Mongo GetAgentNote: " + E.getMessage());
            return "";
        }
    }

    @Override
    public boolean CreateOperator(String Username, String PasswordHash, OperatorRole Role) {
        try {
            ColOperators.insertOne(
                new Document()
                    .append("username", Username)
                    .append("passwordhash", PasswordHash)
                    .append("role", Role.name())
                    .append("lastseen", "Never")
                    .append("createdat", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            );
            return true;
        } catch (Exception E) {
            Logger.Error("Mongo CreateOperator: " + E.getMessage());
            return false;
        }
    }

    @Override
    public boolean ValidateOperator(String Username, String PasswordHash) {
        try {
            return ColOperators.find(Filters.and(Filters.eq("username", Username), Filters.eq("passwordhash", PasswordHash))).first() != null;
        } catch (Exception E) {
            return false;
        }
    }

    @Override
    public OperatorRole GetOperatorRole(String Username) {
        try {
            Document Doc = ColOperators.find(Filters.eq("username", Username)).first();
            if (Doc == null) return OperatorRole.MEMBER;
            return OperatorRole.FromString(Doc.getString("role"));
        } catch (Exception E) {
            return OperatorRole.MEMBER;
        }
    }

    @Override
    public List<Map<String, Object>> GetOperators() {
        try {
            List<Map<String, Object>> Result = new ArrayList<>();
            ColOperators.find().forEach(Doc -> {
                Map<String, Object> M = new LinkedHashMap<>();
                M.put("Username", Doc.getString("username"));
                M.put("Role", Doc.getString("role"));
                Object Ls = Doc.get("lastseen");
                M.put("LastSeen", Ls != null ? Ls.toString() : "Never");
                Object Ca = Doc.get("createdat");
                M.put("CreatedAt", Ca != null ? Ca.toString() : "");
                Result.add(M);
            });
            return Result;
        } catch (Exception E) {
            Logger.Error("Mongo GetOperators: " + E.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean UpdateOperatorRole(String Username, OperatorRole Role) {
        try {
            ColOperators.updateOne(Filters.eq("username", Username), Updates.set("role", Role.name()));
            return true;
        } catch (Exception E) {
            Logger.Error("Mongo UpdateOperatorRole: " + E.getMessage());
            return false;
        }
    }

    @Override
    public boolean UpdateOperatorPassword(String Username, String PasswordHash) {
        try {
            ColOperators.updateOne(Filters.eq("username", Username), Updates.set("passwordhash", PasswordHash));
            return true;
        } catch (Exception E) {
            Logger.Error("Mongo UpdateOperatorPassword: " + E.getMessage());
            return false;
        }
    }

    @Override
    public boolean DeleteOperator(String Username) {
        if ("admin".equalsIgnoreCase(Username)) return false;
        try {
            ColOperators.deleteOne(Filters.eq("username", Username));
            return true;
        } catch (Exception E) {
            Logger.Error("Mongo DeleteOperator: " + E.getMessage());
            return false;
        }
    }

    @Override
    public void UpdateLastSeen(String Username) {
        try {
            ColOperators.updateOne(Filters.eq("username", Username), Updates.set("lastseen", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        } catch (Exception E) {
            Logger.Error("Mongo UpdateLastSeen: " + E.getMessage());
        }
    }

    @Override
    public String GetLastSeen(String Username) {
        try {
            Document Doc = ColOperators.find(Filters.eq("username", Username)).first();
            if (Doc == null) return "Never";
            Object Val = Doc.get("lastseen");
            return Val != null ? Val.toString() : "Never";
        } catch (Exception E) {
            Logger.Error("Mongo GetLastSeen: " + E.getMessage());
            return "Never";
        }
    }

    @Override
    public void SaveChatLog(String FromOperator, String ToOperators, String Message) {
        try {
            ColChatLogs.insertOne(
                new Document()
                    .append("from_operator", FromOperator)
                    .append("to_operators", ToOperators)
                    .append("message", Message)
                    .append("timestamp", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            );
        } catch (Exception E) {
            Logger.Error("SaveChatLog: " + E.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> GetChatLogs(int Limit) {
        try {
            List<Map<String, Object>> Result = new ArrayList<>();
            ColChatLogs.find()
                .sort(new Document("timestamp", -1))
                .limit(Limit)
                .forEach(Doc -> {
                    Map<String, Object> M = new LinkedHashMap<>();
                    M.put("from_operator", Doc.getString("from_operator"));
                    M.put("to_operators", Doc.getString("to_operators"));
                    M.put("message", Doc.getString("message"));
                    M.put("timestamp", Doc.getString("timestamp"));
                    Result.add(M);
                });
            return Result;
        } catch (Exception E) {
            Logger.Error("GetChatLogs: " + E.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void Close() {
        try {
            Client.close();
        } catch (Exception Ignored) {}
    }

    private List<Map<String, Object>> DocToList(FindIterable<Document> Cursor) {
        List<Map<String, Object>> Result = new ArrayList<>();
        Cursor.forEach(Doc -> {
            Map<String, Object> R = new LinkedHashMap<>(Doc);
            R.remove("_id");
            Result.add(R);
        });
        return Result;
    }
}
