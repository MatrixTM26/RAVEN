package com.raven.interfaces.GUI;

import com.raven.core.event.EventManager.EventType;
import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.frame.StyleHelper;
import com.raven.interfaces.GUI.module.core.database.AuthService;
import com.raven.interfaces.GUI.module.core.server.CommandDispatcher;
import com.raven.interfaces.GUI.module.core.server.ServerController;
import com.raven.interfaces.GUI.module.core.session.SessionManager;
import com.raven.interfaces.GUI.module.core.session.SessionRow;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

public class GUI extends Application {

    /* ── Static launch state ─────────────────────────────────── */
    private static ServerConfig Config;
    private static boolean TeamMode = false;

    public static void Launch(ServerConfig cfg) {
        Config = cfg;
        TeamMode = false;
        Application.launch(GUI.class);
    }

    public static void LaunchTeam(ServerConfig cfg) {
        Config = cfg;
        TeamMode = true;
        Application.launch(GUI.class);
    }

    /* ── Runtime fields ──────────────────────────────────────── */
    private AuthService Auth;
    private SessionManager SessionMgr;
    private ServerController ServerCtrl;
    private CommandDispatcher Dispatcher;

    private final ObservableList<SessionRow> SessionRows = FXCollections.observableArrayList();
    private final ObservableList<String> LogEntries = FXCollections.observableArrayList();
    private int SelectedSid = -1;

    /* ── UI references ───────────────────────────────────────── */
    private Label StatusDot;
    private Label UptimeLabel;
    private Label SessionCountLabel;
    private Label ServerStatusLabel;
    private Label ServerInfoLabel;
    private Label SelectedLabel;
    private Label SrvToggleLabel;

    private TableView<SessionRow> SessionTable;
    private TextArea TerminalOutput;
    private TextArea LogOutput;
    private TextField TermCmdField;
    private TextField SessionIdField;
    private TextField HostField;
    private TextField PortField;

    private ToggleButton ServerToggle;
    private VBox Sidebar;
    private StackPane ContentArea;
    private boolean SidebarCollapsed = false;

    /* ── Nav pages ───────────────────────────────────────────── */
    private final Map<String, Node> Pages = new LinkedHashMap<>();
    private String ActivePage = "Overview";
    private final Map<String, Label> NavItems = new LinkedHashMap<>();

    /* ── Material Icons codepoints (ligature names) ──────────── */
    private static final String I_DASHBOARD = "\uE871"; // dashboard
    private static final String I_DEVICES = "\uE32B"; // devices
    private static final String I_TERMINAL = "\uEB8E"; // terminal
    private static final String I_CODE = "\uE86F"; // code
    private static final String I_LIST = "\uE896"; // list_alt
    private static final String I_SETTINGS = "\uE8B8"; // settings
    private static final String I_REFRESH = "\uE5D5"; // refresh
    private static final String I_PLAY = "\uE037"; // play_arrow
    private static final String I_STOP = "\uE047"; // stop
    private static final String I_DELETE = "\uE872"; // delete
    private static final String I_SEND = "\uE163"; // send
    private static final String I_CLEAR = "\uE14C"; // clear
    private static final String I_SEARCH = "\uE8B6"; // search
    private static final String I_MENU = "\uE5D2"; // menu
    private static final String I_WIFI = "\uE63E"; // wifi
    private static final String I_WIFI_OFF = "\uE648"; // wifi_off
    private static final String I_BROADCAST = "\uE0C9"; // cell_tower / rss_feed
    private static final String I_EXPORT = "\uE2C4"; // save_alt
    private static final String I_CLOSE = "\uE5CD"; // close
    private static final String I_CIRCLE = "\uEF4A"; // circle (filled)
    private static final String I_DNS = "\uE875"; // dns

    /* ══════════════════════════════════════════════════════════
       start()
       ══════════════════════════════════════════════════════════ */
    @Override
    public void start(Stage stage) {
        Auth = new AuthService(Config);
        if (TeamMode && !ShowLogin(stage)) {
            Platform.exit();
            return;
        }

        stage.setTitle("RAVEN");
        stage.setWidth(1400);
        stage.setHeight(880);
        stage.setMinWidth(900);
        stage.setMinHeight(580);

        /* Load Material Icons font */
        try {
            Font.loadFont(getClass().getResourceAsStream("/fonts/MaterialIcons-Regular.ttf"), 16);
        } catch (Exception ignored) {}

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + Palette.BG + ";");

        Sidebar = BuildSidebar();
        ContentArea = new StackPane();
        ContentArea.setStyle("-fx-background-color:" + Palette.BG + ";");

        BuildPages();
        ShowPage("Overview");

        VBox centerCol = new VBox(0);
        VBox.setVgrow(ContentArea, Priority.ALWAYS);
        centerCol.getChildren().addAll(BuildTopBar(), ContentArea, BuildStatusBar());

        root.setLeft(Sidebar);
        root.setCenter(centerCol);

        Scene scene = new Scene(root);
        URL css = getClass().getResource("styles/css/raven.css");
        if (css == null) css = getClass().getResource("/com/raven/interfaces/GUI/styles/css/raven.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (ServerCtrl != null) ServerCtrl.Stop();
            Platform.exit();
        });
        stage.show();
        StartUptimeThread();
    }

    /* ══════════════════════════════════════════════════════════
       SIDEBAR
       ══════════════════════════════════════════════════════════ */
    private VBox BuildSidebar() {
        VBox sb = new VBox(0);
        sb.setPrefWidth(210);
        sb.getStyleClass().add("sidebar");

        /* Brand row */
        HBox brand = new HBox(10);
        brand.getStyleClass().add("sidebar-brand");
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox brandText = new VBox(3);
        Label brandName = new Label("RAVEN");
        brandName.getStyleClass().add("sidebar-brand-name");
        Label brandSub = new Label("Command and Control");
        brandSub.getStyleClass().add("sidebar-brand-sub");
        Label ver = new Label("v3.0");
        ver.getStyleClass().add("sidebar-version");
        brandText.getChildren().addAll(brandName, brandSub);
        HBox.setHgrow(brandText, Priority.ALWAYS);

        Button burger = new Button(I_MENU);
        burger.getStyleClass().add("sidebar-burger");
        burger.setOnAction(e -> ToggleSidebar(sb));

        brand.getChildren().addAll(MatIcon(I_DNS, Palette.BLUE, 18), brandText, burger);
        sb.getChildren().add(brand);

        /* Nav sections */
        sb.getChildren().add(SectionLabel("GENERAL"));
        addNav(sb, "Overview", I_DASHBOARD, Palette.BLUE);
        addNav(sb, "Sessions", I_DEVICES, Palette.GREEN);
        addNav(sb, "Terminal", I_TERMINAL, Palette.PINK);
        addNav(sb, "Command Center", I_CODE, Palette.GREY);
        addNav(sb, "Logs", I_LIST, Palette.GREY);
        sb.getChildren().add(SectionLabel("CONFIGURATION"));
        addNav(sb, "Settings", I_SETTINGS, Palette.GREY);

        Region spring = new Region();
        VBox.setVgrow(spring, Priority.ALWAYS);
        sb.getChildren().add(spring);

        /* Footer */
        VBox footer = new VBox(4);
        footer.getStyleClass().add("sidebar-footer");
        StatusDot = new Label(I_CIRCLE + "  Offline");
        StatusDot.setStyle("-fx-text-fill:" + Palette.DANGER + "; -fx-font-size:11px; -fx-font-family:'Material Icons','Segoe UI'; -fx-graphic-text-gap:0;");
        Label author = new Label("MatrixTM26");
        author.getStyleClass().add("text-dim");
        author.setStyle("-fx-font-size:9px; -fx-text-fill:" + Palette.TEXT_DIM + ";");
        footer.getChildren().addAll(StatusDot, author);
        sb.getChildren().add(footer);
        return sb;
    }

    private void addNav(VBox sb, String name, String icon, String color) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(7, 14, 7, 14));
        item.setMaxWidth(Double.MAX_VALUE);
        item.setCursor(javafx.scene.Cursor.HAND);

        Label iconLbl = MatIcon(icon, color, 15);
        iconLbl.setMinWidth(18);
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-text-fill:" + Palette.TEXT_MUTED + "; -fx-font-size:12px;");
        item.getChildren().addAll(iconLbl, nameLbl);

        item.setOnMouseEntered(e -> {
            if (!name.equals(ActivePage)) {
                item.setStyle("-fx-background-color:#1e1e1e;");
                nameLbl.setStyle("-fx-text-fill:" + Palette.TEXT + "; -fx-font-size:12px;");
            }
        });
        item.setOnMouseExited(e -> {
            if (!name.equals(ActivePage)) {
                item.setStyle("-fx-background-color:transparent;");
                nameLbl.setStyle("-fx-text-fill:" + Palette.TEXT_MUTED + "; -fx-font-size:12px;");
            }
        });
        item.setOnMouseClicked(e -> ShowPage(name));

        /* store refs so we can update active state */
        NavItems.put(name, nameLbl);
        sb.getChildren().add(item);
        /* store HBox too for bg change */
        item.setUserData(name);
        item.setStyle("-fx-background-color:transparent;");
    }

    private void ShowPage(String name) {
        ActivePage = name;
        /* update nav styles */
        Sidebar.getChildren().forEach(child -> {
            if (child instanceof HBox hb && name.equals(hb.getUserData())) {
                hb.setStyle("-fx-background-color:rgba(129,212,250,0.07);" + "-fx-border-color:transparent transparent transparent #81d4fa;" + "-fx-border-width:0 0 0 2;");
                hb.getChildren().forEach(c -> {
                    if (c instanceof Label l && !l.getFont().getFamily().contains("Material")) l.setStyle("-fx-text-fill:" + Palette.BLUE + "; -fx-font-size:12px; -fx-font-weight:bold;");
                });
            } else if (child instanceof HBox hb && hb.getUserData() instanceof String) {
                hb.setStyle("-fx-background-color:transparent;");
                hb.getChildren().forEach(c -> {
                    if (c instanceof Label l && !l.getFont().getFamily().contains("Material")) l.setStyle("-fx-text-fill:" + Palette.TEXT_MUTED + "; -fx-font-size:12px;");
                });
            }
        });
        Node page = Pages.get(name);
        if (page != null) {
            ContentArea.getChildren().setAll(page);
        }
    }

    private void ToggleSidebar(VBox sb) {
        SidebarCollapsed = !SidebarCollapsed;
        double target = SidebarCollapsed ? 48 : 210;
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(180), new KeyValue(sb.prefWidthProperty(), target, Interpolator.EASE_BOTH)));
        tl.play();
        sb.getChildren().forEach(child -> {
            if (child instanceof VBox || child instanceof Region) return;
            if (child instanceof Label l && l.getStyleClass().contains("sidebar-section")) {
                l.setVisible(!SidebarCollapsed);
                l.setManaged(!SidebarCollapsed);
            }
        });
    }

    /* ══════════════════════════════════════════════════════════
       TOP BAR
       ══════════════════════════════════════════════════════════ */
    private HBox BuildTopBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox heading = new VBox(2);
        Label title = new Label("RAVEN Operations Console");
        title.getStyleClass().add("topbar-title");
        Label sub = new Label("Listener control  ·  Session ops  ·  CLI-aligned command center");
        sub.getStyleClass().add("topbar-sub");
        heading.getChildren().addAll(title, sub);

        Label badge = new Label("development mode");
        badge.getStyleClass().add("topbar-badge");

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        UptimeLabel = new Label("00:00:00");
        UptimeLabel.getStyleClass().add("status-bar-text");

        Region vd = new Region();
        vd.getStyleClass().add("v-div");
        vd.setPrefHeight(16);

        SessionCountLabel = new Label("0 sessions");
        SessionCountLabel.getStyleClass().add("status-bar-accent");

        bar.getChildren().addAll(heading, badge, spring, UptimeLabel, vd, SessionCountLabel);

        if (Auth.GetOperatorName() != null) {
            Region vd2 = new Region();
            vd2.getStyleClass().add("v-div");
            vd2.setPrefHeight(16);
            Label op = new Label(Auth.GetOperatorName() + (Auth.GetOperatorRole() != null ? "  [" + Auth.GetOperatorRole().name() + "]" : ""));
            op.getStyleClass().add("text-muted");
            op.setStyle("-fx-font-size:10px;");
            bar.getChildren().addAll(vd2, op);
        }
        return bar;
    }

    /* ══════════════════════════════════════════════════════════
       STATUS BAR
       ══════════════════════════════════════════════════════════ */
    private HBox BuildStatusBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("statusbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label dot = new Label(I_CIRCLE);
        dot.setStyle("-fx-font-family:'Material Icons'; -fx-font-size:8px; -fx-text-fill:" + Palette.DANGER + ";");
        Label text = new Label("RAVEN v3.0  ·  MatrixTM26");
        text.getStyleClass().add("status-bar-text");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label mode = new Label(Config.GetServerMode().toUpperCase());
        mode.getStyleClass().add("status-bar-text");

        bar.getChildren().addAll(dot, text, sp, mode);
        return bar;
    }

    /* ══════════════════════════════════════════════════════════
       BUILD ALL PAGES
       ══════════════════════════════════════════════════════════ */
    private void BuildPages() {
        Pages.put("Overview", BuildOverview());
        Pages.put("Sessions", BuildSessions());
        Pages.put("Terminal", BuildTerminal());
        Pages.put("Command Center", BuildCommands());
        Pages.put("Logs", BuildLogs());
        Pages.put("Settings", BuildSettings());
    }

    /* ── OVERVIEW ─────────────────────────────────────────────── */
    private VBox BuildOverview() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + Palette.BG + ";");

        /* Stat bar */
        GridPane stats = new GridPane();
        stats.setHgap(0);
        stats.setVgap(0);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            stats.getColumnConstraints().add(cc);
        }
        stats.add(StatCard("SESSIONS", "0", Palette.BLUE, I_DEVICES), 0, 0);
        stats.add(StatCard("RAVEN", "0", Palette.GREEN, I_WIFI), 1, 0);
        stats.add(StatCard("METERPRETER", "0", Palette.GREY, I_CIRCLE), 2, 0);
        stats.add(StatCard("REVERSE SHELL", "0", Palette.PINK, I_TERMINAL), 3, 0);
        page.getChildren().add(stats);

        Region div = new Region();
        div.getStyleClass().add("h-div");
        page.getChildren().add(div);

        /* Info card */
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setFitToHeight(false);
        sp.setStyle("-fx-background-color:" + Palette.BG + ";");
        VBox.setVgrow(sp, Priority.ALWAYS);

        VBox inner = new VBox(12);
        inner.setPadding(new Insets(16));
        inner.setStyle("-fx-background-color:" + Palette.BG + ";");

        VBox infoCard = PanelCard("TOOL INFORMATION", I_LIST, Palette.GREY);
        TextArea info = new TextArea(" Author  : MatrixTM26\n Github  : MatrixTM26\n Version : 3.0\n\n" + " Sessions  — quick actions: Execute, Broadcast, Kill, filter\n" + " Terminal  — interactive agent shell; set session ID then type commands\n" + " Commands  — CLI-aligned server/session utilities with full output log\n\n" + " Available commands:\n" + "   sessions | status | stats | tasks | kill <id> | sysinfo <id>\n" + "   history [id] [limit] | note <id> <text> | getnote <id>\n" + "   broadcast <cmd> | exec <id> <cmd> | whoami <id>\n" + "   sleep <id> <sec> | screenshot <id> | download <id> <path> | upload <id> <l> <r>");
        info.setEditable(false);
        info.setPrefHeight(210);
        StyleHelper.ApplyTerm(info);
        PanelBody(infoCard).getChildren().add(info);
        inner.getChildren().add(infoCard);
        sp.setContent(inner);
        page.getChildren().add(sp);
        return page;
    }

    /* ── SESSIONS ─────────────────────────────────────────────── */
    private VBox BuildSessions() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + Palette.BG + ";");

        /* Toolbar */
        HBox toolbar = new HBox(6);
        toolbar.getStyleClass().add("toolbar-bar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        TextField search = new TextField();
        search.setPromptText(I_SEARCH + "  Filter sessions...");
        search.getStyleClass().add("search-field");
        search.setPrefWidth(220);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button refreshBtn = Btn(I_REFRESH + " Refresh", "btn btn-default");
        Button executeBtn = Btn(I_PLAY + " Execute", "btn btn-accent");
        Button broadcastBtn = Btn(I_BROADCAST + " Broadcast", "btn btn-default");
        Button killBtn = Btn(I_DELETE + " Kill", "btn btn-danger");
        refreshBtn.setOnAction(e -> {
            if (SessionMgr != null) SessionMgr.Refresh();
        });
        executeBtn.setOnAction(e -> OpenExecuteWindow());
        broadcastBtn.setOnAction(e -> OpenBroadcastWindow());
        killBtn.setOnAction(e -> KillSelected());
        toolbar.getChildren().addAll(search, sp, refreshBtn, executeBtn, broadcastBtn, killBtn);
        page.getChildren().add(toolbar);

        /* Command bar */
        HBox cmdBar = new HBox(6);
        cmdBar.getStyleClass().add("cmd-bar");
        cmdBar.setAlignment(Pos.CENTER_LEFT);
        Label prompt = new Label(">");
        prompt.getStyleClass().add("cmd-prompt");
        TextField srvInput = new TextField();
        srvInput.setPromptText("sessions | status | kill <id> | exec <id> <cmd> | sysinfo <id> | history | broadcast <cmd>");
        srvInput.getStyleClass().add("input-field");
        HBox.setHgrow(srvInput, Priority.ALWAYS);
        Button runBtn = Btn("Run", "btn btn-accent");
        runBtn.setOnAction(e -> {
            if (Dispatcher != null) Dispatcher.Dispatch(srvInput.getText().trim(), srvInput);
        });
        srvInput.setOnAction(e -> {
            if (Dispatcher != null) Dispatcher.Dispatch(srvInput.getText().trim(), srvInput);
        });
        cmdBar.getChildren().addAll(prompt, srvInput, runBtn);
        page.getChildren().add(cmdBar);

        /* Table + log vertical split */
        SplitPane vSplit = new SplitPane();
        vSplit.setOrientation(Orientation.VERTICAL);
        vSplit.setDividerPositions(0.65);
        VBox.setVgrow(vSplit, Priority.ALWAYS);

        /* Table */
        SessionTable = new TableView<>();
        SessionTable.getStyleClass().add("session-table");
        FilteredList<SessionRow> filtered = new FilteredList<>(SessionRows, p -> true);
        search.textProperty().addListener((obs, o, n) -> filtered.setPredicate(row -> n == null || n.isBlank() || row.getName().toLowerCase().contains(n.toLowerCase()) || row.getIp().contains(n) || row.getUser().toLowerCase().contains(n.toLowerCase()) || row.getHost().toLowerCase().contains(n.toLowerCase())));
        SessionTable.setItems(filtered);

        String[] cols = { "ID", "Type", "Name / Cert", "IP", "OS", "User", "Host", "Session Key" };
        String[] props = { "id", "type", "name", "ip", "os", "user", "host", "joined" };
        for (int i = 0; i < cols.length; i++) {
            TableColumn<SessionRow, String> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(new PropertyValueFactory<>(props[i]));
            SessionTable.getColumns().add(col);
        }
        SessionTable.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, o, n) -> {
                if (n != null) {
                    SelectedSid = Integer.parseInt(n.getId());
                    if (SelectedLabel != null) SelectedLabel.setText(n.getName() + "  #" + SelectedSid);
                }
            });
        SessionTable.setPlaceholder(PlaceholderLabel("No active sessions"));
        SessionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        /* Bottom log pane */
        VBox logPane = new VBox(0);
        logPane.setStyle("-fx-background-color:" + Palette.TERM_BG + ";");
        HBox logHeader = new HBox(8);
        logHeader.setAlignment(Pos.CENTER_LEFT);
        logHeader.setPadding(new Insets(5, 10, 5, 10));
        logHeader.setStyle("-fx-background-color:#141414; -fx-border-color:transparent transparent #2e2e2e transparent; -fx-border-width:0 0 1 0;");
        logHeader.getChildren().addAll(MatIcon(I_LIST, Palette.GREY, 12), LblSm("OUTPUT", Palette.TEXT_MUTED), Region(true), BtnIcon(I_CLEAR, "Clear", e -> LogOutput.clear()));
        LogOutput = new TextArea();
        LogOutput.setEditable(false);
        StyleHelper.ApplyTerm(LogOutput);
        VBox.setVgrow(LogOutput, Priority.ALWAYS);
        logPane.getChildren().addAll(logHeader, LogOutput);

        vSplit.getItems().addAll(SessionTable, logPane);
        page.getChildren().add(vSplit);
        return page;
    }

    /* ── TERMINAL ─────────────────────────────────────────────── */
    private VBox BuildTerminal() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + Palette.TERM_BG + ";");

        /* Header toolbar */
        HBox toolbar = new HBox(8);
        toolbar.getStyleClass().add("toolbar-bar");
        toolbar.setStyle("-fx-background-color:#141414; -fx-border-color:transparent transparent #2e2e2e transparent; -fx-border-width:0 0 1 0; -fx-padding:6 12 6 12; -fx-spacing:8;");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label sidLbl = LblSm("Session ID", Palette.TEXT_MUTED);
        SessionIdField = new TextField();
        SessionIdField.setPrefWidth(72);
        SessionIdField.getStyleClass().add("input-field");

        Region vd = new Region();
        vd.getStyleClass().add("v-div");
        vd.setPrefHeight(16);

        SelectedLabel = new Label("No session selected");
        SelectedLabel.getStyleClass().add("text-muted");
        SelectedLabel.setStyle("-fx-font-size:11px;");
        HBox.setHgrow(SelectedLabel, Priority.ALWAYS);

        Button clearBtn = BtnIcon(I_CLEAR, "Clear", e -> {
            if (TerminalOutput != null) TerminalOutput.clear();
        });
        toolbar.getChildren().addAll(sidLbl, SessionIdField, vd, SelectedLabel, clearBtn);
        page.getChildren().add(toolbar);

        /* Terminal output — resizable via SplitPane trick: fills all space */
        TerminalOutput = new TextArea();
        TerminalOutput.setEditable(false);
        StyleHelper.ApplyTerm(TerminalOutput);
        TerminalOutput.setStyle(TerminalOutput.getStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(TerminalOutput, Priority.ALWAYS);
        page.getChildren().add(TerminalOutput);

        /* Input bar */
        HBox inputBar = new HBox(6);
        inputBar.getStyleClass().add("input-bar");
        inputBar.setAlignment(Pos.CENTER_LEFT);
        Label prompt = new Label(">");
        prompt.getStyleClass().add("cmd-prompt");
        TermCmdField = new TextField();
        TermCmdField.setPromptText("Enter command...");
        TermCmdField.getStyleClass().add("input-field");
        HBox.setHgrow(TermCmdField, Priority.ALWAYS);
        Button sendBtn = Btn(I_SEND + " Send", "btn btn-accent");
        sendBtn.setOnAction(e -> SendTerminalCmd());
        TermCmdField.setOnAction(e -> SendTerminalCmd());
        inputBar.getChildren().addAll(prompt, TermCmdField, sendBtn);
        page.getChildren().add(inputBar);
        return page;
    }

    /* ── COMMAND CENTER ───────────────────────────────────────── */
    private VBox BuildCommands() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + Palette.BG + ";");

        /* Reference card */
        VBox refCard = new VBox(0);
        refCard.setStyle("-fx-background-color:#1e1e1e; -fx-border-color:transparent transparent #2e2e2e transparent; -fx-border-width:0 0 1 0;");
        HBox refH = new HBox(8);
        refH.setAlignment(Pos.CENTER_LEFT);
        refH.setPadding(new Insets(6, 12, 6, 12));
        refH.setStyle("-fx-background-color:#141414;");
        refH.getChildren().addAll(MatIcon(I_CODE, Palette.GREY, 12), LblSm("REFERENCE", Palette.TEXT_MUTED));
        TextArea help = new TextArea("sessions | status | stats | tasks\n" + "kill <id>  |  exec <id> <cmd>  |  sysinfo <id>  |  whoami <id>\n" + "broadcast <cmd>  |  sleep <id> <sec>  |  screenshot <id>\n" + "download <id> <remote>  |  upload <id> <local> <remote>\n" + "note <id> <text>  |  getnote <id>  |  history [id] [limit]");
        help.setEditable(false);
        help.setPrefHeight(92);
        StyleHelper.ApplyTerm(help);
        help.setStyle(help.getStyle() + "-fx-border-color:transparent;");
        refCard.getChildren().addAll(refH, help);
        page.getChildren().add(refCard);

        /* Command bar */
        HBox cmdBar = new HBox(6);
        cmdBar.getStyleClass().add("cmd-bar");
        cmdBar.setAlignment(Pos.CENTER_LEFT);
        Label prompt = new Label(">");
        prompt.getStyleClass().add("cmd-prompt");
        TextField cmdInput = new TextField();
        cmdInput.setPromptText("Type command...");
        cmdInput.getStyleClass().add("input-field");
        HBox.setHgrow(cmdInput, Priority.ALWAYS);
        Button runBtn = Btn(I_PLAY + " Execute", "btn btn-accent");
        Button clearBtn = Btn(I_CLEAR + " Clear", "btn btn-default");

        TextArea mirror = new TextArea();
        mirror.setEditable(false);
        StyleHelper.ApplyTerm(mirror);
        mirror.setStyle(mirror.getStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(mirror, Priority.ALWAYS);

        runBtn.setOnAction(e -> {
            if (Dispatcher != null) {
                Dispatcher.Dispatch(cmdInput.getText().trim(), cmdInput);
            }
        });
        cmdInput.setOnAction(e -> {
            if (Dispatcher != null) Dispatcher.Dispatch(cmdInput.getText().trim(), cmdInput);
        });
        clearBtn.setOnAction(e -> {
            LogEntries.clear();
            mirror.clear();
        });
        cmdBar.getChildren().addAll(prompt, cmdInput, runBtn, clearBtn);
        page.getChildren().addAll(cmdBar, mirror);
        return page;
    }

    /* ── LOGS ─────────────────────────────────────────────────── */
    private VBox BuildLogs() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + Palette.TERM_BG + ";");

        HBox toolbar = new HBox(6);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setPadding(new Insets(5, 10, 5, 10));
        toolbar.setStyle("-fx-background-color:#141414; -fx-border-color:transparent transparent #2e2e2e transparent; -fx-border-width:0 0 1 0;");

        HBox leftH = new HBox(8);
        leftH.setAlignment(Pos.CENTER_LEFT);
        leftH.getChildren().addAll(MatIcon(I_LIST, Palette.GREY, 12), LblSm("ACTIVITY LOG", Palette.TEXT_MUTED));
        HBox.setHgrow(leftH, Priority.ALWAYS);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button exportBtn = Btn(I_EXPORT + " Export", "btn btn-default");
        Button clearBtn = Btn(I_CLEAR + " Clear", "btn btn-danger");

        /* Use the shared LogOutput so AddLog() writes here */
        if (LogOutput == null) {
            LogOutput = new TextArea();
            LogOutput.setEditable(false);
            StyleHelper.ApplyTerm(LogOutput);
        }
        LogOutput.setStyle(StyleHelper.TermStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(LogOutput, Priority.ALWAYS);

        clearBtn.setOnAction(e -> {
            LogEntries.clear();
            LogOutput.clear();
        });
        toolbar.getChildren().addAll(leftH, exportBtn, clearBtn);
        page.getChildren().addAll(toolbar, LogOutput);
        return page;
    }

    /* ── SETTINGS ─────────────────────────────────────────────── */
    private ScrollPane BuildSettings() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color:" + Palette.BG + ";");

        /* Server toggle card */
        VBox toggleCard = new VBox(14);
        toggleCard.getStyleClass().add("server-toggle-card");

        /* Toggle row */
        HBox toggleRow = new HBox(16);
        toggleRow.setAlignment(Pos.CENTER_LEFT);

        VBox toggleInfo = new VBox(3);
        Label toggleTitle = new Label("Listener");
        toggleTitle.getStyleClass().add("text-head");
        toggleTitle.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#f5f5f5;");
        SrvToggleLabel = new Label("Server is offline");
        SrvToggleLabel.getStyleClass().add("text-muted");
        toggleInfo.getChildren().addAll(toggleTitle, SrvToggleLabel);
        HBox.setHgrow(toggleInfo, Priority.ALWAYS);

        /* Custom toggle switch using ToggleButton styled */
        ServerToggle = new ToggleButton("OFF");
        ServerToggle.getStyleClass().addAll("btn", "toggle-btn");
        ServerToggle.setStyle("-fx-pref-width:80px; -fx-pref-height:30px;" + "-fx-background-color:#252525; -fx-text-fill:" + Palette.TEXT_MUTED + ";" + "-fx-border-color:#383838; -fx-font-weight:bold;");
        ServerToggle.setOnAction(e -> {
            if (ServerToggle.isSelected()) {
                ServerToggle.setText("ON");
                ServerToggle.setStyle("-fx-pref-width:80px; -fx-pref-height:30px;" + "-fx-background-color:rgba(165,214,167,0.12); -fx-text-fill:" + Palette.GREEN + ";" + "-fx-border-color:rgba(165,214,167,0.4); -fx-font-weight:bold;");
                InitServer();
            } else {
                ServerToggle.setText("OFF");
                ServerToggle.setStyle("-fx-pref-width:80px; -fx-pref-height:30px;" + "-fx-background-color:#252525; -fx-text-fill:" + Palette.TEXT_MUTED + ";" + "-fx-border-color:#383838; -fx-font-weight:bold;");
                if (ServerCtrl != null) ServerCtrl.Stop();
            }
        });

        toggleRow.getChildren().addAll(toggleInfo, ServerToggle);
        toggleCard.getChildren().add(toggleRow);

        Region div1 = new Region();
        div1.getStyleClass().add("h-div");
        toggleCard.getChildren().add(div1);

        /* Host / port fields */
        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        ColumnConstraints cc0 = new ColumnConstraints();
        cc0.setMinWidth(55);
        cc0.setMaxWidth(65);
        ColumnConstraints cc1 = new ColumnConstraints();
        cc1.setHgrow(Priority.ALWAYS);
        fields.getColumnConstraints().addAll(cc0, cc1);

        HostField = new TextField(Config.GetServerHost());
        PortField = new TextField(String.valueOf(Config.GetServerPort()));
        HostField.getStyleClass().add("input-field");
        PortField.getStyleClass().add("input-field");

        fields.add(LblSm("Host", Palette.TEXT_MUTED), 0, 0);
        fields.add(HostField, 1, 0);
        fields.add(LblSm("Port", Palette.TEXT_MUTED), 0, 1);
        fields.add(PortField, 1, 1);
        toggleCard.getChildren().add(fields);
        content.getChildren().add(toggleCard);

        /* Status card */
        VBox statusCard = PanelCard("SERVER STATUS", I_DNS, Palette.GREY);
        VBox statusBody = PanelBody(statusCard);

        ServerStatusLabel = new Label("Offline");
        ServerStatusLabel.setStyle("-fx-text-fill:" + Palette.DANGER + "; -fx-font-size:13px; -fx-font-weight:bold;");
        ServerInfoLabel = new Label("Not running");
        ServerInfoLabel.getStyleClass().add("text-muted");

        HBox r1 = Row("Status", ServerStatusLabel);
        HBox r2 = Row("Address", ServerInfoLabel);
        HBox r3 = Row("Mode", LblSm(Config.GetServerMode().toUpperCase(), Palette.BLUE));
        statusBody.getChildren().addAll(r1, r2, r3);
        content.getChildren().add(statusCard);

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:" + Palette.BG + ";");
        return sp;
    }

    /* ══════════════════════════════════════════════════════════
       SERVER INIT
       ══════════════════════════════════════════════════════════ */
    private void InitServer() {
        String host = HostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(PortField.getText().trim());
        } catch (NumberFormatException e) {
            ShowAlert(Alert.AlertType.WARNING, "Invalid port number");
            Platform.runLater(() -> {
                ServerToggle.setSelected(false);
                ServerToggle.setText("OFF");
            });
            return;
        }

        ServerCtrl = new ServerController(
            Config,
            StatusDot,
            ServerStatusLabel,
            ServerInfoLabel,
            null,
            null /* start/stop buttons replaced by toggle */,
            this::AddLog,
            this::OnEvent,
            () -> {
                Platform.runLater(() -> {
                    SrvToggleLabel.setText("Running on " + host + ":" + port);
                    SrvToggleLabel.setStyle("-fx-text-fill:" + Palette.GREEN + ";");
                });
                SessionMgr = new SessionManager(ServerCtrl.GetServer(), Auth.GetDb(), SessionRows, SessionCountLabel);
                Dispatcher = new CommandDispatcher(ServerCtrl.GetServer(), Auth.GetDb(), SessionMgr, this::AddLog, Auth.GetOperatorName());
            },
            () ->
                Platform.runLater(() -> {
                    SrvToggleLabel.setText("Server is offline");
                    SrvToggleLabel.setStyle("-fx-text-fill:" + Palette.TEXT_MUTED + ";");
                    SessionRows.clear();
                    SessionCountLabel.setText("0 sessions");
                })
        );
        ServerCtrl.Start(host, port);
    }

    /* ══════════════════════════════════════════════════════════
       LOGIN DIALOG
       ══════════════════════════════════════════════════════════ */
    private boolean ShowLogin(Stage owner) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("RAVEN — Authentication");
        dlg.setHeaderText("TeamServer Login");
        dlg.initOwner(owner);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.setStyle("-fx-background-color:" + Palette.BG + ";");

        TextField user = new TextField();
        user.setPromptText("Username");
        user.getStyleClass().add("input-field");
        PasswordField pass = new PasswordField();
        pass.setPromptText("Password");
        pass.getStyleClass().add("password-field");
        Label err = new Label("");
        err.setStyle("-fx-text-fill:" + Palette.DANGER + ";");

        grid.add(LblSm("Username", Palette.TEXT_MUTED), 0, 0);
        grid.add(user, 1, 0);
        grid.add(LblSm("Password", Palette.TEXT_MUTED), 0, 1);
        grid.add(pass, 1, 1);
        grid.add(err, 1, 2);

        ButtonType loginBtn = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(loginBtn, ButtonType.CANCEL);
        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().setStyle("-fx-background-color:" + Palette.BG + ";");
        dlg.setResultConverter(btn -> {
            if (btn == loginBtn) return Auth.Authenticate(user.getText().trim(), pass.getText()) ? true : null;
            return false;
        });
        for (int i = 0; i < 3; i++) {
            Optional<Boolean> res = dlg.showAndWait();
            if (res.isEmpty() || Boolean.FALSE.equals(res.get())) return false;
            if (Boolean.TRUE.equals(res.get())) return true;
            err.setText("Invalid credentials");
        }
        return false;
    }

    /* ══════════════════════════════════════════════════════════
       TERMINAL CMD
       ══════════════════════════════════════════════════════════ */
    private void SendTerminalCmd() {
        if (TermCmdField == null || SessionIdField == null) return;
        String sidStr = SessionIdField.getText().trim();
        String cmd = TermCmdField.getText().trim();
        if (sidStr.isEmpty() || cmd.isEmpty()) return;
        int sid;
        try {
            sid = Integer.parseInt(sidStr);
        } catch (NumberFormatException e) {
            WriteTerminal("[!] Invalid session ID\n");
            return;
        }
        if (ServerCtrl == null || !ServerCtrl.IsRunning()) {
            WriteTerminal("[!] Server not running\n");
            return;
        }
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

    /* ══════════════════════════════════════════════════════════
       POPUP WINDOWS
       ══════════════════════════════════════════════════════════ */
    private void OpenExecuteWindow() {
        if (SelectedSid < 0) {
            ShowAlert(Alert.AlertType.WARNING, "Select a session first");
            return;
        }
        Stage win = new Stage();
        win.setTitle("Execute — SESSION-" + SelectedSid);
        win.setWidth(700);
        win.setHeight(520);
        win.setMinWidth(500);
        win.setMinHeight(380);
        VBox layout = new VBox(0);
        layout.setStyle("-fx-background-color:" + Palette.TERM_BG + ";");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 12, 6, 12));
        header.setStyle("-fx-background-color:#141414; -fx-border-color:transparent transparent #2e2e2e transparent; -fx-border-width:0 0 1 0;");
        header.getChildren().addAll(MatIcon(I_TERMINAL, Palette.PINK, 13), LblSm("SESSION-" + SelectedSid, Palette.PINK));

        TextArea out = new TextArea();
        out.setEditable(false);
        StyleHelper.ApplyTerm(out);
        out.setStyle(out.getStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(out, Priority.ALWAYS);

        HBox input = new HBox(6);
        input.getStyleClass().add("input-bar");
        input.setAlignment(Pos.CENTER_LEFT);
        Label prompt = new Label(">");
        prompt.getStyleClass().add("cmd-prompt");
        TextField entry = new TextField();
        entry.setPromptText("Enter command...");
        entry.getStyleClass().add("input-field");
        HBox.setHgrow(entry, Priority.ALWAYS);
        Button run = Btn(I_SEND + " Run", "btn btn-accent");

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
        run.setOnAction(e -> exec.run());
        entry.setOnAction(e -> exec.run());
        input.getChildren().addAll(prompt, entry, run);
        layout.getChildren().addAll(header, out, input);
        win.setScene(new Scene(layout));
        win.show();
        entry.requestFocus();
    }

    private void OpenBroadcastWindow() {
        if (ServerCtrl == null || !ServerCtrl.IsRunning()) {
            ShowAlert(Alert.AlertType.WARNING, "Server not running");
            return;
        }
        Stage win = new Stage();
        win.setTitle("Broadcast Command");
        win.setWidth(660);
        win.setHeight(520);
        win.setMinWidth(460);
        win.setMinHeight(360);
        VBox layout = new VBox(0);
        layout.setStyle("-fx-background-color:" + Palette.TERM_BG + ";");

        HBox targetRow = new HBox(8);
        targetRow.setAlignment(Pos.CENTER_LEFT);
        targetRow.setPadding(new Insets(6, 10, 6, 10));
        targetRow.setStyle("-fx-background-color:#141414; -fx-border-color:transparent transparent #2e2e2e transparent; -fx-border-width:0 0 1 0;");
        TextField targetField = new TextField();
        targetField.setPromptText("Target: 1,2,3  or  all");
        targetField.getStyleClass().add("input-field");
        HBox.setHgrow(targetField, Priority.ALWAYS);
        targetRow.getChildren().addAll(MatIcon(I_BROADCAST, Palette.BLUE, 13), LblSm("Target", Palette.TEXT_MUTED), targetField);

        TextArea out = new TextArea();
        out.setEditable(false);
        StyleHelper.ApplyTerm(out);
        out.setStyle(out.getStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(out, Priority.ALWAYS);

        HBox cmdRow = new HBox(6);
        cmdRow.getStyleClass().add("input-bar");
        cmdRow.setAlignment(Pos.CENTER_LEFT);
        Label prompt = new Label(">");
        prompt.getStyleClass().add("cmd-prompt");
        TextField cmdF = new TextField();
        cmdF.setPromptText("Enter command...");
        cmdF.getStyleClass().add("input-field");
        HBox.setHgrow(cmdF, Priority.ALWAYS);
        Button runBtn = Btn(I_BROADCAST + " Broadcast", "btn btn-accent");

        Runnable doBcast = () -> {
            String target = targetField.getText().trim();
            String cmd = cmdF.getText().trim();
            if (target.isEmpty() || cmd.isEmpty()) return;
            out.appendText("Broadcast [" + target + "] > " + cmd + "\n");
            cmdF.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                Map<Integer, String[]> results;
                if (target.equalsIgnoreCase("all")) {
                    results = ServerCtrl.GetServer().BroadcastAll(cmd);
                } else {
                    List<Integer> ids = new ArrayList<>();
                    for (String s : target.split(",")) {
                        try {
                            ids.add(Integer.parseInt(s.trim()));
                        } catch (Exception ignored) {}
                    }
                    results = ServerCtrl.GetServer().BroadcastCommand(ids, cmd);
                }
                final Map<Integer, String[]> fr = results;
                Platform.runLater(() ->
                    fr.forEach((id, res) -> {
                        boolean ok = Boolean.parseBoolean(res[0]);
                        out.appendText("  #" + id + "  " + (ok ? "OK" : "ERR") + "\n" + res[1] + "\n\n");
                        Auth.GetDb().SaveCommandLog(id, "operator", cmd, res[1], ok);
                    })
                );
            });
        };
        runBtn.setOnAction(e -> doBcast.run());
        cmdF.setOnAction(e -> doBcast.run());
        cmdRow.getChildren().addAll(prompt, cmdF, runBtn);
        layout.getChildren().addAll(targetRow, out, cmdRow);
        win.setScene(new Scene(layout));
        win.show();
        cmdF.requestFocus();
    }

    private void KillSelected() {
        if (SelectedSid < 0) {
            ShowAlert(Alert.AlertType.WARNING, "Select a session first");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Terminate SESSION-" + SelectedSid + "?");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                if (SessionMgr != null) SessionMgr.Kill(SelectedSid);
                SelectedSid = -1;
                if (SelectedLabel != null) SelectedLabel.setText("No session selected");
            }
        });
    }

    /* ══════════════════════════════════════════════════════════
       EVENT HANDLER
       ══════════════════════════════════════════════════════════ */
    private void OnEvent(EventType type, Map<String, Object> data) {
        switch (type) {
            case AgentConnected -> {
                AddLog("[+] [" + data.get("Type") + "] SESSION-" + data.get("ID") + ": " + data.get("AgentName") + " (" + data.get("OS") + ")");
                Platform.runLater(() -> {
                    if (SessionMgr != null) SessionMgr.Refresh();
                });
            }
            case AgentDisconnected -> {
                AddLog("[-] SESSION-" + data.get("ID") + " disconnected: " + data.get("Reason"));
                Platform.runLater(() -> {
                    if (SessionMgr != null) SessionMgr.Refresh();
                });
            }
            case Error -> AddLog("[!] " + data.get("Message"));
        }
    }

    /* ══════════════════════════════════════════════════════════
       UTILITIES
       ══════════════════════════════════════════════════════════ */
    private void AddLog(String msg) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + ts + "]  " + msg;
        LogEntries.add(entry);
        if (LogEntries.size() > Config.GetMaxLogEntries()) LogEntries.remove(0);
        Platform.runLater(() -> {
            if (LogOutput != null) LogOutput.appendText(entry + "\n");
        });
    }

    private void WriteTerminal(String text) {
        if (TerminalOutput != null) TerminalOutput.appendText(text);
    }

    private void StartUptimeThread() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                if (ServerCtrl != null && ServerCtrl.GetStartTime() != null) {
                    long s = java.time.Duration.between(ServerCtrl.GetStartTime(), java.time.Instant.now()).getSeconds();
                    String up = SystemHelper.FormatUptime(s);
                    Platform.runLater(() -> {
                        if (UptimeLabel != null) UptimeLabel.setText(up);
                    });
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void ShowAlert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type, msg);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /* ── UI builder helpers ───────────────────────────────────── */

    private Label MatIcon(String code, String color, int size) {
        Label l = new Label(code);
        l.setStyle("-fx-font-family:'Material Icons'; -fx-font-size:" + size + "px; -fx-text-fill:" + color + ";");
        return l;
    }

    private Label SectionLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("sidebar-section");
        return l;
    }

    private Label LblSm(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + color + "; -fx-font-size:10px; -fx-font-weight:bold;");
        return l;
    }

    private Button Btn(String text, String styleClasses) {
        Button b = new Button(text);
        for (String cls : styleClasses.split(" ")) b.getStyleClass().add(cls);
        return b;
    }

    private Button BtnIcon(String icon, String tooltip, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button b = new Button(icon);
        b.getStyleClass().add("btn-icon");
        b.setTooltip(new Tooltip(tooltip));
        b.setOnAction(handler);
        return b;
    }

    private Region Region(boolean hgrow) {
        Region r = new Region();
        if (hgrow) HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Creates a panel card VBox with header. Body is child index 1. */
    private VBox PanelCard(String title, String icon, String iconColor) {
        VBox card = new VBox(0);
        card.getStyleClass().add("panel-card");
        HBox header = new HBox(8);
        header.getStyleClass().add("panel-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(MatIcon(icon, iconColor, 13), LblSm(title, Palette.TEXT_MUTED));
        VBox body = new VBox(8);
        body.setPadding(new Insets(12));
        card.getChildren().addAll(header, body);
        return card;
    }

    private VBox PanelBody(VBox card) {
        return (VBox) card.getChildren().get(1);
    }

    private VBox StatCard(String label, String value, String color, String icon) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stat-card");
        Label iconLbl = MatIcon(icon, color, 18);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-label");
        Label val = new Label(value);
        val.getStyleClass().add("stat-value");
        val.setStyle("-fx-text-fill:" + color + "; -fx-font-size:28px; -fx-font-weight:bold;");
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getChildren().addAll(iconLbl, lbl);
        card.getChildren().addAll(top, val);
        return card;
    }

    private HBox Row(String labelText, Node value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(labelText);
        lbl.setMinWidth(70);
        lbl.getStyleClass().add("text-muted");
        lbl.setStyle("-fx-font-size:11px; -fx-text-fill:" + Palette.TEXT_MUTED + ";");
        row.getChildren().addAll(lbl, value);
        return row;
    }

    private Label PlaceholderLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#3a3a3a; -fx-font-size:12px;");
        return l;
    }
}
