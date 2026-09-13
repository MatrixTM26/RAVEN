package com.raven.interfaces.GUI.module.core.database;

import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.output.Logger;
import com.raven.utils.ServerConfig;

public class AuthService {

    private final TeamDatabase Database;
    private String OperatorName;
    private OperatorRole CurrentRole;

    public AuthService(ServerConfig Config) {
        this.Database = TeamDatabase.Connect(Config);
    }

    public boolean Authenticate(String Username, String Password) {
        if (Database.ValidateOperator(Username, Password)) {
            OperatorName = Username;
            CurrentRole  = Database.GetOperatorRole(Username);
            Database.UpdateLastSeen(Username);
            Logger.Info("Operator login: " + OperatorName + " [" + CurrentRole + "]");
            return true;
        }
        return false;
    }

    public String        GetOperatorName() { return OperatorName; }
    public OperatorRole  GetOperatorRole() { return CurrentRole; }
    public TeamDatabase  GetDb()           { return Database; }
}
