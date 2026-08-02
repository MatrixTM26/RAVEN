package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.core.database.AuthService;
import com.raven.interfaces.GUI.module.core.server.ServerController;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class PopupBuilder {

    private PopupBuilder() {}

    public static void ShowExecuteWindow(int SessionId, ServerController SrvCtrl) {
        Stage Win = new Stage();
        Win.setTitle("SESSION-" + SessionId + " — Shell");
        Win.setWidth(720);
        Win.setHeight(520);
        Win.setMinWidth(480);
        Win.setMinHeight(360);

        VBox Layout = new VBox(0);
        Layout.setStyle("-fx-background-color:" + Palette.TermBg + ";");

        HBox Header = new HBox(10);
        Header.setAlignment(Pos.CENTER_LEFT);
        Header.setPadding(new Insets(7, 12, 7, 12));
        Header.setStyle(
            "-fx-background-color:" + Palette.BgVoid + ";" +
            "-fx-border-color:transparent transparent " + Palette.Border + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        Label Title = new Label("SESSION-" + SessionId + "  ·  Interactive Shell");
        Title.setStyle("-fx-text-fill:" + Palette.Red + "; -fx-font-size:11px; -fx-font-weight:bold; -fx-letter-spacing:0.06em;");
        Region Spacer = new Region();
        HBox.setHgrow(Spacer, Priority.ALWAYS);
        Button ClearBtn = new Button("Clear");
        ClearBtn.getStyleClass().addAll("btn", "btn-default");
        Header.getChildren().addAll(Title, Spacer, ClearBtn);

        TextArea Output = new TextArea();
        Output.setEditable(false);
        Output.setWrapText(true);
        Output.getStyleClass().add("terminal-area");
        VBox.setVgrow(Output, Priority.ALWAYS);
        ClearBtn.setOnAction(e -> Output.clear());

        HBox InputRow = new HBox(7);
        InputRow.setAlignment(Pos.CENTER_LEFT);
        InputRow.setPadding(new Insets(7, 10, 7, 10));
        InputRow.setStyle(
            "-fx-background-color:" + Palette.BgVoid + ";" +
            "-fx-border-color:" + Palette.Border + " transparent transparent transparent;" +
            "-fx-border-width:1 0 0 0;"
        );
        Label Prompt = new Label("[#" + SessionId + "]❯");
        Prompt.setStyle("-fx-text-fill:" + Palette.Red + "; -fx-font-size:13px; -fx-font-weight:bold;");
        TextField CmdField = new TextField();
        CmdField.setPromptText("Enter command...");
        CmdField.getStyleClass().add("input-field");
        HBox.setHgrow(CmdField, Priority.ALWAYS);
        Button RunBtn = new Button("Send");
        RunBtn.getStyleClass().addAll("btn", "btn-primary");

        Runnable Exec = () -> {
            String Cmd = CmdField.getText().trim();
            if (Cmd.isEmpty()) return;
            Output.appendText("[#" + SessionId + "]❯ " + Cmd + "\n");
            CmdField.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                String[] Res = SrvCtrl.GetServer().ExecuteCommand(SessionId, Cmd);
                Platform.runLater(() -> Output.appendText(Res[1] + "\n\n"));
            });
        };
        RunBtn.setOnAction(e -> Exec.run());
        CmdField.setOnAction(e -> Exec.run());
        InputRow.getChildren().addAll(Prompt, CmdField, RunBtn);
        Layout.getChildren().addAll(Header, Output, InputRow);

        Scene Sc = new Scene(Layout);
        URL Css = PopupBuilder.class.getResource("/com/raven/interfaces/GUI/styles/raven.css");
        if (Css != null) Sc.getStylesheets().add(Css.toExternalForm());
        Win.setScene(Sc);
        Win.show();
        CmdField.requestFocus();
    }

    public static void ShowBroadcastWindow(ServerController SrvCtrl, AuthService Auth) {
        Stage Win = new Stage();
        Win.setTitle("Broadcast Command");
        Win.setWidth(680);
        Win.setHeight(500);
        Win.setMinWidth(480);
        Win.setMinHeight(340);

        VBox Layout = new VBox(0);
        Layout.setStyle("-fx-background-color:" + Palette.TermBg + ";");

        HBox TargetRow = new HBox(10);
        TargetRow.setAlignment(Pos.CENTER_LEFT);
        TargetRow.setPadding(new Insets(7, 12, 7, 12));
        TargetRow.setStyle(
            "-fx-background-color:" + Palette.BgVoid + ";" +
            "-fx-border-color:transparent transparent " + Palette.Border + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        Label TargetLbl = new Label("TARGET");
        TargetLbl.setStyle("-fx-text-fill:" + Palette.Red + "; -fx-font-size:10px; -fx-font-weight:bold; -fx-letter-spacing:0.08em;");
        TextField TargetField = new TextField();
        TargetField.setPromptText("1,2,3  or  all");
        TargetField.getStyleClass().add("input-field");
        TargetField.setPrefWidth(200);
        Label Hint = new Label("comma-separated IDs or 'all'");
        Hint.setStyle("-fx-text-fill:" + Palette.WhiteFaint + "; -fx-font-size:10px;");
        TargetRow.getChildren().addAll(TargetLbl, TargetField, Hint);

        TextArea Output = new TextArea();
        Output.setEditable(false);
        Output.setWrapText(true);
        Output.getStyleClass().add("terminal-area");
        VBox.setVgrow(Output, Priority.ALWAYS);

        HBox CmdRow = new HBox(7);
        CmdRow.setAlignment(Pos.CENTER_LEFT);
        CmdRow.setPadding(new Insets(7, 10, 7, 10));
        CmdRow.setStyle(
            "-fx-background-color:" + Palette.BgVoid + ";" +
            "-fx-border-color:" + Palette.Border + " transparent transparent transparent;" +
            "-fx-border-width:1 0 0 0;"
        );
        Label Prompt = new Label("❯");
        Prompt.setStyle("-fx-text-fill:" + Palette.Red + "; -fx-font-size:13px; -fx-font-weight:bold;");
        TextField CmdField = new TextField();
        CmdField.setPromptText("Command to broadcast...");
        CmdField.getStyleClass().add("input-field");
        HBox.setHgrow(CmdField, Priority.ALWAYS);
        Button BcastBtn = new Button("Broadcast");
        BcastBtn.getStyleClass().addAll("btn", "btn-primary");

        Runnable DoBcast = () -> {
            String Target = TargetField.getText().trim();
            String Cmd    = CmdField.getText().trim();
            if (Target.isEmpty() || Cmd.isEmpty()) return;
            Output.appendText("[broadcast → " + Target + "]  " + Cmd + "\n");
            CmdField.clear();
            Executors.newSingleThreadExecutor().submit(() -> {
                Map<Integer, String[]> Results;
                if (Target.equalsIgnoreCase("all")) {
                    Results = SrvCtrl.GetServer().BroadcastAll(Cmd);
                } else {
                    List<Integer> Ids = new ArrayList<>();
                    for (String P : Target.split(",")) {
                        try { Ids.add(Integer.parseInt(P.trim())); } catch (Exception Ignored) {}
                    }
                    Results = SrvCtrl.GetServer().BroadcastCommand(Ids, Cmd);
                }
                final Map<Integer, String[]> Final = Results;
                Platform.runLater(() -> Final.forEach((Id, Res) -> {
                    boolean Ok = Boolean.parseBoolean(Res[0]);
                    Output.appendText("  [#" + Id + "]  " + (Ok ? "OK" : "ERR") + "  " + Res[1] + "\n");
                    Auth.GetDb().SaveCommandLog(Id, Auth.GetOperatorName(), Cmd, Res[1], Ok);
                }));
            });
        };
        BcastBtn.setOnAction(e -> DoBcast.run());
        CmdField.setOnAction(e -> DoBcast.run());
        CmdRow.getChildren().addAll(Prompt, CmdField, BcastBtn);
        Layout.getChildren().addAll(TargetRow, Output, CmdRow);

        Scene Sc = new Scene(Layout);
        URL Css = PopupBuilder.class.getResource("/com/raven/interfaces/GUI/styles/raven.css");
        if (Css != null) Sc.getStylesheets().add(Css.toExternalForm());
        Win.setScene(Sc);
        Win.show();
        CmdField.requestFocus();
    }

    public static boolean ShowLoginDialog(Stage Owner, AuthService Auth) {
        Dialog<Boolean> Dlg = new Dialog<>();
        Dlg.setTitle("RAVEN — Authentication");
        Dlg.setHeaderText("TeamServer Login");
        Dlg.initOwner(Owner);

        GridPane Grid = new GridPane();
        Grid.setHgap(10);
        Grid.setVgap(10);
        Grid.setPadding(new Insets(14));
        Grid.setStyle("-fx-background-color:" + Palette.Bg + ";");

        ColumnConstraints LblCol = new ColumnConstraints();
        LblCol.setMinWidth(80);
        ColumnConstraints InpCol = new ColumnConstraints();
        InpCol.setHgrow(Priority.ALWAYS);
        Grid.getColumnConstraints().addAll(LblCol, InpCol);

        TextField UserField = new TextField();
        UserField.setPromptText("Username");
        UserField.getStyleClass().add("input-field");
        PasswordField PassField = new PasswordField();
        PassField.setPromptText("Password");
        PassField.getStyleClass().add("password-field");
        Label ErrLabel = new Label("");
        ErrLabel.setStyle("-fx-text-fill:" + Palette.Red + "; -fx-font-size:11px;");

        Label UserLbl = new Label("Username");
        Label PassLbl = new Label("Password");
        UserLbl.setStyle("-fx-text-fill:" + Palette.WhiteDim + "; -fx-font-size:11px;");
        PassLbl.setStyle("-fx-text-fill:" + Palette.WhiteDim + "; -fx-font-size:11px;");

        Grid.add(UserLbl,  0, 0); Grid.add(UserField, 1, 0);
        Grid.add(PassLbl,  0, 1); Grid.add(PassField, 1, 1);
        Grid.add(ErrLabel, 1, 2);

        ButtonType LoginBtn = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        Dlg.getDialogPane().getButtonTypes().addAll(LoginBtn, ButtonType.CANCEL);
        Dlg.getDialogPane().setContent(Grid);
        Dlg.getDialogPane().setStyle("-fx-background-color:" + Palette.Bg + ";");
        Dlg.setResultConverter(Btn -> {
            if (Btn == LoginBtn)
                return Auth.Authenticate(UserField.getText().trim(), PassField.getText()) ? true : null;
            return false;
        });

        for (int Attempt = 0; Attempt < 3; Attempt++) {
            java.util.Optional<Boolean> Res = Dlg.showAndWait();
            if (Res.isEmpty() || Boolean.FALSE.equals(Res.get())) return false;
            if (Boolean.TRUE.equals(Res.get())) return true;
            ErrLabel.setText("Invalid credentials — " + (2 - Attempt) + " attempt(s) remaining");
        }
        return false;
    }
}
