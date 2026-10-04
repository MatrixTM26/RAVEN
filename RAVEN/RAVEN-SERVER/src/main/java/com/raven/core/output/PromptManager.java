package com.raven.core.output;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PromptManager {

    private static volatile String      ActivePrompt  = "";
    private static volatile int         PromptLineCount = 1;
    private static final AtomicBoolean  PromptVisible = new AtomicBoolean(false);

    private PromptManager() {}

    public static void SetPrompt(String FullPrompt) {
        ActivePrompt    = FullPrompt;
        PromptLineCount = (int) FullPrompt.chars().filter(Character -> Character == '\n').count() + 1;
    }

    public static void MarkVisible(boolean Visible) {
        PromptVisible.set(Visible);
    }

    public static boolean IsActive() {
        return PromptVisible.get();
    }

    public static synchronized void PrintLine(String Line) {
        if (PromptVisible.get()) {
            for (int LineIndex = 1; LineIndex < PromptLineCount; LineIndex++) {
                System.out.print("\033[1A");
            }
            System.out.print("\r\033[J");
            System.out.println(Line);
            System.out.print(ActivePrompt);
            System.out.flush();
        } else {
            System.out.println(Line);
        }
    }

    public static synchronized void Redraw() {
        System.out.print(ActivePrompt);
        System.out.flush();
    }

    public static synchronized void Erase() {
        for (int LineIndex = 1; LineIndex < PromptLineCount; LineIndex++) {
            System.out.print("\033[1A");
        }
        System.out.print("\r\033[J");
        System.out.flush();
    }
}
