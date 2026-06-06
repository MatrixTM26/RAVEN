package com.tomcat.core.db;

import com.google.gson.Gson;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.tomcat.core.output.Logger;
import com.tomcat.utils.ServerConfig;
import java.util.*;
import org.bson.Document;

public final class MongoDatabase extends TeamDatabase {

    private static final Gson GsonInst = new Gson();

    private final MongoClient Client;
    private final com.mongodb.client.MongoDatabase MongoDatabaseRef;

    private final MongoCollection<Document> ColLogs;
    private final MongoCollection<Document> ColCommands;
    private final MongoCollection<Document> ColSessions;
    private final MongoCollection<Document> ColNotes;

    public MongoDatabase(ServerConfig Config) throws Exception {
        try {
            Client = MongoClients.create(Config.GetDbUrl());
            MongoDatabaseRef = Client.getDatabase(Config.GetDbName());
            ColLogs = MongoDatabaseRef.getCollection("tc2_logs");
            ColCommands = MongoDatabaseRef.getCollection("tc2_commands");
            ColSessions = MongoDatabaseRef.getCollection("tc2_sessions");
            ColNotes = MongoDatabaseRef.getCollection("tc2_notes");
            ColCommands.createIndex(Indexes.descending("created"));
            ColSessions.createIndex(Indexes.descending("created"));
            Logger.Info("MongoDB connected: " + Config.GetDbUrl() + "/" + Config.GetDbName());
        } catch (Exception E) {
            throw new Exception("MongoDB connect failed: " + E.getMessage());
        }
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
            ColLogs.insertOne(new Document("entry", Entry).append("created", new java.util.Date()));
        } catch (Exception E) {
            Logger.Error("Mongo SaveLog: " + E.getMessage());
        }
    }

    @Override
    public void SaveCommandLog(int AgentId, String Operator, String Command, String Output, boolean Success) {
        try {
            ColCommands.insertOne(
                new Document()
                    .append("agent_id", AgentId)
                    .append("operator", Operator)
                    .append("command", Command)
                    .append("output", Output)
                    .append("success", Success)
                    .append("created", new java.util.Date())
            );
        } catch (Exception E) {
            Logger.Error("Mongo SaveCommandLog: " + E.getMessage());
        }
    }

    @Override
    public void SaveSessionEvent(Map<String, Object> Data, String Event) {
        try {
            Document Doc = new Document(Data);
            Doc.append("event", Event);
            Doc.append("created", new java.util.Date());
            ColSessions.insertOne(Doc);
        } catch (Exception E) {
            Logger.Error("Mongo SaveSessionEvent: " + E.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> GetCommandHistory(int AgentId, int Limit) {
        try {
            FindIterable<Document> Cursor = AgentId == 0
                ? ColCommands.find()
                : ColCommands.find(Filters.eq("agent_id", AgentId));
            return DocToList(Cursor.sort(Sorts.descending("created")).limit(Limit));
        } catch (Exception E) {
            Logger.Error("Mongo GetCommandHistory: " + E.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> GetSessionHistory(int Limit) {
        try {
            return DocToList(ColSessions.find().sort(Sorts.descending("created")).limit(Limit));
        } catch (Exception E) {
            Logger.Error("Mongo GetSessionHistory: " + E.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> GetOperators() {
        try {
            List<Map<String, Object>> Result = new ArrayList<>();
            ColCommands.distinct("operator", String.class).forEach(Op -> Result.add(Map.of("Name", (Object) Op)));
            return Result;
        } catch (Exception E) {
            Logger.Error("Mongo GetOperators: " + E.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void SetAgentNote(int AgentId, String Note) {
        try {
            ColNotes.replaceOne(
                Filters.eq("agent_id", AgentId),
                new Document("agent_id", AgentId).append("note", Note).append("updated", new java.util.Date()),
                new ReplaceOptions().upsert(true)
            );
        } catch (Exception E) {
            Logger.Error("Mongo SetAgentNote: " + E.getMessage());
        }
    }

    @Override
    public String GetAgentNote(int AgentId) {
        try {
            Document Doc = ColNotes.find(Filters.eq("agent_id", AgentId)).first();
            if (Doc == null) return "";
            Object V = Doc.get("note");
            return V != null ? V.toString() : "";
        } catch (Exception E) {
            Logger.Error("Mongo GetAgentNote: " + E.getMessage());
            return "";
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
            Map<String, Object> Row = new LinkedHashMap<>(Doc);
            Row.remove("_id");
            Result.add(Row);
        });
        return Result;
    }
}
