package com.raven.interfaces.GUI.module.UI.frame;

import com.raven.interfaces.GUI.module.UI.color.Palette;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class SidebarBuilder {

    private static final double EXPANDED  = 210.0;
    private static final double COLLAPSED = 48.0;

    private static final Map<String, String> NAV_ICONS = new LinkedHashMap<>() {{
        put("Overview",       "");
        put("Sessions",       "");
        put("Terminal",       "");
        put("Listener",       "");
        put("Command Center", "");
        put("Logs",           "");
        put("Payload Gen",    "");
        put("Keylogger",      "");
        put("Network Map",    "");
        put("File Manager",   "");
        put("Tasks",          "");
        put("Scheduler",      "");
        put("Sysinfo",        "");
        put("Settings",       "");
    }};

    private static final Map<String, String> SECTIONS = new LinkedHashMap<>() {{
        put("Overview",       "GENERAL");
        put("Sessions",       null);
        put("Terminal",       null);
        put("Listener",       null);
        put("Command Center", null);
        put("Logs",           null);
        put("Payload Gen",    "TOOLS");
        put("Keylogger",      null);
        put("Network Map",    null);
        put("File Manager",   null);
        put("Tasks",          null);
        put("Scheduler",      null);
        put("Sysinfo",        null);
        put("Settings",       "CONFIGURATION");
    }};

    private SidebarBuilder() {}

    public static VBox Build(Consumer<String> OnNavigate,
                             Map<String, HBox> NavItemMapOut,
                             Label[] StatusIndicatorOut,
                             String OperatorName) {
        VBox Sidebar = new VBox(0);
        Sidebar.setPrefWidth(EXPANDED);
        Sidebar.setMinWidth(COLLAPSED);
        Sidebar.setMaxWidth(EXPANDED);
        Sidebar.getStyleClass().add("sidebar");

        HBox Brand = new HBox(9);
        Brand.setAlignment(Pos.CENTER_LEFT);
        Brand.setPadding(new Insets(0, 10, 0, 12));
        Brand.setMinHeight(52);
        Brand.setMaxHeight(52);
        Brand.getStyleClass().add("sidebar-brand");

        Label LogoIcon = new Label("☠");
        LogoIcon.setStyle(
            "-fx-text-fill:" + Palette.Red + ";" +
            "-fx-font-size:18px; -fx-min-width:24;"
        );

        VBox BrandText = new VBox(2);
        Label BrandName = new Label("RAVEN");
        BrandName.setStyle(
            "-fx-text-fill:" + Palette.White + ";" +
            "-fx-font-size:13px; -fx-font-weight:bold; -fx-letter-spacing:0.14em;"
        );
        Label BrandSub = new Label("C2 Framework");
        BrandSub.setStyle("-fx-text-fill:" + Palette.WhiteGhost + "; -fx-font-size:9px;");
        BrandText.getChildren().addAll(BrandName, BrandSub);
        HBox.setHgrow(BrandText, Priority.ALWAYS);

        Label CollapseBtn = new Label("≡");
        CollapseBtn.setStyle(
            "-fx-text-fill:" + Palette.WhiteFaint + ";" +
            "-fx-font-size:15px; -fx-cursor:hand; -fx-padding:4 4 4 4;"
        );
        CollapseBtn.setCursor(Cursor.HAND);
        CollapseBtn.setOnMouseClicked(e -> ToggleCollapse(Sidebar, BrandText, CollapseBtn, NavItemMapOut));

        Brand.getChildren().addAll(LogoIcon, BrandText, CollapseBtn);
        Sidebar.getChildren().add(Brand);

        SECTIONS.forEach((Page, Section) -> {
            if (Section != null) {
                Label SectionLabel = new Label(Section);
                SectionLabel.getStyleClass().add("sidebar-section");
                SectionLabel.setMaxWidth(Double.MAX_VALUE);
                NavItemMapOut.put("__sec__" + Section, new HBox(SectionLabel));
                Sidebar.getChildren().add(SectionLabel);
            }
            Sidebar.getChildren().add(BuildNavItem(Page, NAV_ICONS.getOrDefault(Page, "■"), NavItemMapOut, OnNavigate));
        });

        Sidebar.getChildren().add(BuildFlexSpacer());

        VBox Footer = new VBox(5);
        Footer.setPadding(new Insets(9, 12, 11, 12));
        Footer.setStyle(
            "-fx-background-color:" + Palette.BgVoid + ";" +
            "-fx-border-color:" + Palette.Border + " transparent transparent transparent;" +
            "-fx-border-width:1 0 0 0;"
        );

        Label StatusLabel = new Label("● Offline");
        StatusLabel.setStyle(
            "-fx-text-fill:" + Palette.Red + ";" +
            "-fx-font-size:11px;"
        );
        if (StatusIndicatorOut != null && StatusIndicatorOut.length > 0)
            StatusIndicatorOut[0] = StatusLabel;

        String DisplayName = OperatorName != null ? OperatorName : "MatrixTM26";
        Label AuthorLabel = new Label(DisplayName);
        AuthorLabel.setStyle("-fx-font-size:10px; -fx-text-fill:" + Palette.WhiteFaint + ";");

        Footer.getChildren().addAll(StatusLabel, AuthorLabel);
        Sidebar.getChildren().add(Footer);
        return Sidebar;
    }

    private static HBox BuildNavItem(String PageName, String Icon,
                                     Map<String, HBox> NavItemMapOut,
                                     Consumer<String> OnNavigate) {
        HBox Item = new HBox(10);
        Item.setAlignment(Pos.CENTER_LEFT);
        Item.setPadding(new Insets(8, 12, 8, 14));
        Item.setMaxWidth(Double.MAX_VALUE);
        Item.setCursor(Cursor.HAND);
        Item.setStyle("-fx-background-color:transparent;");

        Label IconLabel = new Label(Icon);
        IconLabel.setStyle(
            "-fx-text-fill:" + Palette.Red + ";" +
            "-fx-font-size:13px; -fx-min-width:20; -fx-max-width:20;" +
            "-fx-alignment:CENTER;"
        );

        Label NameLabel = new Label(PageName);
        NameLabel.setStyle("-fx-text-fill:" + Palette.WhiteFaint + "; -fx-font-size:11px;");
        NameLabel.setMaxWidth(Double.MAX_VALUE);

        Item.getChildren().addAll(IconLabel, NameLabel);
        Item.setUserData(PageName);
        NavItemMapOut.put(PageName, Item);

        Item.setOnMouseEntered(e -> {
            if (!IsActive(Item))
                Item.setStyle("-fx-background-color:" + Palette.BgPanel + ";");
        });
        Item.setOnMouseExited(e -> {
            if (!IsActive(Item))
                Item.setStyle("-fx-background-color:transparent;");
        });
        Item.setOnMouseClicked(e -> OnNavigate.accept(PageName));
        return Item;
    }

    public static void SetActive(Map<String, HBox> NavItemMap, String ActivePage) {
        NavItemMap.forEach((Name, Item) -> {
            if (Name.startsWith("__sec__")) return;
            if (!(Item.getChildren().get(1) instanceof Label NameLabel)) return;
            if (Name.equals(ActivePage)) {
                Item.setStyle(
                    "-fx-background-color:#200808;" +
                    "-fx-border-color:transparent transparent transparent " + Palette.Red + ";" +
                    "-fx-border-width:0 0 0 2;"
                );
                NameLabel.setStyle("-fx-text-fill:" + Palette.Red + "; -fx-font-size:11px; -fx-font-weight:bold;");
            } else {
                Item.setStyle("-fx-background-color:transparent;");
                NameLabel.setStyle("-fx-text-fill:" + Palette.WhiteFaint + "; -fx-font-size:11px;");
            }
        });
    }

    private static void ToggleCollapse(VBox Sidebar, VBox BrandText,
                                       Label CollapseBtn,
                                       Map<String, HBox> NavItemMap) {
        boolean Expanding = Sidebar.getPrefWidth() < EXPANDED - 10;
        double Target = Expanding ? EXPANDED : COLLAPSED;

        Timeline Anim = new Timeline(
            new KeyFrame(Duration.millis(200),
                new KeyValue(Sidebar.prefWidthProperty(), Target),
                new KeyValue(Sidebar.maxWidthProperty(), Target)
            )
        );
        Anim.play();

        BrandText.setVisible(Expanding);
        BrandText.setManaged(Expanding);

        NavItemMap.forEach((Name, Item) -> {
            if (Name.startsWith("__sec__")) return;
            if (Item.getChildren().size() < 2) return;
            Node NameNode = Item.getChildren().get(1);
            NameNode.setVisible(Expanding);
            NameNode.setManaged(Expanding);
        });

        Sidebar.getChildren().stream()
            .filter(C -> C instanceof Label L && L.getStyleClass().contains("sidebar-section"))
            .forEach(C -> { C.setVisible(Expanding); C.setManaged(Expanding); });
    }

    private static boolean IsActive(HBox Item) {
        return Item.getStyle().contains("#200808") || Item.getStyle().contains(Palette.Red);
    }

    private static Region BuildFlexSpacer() {
        Region S = new Region();
        VBox.setVgrow(S, Priority.ALWAYS);
        return S;
    }
}
