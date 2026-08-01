package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.component.ComponentFactory;
import com.raven.interfaces.GUI.module.core.database.AuthService;
import com.raven.interfaces.GUI.module.core.server.ServerController;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class PopupBuilder {

    private static final String IconTerminal  = "\uEB8E";
    private static final String IconBroadcast = "\uE0C9";
    private static final String IconSend      = "\uE163";

    private PopupBuilder() {}

    public static void ShowExecuteWindow(int SessionId, ServerController ServerControl) {
        Stage PopupStage = new Stage();
        PopupStage.setTitle("Execute — SESSION-" + SessionId);
        PopupStage.setWidth(740);
        PopupStage.setHeight(560);
        PopupStage.setMinWidth(540);
        PopupStage.setMinHeight(420);

        VBox Layout = new VBox(0);
        Layout.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");

        HBox Header = new HBox(10);
        Header.setAlignment(Pos.CENTER_LEFT);
        Header.setPadding(new Insets(8, 14, 8, 14));
        Header.setStyle(
            "-fx-background-color:" + Palette.BackgroundVoid + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        Header.getChildren().addAll(
            ComponentFactory.IconChip(IconTerminal, Palette.AccentPink, 28, 14),
            ComponentFactory.SmallCapsLabel("Session-" + SessionId, Palette.AccentPink),
            ComponentFactory.FlexSpacer(true),
            ComponentFactory.SmallCapsLabel("Interactive Shell", Palette.TextQuaternary)
        );

        TextArea OutputArea = new TextArea();
        OutputArea.setEditable(false);
        StyleHelper.ApplyTerminal(OutputArea);
        OutputArea.setStyle(StyleHelper.TerminalStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(OutputArea, Priority.ALWAYS);

        HBox InputRow = new HBox(8);
        InputRow.getStyleClass().add("input-bar");
        InputRow.setAlignment(Pos.CENTER_LEFT);
        Label Prompt = new Label(">");
        Prompt.getStyleClass().add("cmd-prompt");
        TextField CommandField = new TextField();
        CommandField.setPromptText("Enter command...");
        CommandField.getStyleClass().add("input-field");
        HBox.setHgrow(CommandField, Priority.ALWAYS);
        Button RunButton = ComponentFactory.ActionButton(IconSend + " Run", "btn", "btn-accent");

        Runnable ExecuteAction = () -> {
            String Command = CommandField.getText().trim();
            if (Command.isEmpty()) return;
            OutputArea.appendText("> " + Command + "\n");
            CommandField.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                String[] Result = ServerControl.GetServer().ExecuteCommand(SessionId, Command);
                Platform.runLater(() -> OutputArea.appendText(Result[1] + "\n\n"));
            });
        };
        RunButton.setOnAction(e -> ExecuteAction.run());
        CommandField.setOnAction(e -> ExecuteAction.run());
        InputRow.getChildren().addAll(Prompt, CommandField, RunButton);
        Layout.getChildren().addAll(Header, OutputArea, InputRow);

        Scene PopupScene = new Scene(Layout);
        PopupStage.setScene(PopupScene);
        PopupStage.show();
        CommandField.requestFocus();
    }

    public static void ShowBroadcastWindow(ServerController ServerControl, AuthService Authentication) {
        Stage PopupStage = new Stage();
        PopupStage.setTitle("Broadcast Command");
        PopupStage.setWidth(700);
        PopupStage.setHeight(560);
        PopupStage.setMinWidth(500);
        PopupStage.setMinHeight(400);

        VBox Layout = new VBox(0);
        Layout.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");

        HBox TargetRow = new HBox(10);
        TargetRow.setAlignment(Pos.CENTER_LEFT);
        TargetRow.setPadding(new Insets(8, 12, 8, 12));
        TargetRow.setStyle(
            "-fx-background-color:" + Palette.BackgroundVoid + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        Label TargetHint = ComponentFactory.MutedLabel("1,2,3  or  all");
        TextField TargetField = new TextField();
        TargetField.setPromptText("Target sessions...");
        TargetField.getStyleClass().add("input-field");
        HBox.setHgrow(TargetField, Priority.ALWAYS);
        TargetRow.getChildren().addAll(
            ComponentFactory.IconChip(IconBroadcast, Palette.AccentBlue, 28, 14),
            ComponentFactory.SmallCapsLabel("Target", Palette.TextTertiary),
            TargetField,
            TargetHint
        );

        TextArea OutputArea = new TextArea();
        OutputArea.setEditable(false);
        StyleHelper.ApplyTerminal(OutputArea);
        OutputArea.setStyle(StyleHelper.TerminalStyle() + "-fx-border-color:transparent;");
        VBox.setVgrow(OutputArea, Priority.ALWAYS);

        HBox CommandRow = new HBox(8);
        CommandRow.getStyleClass().add("input-bar");
        CommandRow.setAlignment(Pos.CENTER_LEFT);
        Label Prompt = new Label(">");
        Prompt.getStyleClass().add("cmd-prompt");
        TextField CommandField = new TextField();
        CommandField.setPromptText("Enter command to broadcast...");
        CommandField.getStyleClass().add("input-field");
        HBox.setHgrow(CommandField, Priority.ALWAYS);
        Button BroadcastButton = ComponentFactory.ActionButton(IconBroadcast + " Broadcast", "btn", "btn-accent");

        Runnable BroadcastAction = () -> {
            String TargetText  = TargetField.getText().trim();
            String CommandText = CommandField.getText().trim();
            if (TargetText.isEmpty() || CommandText.isEmpty()) return;
            OutputArea.appendText("[broadcast → " + TargetText + "]  " + CommandText + "\n");
            CommandField.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                Map<Integer, String[]> Results;
                if (TargetText.equalsIgnoreCase("all")) {
                    Results = ServerControl.GetServer().BroadcastAll(CommandText);
                } else {
                    List<Integer> IdList = new ArrayList<>();
                    for (String Part : TargetText.split(",")) {
                        try { IdList.add(Integer.parseInt(Part.trim())); }
                        catch (Exception Ignored) {}
                    }
                    Results = ServerControl.GetServer().BroadcastCommand(IdList, CommandText);
                }
                final Map<Integer, String[]> FinalResults = Results;
                Platform.runLater(() -> FinalResults.forEach((Id, Result) -> {
                    boolean Success = Boolean.parseBoolean(Result[0]);
                    OutputArea.appendText(
                        "  [#" + Id + "]  " + (Success ? "OK" : "ERR") + "\n" +
                        Result[1] + "\n\n"
                    );
                    Authentication.GetDb().SaveCommandLog(Id, "operator", CommandText, Result[1], Success);
                }));
            });
        };
        BroadcastButton.setOnAction(e -> BroadcastAction.run());
        CommandField.setOnAction(e -> BroadcastAction.run());
        CommandRow.getChildren().addAll(Prompt, CommandField, BroadcastButton);
        Layout.getChildren().addAll(TargetRow, OutputArea, CommandRow);

        PopupStage.setScene(new Scene(Layout));
        PopupStage.show();
        CommandField.requestFocus();
    }

    public static boolean ShowLoginDialog(Stage OwnerStage, AuthService Authentication) {
        Dialog<Boolean> LoginDialog = new Dialog<>();
        LoginDialog.setTitle("RAVEN — Authentication");
        LoginDialog.setHeaderText("TeamServer Login");
        LoginDialog.initOwner(OwnerStage);

        GridPane LoginGrid = new GridPane();
        LoginGrid.setHgap(12);
        LoginGrid.setVgap(12);
        LoginGrid.setPadding(new Insets(18));
        LoginGrid.setStyle("-fx-background-color:" + Palette.Background + ";");

        TextField UsernameField = new TextField();
        UsernameField.setPromptText("Username");
        UsernameField.getStyleClass().add("input-field");
        PasswordField PasswordInputField = new PasswordField();
        PasswordInputField.setPromptText("Password");
        PasswordInputField.getStyleClass().add("password-field");
        Label ErrorLabel = new Label("");
        ErrorLabel.setStyle("-fx-text-fill:" + Palette.AccentRed + "; -fx-font-size:11px;");

        Label UsernameLabel = ComponentFactory.MutedLabel("Username");
        Label PasswordLabel = ComponentFactory.MutedLabel("Password");
        UsernameLabel.setMinWidth(70);
        PasswordLabel.setMinWidth(70);

        LoginGrid.add(UsernameLabel,  0, 0);
        LoginGrid.add(UsernameField,  1, 0);
        LoginGrid.add(PasswordLabel,  0, 1);
        LoginGrid.add(PasswordInputField, 1, 1);
        LoginGrid.add(ErrorLabel,     1, 2);

        ButtonType LoginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        LoginDialog.getDialogPane().getButtonTypes().addAll(LoginButtonType, ButtonType.CANCEL);
        LoginDialog.getDialogPane().setContent(LoginGrid);
        LoginDialog.getDialogPane().setStyle("-fx-background-color:" + Palette.Background + ";");
        LoginDialog.setResultConverter(ButtonClicked -> {
            if (ButtonClicked == LoginButtonType)
                return Authentication.Authenticate(UsernameField.getText().trim(), PasswordInputField.getText()) ? true : null;
            return false;
        });

        for (int Attempt = 0; Attempt < 3; Attempt++) {
            java.util.Optional<Boolean> Result = LoginDialog.showAndWait();
            if (Result.isEmpty() || Boolean.FALSE.equals(Result.get())) return false;
            if (Boolean.TRUE.equals(Result.get())) return true;
            ErrorLabel.setText("Invalid credentials — " + (2 - Attempt) + " attempt(s) remaining");
        }
        return false;
    }
}
