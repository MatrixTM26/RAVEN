package com.raven;

import com.raven.core.crypto.CertificateManager;
import com.raven.core.db.TeamDatabase;
import com.raven.core.db.TeamDatabase.OperatorRole;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.iface.CLI;
import com.raven.iface.GUI;
import com.raven.iface.TeamServer;
import com.raven.iface.WebApp;
import com.raven.iface.banner.TBanner;
import com.raven.utils.Helper;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Start {

    private static ServerConfig Config;

    public static void main(String[] Args) {
        Config = new ServerConfig();
        Logger.Configure(Config.GetLoggingLevel(), Config.IsVerbose(), Config.IsFileLoggingEnabled(), Config.GetLogFile(), Config.GetMaxLogEntries());
        ProcessArgs(Arrays.asList(Args));
    }

    private static void ProcessArgs(List<String> Args) {
        if (Args.contains("-h") || Args.contains("-help")) {
            Helper.PrintHelp();
            return;
        }

        if (Args.contains("-i") || Args.contains("-init-certs")) {
            ShowBanner();
            InitCertificates(Helper.Arg(Args, "-s", "-server", Config.GetServerHost()));
            return;
        }

        String GenAgent = Helper.Arg(Args, "-a", "-agent", null);
        if (GenAgent != null) {
            ShowBanner();
            GenerateAgent(GenAgent, Args);
            return;
        }

        if (Args.contains("-m") || Args.contains("-gen-multi")) {
            ShowBanner();
            GenerateMultiAgent(Args);
            return;
        }

        if (Args.contains("-l") || Args.contains("-list")) {
            ShowBanner();
            ListAgents();
            return;
        }

        String Revoke = Helper.Arg(Args, "-r", "-revoke", null);
        if (Revoke != null) {
            ShowBanner();
            RevokeAgent(Revoke);
            return;
        }

        if (Args.contains("-AO") || Args.contains("-add-operator")) {
            ShowBanner();
            HandleAddOperator(Args);
            return;
        }
        if (Args.contains("-RO") || Args.contains("-remove-operator")) {
            ShowBanner();
            HandleRemoveOperator(Args);
            return;
        }
        if (Args.contains("-OP") || Args.contains("-operator-permission")) {
            ShowBanner();
            HandleOperatorPermission(Args);
            return;
        }

        ShowBanner();

        String Host = Helper.Arg(Args, "-s", "-host", Config.GetServerHost());
        int Port = Helper.ParseInt(Helper.Arg(Args, "-p", "-port", String.valueOf(Config.GetServerPort())), Config.GetServerPort());
        ListenerMode Mode = ResolveMode(Args);

        if (Mode.RequiresTls() && !Files.exists(Paths.get(Config.GetKeystorePath()))) {
            Logger.Error("Keystore not found: " + Config.GetKeystorePath());
            Logger.Warn("Run: java -jar target/raven-3.0.0.jar -i");
            System.exit(1);
        }

        String Interface = ResolveInterface(Args);
        Logger.Info("Mode: " + Mode.name() + " Interface: " + Interface.toUpperCase());
        StartInterface(Host, Port, Mode, Interface, Args);
    }

    private static void ShowBanner() {
        SystemHelper.ClearScreen();
        TBanner.Logo();
    }

    private static ListenerMode ResolveMode(List<String> Args) {
        if (Args.contains("-fmtls") || Args.contains("-F")) return ListenerMode.FMTLS;
        if (Args.contains("-mtls") || Args.contains("-T")) return ListenerMode.MTLS;
        if (Args.contains("-tls")) return ListenerMode.TLS;
        if (Args.contains("-https")) return ListenerMode.HTTPS;
        if (Args.contains("-http")) return ListenerMode.HTTP;
        if (Args.contains("-raw") || Args.contains("-R")) return ListenerMode.RAW;
        if (Args.contains("-multi") || Args.contains("-M")) return ListenerMode.MULTI;
        return ListenerMode.FromString(Config.GetServerMode());
    }

    private static String ResolveInterface(List<String> Args) {
        if (Args.contains("-teamclient") || Args.contains("-TC")) return "teamclient";
        if (Args.contains("-teamserver-cli") || Args.contains("-TSC")) return "teamserver-cli";
        if (Args.contains("-teamserver-web") || Args.contains("-TSW")) return "teamserver-web";
        if (Args.contains("-teamserver-gui") || Args.contains("-TSG")) return "teamserver-gui";
        if (Args.contains("-cli-mode") || Args.contains("-C")) return "cli";
        if (Args.contains("-gui-mode") || Args.contains("-G")) return "gui";
        if (Args.contains("-web-mode") || Args.contains("-W")) return "web";
        return Config.GetInterfaceMode();
    }

    private static void StartInterface(String Host, int Port, ListenerMode Mode, String Interface, List<String> Args) {
        try {
            switch (Interface) {
                case "cli" -> new CLI(Config).Run(Host, Port, Mode);
                case "teamclient" -> {
                    String TsHost = Helper.Arg(Args, "-ts", "-teamserver-host", "127.0.0.1");
                    int TsPort = Helper.ParseInt(Helper.Arg(Args, "-tp", "-teamserver-port", String.valueOf(Config.GetTeamServerPort())), Config.GetTeamServerPort());
                    new com.raven.iface.TeamClient(Config, TsHost, TsPort).Run();
                }
                case "teamserver-cli" -> new CLI(Config).RunTeamServer(Host, Port, Mode);
                case "gui" -> GUI.Launch(Config);
                case "teamserver-gui" -> GUI.LaunchTeamServer(Config);
                case "teamserver-web" -> {
                    int TsPort = Helper.ParseInt(Helper.Arg(Args, "-tp", "-teamserver-port", String.valueOf(Config.GetTeamServerPort())), Config.GetTeamServerPort());
                    new TeamServer(Config, Mode).Run(Config.GetWebHost(), TsPort);
                    Thread.currentThread().join();
                }
                default -> {
                    new WebApp(Config, Mode).Run(Config.GetWebHost(), Config.GetWebPort());
                    Thread.currentThread().join();
                }
            }
        } catch (InterruptedException Ignored) {
            Logger.Warn("Server stopped");
        } catch (Exception E) {
            Logger.Error("Fatal: " + E.getMessage());
            System.exit(1);
        }
    }

    private static void InitCertificates(String Host) {
        try {
            CertificateManager Mgr = new CertificateManager(Config);
            Mgr.Initialize(Host);
            Logger.Success("Certificates stored in: " + Paths.get(Config.GetKeystorePath()).getParent());
            Logger.Info("Next: java -jar target/raven-3.0.0.jar -a <agent-id>");
        } catch (Exception E) {
            Logger.Error("Certificate init failed: " + E.getMessage());
        }
    }

    private static void GenerateAgent(String AgentId, List<String> Args) {
        try {
            AssertCaExists();
            String Host = Helper.Arg(Args, "-ah", "-agent-host", Config.GetServerHost());
            int Port = Helper.ParseInt(Helper.Arg(Args, "-ap", "-agent-port", String.valueOf(Config.GetServerPort())), Config.GetServerPort());
            boolean Mtls = Args.contains("-am") || Args.contains("-agent-mtls");
            boolean Pers = Args.contains("-ps") || Args.contains("-persistent");
            boolean Hide = Args.contains("-hc") || Args.contains("-hide-console");
            String Lang = Helper.Arg(Args, "-al", "-agent-lang", "java");
            CertificateManager Mgr = new CertificateManager(Config);
            Mgr.Initialize(Host);
            DeployAgent(AgentId, Mgr.CreateAgentCertificate(AgentId), Host, Port, Mtls, Pers, Hide, Lang);
        } catch (Exception E) {
            Logger.Error("Agent generation failed: " + E.getMessage());
        }
    }

    private static void GenerateMultiAgent(List<String> Args) {
        int Count = Helper.ParseInt(Helper.Arg(Args, "-c", "-count", "10"), 10);
        String Prefix = Helper.Arg(Args, "-u", "-prefix", "agent");
        Logger.Info("Generating " + Count + " agents — prefix: " + Prefix);
        int Ok = 0;
        for (int I = 1; I <= Count; I++) {
            String Id = String.format("%s-%03d", Prefix, I);
            try {
                GenerateAgent(Id, Args);
                Ok++;
            } catch (Exception E) {
                Logger.Error("Failed " + Id + ": " + E.getMessage());
            }
        }
        Logger.Info("Generated " + Ok + "/" + Count + " agents");
    }

    private static void ListAgents() {
        try {
            Path AgentDir = Paths.get(Config.GetAgentCertDir());
            if (!Files.exists(AgentDir)) {
                Logger.Warn("No agents found — generate with: -a <agent-id>");
                return;
            }
            Files.list(AgentDir)
                .filter(P -> P.toString().endsWith(".p12"))
                .forEach(P -> Logger.Info("Agent: " + P.getFileName()));
        } catch (Exception E) {
            Logger.Error("List agents failed: " + E.getMessage());
        }
    }

    private static void RevokeAgent(String AgentId) {
        try {
            new CertificateManager(Config).RevokeAgentCertificate(AgentId);
        } catch (Exception E) {
            Logger.Error("Revoke failed: " + E.getMessage());
        }
    }

    private static void DeployAgent(String Id, String CertPath, String Host, int Port, boolean Mtls, boolean Persist, boolean HideCon, String Lang) throws IOException {
        String AgentDir = "IMPLANT/" + Id.toUpperCase();
        Files.createDirectories(Paths.get(AgentDir));

        // Copy certs
        Files.copy(Paths.get(CertPath), Paths.get(AgentDir + "/agent.p12"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Paths.get(Config.GetCaPath()), Paths.get(AgentDir + "/ca.p12"), StandardCopyOption.REPLACE_EXISTING);

        // Generate agent source
        String LangNorm = Lang != null && !Lang.isEmpty() ? Lang.toLowerCase() : "java";
        String SrcFile = AgentDir + "/" + com.raven.utils.AgentSourceGen.Filename(LangNorm);
        String SrcCode = com.raven.utils.AgentSourceGen.Generate(LangNorm, Id, Host, Port, Mtls, Persist, HideCon);
        Files.writeString(Paths.get(SrcFile), SrcCode);
        if (LangNorm.equals("bash") || LangNorm.equals("sh")) Paths.get(SrcFile).toFile().setExecutable(true);

        // README
        try (PrintWriter W = new PrintWriter(AgentDir + "/README.txt")) {
            W.println("RAVEN Agent — " + Id);
            W.println("Server  : " + Host + ":" + Port);
            W.println("MTLS    : " + (Mtls ? "ENABLED" : "DISABLED"));
            W.println("Persist : " + Persist);
            W.println("Lang    : " + LangNorm.toUpperCase());
            W.println("Files   : agent.p12  ca.p12  " + com.raven.utils.AgentSourceGen.Filename(LangNorm));
            W.println();
            W.println("-- HOW TO RUN --");
            switch (LangNorm) {
                case "java" -> {
                    W.println("  javac " + com.raven.utils.AgentSourceGen.Filename(LangNorm));
                    W.println("  java RavenAgent");
                    if (Mtls) W.println("  (keep agent.p12 + ca.p12 in same dir)");
                }
                case "python", "py" -> W.println("  python3 agent.py");
                case "go" -> {
                    W.println("  go build -ldflags \"-s -w\" -o agent agent.go");
                    W.println("  ./agent");
                }
                case "bash", "sh" -> W.println("  chmod +x agent.sh && ./agent.sh");
            }
        }

        Logger.Success("Agent generated : " + AgentDir);
        Logger.Info("  Source : " + com.raven.utils.AgentSourceGen.Filename(LangNorm));
        Logger.Info("  Server : " + Host + ":" + Port + "  MTLS=" + Mtls + "  Lang=" + LangNorm.toUpperCase());
    }

    private static void HandleAddOperator(List<String> Args) {
        String User = Helper.Arg(Args, "-u", "-username", null);
        String Pass = Helper.Arg(Args, "-pw", "-password", null);
        String Role = Helper.Arg(Args, "-r", "-role", "OPERATOR");
        if (User == null || Pass == null) {
            Logger.Error("Usage: -AO | -add-operator -u | -username <user> -pw | -password <pass> [-r | -role ROLE]");
            return;
        }
        if (Pass.length() < 8) {
            Logger.Error("Password must be at least 8 characters");
            return;
        }
        TeamDatabase Db = TeamDatabase.Connect(Config);
        OperatorRole R = OperatorRole.FromString(Role);
        if (Db.CreateOperator(User, TeamDatabase.HashPassword(Pass), R)) Logger.Success("Operator created: " + User + " [" + R + "] — " + R.PermissionString());
        else Logger.Error("Failed — username may already exist");
        Db.Close();
    }

    private static void HandleRemoveOperator(List<String> Args) {
        String User = Helper.Arg(Args, "-u", "-username", null);
        if (User == null) {
            Logger.Error("Usage: -RO | -remove-operator -u | -username <user>");
            return;
        }
        if (User.equals("admin")) {
            Logger.Error("Cannot remove the admin account");
            return;
        }
        TeamDatabase Db = TeamDatabase.Connect(Config);
        if (Db.DeleteOperator(User)) Logger.Success("Operator removed: " + User);
        else Logger.Error("Operator not found: " + User);
        Db.Close();
    }

    private static void HandleOperatorPermission(List<String> Args) {
        String User = Helper.Arg(Args, "-u", "-username", null);
        String Role = Helper.Arg(Args, "-r", "-role", null);
        if (User == null && Role == null) {
            Logger.Info("Available roles:");
            for (OperatorRole R : OperatorRole.values()) Logger.Info("  " + R.name() + " — " + R.PermissionString());
            return;
        }
        if (User == null || Role == null) {
            Logger.Error("Usage: -OP | -operator-permission -u | -username <user> -r | -role <ROLE>");
            return;
        }
        if (User.equals("admin")) {
            Logger.Error("Cannot change the admin role");
            return;
        }
        TeamDatabase Db = TeamDatabase.Connect(Config);
        OperatorRole R = OperatorRole.FromString(Role);
        if (Db.UpdateOperatorRole(User, R)) Logger.Success("Role updated: " + User + " → " + R + " — " + R.PermissionString());
        else Logger.Error("Failed to update role for: " + User);
        Db.Close();
    }

    private static void AssertCaExists() {
        if (!Files.exists(Paths.get(Config.GetCaPath()))) {
            Logger.Error("CA not found: " + Config.GetCaPath());
            Logger.Warn("Run: java -jar target/raven-3.0.0.jar -i");
            System.exit(1);
        }
    }
}
