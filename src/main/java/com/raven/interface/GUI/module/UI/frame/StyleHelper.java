package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.geometry.Insets;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;

public final class StyleHelper {

    private StyleHelper() {}

    public static void ApplyInputStyle(TextField F) {
        F.setStyle(
            "-fx-background-color: " + Palette.CARD2 + ";" +
            "-fx-text-fill: " + Palette.TEXT + ";" +
            "-fx-prompt-text-fill: " + Palette.MUTED + ";" +
            "-fx-font-family: 'Consolas';" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 9 12 9 12;" +
            "-fx-background-radius: 9;" +
            "-fx-border-color: " + Palette.BORDER + ";" +
            "-fx-border-radius: 9;"
        );
    }

    public static void ApplyTermStyle(TextArea A) {
        A.setStyle(
            "-fx-background-color: " + Palette.TERM_BG + ";" +
            "-fx-control-inner-background: " + Palette.TERM_BG + ";" +
            "-fx-text-fill: " + Palette.TEXT + ";" +
            "-fx-highlight-fill: " + Palette.RED + ";" +
            "-fx-font-family: 'Consolas';" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 12 14 12 14;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: " + Palette.BORDER + ";" +
            "-fx-border-radius: 12;"
        );
        A.setWrapText(true);
    }

    public static Region VDivider() {
        Region R = new Region();
        R.setStyle("-fx-background-color: " + Palette.BORDER + ";");
        R.setPrefWidth(1);
        R.setPrefHeight(20);
        return R;
    }

    public static Separator HDivider() {
        Separator S = new Separator();
        S.setPadding(new Insets(4, 16, 4, 16));
        S.setStyle("-fx-background-color: " + Palette.BORDER + ";");
        return S;
    }
}
