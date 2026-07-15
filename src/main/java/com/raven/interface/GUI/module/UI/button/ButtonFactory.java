package com.raven.interfaces.GUI.module.UI.button;

import javafx.scene.control.Button;

public final class ButtonFactory {

    private ButtonFactory() {}

    public static Button Styled(String Text, String Hex, boolean Outline) {
        Button B = new Button(Text);
        B.setStyle(
            "-fx-background-color: " + (Outline ? "transparent" : Hex) + ";" +
            "-fx-text-fill: " + (Outline ? Hex : "#ffffff") + ";" +
            "-fx-border-color: " + Hex + ";" +
            "-fx-border-width: 1.2;" +
            "-fx-border-radius: 10;" +
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 10px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 8 16 8 16;" +
            "-fx-background-radius: 10;" +
            "-fx-cursor: hand;"
        );
        return B;
    }
}
