package com.raven.interfaces.GUI.module.core.database;

import com.raven.core.db.TeamDatabase;
import com.raven.core.output.Logger;
import com.raven.utils.ServerConfig;

public class AuthService {

    private final TeamDatabase db;
    private String operatorName;
    private TeamDatabase.OperatorRole operatorRole;

    public AuthService(ServerConfig config) {
        this.db = TeamDatabase.Connect(config);
    }

    public boolean Authenticate(String username, String password) {
        if (db.ValidateOperator(username, TeamDatabase.HashPassword(password))) {
            operatorName = username;
            operatorRole = db.GetOperatorRole(username);
            Logger.Info("Operator login: " + operatorName + " [" + operatorRole + "]");
            return true;
        }
        return false;
    }

    public String                    GetOperatorName() { return operatorName; }
    public TeamDatabase.OperatorRole GetOperatorRole() { return operatorRole; }
    public TeamDatabase              GetDb()           { return db; }
}
