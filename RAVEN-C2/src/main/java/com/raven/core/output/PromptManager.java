package com.raven.core.output;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PromptManager {

    private static volatile String ActivePromptLine = "";
    private static final AtomicBoolean PromptVisible = new AtomicBoolean(false);

    private PromptManager() {}

    public static void SetPrompt(String FullPromptLine) {
        ActivePromptLine = FullPromptLine;
    }

    public static void MarkVisible(boolean Visible) {
        PromptVisible.set(Visible);
    }

    public static synchronized void PrintLine(String Line) {
        if (PromptVisible.get()) {
            System.out.println();
            System.out.println(Line);
            System.out.print(ActivePromptLine);
            System.out.flush();
        } else {
            System.out.println(Line);
        }
    }
}
