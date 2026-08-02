package com.raven.interfaces.GUI.module.UI.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TerminalController {

    @FXML private TextField SessionIdField;
    @FXML private Label     SelectedLabel;
    @FXML private TextArea  TermOutput;
    @FXML private Label     PromptLabel;
    @FXML private TextField CmdField;

    private BiConsumer<Integer, String> OnSendCommand;
    private Consumer<Integer>           OnSysinfo;

    @FXML
    private void initialize() {
        SessionIdField.textProperty().addListener((Obs, Old, Nv) -> {
            if (Nv == null || Nv.isBlank()) {
                SelectedLabel.setText("No session selected");
                PromptLabel.setText("❯");
            }
        });
    }

    public void SetCallbacks(BiConsumer<Integer, String> Send, Consumer<Integer> Sysinfo) {
        OnSendCommand = Send;
        OnSysinfo     = Sysinfo;
    }

    public void SetSelectedSession(int Id, String DisplayName) {
        Platform.runLater(() -> {
            SessionIdField.setText(String.valueOf(Id));
            SelectedLabel.setText("#" + Id + "  " + DisplayName);
            PromptLabel.setText("[#" + Id + "]❯");
            TermOutput.appendText("[connected to SESSION-" + Id + "  " + DisplayName + "]\n");
        });
    }

    public void Append(String Text) {
        Platform.runLater(() -> TermOutput.appendText(Text + "\n"));
    }

    @FXML
    private void OnSend() {
        String Cmd = CmdField.getText().trim();
        if (Cmd.isEmpty()) return;
        int Id = ParseId();
        if (Id < 0) { Append("[!] Enter a valid session ID"); return; }
        TermOutput.appendText(PromptLabel.getText() + " " + Cmd + "\n");
        CmdField.clear();
        if (OnSendCommand != null) OnSendCommand.accept(Id, Cmd);
    }

    @FXML
    private void OnClear() { TermOutput.clear(); }

    @FXML
    private void OnSysinfo() {
        int Id = ParseId();
        if (Id < 0) { Append("[!] Enter a valid session ID"); return; }
        if (OnSysinfo != null) OnSysinfo.accept(Id);
    }

    private int ParseId() {
        try { return Integer.parseInt(SessionIdField.getText().trim()); }
        catch (NumberFormatException E) { return -1; }
    }
}
