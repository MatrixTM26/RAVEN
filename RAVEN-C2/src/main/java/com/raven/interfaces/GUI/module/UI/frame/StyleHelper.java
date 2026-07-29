package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;

public final class StyleHelper {

    private StyleHelper() {}

    public static void ApplyTerminal(TextArea TextAreaNode) {
        TextAreaNode.setStyle(TerminalStyle());
        TextAreaNode.setWrapText(true);
    }

    public static void ApplyInput(TextField TextFieldNode) {
        String BaseStyle = InputBaseStyle();
        TextFieldNode.setStyle(BaseStyle);
        TextFieldNode.focusedProperty().addListener((Observable, OldValue, Focused) ->
            TextFieldNode.setStyle(Focused ? InputFocusedStyle() : BaseStyle)
        );
    }

    public static String TerminalStyle() {
        return "-fx-background-color:" + Palette.TerminalBackground + ";" +
               "-fx-control-inner-background:" + Palette.TerminalBackground + ";" +
               "-fx-text-fill:" + Palette.TerminalText + ";" +
               "-fx-highlight-fill:rgba(10,132,255,0.25);" +
               "-fx-font-family:'JetBrains Mono','Cascadia Code','Consolas',monospace;" +
               "-fx-font-size:12px;" +
               "-fx-padding:12 14 12 14;" +
               "-fx-background-radius:0;" +
               "-fx-border-color:transparent;";
    }

    public static Region HorizontalDivider() {
        Region Divider = new Region();
        Divider.getStyleClass().add("h-div");
        Divider.setPrefHeight(1);
        Divider.setMaxWidth(Double.MAX_VALUE);
        return Divider;
    }

    public static Region VerticalDivider() {
        Region Divider = new Region();
        Divider.getStyleClass().add("v-div");
        Divider.setPrefWidth(1);
        Divider.setPrefHeight(16);
        return Divider;
    }

    private static String InputBaseStyle() {
        return "-fx-background-color:" + Palette.BackgroundInput + ";" +
               "-fx-text-fill:" + Palette.TextPrimary + ";" +
               "-fx-prompt-text-fill:" + Palette.TextQuaternary + ";" +
               "-fx-font-family:'Segoe UI';" +
               "-fx-font-size:12px;" +
               "-fx-padding:7 11 7 11;" +
               "-fx-background-radius:7;" +
               "-fx-border-color:" + Palette.BorderDefault + ";" +
               "-fx-border-width:1;" +
               "-fx-border-radius:7;";
    }

    private static String InputFocusedStyle() {
        return "-fx-background-color:" + Palette.BackgroundInput + ";" +
               "-fx-text-fill:" + Palette.TextPrimary + ";" +
               "-fx-prompt-text-fill:" + Palette.TextQuaternary + ";" +
               "-fx-font-family:'Segoe UI';" +
               "-fx-font-size:12px;" +
               "-fx-padding:7 11 7 11;" +
               "-fx-background-radius:7;" +
               "-fx-border-color:" + Palette.AccentBlue + ";" +
               "-fx-border-width:1;" +
               "-fx-border-radius:7;";
    }
}
