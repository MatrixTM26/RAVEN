package com.raven.interfaces.GUI.module.UI.controller;

import com.raven.core.command.CommandRegistry;
import com.raven.core.command.CommandRegistry.Category;
import com.raven.core.command.CommandRegistry.CommandDef;
import com.raven.interfaces.GUI.module.core.server.CommandDispatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    private CommandDispatcher Dispatcher;
    private final ObservableList<CommandDef> AllCommands = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        ColCmdName.setCellValueFactory(C -> new SimpleStringProperty(C.getValue().Name()));
        ColCmdUsage.setCellValueFactory(C -> new SimpleStringProperty(C.getValue().Usage()));
        ColCmdCat.setCellValueFactory(C -> new SimpleStringProperty(CategoryOf(C.getValue())));
        ColCmdDesc.setCellValueFactory(C -> new SimpleStringProperty(C.getValue().Description()));

        AllCommands.addAll(CommandRegistry.All().values());
        FilteredList<CommandDef> Filtered = new FilteredList<>(AllCommands, C -> true);

        CategoryFilter.getItems().add("ALL");
        Arrays.stream(Category.values()).forEach(Cat -> CategoryFilter.getItems().add(Cat.name()));
        CategoryFilter.setValue("ALL");

        CmdSearch.textProperty().addListener((Obs, Old, Nv) -> ApplyFilter(Filtered, CategoryFilter.getValue(), Nv));
        CategoryFilter.valueProperty().addListener((Obs, Old, Nv) -> ApplyFilter(Filtered, Nv, CmdSearch.getText()));

        CmdTable.setItems(Filtered);
        CmdTable.getSelectionModel()
            .selectedItemProperty()
            .addListener((Obs, Old, Row) -> {
                if (Row != null) CmdInput.setText(Row.Usage());
            });
    }

    public void SetDispatcher(CommandDispatcher D) {
        Dispatcher = D;
    }

    public void AppendOutput(String Text) {
        Platform.runLater(() -> OutputArea.appendText(Text + "\n"));
    }

    @FXML
    private void OnFilterCategory() {
        ApplyFilter((FilteredList<CommandDef>) CmdTable.getItems(), CategoryFilter.getValue(), CmdSearch.getText());
    }

    @FXML
    private void OnExecute() {
        String Cmd = CmdInput.getText().trim();
        if (Cmd.isEmpty()) return;
        OutputArea.appendText("❯ " + Cmd + "\n");
        if (Dispatcher != null) Dispatcher.Dispatch(Cmd, CmdInput);
        else CmdInput.clear();
    }

    @FXML
    private void OnClearOutput() {
        OutputArea.clear();
    }

    private void ApplyFilter(FilteredList<CommandDef> List, String Category, String Search) {
        List.setPredicate(C -> {
            boolean CatOk = Category == null || Category.equals("ALL") || CategoryOf(C).equals(Category);
            boolean SrchOk = Search == null || Search.isBlank() || C.Name().toLowerCase().contains(Search.toLowerCase()) || C.Usage().toLowerCase().contains(Search.toLowerCase()) || C.Description().toLowerCase().contains(Search.toLowerCase());
            return CatOk && SrchOk;
        });
    }

    private static String CategoryOf(CommandDef Def) {
        for (Category Cat : Category.values()) {
            List<CommandDef> InCat = CommandRegistry.ByCategory(Cat);
            for (CommandDef D : InCat) {
                if (D.Name().equals(Def.Name())) return Cat.name();
            }
        }
        return "SYSTEM";
    }
}
