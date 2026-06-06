package com.tomcat.core.db;

import com.tomcat.core.output.Logger;
import com.tomcat.utils.ServerConfig;
import java.util.*;

public abstract class TeamDatabase {

    public static TeamDatabase Connect(ServerConfig Config) {
        String Type = Config.GetDbType().toLowerCase();
        try {
            return switch (Type) {
                case "postgresql", "postgres" -> new PostgresDatabase(Config);
                case "mongodb", "mongo" -> new MongoDatabase(Config);
                default -> {
                    Logger.Info("DB disabled — using in-memory store");
                    yield new MemoryDatabase();
                }
            };
        } catch (Exception E) {
            Logger.Warn("DB connection failed (" + Type + "): " + E.getMessage() + " — fallback to memory");
            return new MemoryDatabase();
        }
    }

    public abstract boolean IsConnected();

    public abstract void SaveLog(String Entry);

    public abstract void SaveCommandLog(int AgentId, String Operator, String Command, String Output, boolean Success);

    public abstract void SaveSessionEvent(Map<String, Object> Data, String Event);

    public abstract List<Map<String, Object>> GetCommandHistory(int AgentId, int Limit);

    public abstract List<Map<String, Object>> GetSessionHistory(int Limit);

    public abstract List<Map<String, Object>> GetOperators();

    public abstract void SetAgentNote(int AgentId, String Note);

    public abstract String GetAgentNote(int AgentId);

    public abstract void Close();
}
