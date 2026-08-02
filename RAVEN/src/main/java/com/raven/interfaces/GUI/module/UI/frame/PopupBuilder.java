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
    private static final String IconShield    = "\uE9E0";

    private PopupBuilder() {}

    public static void ShowExecuteWindow(int SessionId, ServerController ServerControl) {
        Stage PopupStage = new Stage();
        PopupStage.setTitle("Execute — SESSION-" + SessionId);
        PopupStage.setWidth(720);
        PopupStage.setHeight(540);
        PopupStage.setMinWidth(520);
        PopupStage.setMinHeight(400);

        VBox Layout = new VBox(0);
        Layout.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");

        HBox Header = new HBox(9);
        Header.setAlignment(Pos.CENTER_LEFT);
        Header.setPadding(new Insets(7, 12, 7, 12));
        Header.setStyle(
            "-fx-background-color:" + Palette.BackgroundVoid + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        Header.getChildren().addAll(
            ComponentFactory.IconChip(IconTerminal, Palette.AccentPink, 26, 13),
            ComponentFactory.SmallCapsLabel("Session-" + SessionId, Palette.AccentPink),
            ComponentFactory.FlexSpacer(true),
            ComponentFactory.SmallCapsLabel("Interactive Shell", Palette.TextQuaternary)
        );

        TextArea OutputArea = new TextArea();
        OutputArea.setEditable(false);
        StyleHelper.ApplyTerminal(OutputArea);
        VBox.setVgrow(OutputArea, Priority.ALWAYS);

        HBox InputRow = new HBox(7);
        InputRow.getStyleClass().add("input-bar");
        InputRow.setAlignment(Pos.CENTER_LEFT);
        Label Prompt = new Label("❯");
        Prompt.getStyleClass().add("cmd-prompt");
        TextField CmdField = new TextField();
        CmdField.setPromptText("Enter command...");
        CmdField.getStyleClass().add("input-field");
        HBox.setHgrow(CmdField, Priority.ALWAYS);
        Button RunBtn = ComponentFactory.ActionButton(IconSend + " Run", "btn", "btn-accent");

        Runnable ExecuteAction = () -> {
            String Cmd = CmdField.getText().trim();
            if (Cmd.isEmpty()) return;
            OutputArea.appendText("> " + Cmd + "\n");
            CmdField.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                String[] Result = ServerControl.GetServer().ExecuteCommand(SessionId, Cmd);
                Platform.runLater(() -> OutputArea.appendText(Result[1] + "\n\n"));
            });
        };
        RunBtn.setOnAction(e -> ExecuteAction.run());
        CmdField.setOnAction(e -> ExecuteAction.run());
        InputRow.getChildren().addAll(Prompt, CmdField, RunBtn);
        Layout.getChildren().addAll(Header, OutputArea, InputRow);

        Scene PopupScene = new Scene(Layout);
        PopupStage.setScene(PopupScene);
        PopupStage.show();
        CmdField.requestFocus();
    }

    public static void ShowBroadcastWindow(ServerController ServerControl, AuthService Authentication) {
        Stage PopupStage = new Stage();
        PopupStage.setTitle("Broadcast Command");
        PopupStage.setWidth(680);
        PopupStage.setHeight(540);
        PopupStage.setMinWidth(480);
        PopupStage.setMinHeight(380);

        VBox Layout = new VBox(0);
        Layout.setStyle("-fx-background-color:" + Palette.TerminalBackground + ";");

        HBox TargetRow = new HBox(9);
        TargetRow.setAlignment(Pos.CENTER_LEFT);
        TargetRow.setPadding(new Insets(7, 10, 7, 10));
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
            ComponentFactory.IconChip(IconBroadcast, Palette.AccentBlue, 26, 13),
            ComponentFactory.SmallCapsLabel("Target", Palette.TextTertiary),
            TargetField,
            TargetHint
        );

        TextArea OutputArea = new TextArea();
        OutputArea.setEditable(false);
        StyleHelper.ApplyTerminal(OutputArea);
        VBox.setVgrow(OutputArea, Priority.ALWAYS);

        HBox CmdRow = new HBox(7);
        CmdRow.getStyleClass().add("input-bar");
        CmdRow.setAlignment(Pos.CENTER_LEFT);
        Label Prompt = new Label("❯");
        Prompt.getStyleClass().add("cmd-prompt");
        TextField CmdField = new TextField();
        CmdField.setPromptText("Enter command to broadcast...");
        CmdField.getStyleClass().add("input-field");
        HBox.setHgrow(CmdField, Priority.ALWAYS);
        Button BcastBtn = ComponentFactory.ActionButton(IconBroadcast + " Broadcast", "btn", "btn-accent");

        Runnable BroadcastAction = () -> {
            String TargetText  = TargetField.getText().trim();
            String CommandText = CmdField.getText().trim();
            if (TargetText.isEmpty() || CommandText.isEmpty()) return;
            OutputArea.appendText("[broadcast → " + TargetText + "]  " + CommandText + "\n");
            CmdField.clear();
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
        BcastBtn.setOnAction(e -> BroadcastAction.run());
        CmdField.setOnAction(e -> BroadcastAction.run());
        CmdRow.getChildren().addAll(Prompt, CmdField, BcastBtn);
        Layout.getChildren().addAll(TargetRow, OutputArea, CmdRow);

        PopupStage.setScene(new Scene(Layout));
        PopupStage.show();
        CmdField.requestFocus();
    }

    public static boolean ShowLoginDialog(Stage OwnerStage, AuthService Authentication) {
        Dialog<Boolean> LoginDialog = new Dialog<>();
        LoginDialog.setTitle("RAVEN — Authentication");
        LoginDialog.setHeaderText("TeamServer Login");
        LoginDialog.initOwner(OwnerStage);

        GridPane LoginGrid = new GridPane();
        LoginGrid.setHgap(10);
        LoginGrid.setVgap(10);
        LoginGrid.setPadding(new Insets(16));
        LoginGrid.setStyle("-fx-background-color:" + Palette.Background + ";");

        TextField UsernameField = new TextField();
        UsernameField.setPromptText("Username");
        UsernameField.getStyleClass().add("input-field");
        PasswordField PasswordInputField = new PasswordField();
        PasswordInputField.setPromptText("Password");
        PasswordInputField.getStyleClass().add("password-field");
        Label ErrorLabel = new Label("");
        ErrorLabel.setStyle("-fx-text-fill:" + Palette.AccentRed + "; -fx-font-size:11px;");

        Label UsernameLbl = ComponentFactory.MutedLabel("Username");
        Label PasswordLbl = ComponentFactory.MutedLabel("Password");
        UsernameLbl.setMinWidth(70);
        PasswordLbl.setMinWidth(70);

        LoginGrid.add(UsernameLbl,        0, 0);
        LoginGrid.add(UsernameField,      1, 0);
        LoginGrid.add(PasswordLbl,        0, 1);
        LoginGrid.add(PasswordInputField, 1, 1);
        LoginGrid.add(ErrorLabel,         1, 2);

        ButtonType LoginBtnType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        LoginDialog.getDialogPane().getButtonTypes().addAll(LoginBtnType, ButtonType.CANCEL);
        LoginDialog.getDialogPane().setContent(LoginGrid);
        LoginDialog.getDialogPane().setStyle("-fx-background-color:" + Palette.Background + ";");
        LoginDialog.setResultConverter(Btn -> {
            if (Btn == LoginBtnType)
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
