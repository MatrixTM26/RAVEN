package com.raven.interfaces.GUI.module.UI.controller;

import com.raven.interfaces.GUI.module.core.session.SessionRow;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.function.Consumer;

public class SessionsController {

    @FXML private TextField              SearchField;
    @FXML private TableView<SessionRow>  SessionTable;
    @FXML private TableColumn<SessionRow, String> ColId;
    @FXML private TableColumn<SessionRow, String> ColType;
    @FXML private TableColumn<SessionRow, String> ColName;
    @FXML private TableColumn<SessionRow, String> ColIp;
    @FXML private TableColumn<SessionRow, String> ColOs;
    @FXML private TableColumn<SessionRow, String> ColUser;
    @FXML private TableColumn<SessionRow, String> ColHost;
    @FXML private TableColumn<SessionRow, String> ColKey;
    @FXML private TextArea                        LogArea;

    private Runnable OnRefresh;
    private Runnable OnExecute;
    private Runnable OnBroadcast;
    private Runnable OnKill;
    private Consumer<Integer> OnSelect;

    @FXML
    private void initialize() {
        ColId.setCellValueFactory(new PropertyValueFactory<>("id"));
        ColType.setCellValueFactory(new PropertyValueFactory<>("type"));
        ColName.setCellValueFactory(new PropertyValueFactory<>("name"));
        ColIp.setCellValueFactory(new PropertyValueFactory<>("ip"));
        ColOs.setCellValueFactory(new PropertyValueFactory<>("os"));
        ColUser.setCellValueFactory(new PropertyValueFactory<>("user"));
        ColHost.setCellValueFactory(new PropertyValueFactory<>("host"));
        ColKey.setCellValueFactory(new PropertyValueFactory<>("joined"));

        SessionTable.getSelectionModel().selectedItemProperty().addListener((Obs, Old, Row) -> {
            if (Row != null && OnSelect != null)
                OnSelect.accept(Integer.parseInt(Row.getId()));
        });
    }

    public void BindData(ObservableList<SessionRow> Rows) {
        FilteredList<SessionRow> Filtered = new FilteredList<>(Rows, R -> true);
        SearchField.textProperty().addListener((Obs, Old, Nv) ->
            Filtered.setPredicate(R -> {
                if (Nv == null || Nv.isBlank()) return true;
                String Q = Nv.toLowerCase();
                return R.getId().contains(Q) || R.getName().toLowerCase().contains(Q)
                    || R.getIp().contains(Q) || R.getUser().toLowerCase().contains(Q)
                    || R.getHost().toLowerCase().contains(Q) || R.getType().toLowerCase().contains(Q);
            })
        );
        SessionTable.setItems(Filtered);
    }

    public void SetCallbacks(Runnable Refresh, Runnable Execute,
                              Runnable Broadcast, Runnable Kill,
                              Consumer<Integer> Select) {
        OnRefresh   = Refresh;
        OnExecute   = Execute;
        OnBroadcast = Broadcast;
        OnKill      = Kill;
        OnSelect    = Select;
    }

    public void AppendLog(String Text) {
        javafx.application.Platform.runLater(() -> LogArea.appendText(Text + "\n"));
    }

    public int SelectedId() {
        SessionRow Row = SessionTable.getSelectionModel().getSelectedItem();
        if (Row == null) return -1;
        try { return Integer.parseInt(Row.getId()); }
        catch (NumberFormatException E) { return -1; }
    }

    @FXML private void OnRefresh()   { if (OnRefresh   != null) OnRefresh.run(); }
    @FXML private void OnExecute()   { if (OnExecute   != null) OnExecute.run(); }
    @FXML private void OnBroadcast() { if (OnBroadcast != null) OnBroadcast.run(); }
    @FXML private void OnKill()      { if (OnKill      != null) OnKill.run(); }
    @FXML private void OnClearLog()  { LogArea.clear(); }
}
