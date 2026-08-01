package com.raven.interfaces.GUI;

import com.raven.core.event.EventManager.EventType;
import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.component.ComponentFactory;
import com.raven.interfaces.GUI.module.UI.frame.*;
import com.raven.interfaces.GUI.module.core.database.AuthService;
import com.raven.interfaces.GUI.module.core.server.CommandDispatcher;
import com.raven.interfaces.GUI.module.core.server.ServerController;
import com.raven.interfaces.GUI.module.core.session.SessionManager;
import com.raven.interfaces.GUI.module.core.session.SessionRow;
import com.raven.utils.ServerConfig;
import com.raven.utils.SystemHelper;
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
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

public class GUI extends Application {

    private static final String IconCircle = "\uEF4A";

    private static ServerConfig ServerConfiguration;
    private static boolean TeamModeEnabled = false;

    public static void Launch(ServerConfig Configuration) {
        ServerConfiguration = Configuration;
        TeamModeEnabled = false;
        Application.launch(GUI.class);
    }

    public static void LaunchTeam(ServerConfig Configuration) {
        ServerConfiguration = Configuration;
        TeamModeEnabled = true;
        Application.launch(GUI.class);
    }

    private AuthService        Authentication;
    private SessionManager     SessionController;
    private ServerController   ServerControl;
    private CommandDispatcher  CommandDispatcherInstance;

    private final ObservableList<SessionRow> SessionRows = FXCollections.observableArrayList();
    private final ObservableList<String>     LogEntries  = FXCollections.observableArrayList();

    private int    SelectedSessionId     = -1;
    private String ActivePage            = "Overview";

    private final Map<String, Node>  Pages      = new LinkedHashMap<>();
    private final Map<String, HBox>  NavItemMap = new LinkedHashMap<>();

    private Label[]        StatusIndicatorRef    = new Label[1];
    private Label[]        UptimeLabelRef        = new Label[1];
    private Label[]        SessionCountLabelRef  = new Label[1];
    private Label[]        ServerStatusLabelRef  = new Label[1];
    private Label[]        ServerInfoLabelRef    = new Label[1];
    private Label[]        ToggleStatusLabelRef  = new Label[1];
    private Label[]        SelectedLabelRef      = new Label[1];
    private TextArea[]     TerminalOutputRef     = new TextArea[1];
    private TextArea[]     LogOutputRef          = new TextArea[1];
    private TextField[]    TerminalCommandRef    = new TextField[1];
    private TextField[]    SessionIdFieldRef     = new TextField[1];
    private TextField[]    HostFieldRef          = new TextField[1];
    private TextField[]    PortFieldRef          = new TextField[1];
    private ToggleButton[] ServerToggleRef       = new ToggleButton[1];
    private TableView<SessionRow>[] SessionTableRef = new TableView[1];
    private CommandDispatcher[]     DispatcherRef   = new CommandDispatcher[1];

    private StackPane ContentArea;

    @Override
    public void start(Stage Stage) {
        Authentication = new AuthService(ServerConfiguration);
        if (TeamModeEnabled && !PopupBuilder.ShowLoginDialog(Stage, Authentication)) {
            Platform.exit();
            return;
        }

        Stage.setTitle("RAVEN");
        Stage.setWidth(1440);
        Stage.setHeight(900);
        Stage.setMinWidth(980);
        Stage.setMinHeight(620);

        try {
            Font.loadFont(getClass().getResourceAsStream("/fonts/MaterialIcons-Regular.ttf"), 16);
        } catch (Exception Ignored) {}

        ContentArea = new StackPane();
        ContentArea.setStyle("-fx-background-color:" + Palette.Background + ";");

        VBox SidebarNode = SidebarBuilder.Build(
            this::NavigateToPage, NavItemMap, StatusIndicatorRef,
            Authentication.GetOperatorName()
        );

        BuildAllPages();
        NavigateToPage("Overview");

        VBox CenterColumn = new VBox(0);
        VBox.setVgrow(ContentArea, Priority.ALWAYS);
        CenterColumn.getChildren().addAll(BuildTopBar(), ContentArea, BuildStatusBar());

        BorderPane RootLayout = new BorderPane();
        RootLayout.setStyle("-fx-background-color:" + Palette.Background + ";");
        RootLayout.setLeft(SidebarNode);
        RootLayout.setCenter(CenterColumn);

        Scene AppScene = new Scene(RootLayout);
        URL StylesheetUrl = getClass().getResource("styles/css/raven.css");
        if (StylesheetUrl == null)
            StylesheetUrl = getClass().getResource("/com/raven/interfaces/GUI/styles/css/raven.css");
        if (StylesheetUrl != null)
            AppScene.getStylesheets().add(StylesheetUrl.toExternalForm());

        Stage.setScene(AppScene);
        Stage.setOnCloseRequest(e -> {
            if (ServerControl != null) ServerControl.Stop();
            Platform.exit();
        });
        Stage.show();
        StartUptimeThread();
    }

    private HBox BuildTopBar() {
        HBox TopBar = new HBox(12);
        TopBar.setAlignment(Pos.CENTER_LEFT);
        TopBar.setPadding(new Insets(0, 18, 0, 18));
        TopBar.setMinHeight(52);
        TopBar.setMaxHeight(52);
        TopBar.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );

        VBox HeadingBox = new VBox(2);
        Label TitleLabel = new Label("RAVEN Operations Console");
        TitleLabel.getStyleClass().add("topbar-title");
        Label SubtitleLabel = new Label("Listener  ·  Sessions  ·  Terminal  ·  Commands");
        SubtitleLabel.getStyleClass().add("topbar-sub");
        HeadingBox.getChildren().addAll(TitleLabel, SubtitleLabel);

        Label DevelopmentBadge = new Label("development");
        DevelopmentBadge.getStyleClass().add("topbar-badge");

        Region FlexSpacer = ComponentFactory.FlexSpacer(true);

        Label UptimeLabel = new Label("00:00:00");
        UptimeLabel.getStyleClass().add("status-bar-text");
        UptimeLabelRef[0] = UptimeLabel;

        Region Divider = StyleHelper.VerticalDivider();

        Label SessionCountLabel = new Label("0 sessions");
        SessionCountLabel.getStyleClass().add("status-bar-accent");
        SessionCountLabelRef[0] = SessionCountLabel;

        TopBar.getChildren().addAll(HeadingBox, DevelopmentBadge, FlexSpacer, UptimeLabel, Divider, SessionCountLabel);

        if (Authentication.GetOperatorName() != null) {
            TopBar.getChildren().add(StyleHelper.VerticalDivider());
            StackPane OperatorAvatar = ComponentFactory.CircleChip(Authentication.GetOperatorName(), Palette.AccentGreen, 28);
            Label OperatorLabel = new Label(
                Authentication.GetOperatorName() +
                (Authentication.GetOperatorRole() != null
                    ? "  [" + Authentication.GetOperatorRole().name() + "]" : "")
            );
            OperatorLabel.setStyle("-fx-font-size:10px; -fx-text-fill:" + Palette.TextTertiary + ";");
            TopBar.getChildren().addAll(OperatorAvatar, OperatorLabel);
        }
        return TopBar;
    }

    private HBox BuildStatusBar() {
        HBox StatusBar = new HBox(10);
        StatusBar.setAlignment(Pos.CENTER_LEFT);
        StatusBar.setPadding(new Insets(4, 14, 4, 14));
        StatusBar.setStyle(
            "-fx-background-color:" + Palette.BackgroundVoid + ";" +
            "-fx-border-color:" + Palette.BorderSubtle + " transparent transparent transparent;" +
            "-fx-border-width:1 0 0 0;"
        );

        Label StatusDot = new Label(IconCircle);
        StatusDot.setStyle(
            "-fx-font-family:'Material Icons';" +
            "-fx-font-size:7px;" +
            "-fx-text-fill:" + Palette.AccentRed + ";"
        );
        Label StatusText = new Label("RAVEN v3.0  ·  MatrixTM26");
        StatusText.getStyleClass().add("status-bar-text");

        Region StatusSpacer = ComponentFactory.FlexSpacer(true);

        Label ModeLabel = new Label(ServerConfiguration.GetServerMode().toUpperCase());
        ModeLabel.getStyleClass().add("status-bar-text");
        StatusBar.getChildren().addAll(StatusDot, StatusText, StatusSpacer, ModeLabel);
        return StatusBar;
    }

    private void BuildAllPages() {
        TextArea[] CommandCenterOutputRef = new TextArea[1];

        Pages.put("Overview",       PageBuilder.Overview());
        Pages.put("Sessions",       PageBuilder.Sessions(
            SessionRows,
            () -> { if (SessionController != null) SessionController.Refresh(); },
            this::OpenExecutePopup,
            this::OpenBroadcastPopup,
            this::KillSelectedSession,
            DispatcherRef, SessionTableRef, LogOutputRef,
            SelectedId -> {
                SelectedSessionId = SelectedId;
                if (SelectedLabelRef[0] != null) {
                    SessionRows.stream()
                        .filter(Row -> Integer.parseInt(Row.getId()) == SelectedId)
                        .findFirst()
                        .ifPresent(Row -> SelectedLabelRef[0].setText(Row.getName() + "  #" + SelectedId));
                }
            }
        ));
        Pages.put("Terminal",       PageBuilder.Terminal(
            SessionIdFieldRef, TerminalOutputRef, TerminalCommandRef,
            this::ExecuteTerminalCommand, SelectedLabelRef
        ));
        Pages.put("Command Center", PageBuilder.CommandCenter(DispatcherRef, CommandCenterOutputRef));
        Pages.put("Logs",           PageBuilder.Logs(LogOutputRef));
        Pages.put("Settings",       PageBuilder.Settings(
            ServerConfiguration,
            ServerToggleRef, HostFieldRef, PortFieldRef,
            ServerStatusLabelRef, ServerInfoLabelRef, ToggleStatusLabelRef,
            this::InitializeServer,
            () -> { if (ServerControl != null) ServerControl.Stop(); }
        ));
    }

    private void NavigateToPage(String PageName) {
        ActivePage = PageName;
        SidebarBuilder.ApplyActiveState(NavItemMap, PageName);
        Node TargetPage = Pages.get(PageName);
        if (TargetPage != null) {
            FadeTransition FadeIn = new FadeTransition(Duration.millis(150), TargetPage);
            FadeIn.setFromValue(0.5);
            FadeIn.setToValue(1.0);
            ContentArea.getChildren().setAll(TargetPage);
            FadeIn.play();
        }
    }

    private void InitializeServer() {
        if (HostFieldRef[0] == null || PortFieldRef[0] == null) return;
        String HostAddress = HostFieldRef[0].getText().trim();
        int PortNumber;
        try {
            PortNumber = Integer.parseInt(PortFieldRef[0].getText().trim());
        } catch (NumberFormatException Exception) {
            ShowAlert(Alert.AlertType.WARNING, "Invalid port number");
            Platform.runLater(() -> {
                if (ServerToggleRef[0] != null) {
                    ServerToggleRef[0].setSelected(false);
                    ServerToggleRef[0].setText("OFF");
                }
            });
            return;
        }

        ServerControl = new ServerController(
            ServerConfiguration,
            StatusIndicatorRef[0],
            ServerStatusLabelRef[0],
            ServerInfoLabelRef[0],
            null, null,
            this::AddLogEntry,
            this::HandleServerEvent,
            () -> {
                Platform.runLater(() -> {
                    if (ToggleStatusLabelRef[0] != null) {
                        ToggleStatusLabelRef[0].setText("Running on " + HostAddress + ":" + PortNumber);
                        ToggleStatusLabelRef[0].setStyle("-fx-text-fill:" + Palette.AccentGreen + "; -fx-font-size:11px;");
                    }
                });
                SessionController = new SessionManager(
                    ServerControl.GetServer(),
                    Authentication.GetDb(),
                    SessionRows,
                    SessionCountLabelRef[0]
                );
                CommandDispatcherInstance = new CommandDispatcher(
                    ServerControl.GetServer(), Authentication.GetDb(),
                    SessionController, this::AddLogEntry, Authentication.GetOperatorName()
                );
                DispatcherRef[0] = CommandDispatcherInstance;
            },
            () -> Platform.runLater(() -> {
                if (ToggleStatusLabelRef[0] != null) {
                    ToggleStatusLabelRef[0].setText("Server is offline");
                    ToggleStatusLabelRef[0].setStyle("-fx-font-size:11px; -fx-text-fill:" + Palette.TextTertiary + ";");
                }
                SessionRows.clear();
                if (SessionCountLabelRef[0] != null)
                    SessionCountLabelRef[0].setText("0 sessions");
            })
        );
        ServerControl.Start(HostAddress, PortNumber);
    }

    private void ExecuteTerminalCommand() {
        if (SessionIdFieldRef[0] == null || TerminalCommandRef[0] == null) return;
        String SessionIdText = SessionIdFieldRef[0].getText().trim();
        String CommandText   = TerminalCommandRef[0].getText().trim();
        if (SessionIdText.isEmpty() || CommandText.isEmpty()) return;
        int SessionId;
        try {
            SessionId = Integer.parseInt(SessionIdText);
        } catch (NumberFormatException Exception) {
            AppendToTerminal("[!] Invalid session ID\n");
            return;
        }
        if (ServerControl == null || !ServerControl.IsRunning()) {
            AppendToTerminal("[!] Server not running\n");
            return;
        }
        AppendToTerminal("> " + CommandText + "\n");
        TerminalCommandRef[0].clear();
        AddLogEntry("> #" + SessionId + ": " + CommandText);
        final int FinalSessionId = SessionId;
        Executors.newSingleThreadExecutor().submit(() -> {
            String[] Result = ServerControl.GetServer().ExecuteCommand(FinalSessionId, CommandText);
            boolean Success = Boolean.parseBoolean(Result[0]);
            Platform.runLater(() -> {
                AppendToTerminal(Result[1] + "\n\n");
                AddLogEntry(Success ? "[+] OK" : "[!] " + Result[1]);
            });
        });
    }

    private void OpenExecutePopup() {
        if (SelectedSessionId < 0) {
            ShowAlert(Alert.AlertType.WARNING, "Select a session first");
            return;
        }
        if (ServerControl == null || !ServerControl.IsRunning()) {
            ShowAlert(Alert.AlertType.WARNING, "Server not running");
            return;
        }
        PopupBuilder.ShowExecuteWindow(SelectedSessionId, ServerControl);
    }

    private void OpenBroadcastPopup() {
        if (ServerControl == null || !ServerControl.IsRunning()) {
            ShowAlert(Alert.AlertType.WARNING, "Server not running");
            return;
        }
        PopupBuilder.ShowBroadcastWindow(ServerControl, Authentication);
    }

    private void KillSelectedSession() {
        if (SelectedSessionId < 0) {
            ShowAlert(Alert.AlertType.WARNING, "Select a session first");
            return;
        }
        Alert ConfirmDialog = new Alert(Alert.AlertType.CONFIRMATION, "Terminate SESSION-" + SelectedSessionId + "?");
        ConfirmDialog.setHeaderText(null);
        ConfirmDialog.showAndWait().ifPresent(Response -> {
            if (Response == ButtonType.OK) {
                if (SessionController != null) SessionController.Kill(SelectedSessionId);
                SelectedSessionId = -1;
                if (SelectedLabelRef[0] != null) SelectedLabelRef[0].setText("No session selected");
            }
        });
    }

    private void HandleServerEvent(EventType EventType, Map<String, Object> EventData) {
        switch (EventType) {
            case AgentConnected -> {
                AddLogEntry("[+] [" + EventData.get("Type") + "] SESSION-" + EventData.get("ID") +
                    ": " + EventData.get("AgentName") + " (" + EventData.get("OS") + ")");
                Platform.runLater(() -> { if (SessionController != null) SessionController.Refresh(); });
            }
            case AgentDisconnected -> {
                AddLogEntry("[-] SESSION-" + EventData.get("ID") + " disconnected: " + EventData.get("Reason"));
                Platform.runLater(() -> { if (SessionController != null) SessionController.Refresh(); });
            }
            case Error -> AddLogEntry("[!] " + EventData.get("Message"));
        }
    }

    private void AddLogEntry(String Message) {
        String Timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String LogLine   = "[" + Timestamp + "]  " + Message;
        LogEntries.add(LogLine);
        if (LogEntries.size() > ServerConfiguration.GetMaxLogEntries()) LogEntries.remove(0);
        Platform.runLater(() -> {
            if (LogOutputRef[0] != null) LogOutputRef[0].appendText(LogLine + "\n");
        });
    }

    private void AppendToTerminal(String Text) {
        if (TerminalOutputRef[0] != null) TerminalOutputRef[0].appendText(Text);
    }

    private void StartUptimeThread() {
        Thread UptimeThread = new Thread(() -> {
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException Ignored) {}
                if (ServerControl != null && ServerControl.GetStartTime() != null) {
                    long Seconds = java.time.Duration.between(
                        ServerControl.GetStartTime(), java.time.Instant.now()
                    ).getSeconds();
                    String FormattedUptime = SystemHelper.FormatUptime(Seconds);
                    Platform.runLater(() -> {
                        if (UptimeLabelRef[0] != null) UptimeLabelRef[0].setText(FormattedUptime);
                    });
                }
            }
        });
        UptimeThread.setDaemon(true);
        UptimeThread.start();
    }

    private void ShowAlert(Alert.AlertType AlertType, String Message) {
        Alert AlertDialog = new Alert(AlertType, Message);
        AlertDialog.setHeaderText(null);
        AlertDialog.showAndWait();
    }
}
