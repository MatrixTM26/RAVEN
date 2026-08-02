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
    private static final String IconTask     = "\uE8F9";
    private static final String IconNet      = "\uE80C";
    private static final String IconFolder   = "\uE2C7";
    private static final String IconKey      = "\uE886";
    private static final String IconInfo     = "\uE8FD";
    private static final String IconBug      = "\uE868";
    private static final String IconTimer    = "\uE425";

    private SidebarBuilder() {}

    public static VBox Build(Consumer<String> OnNavigate,
                             Map<String, HBox> NavItemMapOut,
                             Label[] StatusIndicatorOut,
                             String OperatorName) {
        VBox Sidebar = new VBox(0);
        Sidebar.setPrefWidth(220);
        Sidebar.setStyle(
            "-fx-background-color:" + Palette.BackgroundDeep + ";" +
            "-fx-border-color:transparent " + Palette.BorderSubtle + " transparent transparent;" +
            "-fx-border-width:0 1 0 0;"
        );

        HBox BrandRow = new HBox(9);
        BrandRow.setAlignment(Pos.CENTER_LEFT);
        BrandRow.setPadding(new Insets(0, 12, 0, 12));
        BrandRow.setMinHeight(56);
        BrandRow.setStyle(
            "-fx-background-color:" + Palette.BackgroundVoid + ";" +
            "-fx-border-color:transparent transparent " + Palette.BorderSubtle + " transparent;" +
            "-fx-border-width:0 0 1 0;"
        );

        StackPane LogoChip = ComponentFactory.IconChip(IconDns, Palette.AccentBlue, 30, 15);

        VBox BrandText = new VBox(2);
        Label BrandName = new Label("RAVEN");
        BrandName.setStyle(
            "-fx-text-fill:" + Palette.TextPrimary + ";" +
            "-fx-font-size:13px; -fx-font-weight:bold;" +
            "-fx-letter-spacing:0.12em;"
        );
        Label BrandSub = new Label("Command and Control");
        BrandSub.setStyle("-fx-text-fill:" + Palette.TextQuaternary + "; -fx-font-size:9px;");
        BrandText.getChildren().addAll(BrandName, BrandSub);
        HBox.setHgrow(BrandText, Priority.ALWAYS);

        Label VersionTag = new Label("v3.0");
        VersionTag.setStyle(
            "-fx-background-color:" + Palette.Background + ";" +
            "-fx-text-fill:" + Palette.TextTertiary + ";" +
            "-fx-font-size:9px; -fx-padding:2 6 2 6;" +
            "-fx-border-color:" + Palette.BorderSubtle + ";" +
            "-fx-border-width:1; -fx-background-radius:0; -fx-border-radius:0;"
        );

        Label BurgerBtn = new Label(IconMenu);
        BurgerBtn.setStyle(
            "-fx-font-family:'Material Icons'; -fx-font-size:17px;" +
            "-fx-text-fill:" + Palette.TextTertiary + ";" +
            "-fx-cursor:hand; -fx-padding:4 4 4 4;" +
            "-fx-background-radius:0;"
        );
        BurgerBtn.setOnMouseEntered(e -> BurgerBtn.setStyle(
            "-fx-font-family:'Material Icons'; -fx-font-size:17px;" +
            "-fx-text-fill:" + Palette.TextPrimary + ";" +
            "-fx-cursor:hand; -fx-padding:4 4 4 4;" +
            "-fx-background-color:" + Palette.BackgroundSurface + ";" +
            "-fx-background-radius:0;"
        ));
        BurgerBtn.setOnMouseExited(e -> BurgerBtn.setStyle(
            "-fx-font-family:'Material Icons'; -fx-font-size:17px;" +
            "-fx-text-fill:" + Palette.TextTertiary + ";" +
            "-fx-cursor:hand; -fx-padding:4 4 4 4;" +
            "-fx-background-radius:0;"
        ));
        BurgerBtn.setOnMouseClicked(e -> AnimateSidebarToggle(Sidebar, NavItemMapOut));

        BrandRow.getChildren().addAll(LogoChip, BrandText, VersionTag, BurgerBtn);
        Sidebar.getChildren().add(BrandRow);

        Sidebar.getChildren().add(SectionLabel("General"));
        AddNavItem(Sidebar, "Overview",       IconDashboard, Palette.AccentBlue,   NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Sessions",       IconDevices,   Palette.AccentGreen,  NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Terminal",       IconTerminal,  Palette.AccentPink,   NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Command Center", IconCode,      Palette.AccentTeal,   NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Logs",           IconList,      Palette.TextTertiary, NavItemMapOut, OnNavigate);

        Sidebar.getChildren().add(SectionLabel("Tools"));
        AddNavItem(Sidebar, "Payload Gen",    IconBug,       Palette.AccentOrange, NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Keylogger",      IconKey,       Palette.AccentPurple, NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Network Map",    IconNet,       Palette.AccentTeal,   NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "File Manager",   IconFolder,    Palette.AccentYellow, NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Tasks",          IconTask,      Palette.AccentBlue,   NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Scheduler",      IconTimer,     Palette.AccentPurple, NavItemMapOut, OnNavigate);
        AddNavItem(Sidebar, "Sysinfo",        IconInfo,      Palette.AccentGreen,  NavItemMapOut, OnNavigate);

        Sidebar.getChildren().add(SectionLabel("Configuration"));
        AddNavItem(Sidebar, "Settings", IconSettings, Palette.TextTertiary, NavItemMapOut, OnNavigate);

        Sidebar.getChildren().add(ComponentFactory.FlexSpacer(false));

        VBox Footer = new VBox(5);
        Footer.setPadding(new Insets(9, 12, 11, 12));
        Footer.setStyle(
            "-fx-background-color:" + Palette.BackgroundVoid + ";" +
            "-fx-border-color:" + Palette.BorderSubtle + " transparent transparent transparent;" +
            "-fx-border-width:1 0 0 0;"
        );

        Label StatusIndicator = new Label(IconCircle + "  Offline");
        StatusIndicator.setStyle(
            "-fx-text-fill:" + Palette.AccentOrange + ";" +
            "-fx-font-size:11px;" +
            "-fx-font-family:'Material Icons','JetBrains Mono',monospace;"
        );
        if (StatusIndicatorOut != null && StatusIndicatorOut.length > 0)
            StatusIndicatorOut[0] = StatusIndicator;

        HBox AuthorRow = new HBox(7);
        AuthorRow.setAlignment(Pos.CENTER_LEFT);
        String DisplayName = OperatorName != null ? OperatorName : "MatrixTM26";
        StackPane AuthorAvatar = ComponentFactory.CircleChip(DisplayName, Palette.AccentBlue, 20);
        Label AuthorLabel = new Label(DisplayName);
        AuthorLabel.setStyle("-fx-font-size:10px; -fx-text-fill:" + Palette.TextTertiary + ";");
        AuthorRow.getChildren().addAll(AuthorAvatar, AuthorLabel);

        Footer.getChildren().addAll(StatusIndicator, AuthorRow);
        Sidebar.getChildren().add(Footer);
        return Sidebar;
    }

    private static void AddNavItem(VBox Sidebar, String PageName, String IconCodepoint,
                                   String IconHex, Map<String, HBox> NavItemMapOut,
                                   Consumer<String> OnNavigate) {
        HBox NavItem = new HBox(10);
        NavItem.setAlignment(Pos.CENTER_LEFT);
        NavItem.setPadding(new Insets(8, 12, 8, 14));
        NavItem.setMaxWidth(Double.MAX_VALUE);
        NavItem.setCursor(javafx.scene.Cursor.HAND);
        NavItem.setStyle("-fx-background-color:transparent;");

        StackPane IconWrapper = new StackPane();
        IconWrapper.setPrefSize(22, 22);
        IconWrapper.setMinSize(22, 22);
        IconWrapper.getChildren().add(ComponentFactory.MaterialIcon(IconCodepoint, IconHex, 14));

        Label NameLabel = new Label(PageName);
        NameLabel.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:11px;");

        NavItem.getChildren().addAll(IconWrapper, NameLabel);
        NavItem.setUserData(PageName);

        NavItem.setOnMouseEntered(e -> {
            if (!IsActive(NavItemMapOut, PageName))
                NavItem.setStyle("-fx-background-color:" + Palette.BackgroundSurface + ";");
        });
        NavItem.setOnMouseExited(e -> {
            if (!IsActive(NavItemMapOut, PageName))
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
                    "-fx-background-color:rgba(61,142,240,0.07);" +
                    "-fx-border-color:transparent transparent transparent " + Palette.AccentBlue + ";" +
                    "-fx-border-width:0 0 0 2;"
                );
                NameLabel.setStyle("-fx-text-fill:" + Palette.AccentBlue + "; -fx-font-size:11px; -fx-font-weight:bold;");
            } else {
                Item.setStyle("-fx-background-color:transparent;");
                NameLabel.setStyle("-fx-text-fill:" + Palette.TextTertiary + "; -fx-font-size:11px;");
            }
        });
    }

    private static void AnimateSidebarToggle(VBox Sidebar, Map<String, HBox> NavItemMap) {
        boolean WillCollapse = Sidebar.getPrefWidth() > 100;
        double Target = WillCollapse ? 52 : 220;
        Timeline Anim = new Timeline(new KeyFrame(
            Duration.millis(200),
            new KeyValue(Sidebar.prefWidthProperty(), Target, Interpolator.EASE_BOTH)
        ));
        Anim.play();
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

    private static boolean IsActive(Map<String, HBox> NavItemMap, String PageName) {
        HBox Item = NavItemMap.get(PageName);
        return Item != null && Item.getStyle().contains(Palette.AccentBlue);
    }

    private static Label SectionLabel(String Text) {
        Label Section = new Label(Text.toUpperCase());
        Section.setStyle(
            "-fx-text-fill:" + Palette.TextQuaternary + ";" +
            "-fx-font-size:9px; -fx-font-weight:bold;" +
            "-fx-padding:14 12 4 16;" +
            "-fx-letter-spacing:0.10em;"
        );
        return Section;
    }
}
