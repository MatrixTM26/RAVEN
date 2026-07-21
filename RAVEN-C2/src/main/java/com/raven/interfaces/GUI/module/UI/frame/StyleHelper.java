package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;

public final class StyleHelper {

    private StyleHelper() {}

    public static void ApplyInput(TextField f) {
        String base = inputBase();
        f.setStyle(base);
        f.focusedProperty().addListener((obs, o, n) ->
            f.setStyle(n ? inputFocused() : base));
    }

    public static void ApplyTerm(TextArea a) {
        a.setStyle(
            "-fx-background-color:" + Palette.TERM_BG + ";" +
            "-fx-control-inner-background:" + Palette.TERM_BG + ";" +
            "-fx-text-fill:" + Palette.TERM_TEXT + ";" +
            "-fx-highlight-fill:" + Palette.ACCENT + ";" +
            "-fx-font-family:'Consolas';" +
            "-fx-font-size:12px;" +
            "-fx-padding:10 12 10 12;" +
            "-fx-background-radius:0;" +
            "-fx-border-color:" + Palette.BORDER + ";" +
            "-fx-border-width:1;" +
            "-fx-border-radius:0;"
        );
        a.setWrapText(true);
    }

    public static Region HDivider() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMaxWidth(Double.MAX_VALUE);
        r.setStyle("-fx-background-color:" + Palette.BORDER + ";");
        return r;
    }

    public static Region VDivider() {
        Region r = new Region();
        r.setPrefWidth(1);
        r.setStyle("-fx-background-color:" + Palette.BORDER + ";");
        return r;
    }

    private static String inputBase() {
        return "-fx-background-color:" + Palette.SURFACE + ";" +
               "-fx-text-fill:" + Palette.TEXT + ";" +
               "-fx-prompt-text-fill:" + Palette.TEXT_DIM + ";" +
               "-fx-font-family:'Segoe UI';" +
               "-fx-font-size:11px;" +
               "-fx-padding:5 8 5 8;" +
               "-fx-background-radius:0;" +
               "-fx-border-color:" + Palette.BORDER + ";" +
               "-fx-border-width:1;" +
               "-fx-border-radius:0;";
    }

    private static String inputFocused() {
        return "-fx-background-color:" + Palette.SURFACE + ";" +
               "-fx-text-fill:" + Palette.TEXT + ";" +
               "-fx-prompt-text-fill:" + Palette.TEXT_DIM + ";" +
               "-fx-font-family:'Segoe UI';" +
               "-fx-font-size:11px;" +
               "-fx-padding:5 8 5 8;" +
               "-fx-background-radius:0;" +
               "-fx-border-color:" + Palette.ACCENT + ";" +
               "-fx-border-width:1;" +
               "-fx-border-radius:0;";
    }
}
