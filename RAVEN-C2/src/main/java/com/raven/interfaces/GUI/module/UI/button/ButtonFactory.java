package com.raven.interfaces.GUI.module.UI.button;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.scene.control.Button;

public final class ButtonFactory {

    public enum Variant { DEFAULT, ACCENT, SUCCESS, DANGER, FLAT, OUTLINED }

    private ButtonFactory() {}

    public static Button Of(String text, Variant variant) {
        Button b = new Button(text);
        b.setStyle(baseStyle(variant));
        b.setOnMouseEntered(e -> b.setStyle(hoverStyle(variant)));
        b.setOnMouseExited(e -> b.setStyle(baseStyle(variant)));
        return b;
    }

    private static String baseStyle(Variant v) {
        return switch (v) {
            case ACCENT -> fill(Palette.ACCENT, Palette.TEXT_HEAD, Palette.ACCENT);
            case SUCCESS -> fill(Palette.SUCCESS, Palette.TEXT_HEAD, Palette.SUCCESS);
            case DANGER -> fill(Palette.DANGER, Palette.TEXT_HEAD, Palette.DANGER);
            case OUTLINED -> outlined(Palette.BORDER_ALT, Palette.TEXT);
            case FLAT -> flat(Palette.TEXT);
            default -> fill(Palette.SURFACE2, Palette.TEXT, Palette.BORDER);
        };
    }

    private static String hoverStyle(Variant v) {
        return switch (v) {
            case ACCENT -> fill(Palette.ACCENT_HOV, Palette.TEXT_HEAD, Palette.ACCENT_HOV);
            case SUCCESS -> fill("#388e3c", Palette.TEXT_HEAD, "#388e3c");
            case DANGER -> fill(Palette.DANGER_HOV, Palette.TEXT_HEAD, Palette.DANGER_HOV);
            case OUTLINED -> outlined(Palette.ACCENT, Palette.ACCENT);
            case FLAT -> flat(Palette.ACCENT);
            default -> fill(Palette.BORDER, Palette.TEXT_HEAD, Palette.BORDER_ALT);
        };
    }

    private static String fill(String bg, String text, String border) {
        return "-fx-background-color:" + bg + ";" +
               "-fx-text-fill:" + text + ";" +
               "-fx-border-color:" + border + ";" +
               "-fx-border-width:1;" +
               "-fx-background-radius:0;" +
               "-fx-border-radius:0;" +
               "-fx-font-family:'Segoe UI';" +
               "-fx-font-size:11px;" +
               "-fx-font-weight:normal;" +
               "-fx-padding:5 14 5 14;" +
               "-fx-cursor:hand;";
    }

    private static String outlined(String border, String text) {
        return "-fx-background-color:transparent;" +
               "-fx-text-fill:" + text + ";" +
               "-fx-border-color:" + border + ";" +
               "-fx-border-width:1;" +
               "-fx-background-radius:0;" +
               "-fx-border-radius:0;" +
               "-fx-font-family:'Segoe UI';" +
               "-fx-font-size:11px;" +
               "-fx-padding:5 14 5 14;" +
               "-fx-cursor:hand;";
    }

    private static String flat(String text) {
        return "-fx-background-color:transparent;" +
               "-fx-text-fill:" + text + ";" +
               "-fx-border-color:transparent;" +
               "-fx-background-radius:0;" +
               "-fx-border-radius:0;" +
               "-fx-font-family:'Segoe UI';" +
               "-fx-font-size:11px;" +
               "-fx-padding:5 14 5 14;" +
               "-fx-cursor:hand;";
    }
}
