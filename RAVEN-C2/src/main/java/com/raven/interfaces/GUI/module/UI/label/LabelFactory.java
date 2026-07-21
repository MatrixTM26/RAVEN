package com.raven.interfaces.GUI.module.UI.label;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class LabelFactory {

    private LabelFactory() {}

    public static Label Of(String text, int size, String hex, boolean bold) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        l.setTextFill(Color.web(hex));
        return l;
    }

    public static Label Head(String text) {
        return Of(text, 11, Palette.TEXT_HEAD, true);
    }

    public static Label Sub(String text) {
        return Of(text, 10, Palette.TEXT_MUTED, false);
    }

    public static Label Body(String text) {
        return Of(text, 11, Palette.TEXT, false);
    }

    public static Label Section(String text) {
        Label l = Of(text.toUpperCase(), 9, Palette.TEXT_MUTED, true);
        l.setStyle("-fx-letter-spacing: 0.08em;");
        return l;
    }

    public static Label Danger(String text) {
        return Of(text, 10, Palette.DANGER, false);
    }

    public static Label Accent(String text) {
        return Of(text, 10, Palette.ACCENT, false);
    }
}
