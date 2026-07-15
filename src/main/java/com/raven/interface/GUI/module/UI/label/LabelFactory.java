package com.raven.interfaces.GUI.module.UI.label;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class LabelFactory {

    private LabelFactory() {}

    public static Label Styled(String Text, int Size, String Hex, boolean Bold) {
        Label L = new Label(Text);
        L.setFont(Font.font("Segoe UI", Bold ? FontWeight.BOLD : FontWeight.NORMAL, Size));
        L.setTextFill(Color.web(Hex));
        return L;
    }

    public static Label Muted(String Text, int Size) {
        return Styled(Text, Size, Palette.MUTED, false);
    }

    public static Label Primary(String Text, int Size, boolean Bold) {
        return Styled(Text, Size, Palette.TEXT, Bold);
    }

    public static Label Danger(String Text, int Size, boolean Bold) {
        return Styled(Text, Size, Palette.RED, Bold);
    }
}
