package com.raven.interfaces.GUI.module.UI.component;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.animation.FadeTransition;
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
import javafx.scene.shape.Circle;
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
            "-fx-font-size:9px;" +
            "-fx-font-weight:bold;" +
            "-fx-letter-spacing:0.06em;"
        );
        return CapsLabel;
    }

    public static Label BodyLabel(String Text) {
        Label BodyLbl = new Label(Text);
        BodyLbl.setStyle("-fx-text-fill:" + Palette.TextSecondary + "; -fx-font-size:12px;");
        return BodyLbl;
    }

    public static Label MutedLabel(String Text) {
        Label MutedLbl = new Label(Text);
        MutedLbl.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:11px;");
        return MutedLbl;
    }

    public static Button ActionButton(String Text, String... StyleClasses) {
        Button ActionBtn = new Button(Text);
        for (String StyleClass : StyleClasses)
            ActionBtn.getStyleClass().add(StyleClass);
        return ActionBtn;
    }

    public static Button IconButton(String Codepoint, String TooltipText,
                                    javafx.event.EventHandler<javafx.event.ActionEvent> OnClick) {
        Button IconBtn = new Button(Codepoint);
        IconBtn.getStyleClass().add("btn-icon");
        IconBtn.setTooltip(new Tooltip(TooltipText));
        IconBtn.setOnAction(OnClick);
        return IconBtn;
    }

    public static StackPane IconChip(String Codepoint, String AccentHex, int ChipSize, int IconSizePx) {
        StackPane Chip = new StackPane();
        Chip.setPrefSize(ChipSize, ChipSize);
        Chip.setMinSize(ChipSize, ChipSize);
        Chip.setStyle(
            "-fx-background-color:" + AccentHex + "1a;" +
            "-fx-background-radius:" + (ChipSize / 3) + ";"
        );
        Chip.getChildren().add(MaterialIcon(Codepoint, AccentHex, IconSizePx));
        return Chip;
    }

    public static StackPane CircleChip(String InitialChar, String AccentHex, int DiameterPx) {
        StackPane CircleContainer = new StackPane();
        CircleContainer.setPrefSize(DiameterPx, DiameterPx);
        CircleContainer.setMinSize(DiameterPx, DiameterPx);
        CircleContainer.setStyle(
            "-fx-background-color:" + AccentHex + "1a;" +
            "-fx-background-radius:" + DiameterPx + ";"
        );
        Label Initial = new Label(InitialChar.substring(0, 1).toUpperCase());
        Initial.setStyle(
            "-fx-text-fill:" + AccentHex + ";" +
            "-fx-font-size:" + (int)(DiameterPx * 0.42) + "px;" +
            "-fx-font-weight:bold;"
        );
        CircleContainer.getChildren().add(Initial);
        return CircleContainer;
    }

    public static VBox PanelCard(String TitleText, String IconCodepoint, String IconHex) {
        VBox Card = new VBox(0);
        Card.getStyleClass().add("panel-card");

        HBox Header = new HBox(8);
        Header.getStyleClass().add("panel-header");
        Header.setAlignment(Pos.CENTER_LEFT);
        Header.getChildren().addAll(
            MaterialIcon(IconCodepoint, IconHex, 13),
            SmallCapsLabel(TitleText, Palette.TextTertiary)
        );

        VBox Body = new VBox(10);
        Body.setPadding(new Insets(14));
        Card.getChildren().addAll(Header, Body);
        return Card;
    }

    public static VBox GetPanelBody(VBox PanelCard) {
        return (VBox) PanelCard.getChildren().get(1);
    }

    public static VBox StatCard(String Title, String Value, String AccentHex,
                                String IconCodepoint, String Delta, boolean DeltaPositive) {
        VBox Card = new VBox(8);
        Card.getStyleClass().add("stat-card");
        Card.setStyle(
            "-fx-background-color:" + Palette.Background + ";" +
            "-fx-border-color:" + Palette.BorderSubtle + ";" +
            "-fx-border-width:1;" +
            "-fx-background-radius:10;" +
            "-fx-border-radius:10;" +
            "-fx-border-left-color:" + AccentHex + ";" +
            "-fx-border-left-width:3;" +
            "-fx-padding:18 20 18 20;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.20),6,0,0,2);"
        );

        HBox TopRow = new HBox();
        TopRow.setAlignment(Pos.CENTER_RIGHT);
        TopRow.getChildren().add(IconChip(IconCodepoint, AccentHex, 36, 16));

        Label ValueLabel = new Label(Value);
        ValueLabel.getStyleClass().add("stat-value");
        ValueLabel.setStyle("-fx-text-fill:" + Palette.TextPrimary + "; -fx-font-size:28px; -fx-font-weight:bold;");

        Label TitleLabel = new Label(Title);
        TitleLabel.getStyleClass().add("stat-label");

        HBox DeltaRow = new HBox(4);
        DeltaRow.setAlignment(Pos.CENTER_LEFT);
        Label DeltaLabel = new Label(Delta);
        DeltaLabel.getStyleClass().add(DeltaPositive ? "stat-delta-pos" : "stat-delta-neg");
        Label CompareLabel = MutedLabel("vs last");
        DeltaRow.getChildren().addAll(DeltaLabel, CompareLabel);

        Card.getChildren().addAll(TopRow, ValueLabel, TitleLabel, DeltaRow);

        FadeTransition FadeIn = new FadeTransition(Duration.millis(320), Card);
        FadeIn.setFromValue(0);
        FadeIn.setToValue(1);
        FadeIn.play();
        return Card;
    }

    public static HBox RowEntry(String LabelText, Node ValueNode) {
        HBox EntryRow = new HBox(10);
        EntryRow.setAlignment(Pos.CENTER_LEFT);
        Label EntryLabel = new Label(LabelText);
        EntryLabel.setMinWidth(76);
        EntryLabel.setStyle("-fx-font-size:11px; -fx-text-fill:" + Palette.TextTertiary + ";");
        EntryRow.getChildren().addAll(EntryLabel, ValueNode);
        return EntryRow;
    }

    public static HBox ActivityRow(String IconCodepoint, String AccentHex, String Message, String Timestamp) {
        HBox Row = new HBox(12);
        Row.setAlignment(Pos.CENTER_LEFT);
        Row.setPadding(new Insets(10, 14, 10, 14));
        Row.setStyle(
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );
        StackPane IconWrapper = new StackPane();
        IconWrapper.setPrefSize(32, 32);
        IconWrapper.setMinSize(32, 32);
        IconWrapper.setStyle("-fx-background-color:" + AccentHex + "18; -fx-background-radius:50;");
        IconWrapper.getChildren().add(MaterialIcon(IconCodepoint, AccentHex, 14));

        VBox Info = new VBox(2);
        Label MessageLabel = new Label(Message);
        MessageLabel.setStyle("-fx-text-fill:" + Palette.TextSecondary + "; -fx-font-size:12px;");
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

        private static final double TrackWidth  = 42;
        private static final double TrackHeight = 24;
        private static final double ThumbRadius = 9;
        private static final double TravelDistance = (TrackWidth / 2) - ThumbRadius - 2;

        private final BooleanProperty SwitchedOn = new SimpleBooleanProperty(false);
        private final Rectangle Track  = new Rectangle(TrackWidth, TrackHeight);
        private final Circle    Thumb  = new Circle(ThumbRadius);
        private final TranslateTransition Animation = new TranslateTransition(Duration.millis(200), Thumb);

        public ToggleSwitch() {
            Track.setArcWidth(TrackHeight);
            Track.setArcHeight(TrackHeight);
            ApplyTrackColor(false);

            Thumb.setFill(Color.WHITE);
            Thumb.setTranslateX(-TravelDistance);
            Thumb.setEffect(new DropShadow(4, 0, 1, Color.rgb(0, 0, 0, 0.30)));

            getChildren().addAll(Track, Thumb);
            setAlignment(Pos.CENTER);
            setPrefSize(TrackWidth, TrackHeight);
            setMaxSize(TrackWidth, TrackHeight);
            setMinSize(TrackWidth, TrackHeight);
            setCursor(javafx.scene.Cursor.HAND);

            SwitchedOn.addListener((Observable, OldValue, IsOn) -> AnimateSwitch(IsOn));
            setOnMouseClicked(e -> SwitchedOn.set(!SwitchedOn.get()));
        }

        private void AnimateSwitch(boolean IsOn) {
            Animation.stop();
            Animation.setToX(IsOn ? TravelDistance : -TravelDistance);
            Animation.play();
            ApplyTrackColor(IsOn);
        }

        private void ApplyTrackColor(boolean IsOn) {
            Track.setFill(IsOn ? Color.web(Palette.AccentGreen) : Color.web(Palette.BorderStrong));
        }

        public BooleanProperty SwitchedOnProperty()  { return SwitchedOn; }
        public boolean         IsSwitchedOn()         { return SwitchedOn.get(); }
        public void            SetSwitchedOn(boolean Value) { SwitchedOn.set(Value); }
    }

    public static Label PlaceholderLabel(String Text) {
        Label Placeholder = new Label(Text);
        Placeholder.setStyle("-fx-text-fill:" + Palette.TextQuaternary + "; -fx-font-size:12px;");
        return Placeholder;
    }

    public static Region FlexSpacer(boolean Horizontal) {
        Region Spacer = new Region();
        if (Horizontal) HBox.setHgrow(Spacer, Priority.ALWAYS);
        else             VBox.setVgrow(Spacer, Priority.ALWAYS);
        return Spacer;
    }
}
