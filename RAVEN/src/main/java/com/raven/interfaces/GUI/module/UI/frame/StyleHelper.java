package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.scene.control.ComboBox;
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

    public static void ApplyCombo(ComboBox<?> ComboNode) {
        ComboNode.setStyle(ComboBaseStyle());
    }

    public static String TerminalStyle() {
        return "-fx-background-color:" + Palette.TerminalBackground + ";" +
               "-fx-control-inner-background:" + Palette.TerminalBackground + ";" +
               "-fx-text-fill:" + Palette.TerminalText + ";" +
               "-fx-highlight-fill:rgba(61,142,240,0.20);" +
               "-fx-font-family:'JetBrains Mono','Cascadia Code','Consolas',monospace;" +
               "-fx-font-size:11px;" +
               "-fx-padding:10 12 10 12;" +
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
        Divider.setPrefHeight(14);
        return Divider;
    }

    private static String InputBaseStyle() {
        return "-fx-background-color:" + Palette.BackgroundInput + ";" +
               "-fx-text-fill:" + Palette.TextPrimary + ";" +
               "-fx-prompt-text-fill:" + Palette.TextQuaternary + ";" +
               "-fx-font-family:'JetBrains Mono','Consolas',monospace;" +
               "-fx-font-size:11px;" +
               "-fx-padding:5 9 5 9;" +
               "-fx-background-radius:0;" +
               "-fx-border-color:" + Palette.BorderSubtle + ";" +
               "-fx-border-width:1;" +
               "-fx-border-radius:0;";
    }

    private static String InputFocusedStyle() {
        return "-fx-background-color:" + Palette.BackgroundInput + ";" +
               "-fx-text-fill:" + Palette.TextPrimary + ";" +
               "-fx-prompt-text-fill:" + Palette.TextQuaternary + ";" +
               "-fx-font-family:'JetBrains Mono','Consolas',monospace;" +
               "-fx-font-size:11px;" +
               "-fx-padding:5 9 5 9;" +
               "-fx-background-radius:0;" +
               "-fx-border-color:" + Palette.AccentBlue + ";" +
               "-fx-border-width:1;" +
               "-fx-border-radius:0;";
    }

    private static String ComboBaseStyle() {
        return "-fx-background-color:" + Palette.BackgroundInput + ";" +
               "-fx-text-fill:" + Palette.TextPrimary + ";" +
               "-fx-font-size:11px;" +
               "-fx-background-radius:0;" +
               "-fx-border-color:" + Palette.BorderSubtle + ";" +
               "-fx-border-width:1;" +
               "-fx-border-radius:0;";
    }

    public static String PanelHeaderStyle() {
        return "-fx-background-color:" + Palette.BackgroundPanel + ";" +
               "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
               "-fx-border-width:0 0 1 0;" +
               "-fx-padding:6 11 6 11;";
    }

    public static String PanelCardStyle() {
        return "-fx-background-color:" + Palette.BackgroundDeep + ";" +
               "-fx-border-color:" + Palette.BorderSubtle + ";" +
               "-fx-border-width:1;" +
               "-fx-background-radius:0;" +
               "-fx-border-radius:0;";
    }

    public static String AccentTopBorderStyle(String AccentHex) {
        return PanelCardStyle() +
               "-fx-border-color:" + AccentHex + " " + Palette.BorderSubtle + " " +
               Palette.BorderSubtle + " " + Palette.BorderSubtle + ";" +
               "-fx-border-width:2 1 1 1;";
    }
}
