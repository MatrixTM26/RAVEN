package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.component.ComponentFactory;
import com.raven.interfaces.GUI.module.core.server.CommandDispatcher;
import com.raven.interfaces.GUI.module.core.server.ServerController;
import com.raven.interfaces.GUI.module.core.session.SessionManager;
import com.raven.interfaces.GUI.module.core.session.SessionRow;
import com.raven.utils.ServerConfig;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
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

    private PageBuilder() {}

    public static VBox Overview() {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");

        GridPane StatGrid = new GridPane();
        StatGrid.setHgap(16);
        StatGrid.setVgap(0);
        StatGrid.setPadding(new Insets(16));
        for (int Index = 0; Index < 4; Index++) {
            ColumnConstraints Column = new ColumnConstraints();
            Column.setPercentWidth(25);
            Column.setHgrow(Priority.ALWAYS);
            StatGrid.getColumnConstraints().add(Column);
        }
        StatGrid.add(ComponentFactory.StatCard("Sessions",      "0", Palette.AccentBlue,   IconDevices,  "+0", true), 0, 0);
        StatGrid.add(ComponentFactory.StatCard("Raven Agents",  "0", Palette.AccentGreen,  IconWifi,     "+0", true), 1, 0);
        StatGrid.add(ComponentFactory.StatCard("Meterpreter",   "0", Palette.AccentTeal,   IconCircle,   "+0", true), 2, 0);
        StatGrid.add(ComponentFactory.StatCard("Reverse Shell", "0", Palette.AccentPink,   IconTerminal, "+0", true), 3, 0);
        Page.getChildren().add(StatGrid);

        ScrollPane ScrollWrapper = new ScrollPane();
        ScrollWrapper.setFitToWidth(true);
        ScrollWrapper.setStyle("-fx-background-color:" + Palette.Background + ";");
        VBox.setVgrow(ScrollWrapper, Priority.ALWAYS);

        VBox InnerContent = new VBox(14);
        InnerContent.setPadding(new Insets(0, 16, 16, 16));
        InnerContent.setStyle("-fx-background-color:" + Palette.Background + ";");

        VBox ActivityCard = ComponentFactory.PanelCard("Recent Activity", IconList, Palette.AccentBlue);
        VBox ActivityBody = ComponentFactory.GetPanelBody(ActivityCard);
        ActivityBody.setPadding(new Insets(0));
        ActivityBody.getChildren().addAll(
            ComponentFactory.ActivityRow(IconCheck,   Palette.AccentGreen,  "Server initialized and ready",      "just now"),
            ComponentFactory.ActivityRow(IconShield,  Palette.AccentBlue,   "Authentication module loaded",      "just now"),
            ComponentFactory.ActivityRow(IconWarning, Palette.AccentOrange, "Awaiting first session connection", "idle")
        );

        VBox InfoCard = ComponentFactory.PanelCard("Tool Information", IconDns, Palette.AccentTeal);
        VBox InfoBody = ComponentFactory.GetPanelBody(InfoCard);
        TextArea InfoText = new TextArea(
            " Author  : MatrixTM26\n Version : 3.0\n\n" +
            " Sessions  — Execute, Broadcast, Kill, filter\n" +
            " Terminal  — Interactive agent shell per session\n" +
            " Commands  — CLI-aligned server/session utilities\n\n" +
            " sessions | status | stats | tasks | kill <id> | sysinfo <id>\n" +
            " history [id] [limit] | note <id> <text> | getnote <id>\n" +
            " broadcast <cmd> | exec <id> <cmd> | whoami <id>\n" +
            " sleep <id> <sec> | screenshot <id> | download | upload"
        );
        InfoText.setEditable(false);
        InfoText.setPrefHeight(180);
        StyleHelper.ApplyTerminal(InfoText);
        InfoText.setStyle(StyleHelper.TerminalStyle() + "-fx-border-color:transparent;");
        InfoBody.setPadding(new Insets(0));
        InfoBody.getChildren().add(InfoText);

        InnerContent.getChildren().addAll(ActivityCard, InfoCard);
        ScrollWrapper.setContent(InnerContent);
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

        HBox Toolbar = new HBox(8);
        Toolbar.getStyleClass().add("toolbar-bar");
        Toolbar.setAlignment(Pos.CENTER_LEFT);

        TextField SearchField = new TextField();
        SearchField.setPromptText("Filter sessions...");
        SearchField.getStyleClass().add("search-field");
        SearchField.setPrefWidth(230);

        Toolbar.getChildren().addAll(
            SearchField,
            ComponentFactory.FlexSpacer(true),
            ComponentFactory.ActionButton(IconRefresh   + " Refresh",   "btn", "btn-default"),
            ComponentFactory.ActionButton(IconPlay      + " Execute",   "btn", "btn-accent"),
            ComponentFactory.ActionButton(IconBroadcast + " Broadcast", "btn", "btn-default"),
            ComponentFactory.ActionButton(IconDelete    + " Kill",      "btn", "btn-danger")
        );

        Button RefreshButton   = (Button) Toolbar.getChildren().get(2);
        Button ExecuteButton   = (Button) Toolbar.getChildren().get(3);
        Button BroadcastButton = (Button) Toolbar.getChildren().get(4);
        Button KillButton      = (Button) Toolbar.getChildren().get(5);
        RefreshButton.setOnAction(e -> OnRefresh.run());
        ExecuteButton.setOnAction(e -> OnExecute.run());
        BroadcastButton.setOnAction(e -> OnBroadcast.run());
        KillButton.setOnAction(e -> OnKill.run());
        Page.getChildren().add(Toolbar);

        HBox CommandBar = new HBox(8);
        CommandBar.getStyleClass().add("cmd-bar");
        CommandBar.setAlignment(Pos.CENTER_LEFT);
        Label CommandPrompt = new Label(">");
        CommandPrompt.getStyleClass().add("cmd-prompt");
        TextField CommandField = new TextField();
        CommandField.setPromptText("sessions | status | kill <id> | exec <id> <cmd> | sysinfo <id> | broadcast <cmd>");
        CommandField.getStyleClass().add("input-field");
        HBox.setHgrow(CommandField, Priority.ALWAYS);
        Button RunButton = ComponentFactory.ActionButton("Run", "btn", "btn-accent");
        RunButton.setOnAction(e -> {
            if (DispatcherRef[0] != null) DispatcherRef[0].Dispatch(CommandField.getText().trim(), CommandField);
        });
        CommandField.setOnAction(e -> {
            if (DispatcherRef[0] != null) DispatcherRef[0].Dispatch(CommandField.getText().trim(), CommandField);
        });
        CommandBar.getChildren().addAll(CommandPrompt, CommandField, RunButton);
        Page.getChildren().add(CommandBar);

        SplitPane VerticalSplit = new SplitPane();
        VerticalSplit.setOrientation(Orientation.VERTICAL);
        VerticalSplit.setDividerPositions(0.65);
        VBox.setVgrow(VerticalSplit, Priority.ALWAYS);

        TableView<SessionRow> SessionTable = new TableView<>();
        SessionTable.getStyleClass().add("session-table");
        FilteredList<SessionRow> FilteredRows = new FilteredList<>(SessionRows, Row -> true);
        SearchField.textProperty().addListener((Observable, Old, NewValue) ->
            FilteredRows.setPredicate(Row ->
                NewValue == null || NewValue.isBlank()
                    || Row.getName().toLowerCase().contains(NewValue.toLowerCase())
                    || Row.getIp().contains(NewValue)
                    || Row.getUser().toLowerCase().contains(NewValue.toLowerCase())
                    || Row.getHost().toLowerCase().contains(NewValue.toLowerCase())
            )
        );
        SessionTable.setItems(FilteredRows);
        String[] Headers    = {"ID", "Type", "Name / Cert", "IP", "OS", "User", "Host", "Session Key"};
        String[] Properties = {"id", "type", "name",        "ip", "os", "user", "host", "joined"};
        for (int Index = 0; Index < Headers.length; Index++) {
            TableColumn<SessionRow, String> Column = new TableColumn<>(Headers[Index]);
            Column.setCellValueFactory(new PropertyValueFactory<>(Properties[Index]));
            SessionTable.getColumns().add(Column);
        }
        SessionTable.getSelectionModel().selectedItemProperty().addListener((Observable, Old, NewRow) -> {
            if (NewRow != null) OnSelectionChanged.accept(Integer.parseInt(NewRow.getId()));
        });
        SessionTable.setPlaceholder(ComponentFactory.PlaceholderLabel("No active sessions"));
        SessionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (TableRef != null && TableRef.length > 0) TableRef[0] = SessionTable;

        VBox LogPanel = new VBox(0);
        LogPanel.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");
        HBox LogHeader = new HBox(8);
        LogHeader.setAlignment(Pos.CENTER_LEFT);
        LogHeader.setPadding(new Insets(6, 12, 6, 12));
        LogHeader.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        TextArea LogOutputArea = new TextArea();
        LogOutputArea.setEditable(false);
        StyleHelper.ApplyTerminal(LogOutputArea);
        LogOutputArea.setStyle(StyleHelper.TerminalStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(LogOutputArea, Priority.ALWAYS);
        if (LogRef != null && LogRef.length > 0) LogRef[0] = LogOutputArea;

        LogHeader.getChildren().addAll(
            ComponentFactory.MaterialIcon(IconList, Palette.TextTertiary, 12),
            ComponentFactory.SmallCapsLabel("Output", Palette.TextTertiary),
            ComponentFactory.FlexSpacer(true),
            ComponentFactory.IconButton(IconClear, "Clear", e -> LogOutputArea.clear())
        );
        LogPanel.getChildren().addAll(LogHeader, LogOutputArea);

        VerticalSplit.getItems().addAll(SessionTable, LogPanel);
        Page.getChildren().add(VerticalSplit);
        return Page;
    }

    public static VBox Terminal(TextField[] SessionIdRef,
                                TextArea[]  TerminalOutputRef,
                                TextField[] CommandFieldRef,
                                Runnable    OnSendCommand,
                                Label[]     SelectedLabelRef) {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");

        HBox TerminalBar = new HBox(10);
        TerminalBar.setAlignment(Pos.CENTER_LEFT);
        TerminalBar.setPadding(new Insets(8, 14, 8, 14));
        TerminalBar.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );

        TextField SessionIdField = new TextField();
        SessionIdField.setPrefWidth(80);
        SessionIdField.getStyleClass().add("input-field");
        SessionIdField.setPromptText("Session ID");
        if (SessionIdRef != null && SessionIdRef.length > 0) SessionIdRef[0] = SessionIdField;

        Region TerminalDivider = StyleHelper.VerticalDivider();

        Label SelectedLabel = new Label("No session selected");
        SelectedLabel.setStyle("-fx-font-size:11px; -fx-text-fill:" + Palette.TextTertiary + ";");
        HBox.setHgrow(SelectedLabel, Priority.ALWAYS);
        if (SelectedLabelRef != null && SelectedLabelRef.length > 0) SelectedLabelRef[0] = SelectedLabel;

        TextArea TerminalOutput = new TextArea();
        TerminalOutput.setEditable(false);
        StyleHelper.ApplyTerminal(TerminalOutput);
        TerminalOutput.setStyle(StyleHelper.TerminalStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(TerminalOutput, Priority.ALWAYS);
        if (TerminalOutputRef != null && TerminalOutputRef.length > 0) TerminalOutputRef[0] = TerminalOutput;

        TerminalBar.getChildren().addAll(
            ComponentFactory.SmallCapsLabel("Session", Palette.TextTertiary),
            SessionIdField,
            TerminalDivider,
            SelectedLabel,
            ComponentFactory.IconButton(IconClear, "Clear", e -> TerminalOutput.clear())
        );
        Page.getChildren().addAll(TerminalBar, TerminalOutput);

        HBox InputRow = new HBox(8);
        InputRow.getStyleClass().add("input-bar");
        InputRow.setAlignment(Pos.CENTER_LEFT);
        Label Prompt = new Label(">");
        Prompt.getStyleClass().add("cmd-prompt");
        TextField CommandField = new TextField();
        CommandField.setPromptText("Enter command...");
        CommandField.getStyleClass().add("input-field");
        HBox.setHgrow(CommandField, Priority.ALWAYS);
        if (CommandFieldRef != null && CommandFieldRef.length > 0) CommandFieldRef[0] = CommandField;
        Button SendButton = ComponentFactory.ActionButton(IconSend + " Send", "btn", "btn-accent");
        SendButton.setOnAction(e -> OnSendCommand.run());
        CommandField.setOnAction(e -> OnSendCommand.run());
        InputRow.getChildren().addAll(Prompt, CommandField, SendButton);
        Page.getChildren().add(InputRow);
        return Page;
    }

    public static VBox CommandCenter(CommandDispatcher[] DispatcherRef, TextArea[] OutputRef) {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.Background + ";");

        VBox ReferenceBar = new VBox(0);
        ReferenceBar.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        HBox RefHeader = new HBox(8);
        RefHeader.setAlignment(Pos.CENTER_LEFT);
        RefHeader.setPadding(new Insets(7, 14, 7, 14));
        RefHeader.setStyle("-fx-background-color:" + Palette.BackgroundVoid + ";");
        RefHeader.getChildren().addAll(
            ComponentFactory.MaterialIcon(IconCode, Palette.TextTertiary, 12),
            ComponentFactory.SmallCapsLabel("Command Reference", Palette.TextTertiary)
        );
        TextArea ReferenceText = new TextArea(
            "sessions | status | stats | tasks\n" +
            "kill <id>  |  exec <id> <cmd>  |  sysinfo <id>  |  whoami <id>\n" +
            "broadcast <cmd>  |  sleep <id> <sec>  |  screenshot <id>\n" +
            "download <id> <remote>  |  upload <id> <local> <remote>\n" +
            "note <id> <text>  |  getnote <id>  |  history [id] [limit]"
        );
        ReferenceText.setEditable(false);
        ReferenceText.setPrefHeight(90);
        StyleHelper.ApplyTerminal(ReferenceText);
        ReferenceText.setStyle(StyleHelper.TerminalStyle() + "-fx-border-color:transparent;");
        ReferenceBar.getChildren().addAll(RefHeader, ReferenceText);
        Page.getChildren().add(ReferenceBar);

        HBox CommandBar = new HBox(8);
        CommandBar.getStyleClass().add("cmd-bar");
        CommandBar.setAlignment(Pos.CENTER_LEFT);
        Label Prompt = new Label(">");
        Prompt.getStyleClass().add("cmd-prompt");
        TextField CommandField = new TextField();
        CommandField.setPromptText("Type command...");
        CommandField.getStyleClass().add("input-field");
        HBox.setHgrow(CommandField, Priority.ALWAYS);
        Button ExecuteButton = ComponentFactory.ActionButton(IconPlay  + " Execute", "btn", "btn-accent");
        Button ClearButton   = ComponentFactory.ActionButton(IconClear + " Clear",   "btn", "btn-default");

        TextArea OutputArea = new TextArea();
        OutputArea.setEditable(false);
        StyleHelper.ApplyTerminal(OutputArea);
        OutputArea.setStyle(StyleHelper.TerminalStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(OutputArea, Priority.ALWAYS);
        if (OutputRef != null && OutputRef.length > 0) OutputRef[0] = OutputArea;

        ExecuteButton.setOnAction(e -> {
            if (DispatcherRef[0] != null) DispatcherRef[0].Dispatch(CommandField.getText().trim(), CommandField);
        });
        CommandField.setOnAction(e -> {
            if (DispatcherRef[0] != null) DispatcherRef[0].Dispatch(CommandField.getText().trim(), CommandField);
        });
        ClearButton.setOnAction(e -> OutputArea.clear());
        CommandBar.getChildren().addAll(Prompt, CommandField, ExecuteButton, ClearButton);
        Page.getChildren().addAll(CommandBar, OutputArea);
        return Page;
    }

    public static VBox Logs(TextArea[] LogOutputRef) {
        VBox Page = new VBox(0);
        Page.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");

        HBox LogToolbar = new HBox(8);
        LogToolbar.setAlignment(Pos.CENTER_LEFT);
        LogToolbar.setPadding(new Insets(6, 12, 6, 12));
        LogToolbar.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        HBox LogTitleGroup = new HBox(8);
        LogTitleGroup.setAlignment(Pos.CENTER_LEFT);
        LogTitleGroup.getChildren().addAll(
            ComponentFactory.MaterialIcon(IconList, Palette.TextTertiary, 12),
            ComponentFactory.SmallCapsLabel("Activity Log", Palette.TextTertiary)
        );
        HBox.setHgrow(LogTitleGroup, Priority.ALWAYS);

        TextArea LogOutputArea = new TextArea();
        LogOutputArea.setEditable(false);
        StyleHelper.ApplyTerminal(LogOutputArea);
        LogOutputArea.setStyle(StyleHelper.TerminalStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(LogOutputArea, Priority.ALWAYS);
        if (LogOutputRef != null && LogOutputRef.length > 0) LogOutputRef[0] = LogOutputArea;

        Button ExportButton = ComponentFactory.ActionButton(IconExport + " Export", "btn", "btn-default");
        Button ClearButton  = ComponentFactory.ActionButton(IconClear  + " Clear",  "btn", "btn-danger");
        ClearButton.setOnAction(e -> LogOutputArea.clear());
        LogToolbar.getChildren().addAll(LogTitleGroup, ExportButton, ClearButton);
        Page.getChildren().addAll(LogToolbar, LogOutputArea);
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
        VBox Content = new VBox(16);
        Content.setPadding(new Insets(18));
        Content.setStyle("-fx-background-color:" + Palette.Background + ";");

        VBox ServerToggleCard = new VBox(16);
        ServerToggleCard.getStyleClass().add("server-toggle-card");

        HBox ToggleRow = new HBox(16);
        ToggleRow.setAlignment(Pos.CENTER_LEFT);

        VBox ToggleInfo = new VBox(4);
        Label ToggleTitle = new Label("Listener");
        ToggleTitle.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:" + Palette.TextPrimary + ";");
        Label ToggleStatus = new Label("Server is offline");
        ToggleStatus.setStyle("-fx-font-size:11px; -fx-text-fill:" + Palette.TextTertiary + ";");
        ToggleInfo.getChildren().addAll(ToggleTitle, ToggleStatus);
        HBox.setHgrow(ToggleInfo, Priority.ALWAYS);
        if (ToggleStatusLabelRef != null && ToggleStatusLabelRef.length > 0) ToggleStatusLabelRef[0] = ToggleStatus;

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
        if (ServerToggleRef != null && ServerToggleRef.length > 0) ServerToggleRef[0] = ServerToggle;
        ToggleRow.getChildren().addAll(ToggleInfo, ServerToggle);
        ServerToggleCard.getChildren().add(ToggleRow);
        ServerToggleCard.getChildren().add(StyleHelper.HorizontalDivider());

        GridPane ConnectionFields = new GridPane();
        ConnectionFields.setHgap(12);
        ConnectionFields.setVgap(10);
        ColumnConstraints LabelColumn = new ColumnConstraints();
        LabelColumn.setMinWidth(50);
        LabelColumn.setMaxWidth(60);
        ColumnConstraints InputColumn = new ColumnConstraints();
        InputColumn.setHgrow(Priority.ALWAYS);
        ConnectionFields.getColumnConstraints().addAll(LabelColumn, InputColumn);

        TextField HostField = new TextField(Config.GetServerHost());
        TextField PortField = new TextField(String.valueOf(Config.GetServerPort()));
        HostField.getStyleClass().add("input-field");
        PortField.getStyleClass().add("input-field");
        if (HostFieldRef != null && HostFieldRef.length > 0) HostFieldRef[0] = HostField;
        if (PortFieldRef != null && PortFieldRef.length > 0) PortFieldRef[0] = PortField;

        ConnectionFields.add(ComponentFactory.MutedLabel("Host"), 0, 0);
        ConnectionFields.add(HostField, 1, 0);
        ConnectionFields.add(ComponentFactory.MutedLabel("Port"), 0, 1);
        ConnectionFields.add(PortField, 1, 1);
        ServerToggleCard.getChildren().add(ConnectionFields);
        Content.getChildren().add(ServerToggleCard);

        VBox StatusCard = ComponentFactory.PanelCard("Server Status", IconDns, Palette.AccentTeal);
        VBox StatusBody = ComponentFactory.GetPanelBody(StatusCard);

        Label ServerStatusLabel = new Label("Offline");
        ServerStatusLabel.setStyle("-fx-text-fill:" + Palette.AccentRed + "; -fx-font-size:12px; -fx-font-weight:bold;");
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

        ScrollPane SettingsScroll = new ScrollPane(Content);
        SettingsScroll.setFitToWidth(true);
        SettingsScroll.setStyle("-fx-background-color:" + Palette.Background + ";");
        return SettingsScroll;
    }
}
