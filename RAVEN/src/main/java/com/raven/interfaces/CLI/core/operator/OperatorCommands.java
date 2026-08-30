package com.raven.interfaces.CLI.core.operator;

import com.raven.utils.TerminalHelper;
import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.output.Logger;
import com.raven.interfaces.banner.CLIBanner;
import com.raven.utils.AnsiColor;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class OperatorCommands {

    private final TeamDatabase Database;

    private String OperatorName;
    private OperatorRole OperatorRoleValue;
    private boolean IsTeamServerMode;

    public OperatorCommands(TeamDatabase Database) {
        this.Database = Database;
    }

    public String GetOperatorName() {
        return OperatorName;
    }

    public OperatorRole GetOperatorRoleValue() {
        return OperatorRoleValue;
    }

    public void SetTeamServerMode(boolean IsTeamServerMode) {
        this.IsTeamServerMode = IsTeamServerMode;
    }

    public boolean CanManage() {
        return !IsTeamServerMode || (OperatorRoleValue != null && OperatorRoleValue.CanManage());
    }

    public boolean CanKick() {
        return !IsTeamServerMode || (OperatorRoleValue != null && OperatorRoleValue.CanKickOperator());
    }

    public boolean CanExecute() {
        return !IsTeamServerMode || (OperatorRoleValue != null && OperatorRoleValue.CanExecute());
    }

    public boolean Login(BufferedReader Reader) throws IOException {
        System.out.println(TerminalHelper.Box("TEAMSERVER LOGIN"));
        System.out.println();
        Logger.Custom("  %sDefault credentials: admin / admin (change after first login)%s%n%n", AnsiColor.White, AnsiColor.Reset);

        for (int Attempt = 1; Attempt <= 3; Attempt++) {
            Logger.Custom("  %sUsername:%s ", AnsiColor.Red, AnsiColor.Reset);
            System.out.flush();
            String Username = Reader.readLine();
            if (Username == null) return false;
            Username = Username.trim();

            Logger.Custom("  %sPassword:%s ", AnsiColor.Red, AnsiColor.Reset);
            System.out.flush();
            String Password;
            Console SystemConsole = System.console();
            if (SystemConsole != null) {
                char[] PasswordChars = SystemConsole.readPassword();
                Password = PasswordChars != null ? new String(PasswordChars) : "";
            } else {
                Password = Reader.readLine();
            }
            if (Password == null) return false;

            if (!Database.ValidateOperator(Username, Password)) {
                Logger.Custom("  %sInvalid credentials - Attempt %d/3%s%n%n", AnsiColor.Red, Attempt, AnsiColor.Reset);
                continue;
            }

            OperatorName = Username;
            OperatorRoleValue = Database.GetOperatorRole(Username);
            Database.UpdateLastSeen(Username);
            Logger.Info("Operator login: " + Username + " [" + OperatorRoleValue + "]");
            Logger.Custom("  %n%sWelcome, %s [%s]%s%n", AnsiColor.Green, Username, OperatorRoleValue, AnsiColor.Reset);
            Logger.Custom("  %sPermissions:%s %s%n%n", AnsiColor.Red, AnsiColor.White, OperatorRoleValue.PermissionString());
            return true;
        }

        Logger.Error("authentication failed - exit.");
        return false;
    }

    public void ShowHelp() {
        System.out.println(TerminalHelper.Box("COMMAND REFERENCE"));
        System.out.println();
        CLIBanner.Print();
        if (IsTeamServerMode && OperatorName != null) {
            System.out.println();
            Logger.Custom("  %s[TEAMSERVER MODE]%s  Operator: %s%s%s  Role: %s%s%s%n", AnsiColor.Red, AnsiColor.Reset, AnsiColor.White, OperatorName, AnsiColor.Reset, AnsiColor.White, OperatorRoleValue != null ? OperatorRoleValue.name() : "?", AnsiColor.Reset);
            if (OperatorRoleValue != null) Logger.Custom("  %sPermissions:%s %s%n", AnsiColor.Red, AnsiColor.White, OperatorRoleValue.PermissionString());
        }
        System.out.println();
    }

    public void ShowOperators() {
        List<Map<String, Object>> Operators = Database.GetOperators();
        System.out.println(TerminalHelper.Box("OPERATORS (" + Operators.size() + ")"));
        System.out.println();
        Logger.Custom("  %s%-18s %-14s %-30s %-20s%s%n", AnsiColor.Green, "USERNAME", "ROLE", "PERMISSIONS", "LAST SEEN", AnsiColor.Reset);
        System.out.println(TerminalHelper.Divider());

        for (Map<String, Object> Operator : Operators) {
            OperatorRole Role = OperatorRole.FromString(Operator.get("Role").toString());
            boolean IsSelf = Operator.get("Username").toString().equals(OperatorName);
            String Mark = IsSelf ? AnsiColor.Green + " < YOU" + AnsiColor.White : "";
            Logger.Custom("  %s%-18s %-14s %-30s %-20s%s%s%n", AnsiColor.White, Operator.get("Username"), Role.name(), Role.PermissionString(), Operator.getOrDefault("LastSeen", "Never"), Mark, AnsiColor.Reset);
        }

        System.out.println();
        Logger.Custom("  %sRole Reference:%s%n", AnsiColor.Red, AnsiColor.Reset);
        for (OperatorRole Role : OperatorRole.values()) Logger.Custom("    %s%-14s%s %s%n", AnsiColor.White, Role.name(), AnsiColor.Reset, Role.PermissionString());
        System.out.println();
    }

    public void AddOperator(String Username, String Password, String RoleName, String AdminUsername) {
        if (!CanManage()) {
            Logger.Warn("ADMIN/SUPER required");
            return;
        }
        if (Password.length() < 8) {
            Logger.Warn("password must be >= 8 chars");
            return;
        }
        OperatorRole Role = OperatorRole.FromString(RoleName);
        if (Role == OperatorRole.SUPER && (OperatorRoleValue == null || !OperatorRoleValue.IsSuperAdmin())) {
            Logger.Warn("only SUPER can create SUPER accounts");
            return;
        }
        if (Database.CreateOperator(Username, Password, Role)) Logger.Custom("  Operator created: %s [%s]  %s%n", Username, Role, Role.PermissionString());
        else Logger.Warn("username already exists");
    }

    public void DeleteOperator(String Username, String AdminUsername) {
        if (!CanManage()) {
            Logger.Warn("ADMIN/SUPER required");
            return;
        }
        if (Username.equalsIgnoreCase(AdminUsername)) {
            Logger.Warn("cannot delete admin");
            return;
        }
        if (Database.DeleteOperator(Username)) Logger.Custom("  Deleted: %s%n", Username);
        else Logger.Warn("operator not found");
    }

    public void KickOperator(String Username, String AdminUsername) {
        if (!CanKick()) {
            Logger.Warn("SUPER role required to kick operators");
            return;
        }
        if (Username.equalsIgnoreCase(AdminUsername) || Username.equals(OperatorName)) {
            Logger.Warn("cannot kick admin or yourself");
            return;
        }
        if (Database.DeleteOperator(Username)) Logger.Custom("  Kicked (removed): %s%n", Username);
        else Logger.Warn("operator not found");
    }

    public void SetRole(String Username, String RoleName, String AdminUsername) {
        if (!CanManage()) {
            Logger.Warn("ADMIN/SUPER required");
            return;
        }
        if (Username.equalsIgnoreCase(AdminUsername)) {
            Logger.Warn("cannot change admin role");
            return;
        }
        OperatorRole NewRole = OperatorRole.FromString(RoleName);
        if (NewRole == OperatorRole.SUPER && (OperatorRoleValue == null || !OperatorRoleValue.IsSuperAdmin())) {
            Logger.Warn("only SUPER can promote to SUPER");
            return;
        }
        if (Database.UpdateOperatorRole(Username, NewRole)) Logger.Custom("  Role updated: %s > %s  %s%n", Username, NewRole, NewRole.PermissionString());
        else Logger.Warn("operator not found");
    }

    public void ChangePassword(String Username, String NewPassword) {
        if (!CanManage()) {
            Logger.Warn("ADMIN/SUPER required");
            return;
        }
        if (NewPassword.length() < 8) {
            Logger.Warn("password must be >= 8 chars");
            return;
        }
        if (Database.UpdateOperatorPassword(Username, NewPassword)) Logger.Custom("  Password updated: %s%n", Username);
        else Logger.Warn("operator not found");
    }
}
