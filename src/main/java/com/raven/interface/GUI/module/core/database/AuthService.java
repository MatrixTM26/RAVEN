package com.raven.interfaces.GUI.module.core.database;

import com.raven.core.db.TeamDatabase;
import com.raven.core.output.Logger;
import com.raven.utils.ServerConfig;

public class AuthService {

    private final TeamDatabase Db;
    private String OperatorName;
    private TeamDatabase.OperatorRole OperatorRole;

    public AuthService(ServerConfig Config) {
        this.Db = TeamDatabase.Connect(Config);
    }

    public boolean Authenticate(String Username, String Password) {
        if (Db.ValidateOperator(Username, TeamDatabase.HashPassword(Password))) {
            OperatorName = Username;
            OperatorRole = Db.GetOperatorRole(Username);
            Logger.Info("GUI operator login: " + OperatorName + " [" + OperatorRole + "]");
            return true;
        }
        return false;
    }

    public String GetOperatorName() { return OperatorName; }
    public TeamDatabase.OperatorRole GetOperatorRole() { return OperatorRole; }
    public TeamDatabase GetDb() { return Db; }
}
