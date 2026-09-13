package com.raven.interfaces.GUI.module.UI.controller;

import com.raven.core.command.AgentCommandDispatcher;
import com.raven.core.command.CommandRegistry;
import com.raven.core.command.CommandRegistry.Category;
import com.raven.core.command.CommandRegistry.CommandDef;
import java.util.Arrays;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class CommandCenterController {

    @FXML
    private ComboBox<String> CategoryFilter;

    @FXML
    private TextField CmdSearch;

    @FXML
    private TableView<CommandDef> CmdTable;

    @FXML
    private TableColumn<CommandDef, String> ColCmdName;

    @FXML
    private TableColumn<CommandDef, String> ColCmdUsage;

    @FXML
    private TableColumn<CommandDef, String> ColCmdCat;

    @FXML
    private TableColumn<CommandDef, String> ColCmdDesc;

    @FXML
    private TextArea OutputArea;

    @FXML
    private TextField CmdInput;

    private AgentCommandDispatcher Dispatcher;
    private int ActiveSessionId = -1;
    private final ObservableList<CommandDef> AllCommands = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        ColCmdName.setCellValueFactory(C -> new SimpleStringProperty(C.getValue().Name()));
        ColCmdUsage.setCellValueFactory(C -> new SimpleStringProperty(C.getValue().Usage()));
        ColCmdCat.setCellValueFactory(C -> new SimpleStringProperty(C.getValue().Category().name()));
        ColCmdDesc.setCellValueFactory(C -> new SimpleStringProperty(C.getValue().Description()));

        AllCommands.addAll(CommandRegistry.All().values());
        FilteredList<CommandDef> Filtered = new FilteredList<>(AllCommands, C -> true);

        CategoryFilter.getItems().add("ALL");
        Arrays.stream(Category.values()).forEach(Cat -> CategoryFilter.getItems().add(Cat.name()));
        CategoryFilter.setValue("ALL");

        CmdSearch.textProperty().addListener((O, Ov, Nv) -> Refilter(Filtered));
        CategoryFilter.valueProperty().addListener((O, Ov, Nv) -> Refilter(Filtered));

        CmdTable.setItems(Filtered);
        CmdTable.getSelectionModel()
            .selectedItemProperty()
            .addListener((O, Ov, Row) -> {
                if (Row != null) CmdInput.setText(Row.Usage());
            });
    }

    public void SetDispatcher(AgentCommandDispatcher D) {
        Dispatcher = D;
    }

    public void SetActiveSession(int Id) {
        ActiveSessionId = Id;
    }

    public void AppendOutput(String Text) {
        Platform.runLater(() -> OutputArea.appendText(Text + "\n"));
    }

    @FXML
    private void OnExecute() {
        String Cmd = CmdInput.getText().trim();
        if (Cmd.isEmpty()) return;
        if (Dispatcher == null) {
            OutputArea.appendText("[!] Server not running\n");
            return;
        }
        if (ActiveSessionId < 0) {
            OutputArea.appendText("[!] No session selected\n");
            return;
        }
        OutputArea.appendText("❯ " + Cmd + "\n");
        CmdInput.clear();
        new Thread(() -> {
            AgentCommandDispatcher.CommandResult Res = Dispatcher.Dispatch(ActiveSessionId, Cmd);
            Platform.runLater(() -> OutputArea.appendText(Res.Output() + "\n\n"));
        }).start();
    }

    @FXML
    private void OnFilterCategory() {
        Refilter((FilteredList<CommandDef>) CmdTable.getItems());
    }

    @FXML
    private void OnClearOutput() {
        OutputArea.clear();
    }

    private void Refilter(FilteredList<CommandDef> List) {
        String Cat = CategoryFilter.getValue();
        String Q = CmdSearch.getText();
        List.setPredicate(C -> {
            boolean CatOk = Cat == null || Cat.equals("ALL") || C.Category().name().equals(Cat);
            boolean SrchOk = Q == null || Q.isBlank() || C.Name().toLowerCase().contains(Q.toLowerCase()) || C.Usage().toLowerCase().contains(Q.toLowerCase()) || C.Description().toLowerCase().contains(Q.toLowerCase());
            return CatOk && SrchOk;
        });
    }
}
