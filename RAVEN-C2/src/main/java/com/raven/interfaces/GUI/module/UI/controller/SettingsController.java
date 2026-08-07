package com.raven.interfaces.GUI.module.UI.controller;

import com.raven.utils.ServerConfig;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;

public class SettingsController {

    @FXML private TextField HandleField;
    @FXML private TextField LhostField;
    @FXML private TextField LportField;
    @FXML private TextField TimeoutField;
    @FXML private TextField LogRetField;

    @FXML private ToggleSwitchController NotifSessionController;
    @FXML private ToggleSwitchController NotifDropController;
    @FXML private ToggleSwitchController NotifTransferController;
    @FXML private ToggleSwitchController NotifSoundController;
    @FXML private ToggleSwitchController SecEncryptController;
    @FXML private ToggleSwitchController SecTlsController;
    @FXML private ToggleSwitchController SecAuthController;
    @FXML private ToggleSwitchController SecOpsecController;

    private ServerConfig Config;

    @FXML
    private void initialize() {
        if (SecEncryptController   != null) SecEncryptController.SetOn(true);
        if (SecAuthController      != null) SecAuthController.SetOn(true);
        if (NotifSessionController != null) NotifSessionController.SetOn(true);
        if (NotifDropController    != null) NotifDropController.SetOn(true);
    }

    public void BindConfig(ServerConfig Cfg) {
        Config = Cfg;
        HandleField.setText(Cfg.GetAdminUsername());
        LhostField.setText(Cfg.GetServerHost());
        LportField.setText(String.valueOf(Cfg.GetServerPort()));
    }

    @FXML
    private void OnSave() {
        Alert Ok = new Alert(Alert.AlertType.INFORMATION,
            "Settings saved. Restart required for connection changes.");
        Ok.setHeaderText(null);
        Ok.showAndWait();
    }

    @FXML
    private void OnReset() {
        if (Config != null) BindConfig(Config);
        TimeoutField.setText("300");
        LogRetField.setText("5000");
        if (NotifSessionController  != null) NotifSessionController.SetOn(true);
        if (NotifDropController     != null) NotifDropController.SetOn(true);
        if (NotifTransferController != null) NotifTransferController.SetOn(false);
        if (NotifSoundController    != null) NotifSoundController.SetOn(false);
        if (SecEncryptController    != null) SecEncryptController.SetOn(true);
        if (SecTlsController        != null) SecTlsController.SetOn(false);
        if (SecAuthController       != null) SecAuthController.SetOn(true);
        if (SecOpsecController      != null) SecOpsecController.SetOn(false);
    }
}
