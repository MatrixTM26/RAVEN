package com.raven.interfaces.GUI.module.UI.controller;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.core.server.ServerController;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ListenerController {

    @FXML private Label     OnlineLabel;
    @FXML private TextField HostField;
    @FXML private TextField PortField;
    @FXML private ComboBox<String> ModeCombo;
    @FXML private ComboBox<String> ProtoCombo;
    @FXML private ToggleSwitchController SslToggleController;
    @FXML private ToggleSwitchController VerboseToggleController;
    @FXML private ToggleSwitchController AutoAcceptToggleController;
    @FXML private Label     StatusText;
    @FXML private Button    StartBtn;
    @FXML private Button    StopBtn;
    @FXML private Label     SrvStatus;
    @FXML private Label     SrvAddress;
    @FXML private Label     SrvMode;
    @FXML private Label     SrvConnTotal;
    @FXML private Label     SrvConnActive;
    @FXML private Label     SrvConnDrop;
    @FXML private TextArea  ConnLog;

    private Runnable OnStart;
    private Runnable OnStop;
    private int ConnTotal  = 0;
    private int ConnActive = 0;
    private int ConnDrop   = 0;

    @FXML
    private void initialize() {
        ModeCombo.getItems().addAll("MULTI", "SINGLE", "STAGED");
        ModeCombo.setValue("MULTI");
        ProtoCombo.getItems().addAll("TCP", "HTTP", "HTTPS", "DNS");
        ProtoCombo.setValue("TCP");
    }

    public void SetCallbacks(Runnable Start, Runnable Stop) {
        OnStart = Start;
        OnStop  = Stop;
    }

    public String GetHost()  { return HostField.getText().trim(); }
    public int    GetPort()  {
        try { return Integer.parseInt(PortField.getText().trim()); }
        catch (NumberFormatException E) { return 4444; }
    }
    public String GetMode()  { return ModeCombo.getValue(); }
    public String GetProto() { return ProtoCombo.getValue(); }

    public void SetOnline(boolean Online, String Address) {
        Platform.runLater(() -> {
            OnlineLabel.setText(Online ? "ONLINE" : "OFFLINE");
            OnlineLabel.setStyle(Online
                ? "-fx-background-color:#061408; -fx-text-fill:#22aa55; -fx-border-color:#135528; -fx-border-width:1; -fx-padding:1 6 1 6; -fx-font-size:10px; -fx-font-weight:bold;"
                : "-fx-background-color:#200808; -fx-text-fill:#cc2222; -fx-border-color:#7a1414; -fx-border-width:1; -fx-padding:1 6 1 6; -fx-font-size:10px; -fx-font-weight:bold;");
            StatusText.setText(Online ? "Running on " + Address : "Server is offline");
            StatusText.setStyle(Online
                ? "-fx-text-fill:" + Palette.TermGreen + ";"
                : "-fx-text-fill:" + Palette.WhiteDim + ";");
            SrvStatus.setText(Online ? "Online" : "Offline");
            SrvStatus.setStyle(Online
                ? "-fx-text-fill:" + Palette.TermGreen + ";"
                : "-fx-text-fill:" + Palette.Red + ";");
            SrvAddress.setText(Online ? Address : "Not running");
            SrvMode.setText(GetMode());
            if (Online) { ConnTotal++; ConnActive++; UpdateConnLabels(); }
        });
    }

    public void SetOffline() {
        Platform.runLater(() -> {
            ConnDrop += ConnActive;
            ConnActive = 0;
            UpdateConnLabels();
        });
        SetOnline(false, "");
    }

    public void AppendLog(String Line) {
        Platform.runLater(() -> ConnLog.appendText(Line + "\n"));
    }

    private void UpdateConnLabels() {
        SrvConnTotal.setText(ConnTotal  + " total");
        SrvConnActive.setText(ConnActive + " active");
        SrvConnDrop.setText(ConnDrop   + " dropped");
    }

    @FXML private void OnStart() { if (OnStart != null) OnStart.run(); }
    @FXML private void OnStop()  { if (OnStop  != null) OnStop.run();  }
    @FXML private void OnClearLog() { ConnLog.clear(); }
}
