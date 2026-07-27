package com.raven.interfaces.CLI.module.terminal;

import com.raven.utils.AnsiColor;

public final class TerminalRenderer {

    private static final String FrameIndent = "  ";

    private final TerminalWidthDetector WidthDetector;

    public TerminalRenderer(TerminalWidthDetector WidthDetector) {
        this.WidthDetector = WidthDetector;
    }

    public int ContentWidth() {
        return Math.max(36, WidthDetector.GetWidth() - FrameIndent.length() - 2);
    }

    public String Indent(String Text) {
        return FrameIndent + Text;
    }

    public String Divider() {
        return Indent(AnsiColor.White + "─".repeat(ContentWidth()) + AnsiColor.Reset);
    }

    public String Box(String Title) {
        int Width      = ContentWidth();
        int Inner      = Math.max(0, Width - 2);
        int PaddingLeft  = Math.max(0, (Inner - Title.length()) / 2);
        int PaddingRight = Math.max(0, Inner - PaddingLeft - Title.length());

        String Top    = AnsiColor.White + "┌" + "─".repeat(Inner) + "┐" + AnsiColor.Reset;
        String Middle = AnsiColor.White + "│" + " ".repeat(PaddingLeft) +
                        AnsiColor.Green + Title + " ".repeat(PaddingRight) +
                        AnsiColor.White + "│" + AnsiColor.Reset;
        String Bottom = AnsiColor.White + "└" + "─".repeat(Inner) + "┘" + AnsiColor.Reset;

        return "\n" + Indent(Top) + "\n" + Indent(Middle) + "\n" + Indent(Bottom);
    }

    public String OutputBox(String Output) {
        int Width     = Math.max(34, ContentWidth());
        int Inner     = Math.max(0, Width - 2);
        int LineWidth = Math.max(1, Inner - 2);
        String Label  = "─ Output ";

        String Top    = AnsiColor.Green + "┌" + Label +
                        "─".repeat(Math.max(0, Inner - Label.length())) + "┐" + AnsiColor.Reset;
        String Bottom = AnsiColor.Green + "└" + "─".repeat(Inner) + "┘" + AnsiColor.Reset;

        StringBuilder Builder = new StringBuilder(Indent(Top) + "\n");

        for (String Line : Output.split("\n", -1)) {
            String Stripped = Line.replaceAll("\u001B\\[[;\\d?]*[A-Za-z]|\u001B[=>]|\r", "");
            while (Stripped.length() > LineWidth) {
                String Chunk = Stripped.substring(0, LineWidth);
                Builder.append(Indent(AnsiColor.Green + "│ " + AnsiColor.White +
                               Chunk + AnsiColor.Green + " │" + AnsiColor.Reset + "\n"));
                Stripped = Stripped.substring(LineWidth);
            }
            int Padding = Math.max(0, LineWidth - Stripped.length());
            Builder.append(Indent(AnsiColor.Green + "│ " + AnsiColor.White +
                           Stripped + " ".repeat(Padding) +
                           AnsiColor.Green + " │" + AnsiColor.Reset + "\n"));
        }

        return Builder.append(Indent(Bottom)).toString();
    }
}
