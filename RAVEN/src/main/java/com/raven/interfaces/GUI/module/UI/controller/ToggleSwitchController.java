package com.raven.interfaces.GUI.module.UI.controller;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class ToggleSwitchController {

    @FXML private StackPane Root;
    @FXML private Rectangle Track;
    @FXML private Rectangle Thumb;

    private final BooleanProperty On = new SimpleBooleanProperty(false);
    private final TranslateTransition Anim = new TranslateTransition(Duration.millis(160));

    @FXML
    private void initialize() {
        Anim.setNode(Thumb);
        ApplyColors(false);

        Root.setCursor(Cursor.HAND);
        Root.setOnMouseClicked(e -> On.set(!On.get()));
        On.addListener((Obs, Old, IsOn) -> {
            Anim.stop();
            Anim.setToX(IsOn ? 13 : -13);
            Anim.play();
            ApplyColors(IsOn);
        });
    }

    private void ApplyColors(boolean IsOn) {
        if (IsOn) {
            Track.setFill(Color.web("#200808"));
            Track.setStroke(Color.web(Palette.Red));
            Track.setStrokeWidth(1);
            Thumb.setFill(Color.web(Palette.Red));
        } else {
            Track.setFill(Color.web(Palette.BgSurface));
            Track.setStroke(Color.web(Palette.Border));
            Track.setStrokeWidth(1);
            Thumb.setFill(Color.web(Palette.WhiteFaint));
        }
    }

    public BooleanProperty OnProperty() { return On; }
    public boolean IsOn()               { return On.get(); }
    public void SetOn(boolean Value)    { On.set(Value); }
}
