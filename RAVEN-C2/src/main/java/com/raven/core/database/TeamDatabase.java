package com.raven.core.database;

import com.raven.core.output.Logger;
import com.raven.utils.ServerConfig;
import java.util.List;
import java.util.Map;

public abstract class TeamDatabase {

    public enum OperatorRole {
        SUPER,
        ADMIN,
        OPERATOR,
        MEMBER;

        public boolean CanExecute() {
            return this == SUPER || this == ADMIN || this == OPERATOR;
        }

        public boolean CanWrite() {
            return this == SUPER || this == ADMIN;
        }

        public boolean CanRead() {
            return true;
        }

        public boolean CanBroadcast() {
            return this == SUPER || this == ADMIN || this == OPERATOR;
        }

        public boolean CanKillSession() {
            return this == SUPER || this == ADMIN;
        }

        public boolean CanManage() {
            return this == SUPER || this == ADMIN;
        }

        public boolean CanKickOperator() {
            return this == SUPER;
        }

        public boolean CanDeleteOperator() {
            return this == SUPER;
        }

        public boolean CanPromote() {
            return this == SUPER;
        }

        public boolean IsSuperAdmin() {
            return this == SUPER;
        }

        public String PermissionString() {
            return switch (this) {
                case SUPER -> "RWXK";
                case ADMIN -> "RWX";
                case OPERATOR -> "RX";
                case MEMBER -> "R";
            };
        }

        public String ShortPerm() {
            return switch (this) {
                case SUPER -> "RWXK";
                case ADMIN -> "RWX";
                case OPERATOR -> "RX";
                case MEMBER -> "R";
            };
        }

        public static OperatorRole FromString(String S) {
            if (S == null) return MEMBER;
            return switch (S.trim().toUpperCase()) {
                case "SUPER", "SUPER_ADMIN", "SUPERADMIN" -> SUPER;
                case "ADMIN" -> ADMIN;
                case "OPERATOR" -> OPERATOR;
                default -> MEMBER;
            };
        }
    }

    public static TeamDatabase Connect(ServerConfig Config) {
        String Type = Config.GetDatabaseType().toLowerCase();
        try {
            return switch (Type) {
                case "postgresql", "postgres" -> new PostgresDatabase(Config);
                case "mongodb", "mongo" -> new MongoDatabase(Config);
                case "sqlite" -> new SqliteDatabase(Config);
                default -> {
                    Logger.Info("DB disabled — using in-memory store");
                    yield new MemoryDatabase(Config);
                }
            };
        } catch (Exception E) {
            Logger.Warn("DB connection failed (" + Type + "): " + E.getMessage() + " — fallback to memory");
            return new MemoryDatabase(Config);
        }
    }

    public abstract boolean IsConnected();

    public abstract void SaveLog(String Entry);

    public abstract void SaveCommandLog(int AgentId, String Operator, String Command, String Output, boolean Success);

    public abstract void SaveChatLog(String FromOperator, String ToOperators, String Message);

    public abstract List<Map<String, Object>> GetChatLogs(int Limit);

    public abstract void SaveSessionEvent(Map<String, Object> Data, String Event);

    public abstract List<Map<String, Object>> GetCommandHistory(int AgentId, int Limit);

    public abstract List<Map<String, Object>> GetSessionHistory(int Limit);

    public abstract void SetAgentNote(int AgentId, String Note);

    public abstract String GetAgentNote(int AgentId);

    public abstract boolean CreateOperator(String Username, String PasswordHash, OperatorRole Role);

    public abstract boolean ValidateOperator(String Username, String PasswordHash);

    public abstract OperatorRole GetOperatorRole(String Username);

    public abstract List<Map<String, Object>> GetOperators();

    public abstract boolean UpdateOperatorRole(String Username, OperatorRole Role);

    public abstract boolean UpdateOperatorPassword(String Username, String PasswordHash);

    public abstract boolean DeleteOperator(String Username);

    public abstract void UpdateLastSeen(String Username);

    public abstract String GetLastSeen(String Username);

    public abstract void Close();

    public static String HashPassword(String Password) {
        try {
            java.security.MessageDigest Md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] Hash = Md.digest((Password + "RAVEN-SALT").getBytes("UTF-8"));
            StringBuilder Hex = new StringBuilder();
            for (byte B : Hash) Hex.append(String.format("%02x", B));
            return Hex.toString();
        } catch (Exception E) {
            throw new RuntimeException("Hash failed: " + E.getMessage());
        }
    }
}
