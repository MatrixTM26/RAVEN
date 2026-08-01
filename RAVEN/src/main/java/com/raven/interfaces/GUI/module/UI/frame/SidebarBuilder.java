package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import com.raven.interfaces.GUI.module.UI.component.ComponentFactory;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class SidebarBuilder {

    private static final String IconDns      = "\uE875";
    private static final String IconMenu     = "\uE5D2";
    private static final String IconDashboard= "\uE871";
    private static final String IconDevices  = "\uE32B";
    private static final String IconTerminal = "\uEB8E";
    private static final String IconCode     = "\uE86F";
    private static final String IconList     = "\uE896";
    private static final String IconSettings = "\uE8B8";
    private static final String IconCircle   = "\uEF4A";

    private SidebarBuilder() {}

    public static VBox Build(Consumer<String> OnNavigate,
                             Map<String, HBox> NavItemMapOut,
                             Label[] StatusIndicatorOut,
                             String OperatorName) {
        VBox Sidebar = new VBox(0);
        Sidebar.setPrefWidth(228);
        Sidebar.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent " + Palette.BorderSubtle + " transparent transparent;" +
            "-fx-border-width:0 1 0 0;"
        );

        HBox BrandRow = new HBox(10);
        BrandRow.setAlignment(Pos.CENTER_LEFT);
        BrandRow.setPadding(new Insets(0, 14, 0, 14));
        BrandRow.setMinHeight(60);
        BrandRow.setStyle(
            "-fx-background-color:" + Palette.BackgroundVoid + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );

        StackPane LogoChip = ComponentFactory.IconChip(IconDns, Palette.AccentBlue, 32, 16);

        VBox BrandText = new VBox(2);
        Label BrandName = new Label("RAVEN");
        BrandName.setStyle(
            "-fx-text-fill:" + Palette.TextPrimary + ";" +
            "-fx-font-size:14px; -fx-font-weight:bold;" +
            "-fx-letter-spacing:0.05em;"
        );
        Label BrandSubtitle = new Label("Command and Control");
        BrandSubtitle.setStyle("-fx-text-fill:" + Palette.TextQuaternary + "; -fx-font-size:9px;");
        BrandText.getChildren().addAll(BrandName, BrandSubtitle);
        HBox.setHgrow(BrandText, Priority.ALWAYS);

        Label VersionTag = new Label("v3.0");
        VersionTag.setStyle(
            "-fx-background-color:" + Palette.Background + ";" +
            "-fx-text-fill:" + Palette.TextTertiary + ";" +
            "-fx-font-size:9px; -fx-padding:2 7 2 7;" +
            "-fx-border-color:" + Palette.BorderSubtle + ";" +
            "-fx-border-width:1; -fx-border-radius:10; -fx-background-radius:10;"
        );

        Label BurgerLabel = new Label(IconMenu);
        BurgerLabel.setStyle(
            "-fx-font-family:'Material Icons'; -fx-font-size:18px;" +
            "-fx-text-fill:" + Palette.TextTertiary + ";" +
            "-fx-cursor:hand; -fx-padding:4 4 4 4;" +
            "-fx-background-radius:6;"
        );
        BurgerLabel.setOnMouseEntered(e -> BurgerLabel.setStyle(
            "-fx-font-family:'Material Icons'; -fx-font-size:18px;" +
            "-fx-text-fill:" + Palette.TextPrimary + ";" +
            "-fx-cursor:hand; -fx-padding:4 4 4 4;" +
            "-fx-background-color:" + Palette.BackgroundSurface + ";" +
            "-fx-background-radius:6;"
        ));
        BurgerLabel.setOnMouseExited(e -> BurgerLabel.setStyle(
            "-fx-font-family:'Material Icons'; -fx-font-size:18px;" +
            "-fx-text-fill:" + Palette.TextTertiary + ";" +
            "-fx-cursor:hand; -fx-padding:4 4 4 4;" +
            "-fx-background-radius:6;"
        ));
        BurgerLabel.setOnMouseClicked(e -> AnimateSidebarToggle(Sidebar, NavItemMapOut));

        BrandRow.getChildren().addAll(LogoChip, BrandText, VersionTag, BurgerLabel);
        Sidebar.getChildren().add(BrandRow);

        Sidebar.getChildren().add(SectionLabel("General"));
        AddNavItem(Sidebar, "Overview",       IconDashboard,  Palette.AccentBlue,  NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Sessions",       IconDevices,    Palette.AccentGreen, NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Terminal",       IconTerminal,   Palette.AccentPink,  NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Command Center", IconCode,       Palette.AccentTeal,  NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Logs",           IconList,       Palette.TextTertiary,NavItemMapOut, OnNavigate);
        Sidebar.getChildren().add(SectionLabel("Configuration"));
        AddNavItem(Sidebar, "Settings",       IconSettings,   Palette.TextTertiary,NavItemMapOut, OnNavigate);

        Sidebar.getChildren().add(ComponentFactory.FlexSpacer(false));

        VBox FooterBox = new VBox(6);
        FooterBox.setPadding(new Insets(10, 14, 12, 14));
        FooterBox.setStyle(
            "-fx-background-color:" + Palette.BackgroundVoid + ";" +
            "-fx-border-color:" + Palette.BorderSubtle + " transparent transparent transparent;" +
            "-fx-border-width:1 0 0 0;"
        );

        Label StatusIndicator = new Label(IconCircle + "  Offline");
        StatusIndicator.setStyle(
            "-fx-text-fill:" + Palette.AccentRed + ";" +
            "-fx-font-size:11px;" +
            "-fx-font-family:'Material Icons','Segoe UI';"
        );
        if (StatusIndicatorOut != null && StatusIndicatorOut.length > 0)
            StatusIndicatorOut[0] = StatusIndicator;

        HBox AuthorRow = new HBox(8);
        AuthorRow.setAlignment(Pos.CENTER_LEFT);
        String DisplayName = OperatorName != null ? OperatorName : "MatrixTM26";
        StackPane AuthorAvatar = ComponentFactory.CircleChip(DisplayName, Palette.AccentBlue, 22);
        Label AuthorLabel = new Label(DisplayName);
        AuthorLabel.setStyle("-fx-font-size:10px; -fx-text-fill:" + Palette.TextTertiary + ";");
        AuthorRow.getChildren().addAll(AuthorAvatar, AuthorLabel);
        FooterBox.getChildren().addAll(StatusIndicator, AuthorRow);
        Sidebar.getChildren().add(FooterBox);
        return Sidebar;
    }

    private static void AddNavItem(VBox Sidebar, String PageName, String IconCodepoint,
                                   String IconHex, Map<String, HBox> NavItemMapOut,
                                   Consumer<String> OnNavigate) {
        HBox NavItem = new HBox(12);
        NavItem.setAlignment(Pos.CENTER_LEFT);
        NavItem.setPadding(new Insets(9, 14, 9, 16));
        NavItem.setMaxWidth(Double.MAX_VALUE);
        NavItem.setCursor(javafx.scene.Cursor.HAND);
        NavItem.setStyle("-fx-background-color:transparent;");

        StackPane IconWrapper = new StackPane();
        IconWrapper.setPrefSize(24, 24);
        IconWrapper.setMinSize(24, 24);
        Label IconLabel = ComponentFactory.MaterialIcon(IconCodepoint, IconHex, 15);
        IconWrapper.getChildren().add(IconLabel);

        Label NameLabel = new Label(PageName);
        NameLabel.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:12px;");

        NavItem.getChildren().addAll(IconWrapper, NameLabel);
        NavItem.setUserData(PageName);

        NavItem.setOnMouseEntered(e -> {
            if (!PageName.equals(GetActiveFromMap(NavItemMapOut)))
                NavItem.setStyle("-fx-background-color:" + Palette.BackgroundSurface + ";");
        });
        NavItem.setOnMouseExited(e -> {
            if (!PageName.equals(GetActiveFromMap(NavItemMapOut)))
                NavItem.setStyle("-fx-background-color:transparent;");
        });
        NavItem.setOnMouseClicked(e -> OnNavigate.accept(PageName));

        NavItemMapOut.put(PageName, NavItem);
        Sidebar.getChildren().add(NavItem);
    }

    public static void ApplyActiveState(Map<String, HBox> NavItemMap, String ActivePage) {
        NavItemMap.forEach((Name, Item) -> {
            Label NameLabel = (Label) Item.getChildren().get(1);
            if (Name.equals(ActivePage)) {
                Item.setStyle(
                    "-fx-background-color:rgba(10,132,255,0.10);" +
                    "-fx-border-color:transparent transparent transparent " + Palette.AccentBlue + ";" +
                    "-fx-border-width:0 0 0 2;"
                );
                NameLabel.setStyle("-fx-text-fill:" + Palette.AccentBlue + "; -fx-font-size:12px; -fx-font-weight:bold;");
            } else {
                Item.setStyle("-fx-background-color:transparent;");
                NameLabel.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:12px;");
            }
        });
    }

    private static void AnimateSidebarToggle(VBox Sidebar, Map<String, HBox> NavItemMap) {
        boolean WillCollapse = Sidebar.getPrefWidth() > 100;
        double TargetWidth = WillCollapse ? 56 : 228;
        Timeline SlideAnimation = new Timeline(new KeyFrame(
            Duration.millis(220),
            new KeyValue(Sidebar.prefWidthProperty(), TargetWidth, Interpolator.EASE_BOTH)
        ));
        SlideAnimation.play();
        NavItemMap.values().forEach(Item -> {
            Label NameLabel = (Label) Item.getChildren().get(1);
            NameLabel.setVisible(!WillCollapse);
            NameLabel.setManaged(!WillCollapse);
        });
        Sidebar.getChildren().forEach(Child -> {
            if (Child instanceof Label Section && Section.getStyle().contains("9px")) {
                Section.setVisible(!WillCollapse);
                Section.setManaged(!WillCollapse);
            }
        });
    }

    private static String GetActiveFromMap(Map<String, HBox> NavItemMap) {
        return NavItemMap.entrySet().stream()
            .filter(Entry -> Entry.getValue().getStyle().contains(Palette.AccentBlue))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("");
    }

    private static Label SectionLabel(String Text) {
        Label Section = new Label(Text.toUpperCase());
        Section.setStyle(
            "-fx-text-fill:" + Palette.TextQuaternary + ";" +
            "-fx-font-size:9px; -fx-font-weight:bold;" +
            "-fx-padding:18 14 6 18;" +
            "-fx-letter-spacing:0.08em;"
        );
        return Section;
    }
}
