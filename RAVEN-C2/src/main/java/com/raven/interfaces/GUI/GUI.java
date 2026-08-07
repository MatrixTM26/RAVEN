package com.raven.interfaces.GUI;

import com.raven.core.command.AgentCommandDispatcher;
import com.raven.core.event.EventManager.EventType;
import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.controller.*;
import com.raven.interfaces.GUI.module.UI.frame.FxmlLoader;
import com.raven.interfaces.GUI.module.UI.frame.FxmlLoader.LoadResult;
import com.raven.interfaces.GUI.module.UI.frame.PopupBuilder;
import com.raven.interfaces.GUI.module.UI.frame.SidebarBuilder;
import com.raven.interfaces.GUI.module.core.database.AuthService;
import com.raven.interfaces.GUI.module.core.server.ServerController;
import com.raven.interfaces.GUI.module.core.session.SessionManager;
import com.raven.interfaces.GUI.module.core.session.SessionRow;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

public class GUI extends Application {

    private static ServerConfig Config;
    private static boolean TeamMode = false;

    public static void Launch(ServerConfig Cfg) {
        Config = Cfg;
        TeamMode = false;
        Application.launch(GUI.class);
    }

    public static void LaunchTeam(ServerConfig Cfg) {
        Config = Cfg;
        TeamMode = true;
        Application.launch(GUI.class);
    }

    private AuthService Auth;
    private SessionManager SessMgr;
    private ServerController SrvCtrl;
    private AgentCommandDispatcher Dispatcher;

    private final ObservableList<SessionRow> SessionRows = FXCollections.observableArrayList();
    private int SelectedId = -1;

    private final Map<String, Node> Pages = new LinkedHashMap<>();
    private final Map<String, HBox> NavItemMap = new LinkedHashMap<>();
    private StackPane ContentArea;

    private Label[] StatusIndicator = new Label[1];
    private Label[] UptimeLabel = new Label[1];
    private Label[] SessionCountLabel = new Label[1];

    private OverviewController OverviewCtrl;
    private SessionsController SessionsCtrl;
    private TerminalController TerminalCtrl;
    private ListenerController ListenerCtrl;
    private CommandCenterController CmdCtrl;
    private LogsController LogsCtrl;
    private SettingsController SettingsCtrl;

    @Override
    public void start(Stage Stage) {
        Auth = new AuthService(Config);
        if (TeamMode && !PopupBuilder.ShowLoginDialog(Stage, Auth)) {
            Platform.exit();
            return;
        }

        Stage.setTitle("RAVEN");
        Stage.setWidth(1440);
        Stage.setHeight(900);
        Stage.setMinWidth(920);
        Stage.setMinHeight(600);

        LoadFonts();

        ContentArea = new StackPane();
        ContentArea.setStyle("-fx-background-color:" + Palette.Bg + ";");

        VBox Sidebar = SidebarBuilder.Build(this::Navigate, NavItemMap, StatusIndicator, Auth.GetOperatorName());

        LoadAllPages();
        Navigate("Overview");

        VBox Center = new VBox(0);
        VBox.setVgrow(ContentArea, Priority.ALWAYS);
        Center.getChildren().addAll(BuildTopBar(), ContentArea, BuildStatusBar());

        BorderPane Root = new BorderPane();
        Root.setStyle("-fx-background-color:" + Palette.Bg + ";");
        Root.setLeft(Sidebar);
        Root.setCenter(Center);

        Scene AppScene = new Scene(Root);
        URL Css = getClass().getResource("styles/raven.css");
        if (Css == null) Css = getClass().getResource("/com/raven/interfaces/GUI/styles/raven.css");
        if (Css != null) AppScene.getStylesheets().add(Css.toExternalForm());

        Stage.setScene(AppScene);
        Stage.setOnCloseRequest(e -> {
            if (SrvCtrl != null) SrvCtrl.Stop();
            Platform.exit();
        });
        Stage.show();
        StartUptimeThread();
    }

    private HBox BuildTopBar() {
        HBox Bar = new HBox(10);
        Bar.setAlignment(Pos.CENTER_LEFT);
        Bar.setPadding(new Insets(0, 16, 0, 16));
        Bar.setMinHeight(44);
        Bar.setMaxHeight(44);
        Bar.getStyleClass().add("topbar");

        VBox Heading = new VBox(2);
        Label Title = new Label("RAVEN Operations Console");
        Title.getStyleClass().add("topbar-title");
        Label Sub = new Label("Listener  ·  Sessions  ·  Terminal  ·  Commands");
        Sub.getStyleClass().add("topbar-sub");
        Heading.getChildren().addAll(Title, Sub);

        Label DevBadge = new Label("development");
        DevBadge.getStyleClass().add("topbar-badge");

        Region Spacer = new Region();
        HBox.setHgrow(Spacer, Priority.ALWAYS);

        Label Uptime = new Label("00:00:00");
        Uptime.getStyleClass().add("status-text");
        UptimeLabel[0] = Uptime;

        Region Div1 = new Region();
        Div1.setMinWidth(1);
        Div1.setPrefWidth(1);
        Div1.setPrefHeight(14);
        Div1.setStyle("-fx-background-color:" + Palette.Border + ";");

        Label SessCount = new Label("0 sessions");
        SessCount.getStyleClass().add("status-accent");
        SessionCountLabel[0] = SessCount;

        Bar.getChildren().addAll(Heading, DevBadge, Spacer, Uptime, Div1, SessCount);

        if (Auth.GetOperatorName() != null) {
            Region Div2 = new Region();
            Div2.setMinWidth(1);
            Div2.setPrefWidth(1);
            Div2.setPrefHeight(14);
            Div2.setStyle("-fx-background-color:" + Palette.Border + ";");

            Label Operator = new Label(Auth.GetOperatorName() + (Auth.GetOperatorRole() != null ? "  [" + Auth.GetOperatorRole().name() + "]" : ""));
            Operator.setStyle("-fx-font-size:10px; -fx-text-fill:" + Palette.WhiteFaint + ";");
            Bar.getChildren().addAll(Div2, Operator);
        }
        return Bar;
    }

    private HBox BuildStatusBar() {
        HBox Bar = new HBox(8);
        Bar.setAlignment(Pos.CENTER_LEFT);
        Bar.setPadding(new Insets(3, 14, 3, 14));
        Bar.setMinHeight(24);
        Bar.setMaxHeight(24);
        Bar.getStyleClass().add("statusbar");

        Label Dot = new Label("●");
        Dot.setStyle("-fx-text-fill:" + Palette.Red + "; -fx-font-size:8px;");
        Label VerLabel = new Label("RAVEN v3.0  ·  MatrixTM26");
        VerLabel.getStyleClass().add("status-text");

        Region Spacer = new Region();
        HBox.setHgrow(Spacer, Priority.ALWAYS);

        Label ModeLabel = new Label(Config.GetServerMode().toUpperCase());
        ModeLabel.getStyleClass().add("status-text");
        Bar.getChildren().addAll(Dot, VerLabel, Spacer, ModeLabel);
        return Bar;
    }

    private void LoadAllPages() {
        LoadResult<Node> OvResult = FxmlLoader.Load("Overview.fxml");
        OverviewCtrl = OvResult.GetController();
        Pages.put("Overview", OvResult.Root());

        LoadResult<Node> SsResult = FxmlLoader.Load("Sessions.fxml");
        SessionsCtrl = SsResult.GetController();
        SessionsCtrl.BindData(SessionRows);
        SessionsCtrl.SetCallbacks(this::RefreshSessions, this::OpenExecutePopup, this::OpenBroadcastPopup, this::KillSelected, Id -> {
            SelectedId = Id;
            if (CmdCtrl != null) CmdCtrl.SetActiveSession(Id);
        });
        Pages.put("Sessions", SsResult.Root());

        LoadResult<Node> TmResult = FxmlLoader.Load("Terminal.fxml");
        TerminalCtrl = TmResult.GetController();
        TerminalCtrl.SetCallbacks(this::ExecTerminal, this::SysinfoTerminal);
        Pages.put("Terminal", TmResult.Root());

        LoadResult<Node> LiResult = FxmlLoader.Load("Listener.fxml");
        ListenerCtrl = LiResult.GetController();
        ListenerCtrl.SetCallbacks(this::StartServer, this::StopServer);
        Pages.put("Listener", LiResult.Root());

        LoadResult<Node> CcResult = FxmlLoader.Load("CommandCenter.fxml");
        CmdCtrl = CcResult.GetController();
        Pages.put("Command Center", CcResult.Root());

        LoadResult<Node> LgResult = FxmlLoader.Load("Logs.fxml");
        LogsCtrl = LgResult.GetController();
        Pages.put("Logs", LgResult.Root());

        LoadResult<Node> StResult = FxmlLoader.Load("Settings.fxml");
        SettingsCtrl = StResult.GetController();
        SettingsCtrl.BindConfig(Config);
        Pages.put("Settings", StResult.Root());
    }

    private void Navigate(String Page) {
        SidebarBuilder.SetActive(NavItemMap, Page);
        Node Target = Pages.get(Page);
        if (Target == null) return;
        FadeTransition Fade = new FadeTransition(Duration.millis(100), Target);
        Fade.setFromValue(0.5);
        Fade.setToValue(1.0);
        ContentArea.getChildren().setAll(Target);
        Fade.play();
    }

    private void StartServer() {
        String Host = ListenerCtrl.GetHost();
        int Port = ListenerCtrl.GetPort();

        SrvCtrl = new ServerController(
            Config,
            StatusIndicator[0],
            null,
            null,
            null,
            null,
            this::Log,
            this::HandleEvent,
            () -> {
                Platform.runLater(() -> {
                    ListenerCtrl.SetOnline(true, Host + ":" + Port);
                    ListenerCtrl.AppendLog("[+] Listener started on " + Host + ":" + Port);
                    if (OverviewCtrl != null) OverviewCtrl.UpdateServerStatus(true, Host + ":" + Port, ListenerCtrl.GetMode(), Auth.GetOperatorName());
                });
                SessMgr = new SessionManager(SrvCtrl.GetServer(), Auth.GetDb(), SessionRows, SessionCountLabel[0]);
                Dispatcher = new AgentCommandDispatcher(SrvCtrl.GetServer(), Auth.GetDb(), Auth.GetOperatorName());
                CmdCtrl.SetDispatcher(Dispatcher);
            },
            () ->
                Platform.runLater(() -> {
                    ListenerCtrl.SetOffline();
                    ListenerCtrl.AppendLog("[-] Listener stopped");
                    if (OverviewCtrl != null) OverviewCtrl.UpdateServerStatus(false, "", Config.GetServerMode(), Auth.GetOperatorName());
                    SessionRows.clear();
                    if (SessionCountLabel[0] != null) SessionCountLabel[0].setText("0 sessions");
                })
        );
        SrvCtrl.Start(Host, Port);
    }

    private void StopServer() {
        if (SrvCtrl != null) SrvCtrl.Stop();
    }

    private void RefreshSessions() {
        if (SessMgr != null) SessMgr.Refresh();
    }

    private void ExecTerminal(int SessionId, String Cmd) {
        if (SrvCtrl == null || !SrvCtrl.IsRunning()) {
            if (TerminalCtrl != null) TerminalCtrl.Append("[!] Server not running");
            return;
        }
        Log("> #" + SessionId + ": " + Cmd);
        Executors.newSingleThreadExecutor().submit(() -> {
            String[] Res = SrvCtrl.GetServer().ExecuteCommand(SessionId, Cmd);
            boolean Ok = Boolean.parseBoolean(Res[0]);
            Platform.runLater(() -> {
                if (TerminalCtrl != null) TerminalCtrl.Append(Res[1]);
                Log(Ok ? "[+] OK" : "[!] " + Res[1]);
            });
        });
    }

    private void SysinfoTerminal(int SessionId) {
        if (Dispatcher != null) Dispatcher.Dispatch(SessionId, "sysinfo");
    }

    private void OpenExecutePopup() {
        if (SelectedId < 0) {
            ShowAlert(Alert.AlertType.WARNING, "Select a session first");
            return;
        }
        if (SrvCtrl == null || !SrvCtrl.IsRunning()) {
            ShowAlert(Alert.AlertType.WARNING, "Server not running");
            return;
        }
        PopupBuilder.ShowExecuteWindow(SelectedId, SrvCtrl);
    }

    private void OpenBroadcastPopup() {
        if (SrvCtrl == null || !SrvCtrl.IsRunning()) {
            ShowAlert(Alert.AlertType.WARNING, "Server not running");
            return;
        }
        PopupBuilder.ShowBroadcastWindow(SrvCtrl, Auth);
    }

    private void KillSelected() {
        if (SelectedId < 0) {
            ShowAlert(Alert.AlertType.WARNING, "Select a session first");
            return;
        }
        Alert Confirm = new Alert(Alert.AlertType.CONFIRMATION, "Terminate SESSION-" + SelectedId + "?");
        Confirm.setHeaderText(null);
        Confirm.showAndWait().ifPresent(R -> {
            if (R == ButtonType.OK) {
                if (SessMgr != null) SessMgr.Kill(SelectedId);
                SelectedId = -1;
            }
        });
    }

    private void HandleEvent(EventType Type, Map<String, Object> Data) {
        switch (Type) {
            case AgentConnected -> {
                String Msg = "[+] [" + Data.get("Type") + "] SESSION-" + Data.get("ID") + ": " + Data.get("AgentName") + " (" + Data.get("OS") + ")";
                Log(Msg);
                if (OverviewCtrl != null) OverviewCtrl.AddActivity(true, Msg, Ts());
                Platform.runLater(() -> {
                    if (SessMgr != null) SessMgr.Refresh();
                    UpdateOverviewStats();
                });
            }
            case AgentDisconnected -> {
                String Msg = "[-] SESSION-" + Data.get("ID") + " disconnected: " + Data.get("Reason");
                Log(Msg);
                if (OverviewCtrl != null) OverviewCtrl.AddActivity(false, Msg, Ts());
                Platform.runLater(() -> {
                    if (SessMgr != null) SessMgr.Refresh();
                    UpdateOverviewStats();
                });
            }
            case Error -> Log("[!] " + Data.get("Message"));
        }
    }

    private void UpdateOverviewStats() {
        if (SrvCtrl == null || OverviewCtrl == null) return;
        Map<String, Integer> Stats = SrvCtrl.GetServer().GetSessions().GetStats();
        OverviewCtrl.UpdateStats(Stats.getOrDefault("Total", 0), Stats.getOrDefault("RAVEN", 0), Stats.getOrDefault("METERPRETER", 0), Stats.getOrDefault("REVERSE_SHELL", 0));
    }

    private void Log(String Msg) {
        if (LogsCtrl != null) {
            String Level = Msg.startsWith("[+]") ? "OK" : Msg.startsWith("[!]") ? "ERROR" : Msg.startsWith("[-]") ? "WARN" : "INFO";
            LogsCtrl.AppendEntry(Level, Msg);
        }
        if (SessionsCtrl != null) SessionsCtrl.AppendLog(Msg);
        if (CmdCtrl != null) CmdCtrl.AppendOutput(Msg);
    }

    private void StartUptimeThread() {
        Thread T = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException E) {
                    break;
                }
                if (SrvCtrl != null && SrvCtrl.GetStartTime() != null) {
                    long Secs = java.time.Duration.between(SrvCtrl.GetStartTime(), java.time.Instant.now()).getSeconds();
                    String Fmt = SystemHelper.FormatUptime(Secs);
                    Platform.runLater(() -> {
                        if (UptimeLabel[0] != null) UptimeLabel[0].setText(Fmt);
                        if (OverviewCtrl != null) OverviewCtrl.UpdateUptime(Fmt);
                    });
                }
            }
        });
        T.setDaemon(true);
        T.start();
    }

    private void LoadFonts() {
        try {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/fonts/MaterialIcons-Regular.ttf"), 16);
        } catch (Exception Ignored) {}
    }

    private void ShowAlert(Alert.AlertType Type, String Msg) {
        Alert A = new Alert(Type, Msg);
        A.setHeaderText(null);
        A.showAndWait();
    }

    private String Ts() {
        return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}
