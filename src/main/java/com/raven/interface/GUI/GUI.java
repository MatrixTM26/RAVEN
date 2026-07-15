package com.raven.interfaces.GUI;

import com.raven.core.db.TeamDatabase;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.interfaces.GUI.module.core.database.AuthService;
import com.raven.interfaces.GUI.module.core.server.CommandDispatcher;
import com.raven.interfaces.GUI.module.core.server.ServerController;
import com.raven.interfaces.GUI.module.core.session.SessionManager;
import com.raven.interfaces.GUI.module.core.session.SessionRow;
import com.raven.interfaces.GUI.module.UI.button.ButtonFactory;
import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.frame.CardBuilder;
import com.raven.interfaces.GUI.module.UI.frame.StyleHelper;
import com.raven.interfaces.GUI.module.UI.label.LabelFactory;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;

public class RavenGUI extends Application {

    private static ServerConfig Config;
    private static boolean TeamMode = false;

    private AuthService Auth;
    private SessionManager SessionMgr;
    private ServerController ServerCtrl;
    private CommandDispatcher Dispatcher;

    private final ObservableList<SessionRow> SessionRows = FXCollections.observableArrayList();
    private final ObservableList<String> LogEntries = FXCollections.observableArrayList();

    private int SelectedSessionId = -1;

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
    private TextField PortField;
    private Button StartBtn;
    private Button StopBtn;

    public static void Launch(ServerConfig Cfg) {
        Config = Cfg;
        TeamMode = false;
        Application.launch(RavenGUI.class);
    }

    public static void LaunchTeamServer(ServerConfig Cfg) {
        Config = Cfg;
        TeamMode = true;
        Application.launch(RavenGUI.class);
    }

    @Override
    public void start(Stage PrimaryStage) {
        Auth = new AuthService(Config);
        if (TeamMode && !ShowLoginDialog(PrimaryStage)) {
            Platform.exit();
            return;
        }

        PrimaryStage.setTitle("RAVEN");
        PrimaryStage.setWidth(1440);
        PrimaryStage.setHeight(900);
        PrimaryStage.setMinWidth(1100);
        PrimaryStage.setMinHeight(720);

        BorderPane Root = new BorderPane();
        Root.setStyle("-fx-background-color: " + Palette.BG + ";");
        Root.setLeft(BuildSidebar(PrimaryStage));
        Root.setCenter(BuildMainContent());

        Scene MainScene = new Scene(Root);
        MainScene.getStylesheets().add(
            getClass().getResource("/com/raven/interfaces/GUI/styles/css/raven.css").toExternalForm()
        );

        PrimaryStage.setScene(MainScene);
        PrimaryStage.setOnCloseRequest(E -> {
            if (ServerCtrl != null) ServerCtrl.Stop();
            Platform.exit();
        });
        PrimaryStage.show();
        StartUptimeTimer();
    }

    private VBox BuildSidebar(Stage Stage) {
        VBox Sidebar = new VBox();
        Sidebar.setPrefWidth(240);
        Sidebar.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #120006, #050505);" +
            "-fx-border-color: transparent " + Palette.RED + " transparent transparent;" +
            "-fx-border-width: 0 1.5 0 0;"
        );

        VBox Logo = new VBox(8);
        Logo.setPadding(new Insets(28, 22, 18, 22));
        Logo.getChildren().addAll(
            LabelFactory.Danger("◆", 38, true),
            LabelFactory.Primary("RAVEN", 28, true),
            LabelFactory.Muted("Black Ops Console", 10)
        );
        Sidebar.getChildren().add(Logo);
        Sidebar.getChildren().add(StyleHelper.HDivider());

        VBox Meta = new VBox(12);
        Meta.setPadding(new Insets(18, 22, 18, 22));
        Meta.getChildren().addAll(
            BuildMiniInfo("VERSION", "3.0.0"),
            BuildMiniInfo("MODE", Config.GetServerMode().toUpperCase()),
            BuildMiniInfo("INTERFACE", TeamMode ? "TEAM GUI" : "SOLO GUI")
        );
        VBox.setVgrow(Meta, Priority.ALWAYS);
        Sidebar.getChildren().add(Meta);

        VBox Footer = new VBox(8);
        Footer.setPadding(new Insets(14, 22, 18, 22));
        Footer.setStyle("-fx-border-color: " + Palette.BORDER + " transparent transparent transparent; -fx-border-width: 1;");
        StatusLabel = LabelFactory.Danger("● Offline", 11, true);
        Footer.getChildren().addAll(StatusLabel, LabelFactory.Muted("MatrixTM26", 9));
        Sidebar.getChildren().add(Footer);
        return Sidebar;
    }

    private VBox BuildMiniInfo(String Key, String Value) {
        VBox Box = new VBox(3);
        Box.setPadding(new Insets(10, 12, 10, 12));
        Box.setStyle(
            "-fx-background-color: rgba(255,31,61,0.08);" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: rgba(255,31,61,0.45);" +
            "-fx-border-radius: 12;"
        );
        Box.getChildren().addAll(LabelFactory.Muted(Key, 8), LabelFactory.Primary(Value, 11, true));
        return Box;
    }

    private BorderPane BuildMainContent() {
        BorderPane Main = new BorderPane();
        Main.setStyle("-fx-background-color: " + Palette.BG + ";");

        HBox TopBar = new HBox();
        TopBar.setPrefHeight(72);
        TopBar.setAlignment(Pos.CENTER_LEFT);
        TopBar.setStyle(
            "-fx-background-color: linear-gradient(to right, #050505, #120006);" +
            "-fx-border-color: transparent transparent " + Palette.RED + " transparent;" +
            "-fx-border-width: 0 0 1.5 0;"
        );
        TopBar.setPadding(new Insets(0, 24, 0, 24));

        UptimeLabel = LabelFactory.Muted("00:00:00", 9);
        SessionCountLabel = LabelFactory.Primary("⊟ 0 Sessions", 9, false);

        HBox Right = new HBox(16);
        Right.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(Right, Priority.ALWAYS);
        Right.getChildren().addAll(UptimeLabel, StyleHelper.VDivider(), SessionCountLabel);

        VBox Heading = new VBox(2);
        Heading.getChildren().addAll(
            LabelFactory.Primary("RAVEN Operations Console", 20, true),
            LabelFactory.Muted("Listener control • Session ops • CLI-aligned command center", 10)
        );

        if (Auth.GetOperatorName() != null) {
            Label OpLabel = LabelFactory.Primary(
                "Op: " + Auth.GetOperatorName() + " [" + (Auth.GetOperatorRole() != null ? Auth.GetOperatorRole().name() : "?") + "]", 8, false
            );
            TopBar.getChildren().addAll(Heading, OpLabel, Right);
        } else {
            TopBar.getChildren().addAll(Heading, Right);
        }
        Main.setTop(TopBar);

        TabPane Tabs = new TabPane();
        Tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tabs.setStyle("-fx-background-color: " + Palette.BG + "; -fx-tab-min-height: 42px; -fx-tab-max-height: 42px;");
        Tabs.getTabs().addAll(
            Tab("Dashboard",  BuildDashboardTab()),
            Tab("Sessions",   BuildSessionsTab()),
            Tab("Terminal",   BuildTerminalTab()),
            Tab("Commands",   BuildCommandCenterTab()),
            Tab("Logs",       BuildLogsTab()),
            Tab("Settings",   BuildSettingsTab())
        );
        Main.setCenter(Tabs);
        return Main;
    }

    private Tab Tab(String Name, javafx.scene.Node Content) {
        Tab T = new Tab(Name);
        T.setContent(Content);
        return T;
    }

    private ScrollPane BuildDashboardTab() {
        VBox Content = new VBox(12);
        Content.setPadding(new Insets(24));
        Content.setStyle("-fx-background-color: " + Palette.BG + ";");

        GridPane Cards = new GridPane();
        Cards.setHgap(8);
        Cards.setVgap(8);
        Cards.add(CardBuilder.StatCard("⊟", "Total Sessions",  "0", Palette.TEXT),  0, 0);
        Cards.add(CardBuilder.StatCard("◉", "RAVEN",           "0", Palette.RED),   1, 0);
        Cards.add(CardBuilder.StatCard("◎", "Meterpreter",     "0", Palette.TEXT),  2, 0);
        Cards.add(CardBuilder.StatCard("◈", "Reverse Shell",   "0", Palette.RED),   3, 0);
        for (int I = 0; I < 4; I++) {
            ColumnConstraints Col = new ColumnConstraints();
            Col.setPercentWidth(25);
            Cards.getColumnConstraints().add(Col);
        }
        Content.getChildren().add(Cards);

        VBox Info = CardBuilder.Panel("TOOL INFORMATION");
        TextArea InfoText = new TextArea(
            " Author  : MatrixTM26\n" +
            " Github  : MatrixTM26\n" +
            " Version : 3.0\n\n" +
            " Use Sessions for fast actions and Commands for CLI-aligned workflows.\n" +
            " Terminal executes agent commands; Command Center executes server/session utilities.\n\n" +
            " GUI Commands:\n" +
            "   • sessions | status | stats | tasks\n" +
            "   • kill <id> | sysinfo <id> | note <id> <text> | getnote <id>\n" +
            "   • history [id] [limit] | broadcast <cmd>\n" +
            "   • exec <id> <cmd> | whoami <id> | sleep <id> <seconds>\n" +
            "   • screenshot <id> | download <id> <path> | upload <id> <local> <remote>"
        );
        InfoText.setEditable(false);
        InfoText.setPrefHeight(200);
        StyleHelper.ApplyTermStyle(InfoText);
        Info.getChildren().add(InfoText);
        Content.getChildren().add(Info);

        ScrollPane Scroll = new ScrollPane(Content);
        Scroll.setFitToWidth(true);
        Scroll.setStyle("-fx-background-color: " + Palette.BG + ";");
        return Scroll;
    }

    private VBox BuildSessionsTab() {
        VBox Content = new VBox(8);
        Content.setPadding(new Insets(12));
        Content.setStyle("-fx-background-color: " + Palette.BG + ";");

        HBox Toolbar = new HBox(8);
        Toolbar.setAlignment(Pos.CENTER_LEFT);
        TextField Search = new TextField();
        Search.setPromptText("🔍 Search sessions...");
        StyleHelper.ApplyInputStyle(Search);
        Search.setPrefWidth(220);
        HBox.setHgrow(Search, Priority.ALWAYS);

        Button Refresh   = ButtonFactory.Styled("⟳ Refresh",   Palette.TEXT, true);
        Button Execute   = ButtonFactory.Styled("▶ Execute",    Palette.RED,  false);
        Button Broadcast = ButtonFactory.Styled("⇶ Broadcast",  Palette.TEXT, true);
        Button Kill      = ButtonFactory.Styled("⊘ Kill",       Palette.RED,  false);
        Refresh.setOnAction(E -> SessionMgr.Refresh());
        Execute.setOnAction(E -> OpenExecuteWindow());
        Broadcast.setOnAction(E -> OpenBroadcastWindow());
        Kill.setOnAction(E -> KillSelected());
        Toolbar.getChildren().addAll(Search, Refresh, Execute, Broadcast, Kill);
        Content.getChildren().add(Toolbar);

        HBox CmdRow = new HBox(8);
        CmdRow.setAlignment(Pos.CENTER_LEFT);
        CmdRow.setPadding(new Insets(4, 0, 4, 0));
        Label CmdLbl = LabelFactory.Danger("COMMAND:", 9);
        CmdLbl.setStyle("-fx-font-weight: bold;");
        TextField SrvInput = new TextField();
        SrvInput.setPromptText("sessions | status | stats | tasks | kill <id> | sysinfo <id> | history [id] [limit] | note <id> <text> | exec <id> <cmd>");
        StyleHelper.ApplyInputStyle(SrvInput);
        HBox.setHgrow(SrvInput, Priority.ALWAYS);
        Button SrvExec = ButtonFactory.Styled("RUN", Palette.RED, false);
        SrvExec.setOnAction(E -> Dispatcher.Dispatch(SrvInput.getText().trim(), SrvInput));
        SrvInput.setOnAction(E -> Dispatcher.Dispatch(SrvInput.getText().trim(), SrvInput));
        CmdRow.getChildren().addAll(CmdLbl, SrvInput, SrvExec);
        Content.getChildren().add(CmdRow);

        SessionTable = new TableView<>(SessionRows);
        SessionTable.setStyle(
            "-fx-background-color: " + Palette.CARD + ";" +
            "-fx-control-inner-background: " + Palette.CARD + ";" +
            "-fx-table-cell-border-color: " + Palette.BORDER + ";" +
            "-fx-table-header-border-color: " + Palette.BORDER + ";" +
            "-fx-text-fill: " + Palette.TEXT + ";" +
            "-fx-background-radius: 12;"
        );
        String[] ColNames = {"ID", "Type", "Name/Cert", "IP", "OS", "User", "Host", "Session Key"};
        String[] Props    = {"id", "type", "name",      "ip", "os", "user", "host", "joined"};
        for (int I = 0; I < ColNames.length; I++) {
            TableColumn<SessionRow, String> Col = new TableColumn<>(ColNames[I]);
            Col.setCellValueFactory(new PropertyValueFactory<>(Props[I]));
            Col.setStyle("-fx-alignment: CENTER_LEFT; -fx-text-fill: " + Palette.TEXT + ";");
            SessionTable.getColumns().add(Col);
        }
        SessionTable.getSelectionModel().selectedItemProperty().addListener((Obs, Old, New) -> {
            if (New != null) {
                SelectedSessionId = Integer.parseInt(New.getId());
                if (SelectedAgentLabel != null)
                    SelectedAgentLabel.setText("● " + New.getName() + " #" + SelectedSessionId);
            }
        });
        VBox.setVgrow(SessionTable, Priority.ALWAYS);
        Content.getChildren().add(SessionTable);
        return Content;
    }

    private VBox BuildTerminalTab() {
        VBox Content = new VBox(8);
        Content.setPadding(new Insets(12));
        Content.setStyle("-fx-background-color: " + Palette.BG + ";");

        HBox Header = new HBox(8);
        Header.setAlignment(Pos.CENTER_LEFT);
        TermInputField = new TextField();
        TermInputField.setPrefWidth(80);
        StyleHelper.ApplyInputStyle(TermInputField);
        SelectedAgentLabel = LabelFactory.Muted("○ No agent selected", 9);
        Button ClearBtn = ButtonFactory.Styled("Clear", Palette.CARD, false);
        ClearBtn.setOnAction(E -> { if (TerminalOutput != null) TerminalOutput.clear(); });
        HBox.setHgrow(SelectedAgentLabel, Priority.ALWAYS);
        Header.getChildren().addAll(LabelFactory.Muted("Session ID:", 9), TermInputField, SelectedAgentLabel, ClearBtn);
        Content.getChildren().add(Header);

        TerminalOutput = new TextArea();
        TerminalOutput.setEditable(false);
        TerminalOutput.setPrefHeight(400);
        StyleHelper.ApplyTermStyle(TerminalOutput);
        VBox.setVgrow(TerminalOutput, Priority.ALWAYS);
        Content.getChildren().add(TerminalOutput);

        HBox InputRow = new HBox(8);
        InputRow.setAlignment(Pos.CENTER_LEFT);
        CmdInputField = new TextField();
        CmdInputField.setPromptText("Enter command...");
        StyleHelper.ApplyInputStyle(CmdInputField);
        HBox.setHgrow(CmdInputField, Priority.ALWAYS);
        Button SendBtn = ButtonFactory.Styled("Send", Palette.ACCENT, false);
        SendBtn.setOnAction(E -> SendTerminalCommand());
        CmdInputField.setOnAction(E -> SendTerminalCommand());
        InputRow.getChildren().addAll(LabelFactory.Primary("❯", 11, true), CmdInputField, SendBtn);
        Content.getChildren().add(InputRow);
        return Content;
    }

    private VBox BuildCommandCenterTab() {
        VBox Content = new VBox(12);
        Content.setPadding(new Insets(18));
        Content.setStyle("-fx-background-color: " + Palette.BG + ";");

        VBox Help = CardBuilder.Panel("CLI-ALIGNED COMMANDS");
        TextArea HelpText = new TextArea(
            "Server: sessions | status | stats | tasks\n" +
            "Session: exec <id> <command> | kill <id> | sysinfo <id>\n" +
            "Ops: broadcast <command> | whoami <id> | sleep <id> <seconds>\n" +
            "Files: screenshot <id> | download <id> <remote-path> | upload <id> <local-path> <remote-path>\n" +
            "Notes/History: note <id> <text> | getnote <id> | history [id] [limit]"
        );
        HelpText.setEditable(false);
        HelpText.setPrefHeight(120);
        StyleHelper.ApplyTermStyle(HelpText);
        Help.getChildren().add(HelpText);

        HBox InputRow = new HBox(10);
        InputRow.setAlignment(Pos.CENTER_LEFT);
        TextField CommandInput = new TextField();
        CommandInput.setPromptText("Type GUI command matching CLI syntax...");
        StyleHelper.ApplyInputStyle(CommandInput);
        HBox.setHgrow(CommandInput, Priority.ALWAYS);
        Button Run   = ButtonFactory.Styled("EXECUTE",    Palette.RED,  false);
        Button Clear = ButtonFactory.Styled("CLEAR LOGS", Palette.TEXT, true);
        Run.setOnAction(E -> Dispatcher.Dispatch(CommandInput.getText().trim(), CommandInput));
        CommandInput.setOnAction(E -> Dispatcher.Dispatch(CommandInput.getText().trim(), CommandInput));
        Clear.setOnAction(E -> {
            LogEntries.clear();
            if (LogOutput != null) LogOutput.clear();
        });
        InputRow.getChildren().addAll(LabelFactory.Danger("❯", 16, true), CommandInput, Run, Clear);

        TextArea Mirror = new TextArea();
        Mirror.setEditable(false);
        Mirror.setText("Command Center writes output to the Logs tab and mirrors operational events.\nStart the server first from Settings, then use Sessions/Commands.");
        StyleHelper.ApplyTermStyle(Mirror);
        VBox.setVgrow(Mirror, Priority.ALWAYS);

        Content.getChildren().addAll(Help, InputRow, Mirror);
        return Content;
    }

    private VBox BuildLogsTab() {
        VBox Content = new VBox(8);
        Content.setPadding(new Insets(12));
        Content.setStyle("-fx-background-color: " + Palette.BG + ";");

        HBox Toolbar = new HBox(8);
        Toolbar.setAlignment(Pos.CENTER_RIGHT);
        Button ExportBtn = ButtonFactory.Styled("Export", Palette.TEXT, true);
        Button ClearBtn  = ButtonFactory.Styled("Clear",  Palette.RED,  false);
        ClearBtn.setOnAction(E -> {
            LogEntries.clear();
            if (LogOutput != null) LogOutput.clear();
        });
        Toolbar.getChildren().addAll(ExportBtn, ClearBtn);
        Content.getChildren().add(Toolbar);

        LogOutput = new TextArea();
        LogOutput.setEditable(false);
        StyleHelper.ApplyTermStyle(LogOutput);
        VBox.setVgrow(LogOutput, Priority.ALWAYS);
        Content.getChildren().add(LogOutput);
        return Content;
    }

    private ScrollPane BuildSettingsTab() {
        VBox Content = new VBox(16);
        Content.setPadding(new Insets(16));
        Content.setStyle("-fx-background-color: " + Palette.BG + ";");

        VBox ServerCard = CardBuilder.Panel("Server Configuration");
        GridPane Fields = new GridPane();
        Fields.setHgap(16);
        Fields.setVgap(8);
        HostField = new TextField(Config.GetServerHost());
        PortField = new TextField(String.valueOf(Config.GetServerPort()));
        StyleHelper.ApplyInputStyle(HostField);
        StyleHelper.ApplyInputStyle(PortField);
        Fields.add(LabelFactory.Muted("Host:", 9), 0, 0);
        Fields.add(HostField, 1, 0);
        Fields.add(LabelFactory.Muted("Port:", 9), 0, 1);
        Fields.add(PortField, 1, 1);
        ServerCard.getChildren().add(Fields);

        HBox Btns = new HBox(8);
        StartBtn = ButtonFactory.Styled("▶ START SERVER", Palette.RED, false);
        StopBtn  = ButtonFactory.Styled("◼ STOP SERVER",  Palette.RED, false);
        StopBtn.setDisable(true);
        StartBtn.setOnAction(E -> InitServer());
        StopBtn.setOnAction(E -> ServerCtrl.Stop());
        Btns.getChildren().addAll(StartBtn, StopBtn);
        ServerCard.getChildren().add(Btns);

        VBox StatusCard = CardBuilder.Panel("Server Status");
        ServerStatusLabel = LabelFactory.Danger("● OFFLINE", 16, true);
        ServerInfoLabel   = LabelFactory.Muted("Not running", 10);
        StatusCard.getChildren().addAll(ServerStatusLabel, ServerInfoLabel);

        Content.getChildren().addAll(ServerCard, StatusCard);
        ScrollPane Scroll = new ScrollPane(Content);
        Scroll.setFitToWidth(true);
        Scroll.setStyle("-fx-background-color: " + Palette.BG + ";");
        return Scroll;
    }

    private void InitServer() {
        String Host = HostField.getText().trim();
        int Port;
        try {
            Port = Integer.parseInt(PortField.getText().trim());
        } catch (NumberFormatException E) {
            ShowAlert("Invalid port number");
            return;
        }
        ServerCtrl = new ServerController(
            Config, StatusLabel, ServerStatusLabel, ServerInfoLabel, StartBtn, StopBtn,
            this::AddLog, this::EventHandler,
            () -> {
                SessionMgr  = new SessionManager(ServerCtrl.GetServer(), Auth.GetDb(), SessionRows, SessionCountLabel);
                Dispatcher  = new CommandDispatcher(ServerCtrl.GetServer(), Auth.GetDb(), SessionMgr, this::AddLog, Auth.GetOperatorName());
            },
            () -> Platform.runLater(() -> {
                SessionRows.clear();
                SessionCountLabel.setText("⊟ 0 Sessions");
            })
        );
        ServerCtrl.Start(Host, Port);
    }

    private boolean ShowLoginDialog(Stage Owner) {
        Dialog<Boolean> Dlg = new Dialog<>();
        Dlg.setTitle("TeamServer Login");
        Dlg.setHeaderText("RAVEN — Authentication");
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
        ErrLabel.setTextFill(Color.web(Palette.RED));

        Grid.add(new Label("Username:"), 0, 0);
        Grid.add(UserField, 1, 0);
        Grid.add(new Label("Password:"), 0, 1);
        Grid.add(PassField, 1, 1);
        Grid.add(ErrLabel, 1, 2);

        ButtonType LoginBtn = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        Dlg.getDialogPane().getButtonTypes().addAll(LoginBtn, ButtonType.CANCEL);
        Dlg.getDialogPane().setContent(Grid);
        Dlg.getDialogPane().setStyle("-fx-background-color: " + Palette.BG + ";");

        Dlg.setResultConverter(Btn -> {
            if (Btn == LoginBtn) {
                return Auth.Authenticate(UserField.getText().trim(), PassField.getText()) ? true : null;
            }
            return false;
        });

        for (int I = 0; I < 3; I++) {
            java.util.Optional<Boolean> Result = Dlg.showAndWait();
            if (Result.isEmpty() || Boolean.FALSE.equals(Result.get())) return false;
            if (Boolean.TRUE.equals(Result.get())) return true;
            ErrLabel.setText("Invalid credentials");
        }
        return false;
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
            WriteTerminal("[!] Invalid session ID\n");
            return;
        }
        if (ServerCtrl == null || !ServerCtrl.IsRunning()) {
            WriteTerminal("[!] Server not running\n");
            return;
        }
        WriteTerminal("❯ " + Cmd + "\n");
        CmdInputField.clear();
        AddLog("[>] #" + Sid + ": " + Cmd);
        final int FinalSid = Sid;
        Executors.newSingleThreadExecutor().submit(() -> {
            String[] Result = ServerCtrl.GetServer().ExecuteCommand(FinalSid, Cmd);
            boolean Ok = Boolean.parseBoolean(Result[0]);
            Platform.runLater(() -> {
                WriteTerminal(Result[1] + "\n\n");
                AddLog((Ok ? "[+]" : "[!]") + " #" + FinalSid + ": " + (Ok ? "OK" : Result[1]));
            });
        });
    }

    private void OpenExecuteWindow() {
        if (SelectedSessionId < 0) { ShowAlert("Select a session first"); return; }
        Stage Win = new Stage();
        Win.setTitle("Execute — SESSION-" + SelectedSessionId);
        Win.setWidth(650);
        Win.setHeight(450);
        VBox Layout = new VBox(8);
        Layout.setPadding(new Insets(12));
        Layout.setStyle("-fx-background-color: " + Palette.BG + ";");
        TextArea Out = new TextArea();
        Out.setEditable(false);
        StyleHelper.ApplyTermStyle(Out);
        VBox.setVgrow(Out, Priority.ALWAYS);
        TextField Entry = new TextField();
        Entry.setPromptText("Enter command...");
        StyleHelper.ApplyInputStyle(Entry);
        HBox.setHgrow(Entry, Priority.ALWAYS);
        Button Run = ButtonFactory.Styled("Run", Palette.RED, false);
        final int Sid = SelectedSessionId;
        Runnable Exec = () -> {
            String Cmd = Entry.getText().trim();
            if (Cmd.isEmpty()) return;
            Out.appendText("❯ " + Cmd + "\n");
            Entry.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                String[] Result = ServerCtrl.GetServer().ExecuteCommand(Sid, Cmd);
                Platform.runLater(() -> Out.appendText(Result[1] + "\n\n"));
            });
        };
        Run.setOnAction(E -> Exec.run());
        Entry.setOnAction(E -> Exec.run());
        HBox Input = new HBox(8);
        Input.getChildren().addAll(LabelFactory.Primary("❯", 11, true), Entry, Run);
        Layout.getChildren().addAll(Out, Input);
        Win.setScene(new Scene(Layout));
        Win.show();
        Entry.requestFocus();
    }

    private void OpenBroadcastWindow() {
        if (ServerCtrl == null || !ServerCtrl.IsRunning()) { ShowAlert("Server not running"); return; }
        Stage Win = new Stage();
        Win.setTitle("Broadcast Command");
        Win.setWidth(600);
        Win.setHeight(500);
        VBox Layout = new VBox(8);
        Layout.setPadding(new Insets(12));
        Layout.setStyle("-fx-background-color: " + Palette.BG + ";");

        TextField TargetField = new TextField();
        TargetField.setPromptText("Session IDs: 1,2,3  or  all");
        StyleHelper.ApplyInputStyle(TargetField);
        HBox.setHgrow(TargetField, Priority.ALWAYS);
        HBox TargetRow = new HBox(8);
        TargetRow.setAlignment(Pos.CENTER_LEFT);
        TargetRow.getChildren().addAll(LabelFactory.Muted("Target:", 9), TargetField);

        TextArea Out = new TextArea();
        Out.setEditable(false);
        StyleHelper.ApplyTermStyle(Out);
        VBox.setVgrow(Out, Priority.ALWAYS);

        TextField CmdField = new TextField();
        CmdField.setPromptText("Enter command...");
        StyleHelper.ApplyInputStyle(CmdField);
        HBox.setHgrow(CmdField, Priority.ALWAYS);
        Button RunBtn = ButtonFactory.Styled("⇶ Broadcast", Palette.RED, false);

        Runnable DoBroadcast = () -> {
            String Target = TargetField.getText().trim();
            String Cmd = CmdField.getText().trim();
            if (Target.isEmpty() || Cmd.isEmpty()) return;
            Out.appendText("⟳ Broadcasting [" + Target + "]: " + Cmd + "\n");
            CmdField.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                Map<Integer, String[]> Results;
                if (Target.equalsIgnoreCase("all")) {
                    Results = ServerCtrl.GetServer().BroadcastAll(Cmd);
                } else {
                    java.util.List<Integer> Ids = new java.util.ArrayList<>();
                    for (String S : Target.split(",")) {
                        try { Ids.add(Integer.parseInt(S.trim())); }
                        catch (NumberFormatException Ignored) {}
                    }
                    Results = ServerCtrl.GetServer().BroadcastCommand(Ids, Cmd);
                }
                final Map<Integer, String[]> FinalResults = Results;
                Platform.runLater(() -> FinalResults.forEach((Id, Res) -> {
                    boolean Ok = Boolean.parseBoolean(Res[0]);
                    Out.appendText("SESSION-" + Id + (Ok ? " ✔" : " ✘") + ":\n" + Res[1] + "\n\n");
                    Auth.GetDb().SaveCommandLog(Id, "operator", Cmd, Res[1], Ok);
                }));
            });
        };
        RunBtn.setOnAction(E -> DoBroadcast.run());
        CmdField.setOnAction(E -> DoBroadcast.run());
        HBox InputRow = new HBox(8);
        InputRow.setAlignment(Pos.CENTER_LEFT);
        InputRow.getChildren().addAll(LabelFactory.Primary("❯", 11, true), CmdField, RunBtn);
        Layout.getChildren().addAll(TargetRow, Out, InputRow);
        Win.setScene(new Scene(Layout));
        Win.show();
        CmdField.requestFocus();
    }

    private void KillSelected() {
        if (SelectedSessionId < 0) { ShowAlert("Select a session first"); return; }
        Alert Confirm = new Alert(Alert.AlertType.CONFIRMATION, "Terminate SESSION-" + SelectedSessionId + "?");
        Confirm.showAndWait().ifPresent(R -> {
            if (R == ButtonType.OK) {
                SessionMgr.Kill(SelectedSessionId);
                SelectedSessionId = -1;
                if (SelectedAgentLabel != null) SelectedAgentLabel.setText("○ No agent selected");
            }
        });
    }

    private void EventHandler(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case AgentConnected    -> { AddLog("[+] [" + Data.get("Type") + "] SESSION-" + Data.get("ID") + ": " + Data.get("AgentName") + " (" + Data.get("OS") + ")"); Platform.runLater(() -> SessionMgr.Refresh()); }
            case AgentDisconnected -> { AddLog("[-] SESSION-" + Data.get("ID") + " disconnected: " + Data.get("Reason")); Platform.runLater(() -> SessionMgr.Refresh()); }
            case Error             -> AddLog("[!] " + Data.get("Message"));
        }
    }

    private void AddLog(String Msg) {
        String Ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String Entry = "[" + Ts + "] " + Msg;
        LogEntries.add(Entry);
        if (LogEntries.size() > Config.GetMaxLogEntries()) LogEntries.remove(0);
        Platform.runLater(() -> { if (LogOutput != null) LogOutput.appendText(Entry + "\n"); });
    }

    private void WriteTerminal(String Text) {
        if (TerminalOutput != null) TerminalOutput.appendText(Text);
    }

    private void StartUptimeTimer() {
        Thread Timer = new Thread(() -> {
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException Ignored) {}
                if (ServerCtrl != null && ServerCtrl.GetStartTime() != null) {
                    long S = Duration.between(ServerCtrl.GetStartTime(), Instant.now()).getSeconds();
                    String Up = SystemHelper.FormatUptime(S);
                    Platform.runLater(() -> { if (UptimeLabel != null) UptimeLabel.setText("TIME: " + Up); });
                }
            }
        });
        Timer.setDaemon(true);
        Timer.start();
    }

    private void ShowAlert(String Msg) {
        Alert A = new Alert(Alert.AlertType.WARNING, Msg);
        A.setHeaderText(null);
        A.showAndWait();
    }
}
