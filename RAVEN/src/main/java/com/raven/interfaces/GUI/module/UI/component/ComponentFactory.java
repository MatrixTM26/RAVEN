package com.raven.interfaces.GUI.module.UI.component;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.frame.StyleHelper;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class ComponentFactory {

    private ComponentFactory() {}

    public static Label MaterialIcon(String Codepoint, String HexColor, int SizePx) {
        Label IconLabel = new Label(Codepoint);
        IconLabel.setStyle(
            "-fx-font-family:'Material Icons';" +
            "-fx-font-size:" + SizePx + "px;" +
            "-fx-text-fill:" + HexColor + ";"
        );
        return IconLabel;
    }

    public static Label SmallCapsLabel(String Text, String HexColor) {
        Label CapsLabel = new Label(Text.toUpperCase());
        CapsLabel.setStyle(
            "-fx-text-fill:" + HexColor + ";" +
            "-fx-font-size:10px;" +
            "-fx-font-weight:bold;" +
            "-fx-letter-spacing:0.09em;"
        );
        return CapsLabel;
    }

    public static Label BodyLabel(String Text) {
        Label Lbl = new Label(Text);
        Lbl.setStyle("-fx-text-fill:" + Palette.TextSecondary + "; -fx-font-size:11px;");
        return Lbl;
    }

    public static Label MutedLabel(String Text) {
        Label Lbl = new Label(Text);
        Lbl.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:11px;");
        return Lbl;
    }

    public static Label ChipLabel(String Text, String StyleClass) {
        Label Chip = new Label(Text);
        Chip.getStyleClass().add(StyleClass);
        return Chip;
    }

    public static Button ActionButton(String Text, String... StyleClasses) {
        Button Btn = new Button(Text);
        for (String Cls : StyleClasses)
            Btn.getStyleClass().add(Cls);
        return Btn;
    }

    public static Button IconButton(String Codepoint, String TooltipText,
                                    javafx.event.EventHandler<javafx.event.ActionEvent> OnClick) {
        Button Btn = new Button(Codepoint);
        Btn.getStyleClass().add("btn-icon");
        Btn.setTooltip(new Tooltip(TooltipText));
        Btn.setOnAction(OnClick);
        return Btn;
    }

    public static StackPane IconChip(String Codepoint, String AccentHex, int ChipSize, int IconSizePx) {
        StackPane Chip = new StackPane();
        Chip.setPrefSize(ChipSize, ChipSize);
        Chip.setMinSize(ChipSize, ChipSize);
        Chip.setStyle(
            "-fx-background-color:" + AccentHex + "18;" +
            "-fx-background-radius:0;" +
            "-fx-border-color:" + AccentHex + "30;" +
            "-fx-border-width:1;"
        );
        Chip.getChildren().add(MaterialIcon(Codepoint, AccentHex, IconSizePx));
        return Chip;
    }

    public static StackPane CircleChip(String InitialChar, String AccentHex, int DiameterPx) {
        StackPane Container = new StackPane();
        Container.setPrefSize(DiameterPx, DiameterPx);
        Container.setMinSize(DiameterPx, DiameterPx);
        Container.setStyle(
            "-fx-background-color:" + AccentHex + "18;" +
            "-fx-background-radius:0;"
        );
        Label Initial = new Label(InitialChar.substring(0, 1).toUpperCase());
        Initial.setStyle(
            "-fx-text-fill:" + AccentHex + ";" +
            "-fx-font-size:" + (int)(DiameterPx * 0.42) + "px;" +
            "-fx-font-weight:bold;"
        );
        Container.getChildren().add(Initial);
        return Container;
    }

    public static VBox PanelCard(String TitleText, String IconCodepoint, String IconHex) {
        VBox Card = new VBox(0);
        Card.setStyle(StyleHelper.PanelCardStyle());

        HBox Header = new HBox(7);
        Header.setStyle(StyleHelper.PanelHeaderStyle());
        Header.setAlignment(Pos.CENTER_LEFT);
        Header.getChildren().addAll(
            MaterialIcon(IconCodepoint, IconHex, 12),
            SmallCapsLabel(TitleText, Palette.TextTertiary)
        );

        VBox Body = new VBox(10);
        Body.setPadding(new Insets(12));
        Card.getChildren().addAll(Header, Body);
        return Card;
    }

    public static VBox PanelCardWithAccent(String TitleText, String IconCodepoint, String AccentHex) {
        VBox Card = new VBox(0);
        Card.setStyle(StyleHelper.AccentTopBorderStyle(AccentHex));

        HBox Header = new HBox(7);
        Header.setStyle(StyleHelper.PanelHeaderStyle());
        Header.setAlignment(Pos.CENTER_LEFT);
        Label LiveDot = new Label("●");
        LiveDot.setStyle("-fx-text-fill:" + AccentHex + "; -fx-font-size:7px;");
        Header.getChildren().addAll(
            LiveDot,
            SmallCapsLabel(TitleText, Palette.TextTertiary)
        );

        VBox Body = new VBox(10);
        Body.setPadding(new Insets(12));
        Card.getChildren().addAll(Header, Body);
        return Card;
    }

    public static VBox GetPanelBody(VBox PanelCard) {
        return (VBox) PanelCard.getChildren().get(1);
    }

    public static VBox StatCard(String Title, String Value, String AccentHex,
                                String IconCodepoint, String Delta, boolean DeltaPositive) {
        VBox Card = new VBox(6);
        Card.setStyle(StyleHelper.AccentTopBorderStyle(AccentHex));
        Card.setPadding(new Insets(10, 12, 10, 12));

        HBox TopRow = new HBox();
        TopRow.setAlignment(Pos.CENTER_RIGHT);
        TopRow.getChildren().add(IconChip(IconCodepoint, AccentHex, 28, 13));

        Label ValueLabel = new Label(Value);
        ValueLabel.setStyle(
            "-fx-text-fill:#dde8f0; -fx-font-size:22px; -fx-font-weight:700;"
        );

        Label TitleLabel = new Label(Title);
        TitleLabel.setStyle(
            "-fx-text-fill:" + Palette.TextTertiary + ";" +
            "-fx-font-size:10px; -fx-font-weight:bold; -fx-letter-spacing:0.09em;"
        );

        HBox DeltaRow = new HBox(4);
        DeltaRow.setAlignment(Pos.CENTER_LEFT);
        Label DeltaLabel = new Label(Delta);
        DeltaLabel.setStyle(DeltaPositive
            ? "-fx-text-fill:#30c87a; -fx-font-size:10px;"
            : "-fx-text-fill:#e86060; -fx-font-size:10px;"
        );
        Label CompareLabel = MutedLabel("vs last");
        DeltaRow.getChildren().addAll(DeltaLabel, CompareLabel);

        Card.getChildren().addAll(TopRow, ValueLabel, TitleLabel, DeltaRow);
        return Card;
    }

    public static HBox RowEntry(String LabelText, Node ValueNode) {
        HBox Row = new HBox(9);
        Row.setAlignment(Pos.CENTER_LEFT);
        Label EntryLabel = new Label(LabelText);
        EntryLabel.setMinWidth(70);
        EntryLabel.setStyle("-fx-font-size:11px; -fx-text-fill:" + Palette.TextTertiary + ";");
        Row.getChildren().addAll(EntryLabel, ValueNode);
        return Row;
    }

    public static HBox ActivityRow(String IconCodepoint, String AccentHex, String Message, String Timestamp) {
        HBox Row = new HBox(10);
        Row.setAlignment(Pos.CENTER_LEFT);
        Row.setPadding(new Insets(8, 12, 8, 12));
        Row.setStyle(
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );

        StackPane IconWrapper = new StackPane();
        IconWrapper.setPrefSize(24, 24);
        IconWrapper.setMinSize(24, 24);
        IconWrapper.setStyle(
            "-fx-background-color:" + AccentHex + "14;" +
            "-fx-border-color:" + AccentHex + "28;" +
            "-fx-border-width:1;" +
            "-fx-background-radius:0;"
        );
        IconWrapper.getChildren().add(MaterialIcon(IconCodepoint, AccentHex, 12));

        VBox Info = new VBox(2);
        Label MessageLabel = new Label(Message);
        MessageLabel.setStyle("-fx-text-fill:" + Palette.TextPrimary + "; -fx-font-size:11px;");
        Label TimestampLabel = new Label(Timestamp);
        TimestampLabel.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:10px;");
        Info.getChildren().addAll(MessageLabel, TimestampLabel);
        HBox.setHgrow(Info, Priority.ALWAYS);

        Row.getChildren().addAll(IconWrapper, Info);
        return Row;
    }

    public static ToggleSwitch BuildToggleSwitch() {
        return new ToggleSwitch();
    }

    public static class ToggleSwitch extends StackPane {

        private static final double TrackW  = 42;
        private static final double TrackH  = 20;
        private static final double ThumbW  = 14;
        private static final double ThumbH  = 14;
        private static final double Travel  = (TrackW / 2) - (ThumbW / 2) - 2;

        private final BooleanProperty SwitchedOn = new SimpleBooleanProperty(false);
        private final Rectangle Track  = new Rectangle(TrackW, TrackH);
        private final Rectangle Thumb  = new Rectangle(ThumbW, ThumbH);
        private final TranslateTransition Anim = new TranslateTransition(Duration.millis(180), Thumb);

        public ToggleSwitch() {
            Track.setArcWidth(0);
            Track.setArcHeight(0);
            ApplyColors(false);

            Thumb.setArcWidth(0);
            Thumb.setArcHeight(0);
            Thumb.setFill(Color.web(Palette.TextTertiary));
            Thumb.setTranslateX(-Travel);
            Thumb.setEffect(new DropShadow(3, 0, 1, Color.rgb(0, 0, 0, 0.35)));

            getChildren().addAll(Track, Thumb);
            setAlignment(Pos.CENTER);
            setPrefSize(TrackW, TrackH);
            setMaxSize(TrackW, TrackH);
            setMinSize(TrackW, TrackH);
            setCursor(javafx.scene.Cursor.HAND);

            SwitchedOn.addListener((Obs, Old, IsOn) -> AnimateToggle(IsOn));
            setOnMouseClicked(e -> SwitchedOn.set(!SwitchedOn.get()));
        }

        private void AnimateToggle(boolean IsOn) {
            Anim.stop();
            Anim.setToX(IsOn ? Travel : -Travel);
            Anim.play();
            ApplyColors(IsOn);
        }

        private void ApplyColors(boolean IsOn) {
            if (IsOn) {
                Track.setFill(Color.web(Palette.AccentGreen + "30"));
                Track.setStroke(Color.web(Palette.AccentGreen));
                Track.setStrokeWidth(1);
                Thumb.setFill(Color.web(Palette.AccentGreen));
            } else {
                Track.setFill(Color.web(Palette.BackgroundSurface));
                Track.setStroke(Color.web(Palette.BorderDefault));
                Track.setStrokeWidth(1);
                Thumb.setFill(Color.web(Palette.TextTertiary));
            }
        }

        public BooleanProperty SwitchedOnProperty() { return SwitchedOn; }
        public boolean         IsSwitchedOn()        { return SwitchedOn.get(); }
        public void            SetSwitchedOn(boolean Value) { SwitchedOn.set(Value); }
    }

    public static Label PlaceholderLabel(String Text) {
        Label Placeholder = new Label(Text);
        Placeholder.setStyle("-fx-text-fill:" + Palette.TextQuaternary + "; -fx-font-size:11px;");
        return Placeholder;
    }

    public static Region FlexSpacer(boolean Horizontal) {
        Region Spacer = new Region();
        if (Horizontal) HBox.setHgrow(Spacer, Priority.ALWAYS);
        else             VBox.setVgrow(Spacer, Priority.ALWAYS);
        return Spacer;
    }
}
