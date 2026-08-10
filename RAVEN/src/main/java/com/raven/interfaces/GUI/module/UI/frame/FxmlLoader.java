package com.raven.interfaces.GUI.module.UI.frame;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;
import java.net.URL;

public final class FxmlLoader {

    private FxmlLoader() {}

    public static <T extends Node> LoadResult<T> Load(String FxmlName) {
        String Path = "/com/raven/interfaces/GUI/fxml/" + FxmlName;
        URL Url = FxmlLoader.class.getResource(Path);
        if (Url == null)
            throw new IllegalStateException("FXML not found: " + Path);
        FXMLLoader Loader = new FXMLLoader(Url);
        try {
            T Root = Loader.load();
            return new LoadResult<>(Root, Loader.getController());
        } catch (IOException E) {
            throw new RuntimeException("Failed to load FXML: " + Path, E);
        }
    }

    public record LoadResult<T extends Node>(T Root, Object Controller) {
        @SuppressWarnings("unchecked")
        public <C> C GetController() { return (C) Controller; }
    }
}
