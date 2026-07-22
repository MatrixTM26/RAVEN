package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.label.LabelFactory;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

public final class CardBuilder {

    private CardBuilder() {}

    public static VBox Panel(String title) {
        VBox card = new VBox(0);
        card.setStyle(
            "-fx-background-color:" + Palette.BG_ALT + ";" +
            "-fx-border-color:" + Palette.BORDER + ";" +
            "-fx-border-width:1;" +
            "-fx-background-radius:0;" +
            "-fx-border-radius:0;"
        );

        VBox header = new VBox();
        header.setPadding(new Insets(8, 12, 8, 12));
        header.setStyle(
            "-fx-background-color:" + Palette.SURFACE + ";" +
            "-fx-border-color:transparent transparent " + Palette.BORDER + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        header.getChildren().add(LabelFactory.Head(title));

        VBox body = new VBox(8);
        body.setPadding(new Insets(12));
        card.getChildren().addAll(header, body);
        return card;
    }

    public static VBox GetBody(VBox panel) {
        return (VBox) panel.getChildren().get(1);
    }

    public static VBox StatCard(String label, String value, String accentColor) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setStyle(
            "-fx-background-color:" + Palette.BG_ALT + ";" +
            "-fx-border-color:" + Palette.BORDER + ";" +
            "-fx-border-left-color:" + accentColor + ";" +
            "-fx-border-width:1 1 1 3;" +
            "-fx-background-radius:0;" +
            "-fx-border-radius:0;"
        );
        card.getChildren().addAll(
            LabelFactory.Section(label),
            LabelFactory.Of(value, 22, accentColor, true)
        );
        return card;
    }
}
