package com.raven.interfaces.GUI.module.UI.controller;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class OverviewController {

    @FXML private Label StatSessions;
    @FXML private Label StatSessionsDelta;
    @FXML private Label StatAgents;
    @FXML private Label StatAgentsDelta;
    @FXML private Label StatMeterp;
    @FXML private Label StatMeterpDelta;
    @FXML private Label StatRevsh;
    @FXML private Label StatRevshDelta;
    @FXML private VBox  ActivityFeed;
    @FXML private Label QuickStatus;
    @FXML private Label QuickAddress;
    @FXML private Label QuickMode;
    @FXML private Label QuickUptime;
    @FXML private Label QuickOperator;

    @FXML
    private void initialize() {
        AddActivity(true,  "Server initialized and ready",      "just now");
        AddActivity(false, "Authentication module loaded",       "just now");
        AddActivity(false, "Awaiting first session connection",  "idle");
    }

    public void UpdateStats(int Sessions, int Agents, int Meterp, int Revsh) {
        Platform.runLater(() -> {
            StatSessions.setText(String.valueOf(Sessions));
            StatAgents.setText(String.valueOf(Agents));
            StatMeterp.setText(String.valueOf(Meterp));
            StatRevsh.setText(String.valueOf(Revsh));
        });
    }

    public void UpdateServerStatus(boolean Online, String Address, String Mode, String Operator) {
        Platform.runLater(() -> {
            QuickStatus.setText(Online ? "Online" : "Offline");
            QuickStatus.setStyle(Online
                ? "-fx-text-fill:" + Palette.TermGreen + ";"
                : "-fx-text-fill:" + Palette.Red + ";");
            QuickAddress.setText(Online ? Address : "—");
            QuickMode.setText(Mode != null ? Mode : "MULTI");
            QuickOperator.setText(Operator != null ? Operator : "—");
        });
    }

    public void UpdateUptime(String Uptime) {
        Platform.runLater(() -> QuickUptime.setText(Uptime));
    }

    public void AddActivity(boolean IsSuccess, String Message, String Timestamp) {
        Platform.runLater(() -> {
            HBox Row = new HBox(10);
            Row.setAlignment(Pos.CENTER_LEFT);
            Row.getStyleClass().add("activity-row");
            Row.setPadding(new Insets(8, 10, 8, 10));

            Label Marker = new Label(IsSuccess ? "✓" : "●");
            Marker.setStyle(
                "-fx-min-width:20; -fx-max-width:20;" +
                "-fx-min-height:20; -fx-max-height:20;" +
                "-fx-alignment:CENTER;" +
                "-fx-background-color:#200808;" +
                "-fx-border-color:#7a1414; -fx-border-width:1;" +
                "-fx-text-fill:" + (IsSuccess ? Palette.TermGreen : Palette.Red) + ";" +
                "-fx-font-size:10px;"
            );

            VBox Info = new VBox(2);
            Label Msg = new Label(Message);
            Msg.setStyle("-fx-text-fill:" + Palette.White + "; -fx-font-size:11px;");
            Label Ts = new Label(Timestamp);
            Ts.setStyle("-fx-text-fill:" + Palette.WhiteFaint + "; -fx-font-size:10px;");
            Info.getChildren().addAll(Msg, Ts);
            HBox.setHgrow(Info, Priority.ALWAYS);

            Row.getChildren().addAll(Marker, Info);

            ActivityFeed.getChildren().add(0, Row);
            if (ActivityFeed.getChildren().size() > 8)
                ActivityFeed.getChildren().remove(ActivityFeed.getChildren().size() - 1);
        });
    }

    private Region Divider() {
        Region D = new Region();
        D.getStyleClass().add("h-div");
        return D;
    }
}
