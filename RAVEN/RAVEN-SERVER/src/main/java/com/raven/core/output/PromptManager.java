package com.raven.core.output;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PromptManager {

    private static volatile String  ActivePrompt = "";
    private static final AtomicBoolean PromptVisible = new AtomicBoolean(false);

    private PromptManager() {}

    public static void SetPrompt(String FullPrompt) {
        ActivePrompt = FullPrompt;
    }

    public static void MarkVisible(boolean Visible) {
        PromptVisible.set(Visible);
    }

    public static synchronized void PrintLine(String Line) {
        if (PromptVisible.get()) {
            System.out.print("\r\033[2K");
            System.out.println(Line);
            System.out.print(ActivePrompt);
            System.out.flush();
        } else {
            System.out.println(Line);
        }
    }
}
