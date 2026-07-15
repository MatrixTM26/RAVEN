package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.label.LabelFactory;
import javafx.geometry.Insets;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

public final class CardBuilder {

    private CardBuilder() {}

    public static VBox StatCard(String Icon, String Title, String Value, String Hex) {
        VBox Card = new VBox(4);
        Card.setPadding(new Insets(18));
        Card.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #111114, #070707);" +
            "-fx-background-radius: 18;" +
            "-fx-border-color: " + Hex + ";" +
            "-fx-border-radius: 18;" +
            "-fx-effect: dropshadow(gaussian, rgba(255,31,61,0.16), 22, 0.22, 0, 10);"
        );
        Card.setMinHeight(104);
        javafx.scene.layout.HBox Row = new javafx.scene.layout.HBox(8);
        Row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Row.getChildren().addAll(
            LabelFactory.Styled(Icon, 28, Hex, false),
            new VBox(2) {{
                getChildren().addAll(
                    LabelFactory.Styled(Title, 10, Palette.MUTED, false),
                    LabelFactory.Styled(Value, 24, Hex, true)
                );
            }}
        );
        Card.getChildren().add(Row);
        return Card;
    }

    public static VBox Panel(String Title) {
        VBox Card = new VBox(8);
        Card.setPadding(new Insets(18));
        Card.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #111114, #090909);" +
            "-fx-background-radius: 18;" +
            "-fx-border-color: " + Palette.BORDER + ";" +
            "-fx-border-radius: 18;" +
            "-fx-effect: dropshadow(gaussian, rgba(255,31,61,0.12), 18, 0.16, 0, 8);"
        );
        Separator Sep = new Separator();
        Sep.setStyle("-fx-background-color: " + Palette.BORDER + ";");
        Card.getChildren().addAll(LabelFactory.Primary(Title, 12, true), Sep);
        return Card;
    }
}
