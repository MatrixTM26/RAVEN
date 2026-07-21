package com.raven.interfaces.GUI;

import com.raven.core.database.TeamDatabase;
import com.raven.core.event.EventManager.EventType;
import com.raven.core.output.Logger;
import com.raven.interfaces.GUI.module.UI.button.ButtonFactory;
import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.frame.CardBuilder;
import com.raven.interfaces.GUI.module.UI.frame.StyleHelper;
import com.raven.interfaces.GUI.module.UI.label.LabelFactory;
import com.raven.interfaces.GUI.module.core.database.AuthService;
import com.raven.interfaces.GUI.module.core.server.CommandDispatcher;
import com.raven.interfaces.GUI.module.core.server.ServerController;
import com.raven.interfaces.GUI.module.core.session.SessionManager;
import com.raven.interfaces.GUI.module.core.session.SessionRow;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import java.util.Optional;
import java.util.concurrent.Executors;

public class GUI extends Application {

    private static ServerConfig Config;
    private static boolean TeamMode = false;

    private AuthService Auth;
    private SessionManager SessionMgr;
    private ServerController ServerCtrl;
    private CommandDispatcher Dispatcher;

    private final ObservableList<SessionRow> SessionRows = FXCollections.observableArrayList();
    private final ObservableList<String> LogEntries      = FXCollections.observableArrayList();

    private int SelectedSid = -1;

    private Label StatusLabel;
    private Label UptimeLabel;
    private Label SessionCountLabel;
    private TableView<SessionRow> SessionTable;
    private TextArea TerminalOutput;
    private TextArea LogOutput;
    private TextField TermCmdField;
    private TextField SessionIdField;
    private Label SelectedLabel;
    private Label ServerStatusLabel;
    private Label ServerInfoLabel;
    private TextField HostField;
    private TextField PortField;
    private Button StartBtn;
    private Button StopBtn;

    public static void Launch(ServerConfig cfg) {
        Config   = cfg;
        TeamMode = false;
        Application.launch(GUI.class);
    }

    public static void LaunchTeam(ServerConfig cfg) {
        Config   = cfg;
        TeamMode = true;
        Application.launch(GUI.class);
    }

    @Override
    public void start(Stage stage) {
        Auth = new AuthService(Config);
        if (TeamMode && !ShowLogin(stage)) { Platform.exit(); return; }

        stage.setTitle("RAVEN");
        stage.setWidth(1360);
        stage.setHeight(860);
        stage.setMinWidth(1080);
        stage.setMinHeight(680);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + Palette.BG + ";");
        root.setLeft(BuildSidebar());
        root.setCenter(BuildCenter());
        root.setBottom(BuildStatusBar());

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
            getClass().getResource("/com/raven/interfaces/styles/css/raven.css").toExternalForm()
        );

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> { if (ServerCtrl != null) ServerCtrl.Stop(); Platform.exit(); });
        stage.show();
        StartUptimeThread();
    }

    private VBox BuildSidebar() {
        VBox sb = new VBox(0);
        sb.setPrefWidth(220);
        sb.setStyle("-fx-background-color:" + Palette.BG_DEEP + ";" +
                    "-fx-border-color:transparent " + Palette.BORDER + " transparent transparent;" +
                    "-fx-border-width:0 1 0 0;");

        VBox brand = new VBox(3);
        brand.setPadding(new Insets(16, 12, 12, 12));
        brand.setStyle("-fx-border-color:transparent transparent " + Palette.BORDER + " transparent; -fx-border-width:0 0 1 0;");
        Label name = LabelFactory.Of("RAVEN", 13, Palette.TEXT_HEAD, true);
        Label ver  = LabelFactory.Of("v3.0", 9, Palette.TEXT_DIM, false);
        Label sub  = LabelFactory.Of("Command and Control", 9, Palette.TEXT_DIM, false);
        ver.setStyle("-fx-background-color:" + Palette.SURFACE + "; -fx-padding:2 5 2 5; " +
                     "-fx-border-color:" + Palette.BORDER + "; -fx-border-width:1;");
        brand.getChildren().addAll(name, ver, sub);
        sb.getChildren().add(brand);

        sb.getChildren().add(SidebarSection("GENERAL"));
        sb.getChildren().add(SidebarItem("Overview",        true));
        sb.getChildren().add(SidebarItem("Sessions",        false));
        sb.getChildren().add(SidebarItem("Terminal",        false));
        sb.getChildren().add(SidebarItem("Command Center",  false));
        sb.getChildren().add(SidebarItem("Logs",            false));
        sb.getChildren().add(SidebarSection("CONFIGURATION"));
        sb.getChildren().add(SidebarItem("Settings",        false));

        Region spring = new Region();
        VBox.setVgrow(spring, Priority.ALWAYS);
        sb.getChildren().add(spring);

        VBox footer = new VBox(4);
        footer.setPadding(new Insets(10, 12, 12, 12));
        footer.setStyle("-fx-border-color:" + Palette.BORDER + " transparent transparent transparent; -fx-border-width:1 0 0 0;");
        StatusLabel = LabelFactory.Of("Offline", 10, Palette.DANGER, false);
        footer.getChildren().addAll(StatusLabel, LabelFactory.Of("MatrixTM26", 9, Palette.TEXT_DIM, false));
        sb.getChildren().add(footer);
        return sb;
    }

    private Label SidebarSection(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + Palette.TEXT_DIM + "; -fx-font-size:9px; -fx-font-weight:bold;" +
                   "-fx-padding:12 12 4 12;");
        return l;
    }

    private Label SidebarItem(String text, boolean active) {
        Label l = new Label(text);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setStyle(active ? activeItem() : defaultItem());
        if (!active) {
            l.setOnMouseEntered(e -> l.setStyle(hoverItem()));
            l.setOnMouseExited(e -> l.setStyle(defaultItem()));
        }
        return l;
    }

    private String defaultItem() {
        return "-fx-background-color:transparent; -fx-text-fill:" + Palette.TEXT_MUTED + ";" +
               "-fx-font-size:11px; -fx-padding:5 12 5 12; -fx-cursor:hand;" +
               "-fx-background-radius:0;";
    }
    private String hoverItem() {
        return "-fx-background-color:" + Palette.BG_ALT + "; -fx-text-fill:" + Palette.TEXT + ";" +
               "-fx-font-size:11px; -fx-padding:5 12 5 12; -fx-cursor:hand;" +
               "-fx-background-radius:0;";
    }
    private String activeItem() {
        return "-fx-background-color:" + Palette.ACCENT + "; -fx-text-fill:#ffffff;" +
               "-fx-font-size:11px; -fx-padding:5 12 5 12;" +
               "-fx-background-radius:0;";
    }

    private VBox BuildCenter() {
        VBox center = new VBox(0);
        center.setStyle("-fx-background-color:" + Palette.BG + ";");

        HBox topbar = BuildTopBar();
        center.getChildren().add(topbar);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("tab-pane");
        VBox.setVgrow(tabs, Priority.ALWAYS);

        tabs.getTabs().addAll(
            MakeTab("Overview",       BuildDashboard()),
            MakeTab("Sessions",       BuildSessions()),
            MakeTab("Terminal",       BuildTerminal()),
            MakeTab("Command Center", BuildCommands()),
            MakeTab("Logs",           BuildLogs()),
            MakeTab("Settings",       BuildSettings())
        );
        center.getChildren().add(tabs);
        return center;
    }

    private HBox BuildTopBar() {
        HBox bar = new HBox(12);
        bar.setPrefHeight(48);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 16, 0, 16));
        bar.setStyle("-fx-background-color:" + Palette.BG + ";" +
                     "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                     "-fx-border-width:0 0 1 0;");

        VBox heading = new VBox(2);
        heading.getChildren().addAll(
            LabelFactory.Of("RAVEN Operations Console", 13, Palette.TEXT_HEAD, true),
            LabelFactory.Of("Listener control   Session ops   CLI-aligned command center", 10, Palette.TEXT_DIM, false)
        );

        Label badge = new Label("app is running in development mode");
        badge.setStyle("-fx-background-color:" + Palette.WARNING + "; -fx-text-fill:#000000;" +
                       "-fx-font-size:9px; -fx-font-weight:bold; -fx-padding:3 8 3 8;" +
                       "-fx-background-radius:0;");

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        UptimeLabel       = LabelFactory.Of("00:00:00", 10, Palette.TEXT_DIM, false);
        SessionCountLabel = LabelFactory.Of("0 sessions", 10, Palette.TEXT_MUTED, false);

        bar.getChildren().addAll(heading, badge, spring, UptimeLabel, StyleHelper.VDivider(), SessionCountLabel);

        if (Auth.GetOperatorName() != null) {
            Label op = LabelFactory.Of("  " + Auth.GetOperatorName() + "  [" +
                        (Auth.GetOperatorRole() != null ? Auth.GetOperatorRole().name() : "?") + "]",
                        10, Palette.TEXT_MUTED, false);
            bar.getChildren().addAll(StyleHelper.VDivider(), op);
        }
        return bar;
    }

    private Tab MakeTab(String name, javafx.scene.Node content) {
        Tab t = new Tab(name);
        t.setContent(content);
        return t;
    }

    private ScrollPane BuildDashboard() {
        VBox content = new VBox(0);
        content.setStyle("-fx-background-color:" + Palette.BG + ";");

        GridPane cards = new GridPane();
        cards.setHgap(0);
        cards.setVgap(0);
        cards.setStyle("-fx-border-color:transparent transparent " + Palette.BORDER + " transparent; -fx-border-width:0 0 1 0;");
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cards.getColumnConstraints().add(cc);
        }

        VBox c0 = CardBuilder.StatCard("SESSIONS",      "0",  Palette.ACCENT);
        VBox c1 = CardBuilder.StatCard("RAVEN",         "0",  Palette.SUCCESS);
        VBox c2 = CardBuilder.StatCard("METERPRETER",   "0",  Palette.TEXT_MUTED);
        VBox c3 = CardBuilder.StatCard("REVERSE SHELL", "0",  Palette.WARNING);

        String cardStyle = "-fx-border-color:" + Palette.BORDER + "; -fx-border-width:0 1 0 0;";
        c0.setStyle(c0.getStyle() + cardStyle);
        c1.setStyle(c1.getStyle() + cardStyle);
        c2.setStyle(c2.getStyle() + cardStyle);
        cards.add(c0, 0, 0);
        cards.add(c1, 1, 0);
        cards.add(c2, 2, 0);
        cards.add(c3, 3, 0);
        content.getChildren().add(cards);

        VBox infoSection = new VBox(0);
        infoSection.setPadding(new Insets(16));
        infoSection.setStyle("-fx-background-color:" + Palette.BG + ";");

        VBox infoCard = new VBox(0);
        infoCard.setStyle("-fx-background-color:" + Palette.BG_ALT + ";" +
                          "-fx-border-color:" + Palette.BORDER + ";" +
                          "-fx-border-width:1;");

        VBox infoHeader = new VBox();
        infoHeader.setPadding(new Insets(8, 12, 8, 12));
        infoHeader.setStyle("-fx-background-color:" + Palette.SURFACE + ";" +
                            "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                            "-fx-border-width:0 0 1 0;");
        infoHeader.getChildren().add(LabelFactory.Of("TOOL INFORMATION", 11, Palette.TEXT_HEAD, true));

        TextArea info = new TextArea(
            " Author  : MatrixTM26\n" +
            " Github  : MatrixTM26\n" +
            " Version : 3.0\n\n" +
            " Sessions tab — quick actions on connected agents (Execute, Broadcast, Kill, filter by search)\n" +
            " Terminal tab — interactive agent shell; set session ID then type commands\n" +
            " Command Center — CLI-aligned server/session utilities with full output log\n\n" +
            " Available commands:\n" +
            "   sessions | status | stats | tasks | kill <id> | sysinfo <id>\n" +
            "   history [id] [limit] | note <id> <text> | getnote <id>\n" +
            "   broadcast <cmd> | exec <id> <cmd> | whoami <id>\n" +
            "   sleep <id> <sec> | screenshot <id> | download <id> <path> | upload <id> <l> <r>"
        );
        info.setEditable(false);
        info.setPrefHeight(210);
        StyleHelper.ApplyTerm(info);
        info.setStyle(info.getStyle() + "-fx-border-color:transparent; -fx-border-width:0;");

        infoCard.getChildren().addAll(infoHeader, info);
        infoSection.getChildren().add(infoCard);
        content.getChildren().add(infoSection);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:" + Palette.BG + ";");
        return sp;
    }

    private VBox BuildSessions() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:" + Palette.BG + ";");

        HBox toolbar = new HBox(6);
        toolbar.setPadding(new Insets(7, 12, 7, 12));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color:" + Palette.BG + ";" +
                         "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                         "-fx-border-width:0 0 1 0;");

        TextField search = new TextField();
        search.setPromptText("Filter sessions...");
        StyleHelper.ApplyInput(search);
        search.setPrefWidth(200);

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        Button refreshBtn   = ButtonFactory.Of("Refresh",   ButtonFactory.Variant.DEFAULT);
        Button executeBtn   = ButtonFactory.Of("Execute",   ButtonFactory.Variant.ACCENT);
        Button broadcastBtn = ButtonFactory.Of("Broadcast", ButtonFactory.Variant.DEFAULT);
        Button killBtn      = ButtonFactory.Of("Kill",      ButtonFactory.Variant.DANGER);

        refreshBtn.setOnAction(e -> { if (SessionMgr != null) SessionMgr.Refresh(); });
        executeBtn.setOnAction(e -> OpenExecuteWindow());
        broadcastBtn.setOnAction(e -> OpenBroadcastWindow());
        killBtn.setOnAction(e -> KillSelected());

        toolbar.getChildren().addAll(search, spring, refreshBtn, executeBtn, broadcastBtn, killBtn);
        root.getChildren().add(toolbar);

        HBox cmdBar = new HBox(6);
        cmdBar.setPadding(new Insets(6, 10, 6, 10));
        cmdBar.setAlignment(Pos.CENTER_LEFT);
        cmdBar.setStyle("-fx-background-color:" + Palette.BG_DEEP + ";" +
                        "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                        "-fx-border-width:0 0 1 0;");

        Label prompt = LabelFactory.Of(">", 13, Palette.ACCENT, true);
        prompt.setStyle(prompt.getStyle() + "-fx-font-family:Consolas;");

        TextField srvInput = new TextField();
        srvInput.setPromptText("sessions  |  status  |  kill <id>  |  exec <id> <cmd>  |  sysinfo <id>  |  history  |  broadcast <cmd>");
        StyleHelper.ApplyInput(srvInput);
        HBox.setHgrow(srvInput, Priority.ALWAYS);

        Button runBtn = ButtonFactory.Of("Run", ButtonFactory.Variant.ACCENT);
        runBtn.setOnAction(e -> { if (Dispatcher != null) Dispatcher.Dispatch(srvInput.getText().trim(), srvInput); });
        srvInput.setOnAction(e -> { if (Dispatcher != null) Dispatcher.Dispatch(srvInput.getText().trim(), srvInput); });

        cmdBar.getChildren().addAll(prompt, srvInput, runBtn);
        root.getChildren().add(cmdBar);

        SessionTable = new TableView<>();
        FilteredList<SessionRow> filtered = new FilteredList<>(SessionRows, p -> true);
        search.textProperty().addListener((obs, o, n) ->
            filtered.setPredicate(row -> n == null || n.isBlank()
                || row.getName().toLowerCase().contains(n.toLowerCase())
                || row.getIp().contains(n)
                || row.getUser().toLowerCase().contains(n.toLowerCase())
                || row.getHost().toLowerCase().contains(n.toLowerCase())));
        SessionTable.setItems(filtered);
        SessionTable.getStyleClass().add("session-table");
        VBox.setVgrow(SessionTable, Priority.ALWAYS);

        String[] colNames = {"ID", "Type", "Name / Cert", "IP", "OS", "User", "Host", "Session Key"};
        String[] props    = {"id", "type", "name",        "ip", "os", "user", "host", "joined"};
        for (int i = 0; i < colNames.length; i++) {
            TableColumn<SessionRow, String> col = new TableColumn<>(colNames[i]);
            col.setCellValueFactory(new PropertyValueFactory<>(props[i]));
            SessionTable.getColumns().add(col);
        }
        SessionTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                SelectedSid = Integer.parseInt(n.getId());
                if (SelectedLabel != null)
                    SelectedLabel.setText(n.getName() + "  #" + SelectedSid);
            }
        });

        root.getChildren().add(SessionTable);
        return root;
    }

    private VBox BuildTerminal() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:" + Palette.BG + ";");

        HBox toolbar = new HBox(8);
        toolbar.setPadding(new Insets(7, 12, 7, 12));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color:" + Palette.BG + ";" +
                         "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                         "-fx-border-width:0 0 1 0;");

        Label sidLabel = LabelFactory.Of("Session ID", 10, Palette.TEXT_MUTED, false);
        SessionIdField = new TextField();
        SessionIdField.setPrefWidth(70);
        StyleHelper.ApplyInput(SessionIdField);

        Region div = StyleHelper.VDivider();
        div.setPrefHeight(18);

        SelectedLabel = LabelFactory.Of("No session selected", 10, Palette.TEXT_MUTED, false);
        HBox.setHgrow(SelectedLabel, Priority.ALWAYS);

        Button clearBtn = ButtonFactory.Of("Clear", ButtonFactory.Variant.FLAT);
        clearBtn.setOnAction(e -> { if (TerminalOutput != null) TerminalOutput.clear(); });

        toolbar.getChildren().addAll(sidLabel, SessionIdField, div, SelectedLabel, clearBtn);
        root.getChildren().add(toolbar);

        TerminalOutput = new TextArea();
        TerminalOutput.setEditable(false);
        StyleHelper.ApplyTerm(TerminalOutput);
        VBox.setVgrow(TerminalOutput, Priority.ALWAYS);
        root.getChildren().add(TerminalOutput);

        HBox cmdBar = new HBox(6);
        cmdBar.setPadding(new Insets(6, 10, 6, 10));
        cmdBar.setAlignment(Pos.CENTER_LEFT);
        cmdBar.setStyle("-fx-background-color:" + Palette.BG_DEEP + ";" +
                        "-fx-border-color:" + Palette.BORDER + " transparent transparent transparent;" +
                        "-fx-border-width:1 0 0 0;");

        Label prompt = LabelFactory.Of(">", 13, Palette.ACCENT, true);
        prompt.setStyle(prompt.getStyle() + "-fx-font-family:Consolas;");

        TermCmdField = new TextField();
        TermCmdField.setPromptText("Enter command...");
        StyleHelper.ApplyInput(TermCmdField);
        HBox.setHgrow(TermCmdField, Priority.ALWAYS);

        Button sendBtn = ButtonFactory.Of("Send", ButtonFactory.Variant.ACCENT);
        sendBtn.setOnAction(e -> SendTerminalCmd());
        TermCmdField.setOnAction(e -> SendTerminalCmd());

        cmdBar.getChildren().addAll(prompt, TermCmdField, sendBtn);
        root.getChildren().add(cmdBar);
        return root;
    }

    private VBox BuildCommands() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:" + Palette.BG + ";");

        VBox refCard = new VBox(0);
        refCard.setStyle("-fx-background-color:" + Palette.BG_ALT + ";" +
                         "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                         "-fx-border-width:0 0 1 0;");
        VBox refHeader = new VBox();
        refHeader.setPadding(new Insets(8, 12, 8, 12));
        refHeader.setStyle("-fx-background-color:" + Palette.SURFACE + ";" +
                           "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                           "-fx-border-width:0 0 1 0;");
        refHeader.getChildren().add(LabelFactory.Of("REFERENCE", 11, Palette.TEXT_HEAD, true));

        TextArea help = new TextArea(
            "sessions  |  status  |  stats  |  tasks\n" +
            "kill <id>  |  exec <id> <cmd>  |  sysinfo <id>  |  whoami <id>\n" +
            "broadcast <cmd>  |  sleep <id> <sec>  |  screenshot <id>\n" +
            "download <id> <remote>  |  upload <id> <local> <remote>\n" +
            "note <id> <text>  |  getnote <id>  |  history [id] [limit]"
        );
        help.setEditable(false);
        help.setPrefHeight(96);
        StyleHelper.ApplyTerm(help);
        help.setStyle(help.getStyle() + "-fx-border-color:transparent; -fx-border-width:0;");
        refCard.getChildren().addAll(refHeader, help);
        root.getChildren().add(refCard);

        HBox cmdBar = new HBox(6);
        cmdBar.setPadding(new Insets(6, 10, 6, 10));
        cmdBar.setAlignment(Pos.CENTER_LEFT);
        cmdBar.setStyle("-fx-background-color:" + Palette.BG_DEEP + ";" +
                        "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                        "-fx-border-width:0 0 1 0;");

        Label prompt = LabelFactory.Of(">", 13, Palette.ACCENT, true);
        prompt.setStyle(prompt.getStyle() + "-fx-font-family:Consolas;");

        TextField cmdInput = new TextField();
        cmdInput.setPromptText("Type command...");
        StyleHelper.ApplyInput(cmdInput);
        HBox.setHgrow(cmdInput, Priority.ALWAYS);

        Button runBtn   = ButtonFactory.Of("Execute",    ButtonFactory.Variant.ACCENT);
        Button clearBtn = ButtonFactory.Of("Clear logs", ButtonFactory.Variant.OUTLINED);

        TextArea mirror = new TextArea();
        mirror.setEditable(false);
        StyleHelper.ApplyTerm(mirror);
        VBox.setVgrow(mirror, Priority.ALWAYS);

        runBtn.setOnAction(e -> {
            if (Dispatcher != null) Dispatcher.Dispatch(cmdInput.getText().trim(), cmdInput);
        });
        cmdInput.setOnAction(e -> {
            if (Dispatcher != null) Dispatcher.Dispatch(cmdInput.getText().trim(), cmdInput);
        });
        clearBtn.setOnAction(e -> { LogEntries.clear(); if (LogOutput != null) LogOutput.clear(); mirror.clear(); });

        cmdBar.getChildren().addAll(prompt, cmdInput, runBtn, clearBtn);
        root.getChildren().addAll(cmdBar, mirror);
        return root;
    }

    private VBox BuildLogs() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:" + Palette.BG + ";");

        HBox toolbar = new HBox(6);
        toolbar.setPadding(new Insets(7, 12, 7, 12));
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setStyle("-fx-background-color:" + Palette.BG + ";" +
                         "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                         "-fx-border-width:0 0 1 0;");

        Button exportBtn = ButtonFactory.Of("Export", ButtonFactory.Variant.OUTLINED);
        Button clearBtn  = ButtonFactory.Of("Clear",  ButtonFactory.Variant.DANGER);
        clearBtn.setOnAction(e -> { LogEntries.clear(); if (LogOutput != null) LogOutput.clear(); });
        toolbar.getChildren().addAll(exportBtn, clearBtn);
        root.getChildren().add(toolbar);

        LogOutput = new TextArea();
        LogOutput.setEditable(false);
        StyleHelper.ApplyTerm(LogOutput);
        LogOutput.setStyle(LogOutput.getStyle() + "-fx-border-color:transparent; -fx-border-width:0;");
        VBox.setVgrow(LogOutput, Priority.ALWAYS);
        root.getChildren().add(LogOutput);
        return root;
    }

    private ScrollPane BuildSettings() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color:" + Palette.BG + ";");

        VBox serverCard = new VBox(0);
        serverCard.setStyle("-fx-background-color:" + Palette.BG_ALT + ";" +
                            "-fx-border-color:" + Palette.BORDER + "; -fx-border-width:1;");
        VBox serverHeader = new VBox();
        serverHeader.setPadding(new Insets(8, 12, 8, 12));
        serverHeader.setStyle("-fx-background-color:" + Palette.SURFACE + ";" +
                              "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                              "-fx-border-width:0 0 1 0;");
        serverHeader.getChildren().add(LabelFactory.Of("Server Configuration", 11, Palette.TEXT_HEAD, true));

        VBox serverBody = new VBox(10);
        serverBody.setPadding(new Insets(12));

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(8);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(60);
        c0.setMaxWidth(80);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        fields.getColumnConstraints().addAll(c0, c1);

        HostField = new TextField(Config.GetServerHost());
        PortField = new TextField(String.valueOf(Config.GetServerPort()));
        StyleHelper.ApplyInput(HostField);
        StyleHelper.ApplyInput(PortField);

        fields.add(LabelFactory.Of("Host", 10, Palette.TEXT_MUTED, false), 0, 0);
        fields.add(HostField, 1, 0);
        fields.add(LabelFactory.Of("Port", 10, Palette.TEXT_MUTED, false), 0, 1);
        fields.add(PortField, 1, 1);

        StartBtn = ButtonFactory.Of("Start server", ButtonFactory.Variant.SUCCESS);
        StopBtn  = ButtonFactory.Of("Stop server",  ButtonFactory.Variant.DANGER);
        StopBtn.setDisable(true);
        StartBtn.setOnAction(e -> InitServer());
        StopBtn.setOnAction(e -> ServerCtrl.Stop());

        HBox btns = new HBox(6);
        btns.getChildren().addAll(StartBtn, StopBtn);

        serverBody.getChildren().addAll(fields, btns);
        serverCard.getChildren().addAll(serverHeader, serverBody);

        VBox statusCard = new VBox(0);
        statusCard.setStyle("-fx-background-color:" + Palette.BG_ALT + ";" +
                            "-fx-border-color:" + Palette.BORDER + "; -fx-border-width:1;");
        VBox statusHeader = new VBox();
        statusHeader.setPadding(new Insets(8, 12, 8, 12));
        statusHeader.setStyle("-fx-background-color:" + Palette.SURFACE + ";" +
                              "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                              "-fx-border-width:0 0 1 0;");
        statusHeader.getChildren().add(LabelFactory.Of("Status", 11, Palette.TEXT_HEAD, true));

        VBox statusBody = new VBox(6);
        statusBody.setPadding(new Insets(12));

        ServerStatusLabel = LabelFactory.Of("Offline", 11, Palette.DANGER, false);
        ServerInfoLabel   = LabelFactory.Of("Not running", 10, Palette.TEXT_DIM, false);

        HBox row1 = new HBox(10);
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.getChildren().addAll(LabelFactory.Of("Server", 10, Palette.TEXT_MUTED, false), ServerStatusLabel);
        HBox row2 = new HBox(10);
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.getChildren().addAll(LabelFactory.Of("Address", 10, Palette.TEXT_MUTED, false), ServerInfoLabel);
        statusBody.getChildren().addAll(row1, row2);
        statusCard.getChildren().addAll(statusHeader, statusBody);

        content.getChildren().addAll(serverCard, statusCard);
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:" + Palette.BG + ";");
        return sp;
    }

    private HBox BuildStatusBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(4, 12, 4, 12));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color:" + Palette.BG_DEEP + ";" +
                     "-fx-border-color:" + Palette.BORDER + " transparent transparent transparent;" +
                     "-fx-border-width:1 0 0 0;");
        bar.getChildren().addAll(
            LabelFactory.Of("RAVEN v3.0", 9, Palette.TEXT_DIM, false),
            StyleHelper.VDivider(),
            LabelFactory.Of("MatrixTM26", 9, Palette.TEXT_DIM, false)
        );
        return bar;
    }

    private void InitServer() {
        String host = HostField.getText().trim();
        int port;
        try { port = Integer.parseInt(PortField.getText().trim()); }
        catch (NumberFormatException e) { Alert(Alert.AlertType.WARNING, "Invalid port number"); return; }

        ServerCtrl = new ServerController(
            Config, StatusLabel, ServerStatusLabel, ServerInfoLabel, StartBtn, StopBtn,
            this::AddLog, this::OnEvent,
            () -> {
                SessionMgr = new SessionManager(ServerCtrl.GetServer(), Auth.GetDb(), SessionRows, SessionCountLabel);
                Dispatcher = new CommandDispatcher(ServerCtrl.GetServer(), Auth.GetDb(), SessionMgr, this::AddLog, Auth.GetOperatorName());
            },
            () -> Platform.runLater(() -> {
                SessionRows.clear();
                SessionCountLabel.setText("0 sessions");
            })
        );
        ServerCtrl.Start(host, port);
    }

    private boolean ShowLogin(Stage owner) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("RAVEN — Authentication");
        dlg.setHeaderText("TeamServer Login");
        dlg.initOwner(owner);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16, 16, 10, 16));
        grid.setStyle("-fx-background-color:" + Palette.BG + ";");

        TextField userField = new TextField();
        userField.setPromptText("Username");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Password");
        Label errLabel = new Label("");
        errLabel.setTextFill(Color.web(Palette.DANGER));
        StyleHelper.ApplyInput(userField);
        StyleHelper.ApplyInput(passField);

        grid.add(new Label("Username"), 0, 0);
        grid.add(userField, 1, 0);
        grid.add(new Label("Password"), 0, 1);
        grid.add(passField, 1, 1);
        grid.add(errLabel, 1, 2);

        ButtonType loginType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(loginType, ButtonType.CANCEL);
        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().setStyle("-fx-background-color:" + Palette.BG + ";");

        dlg.setResultConverter(btn -> {
            if (btn == loginType) return Auth.Authenticate(userField.getText().trim(), passField.getText()) ? true : null;
            return false;
        });

        for (int i = 0; i < 3; i++) {
            Optional<Boolean> res = dlg.showAndWait();
            if (res.isEmpty() || Boolean.FALSE.equals(res.get())) return false;
            if (Boolean.TRUE.equals(res.get())) return true;
            errLabel.setText("Invalid credentials");
        }
        return false;
    }

    private void SendTerminalCmd() {
        if (TermCmdField == null || SessionIdField == null) return;
        String sidStr = SessionIdField.getText().trim();
        String cmd    = TermCmdField.getText().trim();
        if (sidStr.isEmpty() || cmd.isEmpty()) return;
        int sid;
        try { sid = Integer.parseInt(sidStr); }
        catch (NumberFormatException e) { WriteTerminal("[!] Invalid session ID\n"); return; }
        if (ServerCtrl == null || !ServerCtrl.IsRunning()) { WriteTerminal("[!] Server not running\n"); return; }
        WriteTerminal("> " + cmd + "\n");
        TermCmdField.clear();
        AddLog("> #" + sid + ": " + cmd);
        final int fSid = sid;
        Executors.newSingleThreadExecutor().submit(() -> {
            String[] result = ServerCtrl.GetServer().ExecuteCommand(fSid, cmd);
            boolean ok = Boolean.parseBoolean(result[0]);
            Platform.runLater(() -> {
                WriteTerminal(result[1] + "\n\n");
                AddLog(ok ? "[+] OK" : "[!] " + result[1]);
            });
        });
    }

    private void OpenExecuteWindow() {
        if (SelectedSid < 0) { Alert(Alert.AlertType.WARNING, "Select a session first"); return; }
        Stage win = new Stage();
        win.setTitle("Execute — SESSION-" + SelectedSid);
        win.setWidth(640);
        win.setHeight(480);

        VBox layout = new VBox(0);
        layout.setStyle("-fx-background-color:" + Palette.BG + ";");

        TextArea out = new TextArea();
        out.setEditable(false);
        StyleHelper.ApplyTerm(out);
        out.setStyle(out.getStyle() + "-fx-border-color:transparent; -fx-border-width:0;");
        VBox.setVgrow(out, Priority.ALWAYS);

        HBox cmdBar = new HBox(6);
        cmdBar.setPadding(new Insets(6, 10, 6, 10));
        cmdBar.setAlignment(Pos.CENTER_LEFT);
        cmdBar.setStyle("-fx-background-color:" + Palette.BG_DEEP + ";" +
                        "-fx-border-color:" + Palette.BORDER + " transparent transparent transparent;" +
                        "-fx-border-width:1 0 0 0;");

        Label prompt = LabelFactory.Of(">", 13, Palette.ACCENT, true);
        prompt.setStyle(prompt.getStyle() + "-fx-font-family:Consolas;");
        TextField entry = new TextField();
        entry.setPromptText("Enter command...");
        StyleHelper.ApplyInput(entry);
        HBox.setHgrow(entry, Priority.ALWAYS);
        Button runBtn = ButtonFactory.Of("Run", ButtonFactory.Variant.ACCENT);

        final int sid = SelectedSid;
        Runnable exec = () -> {
            String c = entry.getText().trim();
            if (c.isEmpty()) return;
            out.appendText("> " + c + "\n");
            entry.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                String[] res = ServerCtrl.GetServer().ExecuteCommand(sid, c);
                Platform.runLater(() -> out.appendText(res[1] + "\n\n"));
            });
        };
        runBtn.setOnAction(e -> exec.run());
        entry.setOnAction(e -> exec.run());
        cmdBar.getChildren().addAll(prompt, entry, runBtn);
        layout.getChildren().addAll(out, cmdBar);
        win.setScene(new Scene(layout));
        win.show();
        entry.requestFocus();
    }

    private void OpenBroadcastWindow() {
        if (ServerCtrl == null || !ServerCtrl.IsRunning()) { Alert(Alert.AlertType.WARNING, "Server not running"); return; }
        Stage win = new Stage();
        win.setTitle("Broadcast Command");
        win.setWidth(600);
        win.setHeight(480);

        VBox layout = new VBox(0);
        layout.setStyle("-fx-background-color:" + Palette.BG + ";");

        HBox targetBar = new HBox(8);
        targetBar.setPadding(new Insets(7, 10, 7, 10));
        targetBar.setAlignment(Pos.CENTER_LEFT);
        targetBar.setStyle("-fx-background-color:" + Palette.BG + ";" +
                           "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
                           "-fx-border-width:0 0 1 0;");
        TextField targetField = new TextField();
        targetField.setPromptText("Target: 1,2,3  or  all");
        StyleHelper.ApplyInput(targetField);
        HBox.setHgrow(targetField, Priority.ALWAYS);
        targetBar.getChildren().addAll(LabelFactory.Of("Target", 10, Palette.TEXT_MUTED, false), targetField);
        layout.getChildren().add(targetBar);

        TextArea out = new TextArea();
        out.setEditable(false);
        StyleHelper.ApplyTerm(out);
        out.setStyle(out.getStyle() + "-fx-border-color:transparent; -fx-border-width:0;");
        VBox.setVgrow(out, Priority.ALWAYS);
        layout.getChildren().add(out);

        HBox cmdBar = new HBox(6);
        cmdBar.setPadding(new Insets(6, 10, 6, 10));
        cmdBar.setAlignment(Pos.CENTER_LEFT);
        cmdBar.setStyle("-fx-background-color:" + Palette.BG_DEEP + ";" +
                        "-fx-border-color:" + Palette.BORDER + " transparent transparent transparent;" +
                        "-fx-border-width:1 0 0 0;");
        Label prompt = LabelFactory.Of(">", 13, Palette.ACCENT, true);
        prompt.setStyle(prompt.getStyle() + "-fx-font-family:Consolas;");
        TextField cmdField = new TextField();
        cmdField.setPromptText("Enter command...");
        StyleHelper.ApplyInput(cmdField);
        HBox.setHgrow(cmdField, Priority.ALWAYS);
        Button runBtn = ButtonFactory.Of("Broadcast", ButtonFactory.Variant.ACCENT);

        Runnable doBcast = () -> {
            String target = targetField.getText().trim();
            String cmd    = cmdField.getText().trim();
            if (target.isEmpty() || cmd.isEmpty()) return;
            out.appendText("Broadcast [" + target + "]  " + cmd + "\n");
            cmdField.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                Map<Integer, String[]> results;
                if (target.equalsIgnoreCase("all")) {
                    results = ServerCtrl.GetServer().BroadcastAll(cmd);
                } else {
                    java.util.List<Integer> ids = new java.util.ArrayList<>();
                    for (String s : target.split(",")) { try { ids.add(Integer.parseInt(s.trim())); } catch (Exception ignored) {} }
                    results = ServerCtrl.GetServer().BroadcastCommand(ids, cmd);
                }
                final Map<Integer, String[]> fr = results;
                Platform.runLater(() -> fr.forEach((id, res) -> {
                    boolean ok = Boolean.parseBoolean(res[0]);
                    out.appendText("  SESSION-" + id + "  " + (ok ? "OK" : "ERR") + "\n" + res[1] + "\n\n");
                    Auth.GetDb().SaveCommandLog(id, "operator", cmd, res[1], ok);
                }));
            });
        };
        runBtn.setOnAction(e -> doBcast.run());
        cmdField.setOnAction(e -> doBcast.run());
        cmdBar.getChildren().addAll(prompt, cmdField, runBtn);
        layout.getChildren().add(cmdBar);
        win.setScene(new Scene(layout));
        win.show();
        cmdField.requestFocus();
    }

    private void KillSelected() {
        if (SelectedSid < 0) { Alert(Alert.AlertType.WARNING, "Select a session first"); return; }
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
            Alert.AlertType.CONFIRMATION, "Terminate SESSION-" + SelectedSid + "?");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                if (SessionMgr != null) SessionMgr.Kill(SelectedSid);
                SelectedSid = -1;
                if (SelectedLabel != null) SelectedLabel.setText("No session selected");
            }
        });
    }

    private void OnEvent(EventType type, Map<String, Object> data) {
        switch (type) {
            case AgentConnected    -> {
                AddLog("[+] [" + data.get("Type") + "] SESSION-" + data.get("ID") + ": " + data.get("AgentName") + " (" + data.get("OS") + ")");
                Platform.runLater(() -> { if (SessionMgr != null) SessionMgr.Refresh(); });
            }
            case AgentDisconnected -> {
                AddLog("[-] SESSION-" + data.get("ID") + " disconnected: " + data.get("Reason"));
                Platform.runLater(() -> { if (SessionMgr != null) SessionMgr.Refresh(); });
            }
            case Error -> AddLog("[!] " + data.get("Message"));
        }
    }

    private void AddLog(String msg) {
        String ts    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + ts + "]  " + msg;
        LogEntries.add(entry);
        if (LogEntries.size() > Config.GetMaxLogEntries()) LogEntries.remove(0);
        Platform.runLater(() -> { if (LogOutput != null) LogOutput.appendText(entry + "\n"); });
    }

    private void WriteTerminal(String text) {
        if (TerminalOutput != null) TerminalOutput.appendText(text);
    }

    private void StartUptimeThread() {
        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                if (ServerCtrl != null && ServerCtrl.GetStartTime() != null) {
                    long secs = Duration.between(ServerCtrl.GetStartTime(), Instant.now()).getSeconds();
                    String up = SystemHelper.FormatUptime(secs);
                    Platform.runLater(() -> { if (UptimeLabel != null) UptimeLabel.setText(up); });
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void Alert(Alert.AlertType type, String msg) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(type, msg);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
