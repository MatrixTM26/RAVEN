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

    private static volatile TeamDatabase MemoryInstance;

    public static TeamDatabase Connect(ServerConfig Config) {
        String Type = Config.GetDatabaseType().toLowerCase();
        try {
            return switch (Type) {
                case "postgresql", "postgres" -> new PostgresDatabase(Config);
                case "mongodb", "mongo"       -> new MongoDatabase(Config);
                case "sqlite"                 -> new SqliteDatabase(Config);
                default                       -> GetSharedMemoryInstance(Config);
            };
        } catch (Exception Exception) {
            Logger.Warn("DB connection failed (" + Type + "): " + Exception.getMessage() + " — fallback to memory");
            return GetSharedMemoryInstance(Config);
        }
    }

    private static synchronized TeamDatabase GetSharedMemoryInstance(ServerConfig Config) {
        if (MemoryInstance == null) {
            Logger.Info("DB disabled — using in-memory store");
            MemoryInstance = new MemoryDatabase(Config);
        }
        return MemoryInstance;
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

    public abstract List<Map<String, Object>> GetAllAgentNotes();

    public abstract boolean CreateOperator(String Username, String PlaintextPassword, OperatorRole Role);

    public abstract boolean ValidateOperator(String Username, String PlaintextPassword);

    public abstract OperatorRole GetOperatorRole(String Username);

    public abstract List<Map<String, Object>> GetOperators();

    public abstract boolean UpdateOperatorRole(String Username, OperatorRole Role);

    public abstract boolean UpdateOperatorPassword(String Username, String PlaintextPassword);

    public abstract boolean DeleteOperator(String Username);

    public abstract void UpdateLastSeen(String Username);

    public abstract String GetLastSeen(String Username);

    public abstract void Close();

    public static String HashPassword(String Password) {
        try {
            byte[] Salt = new byte[16];
            new java.security.SecureRandom().nextBytes(Salt);
            javax.crypto.spec.PBEKeySpec Spec = new javax.crypto.spec.PBEKeySpec(Password.toCharArray(), Salt, 310_000, 256);
            javax.crypto.SecretKeyFactory Factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] Hash = Factory.generateSecret(Spec).getEncoded();
            return java.util.Base64.getEncoder().encodeToString(Salt) + ":" + java.util.Base64.getEncoder().encodeToString(Hash);
        } catch (Exception E) {
            throw new RuntimeException("Hash failed: " + E.getMessage());
        }
    }

    public static boolean VerifyPassword(String Password, String Stored) {
        try {
            String[] Parts = Stored.split(":", 2);
            if (Parts.length != 2) return false;
            byte[] Salt = java.util.Base64.getDecoder().decode(Parts[0]);
            byte[] ExpectedHash = java.util.Base64.getDecoder().decode(Parts[1]);
            javax.crypto.spec.PBEKeySpec Spec = new javax.crypto.spec.PBEKeySpec(Password.toCharArray(), Salt, 310_000, 256);
            javax.crypto.SecretKeyFactory Factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] ActualHash = Factory.generateSecret(Spec).getEncoded();
            return java.util.Arrays.equals(ExpectedHash, ActualHash);
        } catch (Exception E) {
            return false;
        }
    }
}
