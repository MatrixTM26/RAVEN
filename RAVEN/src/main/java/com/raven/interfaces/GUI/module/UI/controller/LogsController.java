package com.raven.interfaces.GUI.module.UI.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LogsController {

    @FXML private ComboBox<String> LevelFilter;
    @FXML private Label            EntryCount;
    @FXML private TextArea         LogStream;

    private final List<String[]> AllEntries = new ArrayList<>();
    private String ActiveLevel = "ALL";

    @FXML
    private void initialize() {
        LevelFilter.getItems().addAll("ALL", "INFO", "WARN", "ERROR", "OK");
        LevelFilter.setValue("ALL");
    }

    public void AppendEntry(String Level, String Message) {
        String Ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        AllEntries.add(new String[]{Ts, Level, Message});
        Platform.runLater(() -> {
            EntryCount.setText(AllEntries.size() + " entries");
            if (ActiveLevel.equals("ALL") || ActiveLevel.equals(Level))
                LogStream.appendText("[" + Ts + "]  " + Level.toUpperCase() + "  " + Message + "\n");
        });
    }

    @FXML
    private void OnFilterLevel() {
        ActiveLevel = LevelFilter.getValue();
        if (ActiveLevel == null) ActiveLevel = "ALL";
        RedrawStream();
    }

    @FXML
    private void OnClear() {
        AllEntries.clear();
        LogStream.clear();
        Platform.runLater(() -> EntryCount.setText("0 entries"));
    }

    @FXML
    private void OnExport() {
        try {
            File Out = new File("raven_log_" + System.currentTimeMillis() + ".txt");
            try (PrintWriter Pw = new PrintWriter(Out)) {
                AllEntries.forEach(E -> Pw.println("[" + E[0] + "]  " + E[1] + "  " + E[2]));
            }
            AppendEntry("OK", "Log exported to " + Out.getAbsolutePath());
        } catch (IOException E) {
            AppendEntry("ERROR", "Export failed: " + E.getMessage());
        }
    }

    private void RedrawStream() {
        StringBuilder Sb = new StringBuilder();
        AllEntries.forEach(E -> {
            if (ActiveLevel.equals("ALL") || ActiveLevel.equals(E[1]))
                Sb.append("[").append(E[0]).append("]  ").append(E[1].toUpperCase()).append("  ").append(E[2]).append("\n");
        });
        Platform.runLater(() -> {
            LogStream.clear();
            LogStream.setText(Sb.toString());
        });
    }
}
