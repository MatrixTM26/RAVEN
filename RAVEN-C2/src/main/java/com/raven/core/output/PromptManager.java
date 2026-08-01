package com.raven.core.output;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PromptManager {

    private static final String CLEAR_LINE = "\r\033[2K";

    private static volatile String ActivePrompt = "";
    private static final AtomicBoolean PromptVisible = new AtomicBoolean(false);

    private PromptManager() {}

    public static void SetPrompt(String Prompt) {
        ActivePrompt = Prompt;
    }

    public static void MarkVisible(boolean Visible) {
        PromptVisible.set(Visible);
    }

    public static void PrintAbove(String Line) {
        if (PromptVisible.get()) {
            System.out.print(CLEAR_LINE);
        }
        System.out.println(Line);
        if (PromptVisible.get()) {
            System.out.print(ActivePrompt);
            System.out.flush();
        }
    }
}
