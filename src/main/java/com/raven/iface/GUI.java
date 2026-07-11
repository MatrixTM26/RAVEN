package com.raven.iface;

import com.raven.core.db.TeamDatabase;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.core.server.ListenerMode;
import com.raven.core.server.RavenServer;
import com.raven.core.session.Session;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.Stage;

public class GUI extends Application {

    private static ServerConfig Config;
    private TeamDatabase Db;
    private RavenServer Server;
    private Instant ServerStartTime;
    private final ObservableList<SessionRow> SessionRows = FXCollections.observableArrayList();
    private final ObservableList<String> LogEntries = FXCollections.observableArrayList();
    private int SelectedSessionId = -1;

    private static final String BgColor = "#050505";
    private static final String NavColor = "#0b0b0d";
    private static final String CardColor = "#101014";
    private static final String Card2Color = "#17171c";
    private static final String AccentColor = "#ffffff";
    private static final String RedColor = "#ff1f3d";
    private static final String GreenColor = "#ffffff";
    private static final String TextColor = "#ffffff";
    private static final String MutedColor = "#a7a7ad";
    private static final String BorderColor = "#2a2a31";
    private static final String PurpleColor = "#ff4d63";
    private static final String OrangeColor = "#d6d6db";

    private Label StatusLabel;
    private Label UptimeLabel;
    private Label SessionCountLabel;
    private TableView<SessionRow> SessionTable;
    private TextArea TerminalOutput;
    private TextArea LogOutput;
    private TextField TermInputField;
    private TextField CmdInputField;
    private Label SelectedAgentLabel;
    private Label ServerStatusLabel;
    private Label ServerInfoLabel;
    private TextField HostField;
    private static boolean TeamMode = false;
    private String OperatorName = null;
    private TeamDatabase.OperatorRole OperatorRole = null;
    private TextField PortField;
    private Button StartBtn;
    private Button StopBtn;

    public static class SessionRow {

        private final SimpleStringProperty Id, Type, Name, Ip, Os, User, Host, Joined;

        public SessionRow(Session S) {
            Id = new SimpleStringProperty(String.valueOf(S.GetId()));
            Type = new SimpleStringProperty(S.GetSessionType().name());
            Name = new SimpleStringProperty(S.GetDisplayName());
            Ip = new SimpleStringProperty(S.GetAgentIp());
            Os = new SimpleStringProperty(S.GetOs());
            User = new SimpleStringProperty(S.GetUser());
            Host = new SimpleStringProperty(S.GetHostname());
            Joined = new SimpleStringProperty(S.GetSessionKey());
        }

        public String getId() {
            return Id.get();
        }

        public String getType() {
            return Type.get();
        }

        public String getName() {
            return Name.get();
        }

        public String getIp() {
            return Ip.get();
        }

        public String getOs() {
            return Os.get();
        }

        public String getUser() {
            return User.get();
        }

        public String getHost() {
            return Host.get();
        }

        public String getJoined() {
            return Joined.get();
        }

        public SimpleStringProperty IdProperty() {
            return Id;
        }

        public SimpleStringProperty TypeProperty() {
            return Type;
        }

        public SimpleStringProperty NameProperty() {
            return Name;
        }

        public SimpleStringProperty IpProperty() {
            return Ip;
        }

        public SimpleStringProperty OsProperty() {
            return Os;
        }

        public SimpleStringProperty UserProperty() {
            return User;
        }

        public SimpleStringProperty HostProperty() {
            return Host;
        }

        public SimpleStringProperty JoinedProperty() {
            return Joined;
        }
    }

    public static void Launch(ServerConfig Cfg) {
        Config = Cfg;
        TeamMode = false;
        Application.launch(GUI.class);
    }

    public static void LaunchTeamServer(ServerConfig Cfg) {
        Config = Cfg;
        TeamMode = true;
        Application.launch(GUI.class);
    }

    @Override
    public void start(Stage PrimaryStage) {
        Db = TeamDatabase.Connect(Config);
        if (TeamMode && !ShowLoginDialog(PrimaryStage)) {
            Platform.exit();
            return;
        }
        PrimaryStage.setTitle("RAVEN Framework V3");
        PrimaryStage.setWidth(1440);
        PrimaryStage.setHeight(900);
        PrimaryStage.setMinWidth(1100);
        PrimaryStage.setMinHeight(720);

        BorderPane Root = new BorderPane();
        Root.setStyle("-fx-background-color: " + BgColor + ";");
        Root.setLeft(BuildSidebar(PrimaryStage));
        Root.setCenter(BuildMainContent());

        Scene MainScene = new Scene(Root);
        PrimaryStage.setScene(MainScene);
        PrimaryStage.setOnCloseRequest(E -> {
            if (Server != null) Server.StopServer();
            Platform.exit();
        });
        PrimaryStage.show();
        StartUptimeTimer();
    }

    private VBox BuildSidebar(Stage Stage) {
        VBox Sidebar = new VBox();
        Sidebar.setPrefWidth(240);
        Sidebar.setStyle("-fx-background-color: linear-gradient(to bottom, #120006, #050505); -fx-border-color: transparent " + RedColor + " transparent transparent; -fx-border-width: 0 1.5 0 0;");

        VBox Logo = new VBox(8);
        Logo.setPadding(new Insets(28, 22, 18, 22));
        Label Icon = StyledLabel("◆", 38, RedColor, true);
        Label Name = StyledLabel("RAVEN", 28, TextColor, true);
        Label Tag = StyledLabel("Black Ops Console", 10, MutedColor, false);
        Logo.getChildren().addAll(Icon, Name, Tag);
        Sidebar.getChildren().add(Logo);
        Sidebar.getChildren().add(Divider());

        VBox Meta = new VBox(12);
        Meta.setPadding(new Insets(18, 22, 18, 22));
        Meta.getChildren().addAll(MiniInfo("VERSION", "3.0.0"), MiniInfo("MODE", Config.GetServerMode().toUpperCase()), MiniInfo("INTERFACE", TeamMode ? "TEAM GUI" : "SOLO GUI"));
        VBox.setVgrow(Meta, Priority.ALWAYS);
        Sidebar.getChildren().add(Meta);

        VBox Footer = new VBox(8);
        Footer.setPadding(new Insets(14, 22, 18, 22));
        Footer.setStyle("-fx-border-color: " + BorderColor + " transparent transparent transparent; -fx-border-width: 1;");
        Footer.getChildren().addAll((StatusLabel = StyledLabel("● Offline", 11, RedColor, true)), StyledLabel("MatrixTM26", 9, MutedColor, false));
        Sidebar.getChildren().add(Footer);
        return Sidebar;
    }

    private VBox MiniInfo(String Label, String Value) {
        VBox Box = new VBox(3);
        Box.setPadding(new Insets(10, 12, 10, 12));
        Box.setStyle("-fx-background-color: rgba(255,31,61,0.08); -fx-background-radius: 12; -fx-border-color: rgba(255,31,61,0.45); -fx-border-radius: 12;");
        Box.getChildren().addAll(StyledLabel(Label, 8, MutedColor, true), StyledLabel(Value, 11, TextColor, true));
        return Box;
    }

    private Tab BuildNavTab(String Name, javafx.scene.Node Content) {
        Tab T = new Tab(Name);
        T.setContent(Content);
        return T;
    }

    private BorderPane BuildMainContent() {
        BorderPane Main = new BorderPane();
        Main.setStyle("-fx-background-color: " + BgColor + ";");
        HBox TopBar = new HBox();
        TopBar.setPrefHeight(72);
        TopBar.setAlignment(Pos.CENTER_LEFT);
        TopBar.setStyle("-fx-background-color: linear-gradient(to right, #050505, #120006); -fx-border-color: transparent transparent " + RedColor + " transparent; -fx-border-width: 0 0 1.5 0;");
        TopBar.setPadding(new Insets(0, 24, 0, 24));

        UptimeLabel = StyledLabel("00:00:00", 9, MutedColor, false);
        SessionCountLabel = StyledLabel("⊟ 0 Sessions", 9, AccentColor, false);

        HBox Right = new HBox(16);
        Right.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(Right, Priority.ALWAYS);
        Right.getChildren().addAll(UptimeLabel, VDivider(), SessionCountLabel);
        Label OpLabel = OperatorName != null ? StyledLabel("Op: " + OperatorName + " [" + (OperatorRole != null ? OperatorRole.name() : "?") + "]", 8, AccentColor, false) : null;
        VBox Heading = new VBox(2);
        Heading.getChildren().addAll(StyledLabel("RAVEN Operations Console", 20, TextColor, true), StyledLabel("Listener control • Session ops • CLI-aligned command center", 10, MutedColor, false));
        if (OpLabel != null) TopBar.getChildren().addAll(Heading, OpLabel, Right);
        else TopBar.getChildren().addAll(Heading, Right);
        Main.setTop(TopBar);

        TabPane Tabs = new TabPane();
        Tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tabs.setStyle("-fx-background-color: " + BgColor + "; -fx-tab-min-height: 42px; -fx-tab-max-height: 42px;");
        Tabs.getTabs().addAll(BuildNavTab("Dashboard", BuildDashboardTab()), BuildNavTab("Sessions", BuildSessionsTab()), BuildNavTab("Terminal", BuildTerminalTab()), BuildNavTab("Commands", BuildCommandCenterTab()), BuildNavTab("Logs", BuildLogsTab()), BuildNavTab("Settings", BuildSettingsTab()));
        Main.setCenter(Tabs);
        return Main;
    }

    private ScrollPane BuildDashboardTab() {
        VBox Content = new VBox(12);
        Content.setPadding(new Insets(24));
        Content.setStyle("-fx-background-color: " + BgColor + ";");

        GridPane Cards = new GridPane();
        Cards.setHgap(8);
        Cards.setVgap(8);

        Cards.add(BuildStatCard("⊟", "Total Sessions", "0", TextColor), 0, 0);
        Cards.add(BuildStatCard("◉", "RAVEN", "0", RedColor), 1, 0);
        Cards.add(BuildStatCard("◎", "Meterpreter", "0", TextColor), 2, 0);
        Cards.add(BuildStatCard("◈", "Reverse Shell", "0", RedColor), 3, 0);

        for (int I = 0; I < 4; I++) {
            ColumnConstraints Col = new ColumnConstraints();
            Col.setPercentWidth(25);
            Cards.getColumnConstraints().add(Col);
        }
        Content.getChildren().add(Cards);

        VBox Info = BuildCard("TOOL INFORMATION");
        TextArea InfoText = new TextArea(" Author  : MatrixTM26\n" + " Github  : MatrixTM26\n" + " Version : 3.0\n\n" + " Use Sessions for fast actions and Commands for CLI-aligned workflows.\n" + " Terminal executes agent commands; Command Center executes server/session utilities.\n\n" + " GUI Commands:\n" + "   • sessions | status | stats | tasks\n" + "   • kill <id> | sysinfo <id> | note <id> <text> | getnote <id>\n" + "   • history [id] [limit] | broadcast <cmd>\n" + "   • exec <id> <cmd> | whoami <id> | sleep <id> <seconds>\n" + "   • screenshot <id> | download <id> <path> | upload <id> <local> <remote>");
        InfoText.setEditable(false);
        InfoText.setPrefHeight(200);
        ApplyTermStyle(InfoText);
        Info.getChildren().add(InfoText);
        Content.getChildren().add(Info);

        ScrollPane Scroll = new ScrollPane(Content);
        Scroll.setFitToWidth(true);
        Scroll.setStyle("-fx-background-color: " + BgColor + ";");
        return Scroll;
    }

    private VBox BuildSessionsTab() {
        VBox Content = new VBox(8);
        Content.setPadding(new Insets(12));
        Content.setStyle("-fx-background-color: " + BgColor + ";");

        HBox Toolbar = new HBox(8);
        Toolbar.setAlignment(Pos.CENTER_LEFT);
        TextField Search = new TextField();
        Search.setPromptText("🔍 Search sessions...");
        ApplyInputStyle(Search);
        Search.setPrefWidth(220);
        HBox.setHgrow(Search, Priority.ALWAYS);

        Button Refresh = StyledButton("⟳ Refresh", TextColor, true);
        Button Execute = StyledButton("▶ Execute", RedColor, false);
        Button Broadcast = StyledButton("⇶ Broadcast", TextColor, true);
        Button Kill = StyledButton("⊘ Kill", RedColor, false);
        Refresh.setOnAction(E -> RefreshSessions());
        Execute.setOnAction(E -> OpenExecuteWindow());
        Broadcast.setOnAction(E -> OpenBroadcastWindow());
        Kill.setOnAction(E -> KillSelected());
        Toolbar.getChildren().addAll(Search, Refresh, Execute, Broadcast, Kill);
        Content.getChildren().add(Toolbar);

        HBox SrvCmdRow = new HBox(8);
        SrvCmdRow.setAlignment(Pos.CENTER_LEFT);
        SrvCmdRow.setPadding(new Insets(4, 0, 4, 0));
        Label SrvLbl = StyledLabel("COMMAND:", 9, RedColor, true);
        TextField SrvInput = new TextField();
        SrvInput.setPromptText("sessions | status | stats | tasks | kill <id> | sysinfo <id> | history [id] [limit] | note <id> <text> | exec <id> <cmd>");
        ApplyInputStyle(SrvInput);
        HBox.setHgrow(SrvInput, Priority.ALWAYS);
        Button SrvExec = StyledButton("RUN", RedColor, false);
        SrvExec.setOnAction(E -> RunServerCommand(SrvInput.getText().trim(), SrvInput));
        SrvInput.setOnAction(E -> RunServerCommand(SrvInput.getText().trim(), SrvInput));
        SrvCmdRow.getChildren().addAll(SrvLbl, SrvInput, SrvExec);
        Content.getChildren().add(SrvCmdRow);

        SessionTable = new TableView<>(SessionRows);
        SessionTable.setStyle("-fx-background-color: " + CardColor + "; -fx-control-inner-background: " + CardColor + "; -fx-table-cell-border-color: " + BorderColor + "; -fx-table-header-border-color: " + BorderColor + "; -fx-text-fill: " + TextColor + "; -fx-background-radius: 12;");
        String[] ColNames = { "ID", "Type", "Name/Cert", "IP", "OS", "User", "Host", "Session Key" };
        String[] Props = { "id", "type", "name", "ip", "os", "user", "host", "joined" };
        for (int I = 0; I < ColNames.length; I++) {
            TableColumn<SessionRow, String> Col = new TableColumn<>(ColNames[I]);
            Col.setCellValueFactory(new PropertyValueFactory<>(Props[I]));
            Col.setStyle("-fx-alignment: CENTER_LEFT; -fx-text-fill: " + TextColor + ";");
            SessionTable.getColumns().add(Col);
        }
        SessionTable.getSelectionModel()
            .selectedItemProperty()
            .addListener((Obs, Old, New) -> {
                if (New != null) {
                    SelectedSessionId = Integer.parseInt(New.getId());
                    if (SelectedAgentLabel != null) SelectedAgentLabel.setText("● " + New.getName() + " #" + SelectedSessionId);
                }
            });
        VBox.setVgrow(SessionTable, Priority.ALWAYS);
        Content.getChildren().add(SessionTable);
        return Content;
    }

    private void RunServerCommand(String Cmd, TextField Input) {
        if (Cmd == null || Cmd.isBlank()) return;
        if (Server == null || !Server.IsRunning()) {
            AddLog("[!] Server not running");
            return;
        }
        String[] Parts = Cmd.trim().split("\\s+", 2);
        switch (Parts[0].toLowerCase()) {
            case "sessions", "agents" -> {
                int N = Server.GetSessions().Count();
                AddLog("[*] Sessions (" + N + "):");
                Server.GetSessions()
                    .GetAll()
                    .forEach(S -> AddLog("    #" + S.GetId() + " [" + S.GetDisplayName() + "] " + S.GetUser() + "@" + S.GetHostname() + " key=" + S.GetSessionKey()));
                Platform.runLater(() -> RefreshSessions());
            }
            case "kill" -> {
                if (Parts.length < 2) {
                    AddLog("[!] Usage: kill <id>");
                    return;
                }
                try {
                    int Id = Integer.parseInt(Parts[1].trim());
                    Server.RemoveSession(Id);
                    AddLog("[+] Session-" + Id + " terminated");
                    Platform.runLater(() -> RefreshSessions());
                } catch (NumberFormatException E) {
                    AddLog("[!] Invalid session ID");
                }
            }
            case "status" -> {
                AddLog("[*] Status: " + (Server.IsRunning() ? "ONLINE" : "OFFLINE"));
                AddLog("[*] Mode: " + Server.GetMode().name() + " | Host: " + Server.GetHost() + ":" + Server.GetPort());
                AddLog("[*] Sessions: " + Server.GetSessions().Count());
                AddLog("[*] Key: " + Server.GetKeyBase64());
            }
            case "stats" -> {
                Map<String, Integer> Stats = Server.GetSessions().GetStats();
                AddLog("[*] Total: " + Stats.get("Total"));
                AddLog("[*] RAVEN: " + Stats.get("RAVEN"));
                AddLog("[*] Meterpreter: " + Stats.get("METERPRETER"));
                AddLog("[*] Reverse Shell: " + Stats.get("REVERSE_SHELL"));
            }
            case "tasks" -> AddLog("[*] Active sessions: " + Server.GetSessions().Count() + " — use exec/broadcast to run tasks");
            case "broadcast" -> {
                if (Parts.length < 2) {
                    AddLog("[!] Usage: broadcast <cmd>");
                    return;
                }
                String BCmd = Parts[1];
                AddLog("[>] Broadcast → " + BCmd);
                Map<Integer, String[]> R = Server.BroadcastAll(BCmd);
                R.forEach((Id, Res) -> AddLog("    [" + Id + "] " + (Boolean.parseBoolean(Res[0]) ? "✔ " : "✘ ") + Res[1]));
            }
            case "exec" -> RunExecCommand(Parts.length > 1 ? Parts[1] : "");
            case "whoami", "screenshot" -> RunShortcutCommand(Parts, Parts[0].toLowerCase());
            case "sleep" -> RunSleepCommand(Parts);
            case "download" -> RunDownloadCommand(Parts);
            case "upload" -> RunUploadCommand(Parts);
            case "sysinfo", "info" -> ShowSessionInfoLog(Parts.length > 1 ? Parts[1] : "");
            case "note" -> SaveNoteCommand(Parts.length > 1 ? Parts[1] : "");
            case "getnote" -> GetNoteCommand(Parts.length > 1 ? Parts[1] : "");
            case "history" -> ShowHistoryCommand(Parts.length > 1 ? Parts[1] : "");
            default -> AddLog("[!] Unknown command: " + Parts[0]);
        }
        if (Input != null) Platform.runLater(() -> Input.clear());
    }

    private VBox BuildTerminalTab() {
        VBox Content = new VBox(8);
        Content.setPadding(new Insets(12));
        Content.setStyle("-fx-background-color: " + BgColor + ";");

        HBox Header = new HBox(8);
        Header.setAlignment(Pos.CENTER_LEFT);
        Label SessionLbl = StyledLabel("Session ID:", 9, MutedColor, false);
        TermInputField = new TextField();
        TermInputField.setPrefWidth(80);
        ApplyInputStyle(TermInputField);
        SelectedAgentLabel = StyledLabel("○ No agent selected", 9, MutedColor, false);
        Button ClearBtn = StyledButton("Clear", CardColor, false);
        ClearBtn.setOnAction(E -> {
            if (TerminalOutput != null) TerminalOutput.clear();
        });
        HBox.setHgrow(SelectedAgentLabel, Priority.ALWAYS);
        Header.getChildren().addAll(SessionLbl, TermInputField, SelectedAgentLabel, ClearBtn);
        Content.getChildren().add(Header);

        TerminalOutput = new TextArea();
        TerminalOutput.setEditable(false);
        TerminalOutput.setPrefHeight(400);
        ApplyTermStyle(TerminalOutput);
        VBox.setVgrow(TerminalOutput, Priority.ALWAYS);
        Content.getChildren().add(TerminalOutput);

        HBox InputRow = new HBox(8);
        InputRow.setAlignment(Pos.CENTER_LEFT);
        Label Prompt = StyledLabel("❯", 11, AccentColor, true);
        CmdInputField = new TextField();
        CmdInputField.setPromptText("Enter command...");
        ApplyInputStyle(CmdInputField);
        HBox.setHgrow(CmdInputField, Priority.ALWAYS);
        Button SendBtn = StyledButton("Send", AccentColor, false);
        SendBtn.setOnAction(E -> SendTerminalCommand());
        CmdInputField.setOnAction(E -> SendTerminalCommand());
        InputRow.getChildren().addAll(Prompt, CmdInputField, SendBtn);
        Content.getChildren().add(InputRow);
        return Content;
    }

    private VBox BuildCommandCenterTab() {
        VBox Content = new VBox(12);
        Content.setPadding(new Insets(18));
        Content.setStyle("-fx-background-color: " + BgColor + ";");

        VBox Help = BuildCard("CLI-ALIGNED COMMANDS");
        TextArea HelpText = new TextArea("Server: sessions | status | stats | tasks\n" + "Session: exec <id> <command> | kill <id> | sysinfo <id>\n" + "Ops: broadcast <command> | whoami <id> | sleep <id> <seconds>\n" + "Files: screenshot <id> | download <id> <remote-path> | upload <id> <local-path> <remote-path>\n" + "Notes/History: note <id> <text> | getnote <id> | history [id] [limit]");
        HelpText.setEditable(false);
        HelpText.setPrefHeight(120);
        ApplyTermStyle(HelpText);
        Help.getChildren().add(HelpText);

        HBox InputRow = new HBox(10);
        InputRow.setAlignment(Pos.CENTER_LEFT);
        TextField CommandInput = new TextField();
        CommandInput.setPromptText("Type GUI command matching CLI syntax...");
        ApplyInputStyle(CommandInput);
        HBox.setHgrow(CommandInput, Priority.ALWAYS);
        Button Run = StyledButton("EXECUTE", RedColor, false);
        Button Clear = StyledButton("CLEAR LOGS", TextColor, true);
        Run.setOnAction(E -> RunServerCommand(CommandInput.getText().trim(), CommandInput));
        CommandInput.setOnAction(E -> RunServerCommand(CommandInput.getText().trim(), CommandInput));
        Clear.setOnAction(E -> {
            LogEntries.clear();
            if (LogOutput != null) LogOutput.clear();
        });
        InputRow.getChildren().addAll(StyledLabel("❯", 16, RedColor, true), CommandInput, Run, Clear);

        TextArea Mirror = new TextArea();
        Mirror.setEditable(false);
        Mirror.setText("Command Center writes output to the Logs tab and mirrors operational events.\nStart the server first from Settings, then use Sessions/Commands.");
        ApplyTermStyle(Mirror);
        VBox.setVgrow(Mirror, Priority.ALWAYS);

        Content.getChildren().addAll(Help, InputRow, Mirror);
        return Content;
    }

    private VBox BuildLogsTab() {
        VBox Content = new VBox(8);
        Content.setPadding(new Insets(12));
        Content.setStyle("-fx-background-color: " + BgColor + ";");

        HBox Toolbar = new HBox(8);
        Toolbar.setAlignment(Pos.CENTER_RIGHT);
        Button ExportBtn = StyledButton("Export", TextColor, true);
        Button ClearBtn = StyledButton("Clear", RedColor, false);
        ClearBtn.setOnAction(E -> {
            LogEntries.clear();
            if (LogOutput != null) LogOutput.clear();
        });
        Toolbar.getChildren().addAll(ExportBtn, ClearBtn);
        Content.getChildren().add(Toolbar);

        LogOutput = new TextArea();
        LogOutput.setEditable(false);
        ApplyTermStyle(LogOutput);
        VBox.setVgrow(LogOutput, Priority.ALWAYS);
        Content.getChildren().add(LogOutput);
        return Content;
    }

    private ScrollPane BuildSettingsTab() {
        VBox Content = new VBox(16);
        Content.setPadding(new Insets(16));
        Content.setStyle("-fx-background-color: " + BgColor + ";");

        VBox ServerCard = BuildCard("Server Configuration");
        GridPane Fields = new GridPane();
        Fields.setHgap(16);
        Fields.setVgap(8);

        HostField = new TextField(Config.GetServerHost());
        PortField = new TextField(String.valueOf(Config.GetServerPort()));
        ApplyInputStyle(HostField);
        ApplyInputStyle(PortField);

        Fields.add(StyledLabel("Host:", 9, MutedColor, false), 0, 0);
        Fields.add(HostField, 1, 0);
        Fields.add(StyledLabel("Port:", 9, MutedColor, false), 0, 1);
        Fields.add(PortField, 1, 1);
        ServerCard.getChildren().add(Fields);

        HBox Btns = new HBox(8);
        StartBtn = StyledButton("▶ START SERVER", RedColor, false);
        StopBtn = StyledButton("◼ STOP SERVER", RedColor, false);
        StopBtn.setDisable(true);
        StartBtn.setOnAction(E -> StartServer());
        StopBtn.setOnAction(E -> StopServer());
        Btns.getChildren().addAll(StartBtn, StopBtn);
        ServerCard.getChildren().add(Btns);

        VBox StatusCard = BuildCard("Server Status");
        ServerStatusLabel = StyledLabel("● OFFLINE", 16, RedColor, true);
        ServerInfoLabel = StyledLabel("Not running", 10, MutedColor, false);
        StatusCard.getChildren().addAll(ServerStatusLabel, ServerInfoLabel);

        Content.getChildren().addAll(ServerCard, StatusCard);
        ScrollPane Scroll = new ScrollPane(Content);
        Scroll.setFitToWidth(true);
        Scroll.setStyle("-fx-background-color: " + BgColor + ";");
        return Scroll;
    }

    private boolean ShowLoginDialog(Stage Owner) {
        javafx.scene.control.Dialog<Boolean> Dlg = new javafx.scene.control.Dialog<>();
        Dlg.setTitle("TeamServer Login");
        Dlg.setHeaderText("RAVEN — Operator Authentication");
        Dlg.initOwner(Owner);

        GridPane Grid = new GridPane();
        Grid.setHgap(12);
        Grid.setVgap(8);
        Grid.setPadding(new Insets(20, 20, 10, 20));

        TextField UserField = new TextField();
        UserField.setPromptText("Username");
        PasswordField PassField = new PasswordField();
        PassField.setPromptText("Password");
        Label ErrLabel = new Label("");
        ErrLabel.setTextFill(javafx.scene.paint.Color.web(RedColor));
        ErrLabel.setFont(Font.font("Segoe UI", 9));

        Grid.add(new Label("Username:"), 0, 0);
        Grid.add(UserField, 1, 0);
        Grid.add(new Label("Password:"), 0, 1);
        Grid.add(PassField, 1, 1);
        Grid.add(ErrLabel, 1, 2);

        ButtonType LoginBtn = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        Dlg.getDialogPane().getButtonTypes().addAll(LoginBtn, ButtonType.CANCEL);
        Dlg.getDialogPane().setContent(Grid);
        Dlg.getDialogPane().setStyle("-fx-background-color: " + BgColor + ";");

        Dlg.setResultConverter(Btn -> {
            if (Btn == LoginBtn) {
                String User = UserField.getText().trim();
                String Pass = PassField.getText();
                if (Db.ValidateOperator(User, TeamDatabase.HashPassword(Pass))) {
                    OperatorName = User;
                    OperatorRole = Db.GetOperatorRole(User);
                    return true;
                }
                ErrLabel.setText("Invalid credentials");
                return null;
            }
            return false;
        });

        for (int I = 0; I < 3; I++) {
            Optional<Boolean> Result = Dlg.showAndWait();
            if (Result.isEmpty() || Boolean.FALSE.equals(Result.get())) return false;
            if (Boolean.TRUE.equals(Result.get())) {
                Logger.Info("GUI operator login: " + OperatorName + " [" + OperatorRole + "]");
                return true;
            }
        }
        return false;
    }

    private void StartServer() {
        String Host = HostField.getText().trim();
        int Port;
        try {
            Port = Integer.parseInt(PortField.getText().trim());
        } catch (NumberFormatException E) {
            ShowAlert("Invalid port number");
            return;
        }
        Server = new RavenServer(Host, Port, ListenerMode.FromString(Config.GetServerMode()), Config);
        Server.AddEventListener(this::EventHandler);
        boolean[] Result = Server.StartServer();
        if (!Result[0]) {
            ShowAlert("Failed to start server");
            return;
        }
        ServerStartTime = Instant.now();
        Thread T = new Thread(Server::AcceptConnections, "AcceptConnections");
        T.setDaemon(true);
        T.start();
        Platform.runLater(() -> {
            ServerStatusLabel.setText("● ONLINE");
            ServerStatusLabel.setTextFill(javafx.scene.paint.Color.web(TextColor));
            ServerInfoLabel.setText(Host + ":" + Port);
            StatusLabel.setText("● Online");
            StatusLabel.setTextFill(javafx.scene.paint.Color.web(TextColor));
            StartBtn.setDisable(true);
            StopBtn.setDisable(false);
        });
        AddLog("[+] Server started on " + Host + ":" + Port);
        AddLog("[+] Session Key: " + Server.GetKeyBase64());
    }

    private void StopServer() {
        if (Server == null) return;
        Server.StopServer();
        ServerStartTime = null;
        Platform.runLater(() -> {
            ServerStatusLabel.setText("● OFFLINE");
            ServerStatusLabel.setTextFill(javafx.scene.paint.Color.web(RedColor));
            ServerInfoLabel.setText("Not running");
            StatusLabel.setText("● Offline");
            StatusLabel.setTextFill(javafx.scene.paint.Color.web(RedColor));
            StartBtn.setDisable(false);
            StopBtn.setDisable(true);
            SessionRows.clear();
            SessionCountLabel.setText("⊟ 0 Sessions");
        });
        AddLog("[!] Server stopped");
    }

    private void RefreshSessions() {
        if (Server == null) return;
        Platform.runLater(() -> {
            SessionRows.clear();
            for (Session S : Server.GetSessions().GetAll()) SessionRows.add(new SessionRow(S));
            SessionCountLabel.setText("⊟ " + SessionRows.size() + " Session" + (SessionRows.size() != 1 ? "s" : ""));
        });
    }

    private void SendTerminalCommand() {
        if (CmdInputField == null) return;
        String SidStr = TermInputField.getText().trim();
        String Cmd = CmdInputField.getText().trim();
        if (SidStr.isEmpty() || Cmd.isEmpty()) return;
        int Sid;
        try {
            Sid = Integer.parseInt(SidStr);
        } catch (NumberFormatException E) {
            WriteTerminal("[!] Invalid session ID\n", true);
            return;
        }
        if (Server == null) {
            WriteTerminal("[!] Server not running\n", true);
            return;
        }
        WriteTerminal("❯ " + Cmd + "\n", false);
        CmdInputField.clear();
        AddLog("[>] #" + Sid + ": " + Cmd);
        final int FinalSid = Sid;
        Executors.newSingleThreadExecutor().submit(() -> {
            String[] Result = Server.ExecuteCommand(FinalSid, Cmd);
            boolean Ok = Boolean.parseBoolean(Result[0]);
            Platform.runLater(() -> {
                WriteTerminal(Result[1] + "\n\n", false);
                AddLog((Ok ? "[+]" : "[!]") + " #" + FinalSid + ": " + (Ok ? "OK" : Result[1]));
            });
        });
    }

    private void RunExecCommand(String Body) {
        String[] Args = Body.trim().split("\\s+", 2);
        if (Args.length < 2) {
            AddLog("[!] Usage: exec <id> <command>");
            return;
        }
        try {
            int Sid = Integer.parseInt(Args[0]);
            RunAgentCommand(Sid, Args[1]);
        } catch (NumberFormatException E) {
            AddLog("[!] Invalid session ID");
        }
    }

    private void RunShortcutCommand(String[] Parts, String Command) {
        if (Parts.length < 2) {
            AddLog("[!] Usage: " + Command + " <id>");
            return;
        }
        try {
            RunAgentCommand(Integer.parseInt(Parts[1]), Command);
        } catch (NumberFormatException E) {
            AddLog("[!] Invalid session ID");
        }
    }

    private void RunSleepCommand(String[] Parts) {
        if (Parts.length < 2) {
            AddLog("[!] Usage: sleep <id> <seconds>");
            return;
        }
        String[] Args = Parts[1].split("\\s+", 2);
        if (Args.length < 2) {
            AddLog("[!] Usage: sleep <id> <seconds>");
            return;
        }
        try {
            RunAgentCommand(Integer.parseInt(Args[0]), "sleep " + Args[1]);
        } catch (NumberFormatException E) {
            AddLog("[!] Invalid session ID");
        }
    }

    private void RunDownloadCommand(String[] Parts) {
        if (Parts.length < 2) {
            AddLog("[!] Usage: download <id> <remote-path>");
            return;
        }
        String[] Args = Parts[1].split("\\s+", 2);
        if (Args.length < 2) {
            AddLog("[!] Usage: download <id> <remote-path>");
            return;
        }
        try {
            RunAgentCommand(Integer.parseInt(Args[0]), "download " + Args[1]);
        } catch (NumberFormatException E) {
            AddLog("[!] Invalid session ID");
        }
    }

    private void RunUploadCommand(String[] Parts) {
        if (Parts.length < 2) {
            AddLog("[!] Usage: upload <id> <local-path> <remote-path>");
            return;
        }
        String[] Args = Parts[1].split("\\s+", 3);
        if (Args.length < 3) {
            AddLog("[!] Usage: upload <id> <local-path> <remote-path>");
            return;
        }
        try {
            RunAgentCommand(Integer.parseInt(Args[0]), "upload " + Args[1] + " " + Args[2]);
        } catch (NumberFormatException E) {
            AddLog("[!] Invalid session ID");
        }
    }

    private void RunAgentCommand(int Sid, String Cmd) {
        if (Server == null || !Server.IsRunning()) {
            AddLog("[!] Server not running");
            return;
        }
        AddLog("[>] SESSION-" + Sid + " → " + Cmd);
        Executors.newSingleThreadExecutor().submit(() -> {
            String[] Result = Server.ExecuteCommand(Sid, Cmd);
            boolean Ok = Boolean.parseBoolean(Result[0]);
            Db.SaveCommandLog(Sid, OperatorName != null ? OperatorName : "gui", Cmd, Result[1], Ok);
            Platform.runLater(() -> AddLog((Ok ? "[+]" : "[!]") + " SESSION-" + Sid + ": " + Result[1]));
        });
    }

    private void ShowSessionInfoLog(String SidText) {
        try {
            int Sid = Integer.parseInt(SidText.trim());
            Optional<Session> Opt = Server.GetSessions().Get(Sid);
            if (Opt.isEmpty()) {
                AddLog("[!] Session not found");
                return;
            }
            Session S = Opt.get();
            AddLog("[*] ID=" + S.GetId() + " Name=" + S.GetDisplayName() + " Type=" + S.GetSessionType().name());
            AddLog("[*] Host=" + S.GetHostname() + " User=" + S.GetUser() + " OS=" + S.GetOs() + " Arch=" + S.GetArch());
            AddLog("[*] IP=" + S.GetAgentIp() + " Key=" + S.GetSessionKey() + " mTLS=" + S.IsMtlsEnabled());
            AddLog("[*] Note=" + Db.GetAgentNote(Sid));
        } catch (Exception E) {
            AddLog("[!] Usage: sysinfo <id>");
        }
    }

    private void SaveNoteCommand(String Body) {
        String[] Args = Body.trim().split("\\s+", 2);
        if (Args.length < 2) {
            AddLog("[!] Usage: note <id> <text>");
            return;
        }
        try {
            int Sid = Integer.parseInt(Args[0]);
            Db.SetAgentNote(Sid, Args[1]);
            AddLog("[+] Note saved for SESSION-" + Sid);
        } catch (NumberFormatException E) {
            AddLog("[!] Invalid session ID");
        }
    }

    private void GetNoteCommand(String SidText) {
        try {
            int Sid = Integer.parseInt(SidText.trim());
            String Note = Db.GetAgentNote(Sid);
            AddLog("[*] Note SESSION-" + Sid + ": " + (Note.isEmpty() ? "(empty)" : Note));
        } catch (Exception E) {
            AddLog("[!] Usage: getnote <id>");
        }
    }

    private void ShowHistoryCommand(String Body) {
        String[] Args = Body == null || Body.isBlank() ? new String[0] : Body.trim().split("\\s+");
        int Sid = Args.length > 0 ? ParseInt(Args[0], 0) : 0;
        int Limit = Args.length > 1 ? ParseInt(Args[1], 25) : 25;
        List<Map<String, Object>> Hist = Db.GetCommandHistory(Sid, Limit);
        AddLog("[*] History entries: " + Hist.size());
        for (Map<String, Object> R : Hist) AddLog("    #" + R.getOrDefault("AgentId", "?") + " " + R.getOrDefault("Operator", "?") + " " + R.getOrDefault("Command", "") + " [" + R.getOrDefault("Timestamp", "") + "]");
    }

    private int ParseInt(String Value, int Def) {
        try {
            return Integer.parseInt(Value.trim());
        } catch (Exception E) {
            return Def;
        }
    }

    private void OpenExecuteWindow() {
        if (SelectedSessionId < 0) {
            ShowAlert("Select a session first");
            return;
        }
        Stage Win = new Stage();
        Win.setTitle("Execute — SESSION-" + SelectedSessionId);
        Win.setWidth(650);
        Win.setHeight(450);
        VBox Layout = new VBox(8);
        Layout.setPadding(new Insets(12));
        Layout.setStyle("-fx-background-color: " + BgColor + ";");
        TextArea Out = new TextArea();
        Out.setEditable(false);
        ApplyTermStyle(Out);
        VBox.setVgrow(Out, Priority.ALWAYS);
        HBox Input = new HBox(8);
        TextField Entry = new TextField();
        Entry.setPromptText("Enter command...");
        ApplyInputStyle(Entry);
        HBox.setHgrow(Entry, Priority.ALWAYS);
        Button Run = StyledButton("Run", RedColor, false);
        final int Sid = SelectedSessionId;
        Runnable Exec = () -> {
            String Cmd = Entry.getText().trim();
            if (Cmd.isEmpty()) return;
            Out.appendText("❯ " + Cmd + "\n");
            Entry.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                String[] Result = Server.ExecuteCommand(Sid, Cmd);
                Platform.runLater(() -> Out.appendText(Result[1] + "\n\n"));
            });
        };
        Run.setOnAction(E -> Exec.run());
        Entry.setOnAction(E -> Exec.run());
        Input.getChildren().addAll(StyledLabel("❯", 11, AccentColor, true), Entry, Run);
        Layout.getChildren().addAll(Out, Input);
        Win.setScene(new Scene(Layout));
        Win.show();
        Entry.requestFocus();
    }

    private void OpenBroadcastWindow() {
        if (Server == null || !Server.IsRunning()) {
            ShowAlert("Server not running");
            return;
        }
        Stage Win = new Stage();
        Win.setTitle("Broadcast Command");
        Win.setWidth(600);
        Win.setHeight(500);
        VBox Layout = new VBox(8);
        Layout.setPadding(new Insets(12));
        Layout.setStyle("-fx-background-color: " + BgColor + ";");

        HBox TargetRow = new HBox(8);
        TargetRow.setAlignment(Pos.CENTER_LEFT);
        Label TargetLbl = StyledLabel("Target:", 9, MutedColor, false);
        TextField TargetField = new TextField();
        TargetField.setPromptText("Session IDs: 1,2,3  or  all");
        ApplyInputStyle(TargetField);
        HBox.setHgrow(TargetField, Priority.ALWAYS);
        TargetRow.getChildren().addAll(TargetLbl, TargetField);

        TextArea Out = new TextArea();
        Out.setEditable(false);
        ApplyTermStyle(Out);
        VBox.setVgrow(Out, Priority.ALWAYS);

        HBox InputRow = new HBox(8);
        InputRow.setAlignment(Pos.CENTER_LEFT);
        TextField CmdField = new TextField();
        CmdField.setPromptText("Enter command...");
        ApplyInputStyle(CmdField);
        HBox.setHgrow(CmdField, Priority.ALWAYS);
        Button RunBtn = StyledButton("⇶ Broadcast", RedColor, false);

        Runnable DoBroadcast = () -> {
            String Target = TargetField.getText().trim();
            String Cmd = CmdField.getText().trim();
            if (Target.isEmpty() || Cmd.isEmpty()) return;
            Out.appendText("⟳ Broadcasting [" + Target + "]: " + Cmd + "\n");
            CmdField.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                Map<Integer, String[]> Results;
                if (Target.equalsIgnoreCase("all")) {
                    Results = Server.BroadcastAll(Cmd);
                } else {
                    java.util.List<Integer> Ids = new java.util.ArrayList<>();
                    for (String S : Target.split(",")) {
                        try {
                            Ids.add(Integer.parseInt(S.trim()));
                        } catch (NumberFormatException Ignored) {}
                    }
                    Results = Server.BroadcastCommand(Ids, Cmd);
                }
                final Map<Integer, String[]> FinalResults = Results;
                Platform.runLater(() -> {
                    for (Map.Entry<Integer, String[]> En : FinalResults.entrySet()) {
                        boolean Ok = Boolean.parseBoolean(En.getValue()[0]);
                        Out.appendText("SESSION-" + En.getKey() + (Ok ? " ✔" : " ✘") + ":\n");
                        Out.appendText(En.getValue()[1] + "\n\n");
                        Db.SaveCommandLog(En.getKey(), "operator", Cmd, En.getValue()[1], Ok);
                    }
                });
            });
        };
        RunBtn.setOnAction(E -> DoBroadcast.run());
        CmdField.setOnAction(E -> DoBroadcast.run());
        InputRow.getChildren().addAll(StyledLabel("❯", 11, AccentColor, true), CmdField, RunBtn);
        Layout.getChildren().addAll(TargetRow, Out, InputRow);
        Win.setScene(new Scene(Layout));
        Win.show();
        CmdField.requestFocus();
    }

    private void KillSelected() {
        if (SelectedSessionId < 0) {
            ShowAlert("Select a session first");
            return;
        }
        Alert Confirm = new Alert(Alert.AlertType.CONFIRMATION, "Terminate SESSION-" + SelectedSessionId + "?");
        Confirm.showAndWait().ifPresent(R -> {
            if (R == ButtonType.OK) {
                Server.RemoveSession(SelectedSessionId);
                SelectedSessionId = -1;
                RefreshSessions();
                if (SelectedAgentLabel != null) SelectedAgentLabel.setText("○ No agent selected");
            }
        });
    }

    private void EventHandler(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case AgentConnected -> {
                AddLog("[+] [" + Data.get("Type") + "] SESSION-" + Data.get("ID") + ": " + Data.get("AgentName") + " (" + Data.get("OS") + ")");
                Platform.runLater(this::RefreshSessions);
            }
            case AgentDisconnected -> {
                AddLog("[-] SESSION-" + Data.get("ID") + " disconnected: " + Data.get("Reason"));
                Platform.runLater(this::RefreshSessions);
            }
            case Error -> AddLog("[!] " + Data.get("Message"));
        }
    }

    private void AddLog(String Msg) {
        String Ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String Entry = "[" + Ts + "] " + Msg;
        LogEntries.add(Entry);
        if (LogEntries.size() > Config.GetMaxLogEntries()) LogEntries.remove(0);
        Platform.runLater(() -> {
            if (LogOutput != null) {
                LogOutput.appendText(Entry + "\n");
            }
        });
    }

    private void WriteTerminal(String Text, boolean IsError) {
        if (TerminalOutput != null) TerminalOutput.appendText(Text);
    }

    private void StartUptimeTimer() {
        Thread Timer = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException Ignored) {}
                if (ServerStartTime != null) {
                    long S = Duration.between(ServerStartTime, Instant.now()).getSeconds();
                    String Up = SystemHelper.FormatUptime(S);
                    Platform.runLater(() -> {
                        if (UptimeLabel != null) UptimeLabel.setText("TIME: " + Up);
                    });
                }
            }
        });
        Timer.setDaemon(true);
        Timer.start();
    }

    private VBox BuildStatCard(String Icon, String Title, String Value, String Hex) {
        VBox Card = new VBox(4);
        Card.setPadding(new Insets(18));
        Card.setStyle("-fx-background-color: linear-gradient(to bottom right, #111114, #070707); -fx-background-radius: 18; -fx-border-color: " + Hex + "; -fx-border-radius: 18; -fx-effect: dropshadow(gaussian, rgba(255,31,61,0.16), 22, 0.22, 0, 10);");
        Card.setMinHeight(104);
        HBox Row = new HBox(8);
        Row.setAlignment(Pos.CENTER_LEFT);
        Row.getChildren().addAll(
            StyledLabel(Icon, 28, Hex, false),
            new VBox(2) {
                {
                    getChildren().addAll(StyledLabel(Title, 10, MutedColor, false), StyledLabel(Value, 24, Hex, true));
                }
            }
        );
        Card.getChildren().add(Row);
        return Card;
    }

    private VBox BuildCard(String Title) {
        VBox Card = new VBox(8);
        Card.setPadding(new Insets(18));
        Card.setStyle("-fx-background-color: linear-gradient(to bottom right, #111114, #090909); -fx-background-radius: 18; -fx-border-color: " + BorderColor + "; -fx-border-radius: 18; -fx-effect: dropshadow(gaussian, rgba(255,31,61,0.12), 18, 0.16, 0, 8);");
        Label TitleLabel = StyledLabel(Title, 12, TextColor, true);
        Separator Sep = new Separator();
        Sep.setStyle("-fx-background-color: " + BorderColor + ";");
        Card.getChildren().addAll(TitleLabel, Sep);
        return Card;
    }

    private Label StyledLabel(String Text, int Size, String Hex, boolean Bold) {
        Label L = new Label(Text);
        L.setFont(Font.font("Segoe UI", Bold ? FontWeight.BOLD : FontWeight.NORMAL, Size));
        L.setTextFill(javafx.scene.paint.Color.web(Hex));
        return L;
    }

    private Button StyledButton(String Text, String Hex, boolean Outline) {
        Button B = new Button(Text);
        B.setStyle("-fx-background-color: " + (Outline ? "transparent" : Hex) + ";" + "-fx-text-fill: " + (Outline ? Hex : "#ffffff") + ";" + "-fx-border-color: " + Hex + ";" + "-fx-border-width: 1.2;" + "-fx-border-radius: 10;" + "-fx-font-family: 'Segoe UI';" + "-fx-font-size: 10px;" + "-fx-font-weight: bold;" + "-fx-padding: 8 16 8 16;" + "-fx-background-radius: 10;" + "-fx-cursor: hand;");
        return B;
    }

    private void ApplyInputStyle(TextField F) {
        F.setStyle("-fx-background-color: " + Card2Color + ";" + "-fx-text-fill: " + TextColor + ";" + "-fx-prompt-text-fill: " + MutedColor + ";" + "-fx-font-family: 'Consolas';" + "-fx-font-size: 11px;" + "-fx-padding: 9 12 9 12;" + "-fx-background-radius: 9;" + "-fx-border-color: " + BorderColor + ";" + "-fx-border-radius: 9;");
    }

    private void ApplyTermStyle(TextArea A) {
        A.setStyle("-fx-background-color: #050812;" + "-fx-control-inner-background: #050812;" + "-fx-text-fill: " + TextColor + ";" + "-fx-highlight-fill: " + RedColor + ";" + "-fx-font-family: 'Consolas';" + "-fx-font-size: 11px;" + "-fx-padding: 12 14 12 14;" + "-fx-background-radius: 12;" + "-fx-border-color: " + BorderColor + ";" + "-fx-border-radius: 12;");
        A.setWrapText(true);
    }

    private Region VDivider() {
        Region R = new Region();
        R.setStyle("-fx-background-color: " + BorderColor + ";");
        R.setPrefWidth(1);
        R.setPrefHeight(20);
        return R;
    }

    private Separator Divider() {
        Separator S = new Separator();
        S.setPadding(new Insets(4, 16, 4, 16));
        S.setStyle("-fx-background-color: " + BorderColor + ";");
        return S;
    }

    private void ShowAlert(String Msg) {
        Alert A = new Alert(Alert.AlertType.WARNING, Msg);
        A.setHeaderText(null);
        A.showAndWait();
    }
}
