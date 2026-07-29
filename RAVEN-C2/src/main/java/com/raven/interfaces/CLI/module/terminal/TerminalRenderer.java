package com.raven.interfaces.CLI.module.terminal;

import com.raven.utils.AnsiColor;

public final class TerminalRenderer {

    private static final String FrameIndent       = "  ";
    private static final String HorizontalLine    = "─";
    private static final String VerticalLine      = "│";
    private static final String CornerTopLeft     = "┌";
    private static final String CornerTopRight    = "┐";
    private static final String CornerBottomLeft  = "└";
    private static final String CornerBottomRight = "┘";
    private static final String Space             = " ";
    private static final String NewLine           = "\n";
    private static final String OutputBoxLabel    = "─ Output ";

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
        return Indent(AnsiColor.White + HorizontalLine.repeat(ContentWidth()) + AnsiColor.Reset);
    }

    public String Box(String Title) {
        int Width        = ContentWidth();
        int Inner        = Math.max(0, Width - 2);
        int PaddingLeft  = Math.max(0, (Inner - Title.length()) / 2);
        int PaddingRight = Math.max(0, Inner - PaddingLeft - Title.length());

        String Top    = AnsiColor.White
                      + CornerTopLeft + HorizontalLine.repeat(Inner) + CornerTopRight
                      + AnsiColor.Reset;
        String Middle = AnsiColor.White + VerticalLine
                      + Space.repeat(PaddingLeft)
                      + AnsiColor.Green + Title
                      + Space.repeat(PaddingRight)
                      + AnsiColor.White + VerticalLine
                      + AnsiColor.Reset;
        String Bottom = AnsiColor.White
                      + CornerBottomLeft + HorizontalLine.repeat(Inner) + CornerBottomRight
                      + AnsiColor.Reset;

        return NewLine + Indent(Top) + NewLine + Indent(Middle) + NewLine + Indent(Bottom);
    }

    public String OutputBox(String Output) {
        int Width     = Math.max(34, ContentWidth());
        int Inner     = Math.max(0, Width - 2);
        int LineWidth = Math.max(1, Inner - 2);
        int LabelFill = Math.max(0, Inner - OutputBoxLabel.length());

        String Top    = AnsiColor.Green
                      + CornerTopLeft + OutputBoxLabel + HorizontalLine.repeat(LabelFill) + CornerTopRight
                      + AnsiColor.Reset;
        String Bottom = AnsiColor.Green
                      + CornerBottomLeft + HorizontalLine.repeat(Inner) + CornerBottomRight
                      + AnsiColor.Reset;

        StringBuilder Builder = new StringBuilder(Indent(Top) + NewLine);

        for (String Line : Output.split(NewLine, -1)) {
            String Stripped = Line.replaceAll("\u001B\\[[;\\d?]*[A-Za-z]|\u001B[=>]|\r", "");
            while (Stripped.length() > LineWidth) {
                Builder.append(Indent(
                    AnsiColor.Green + VerticalLine + Space
                    + AnsiColor.White + Stripped.substring(0, LineWidth)
                    + AnsiColor.Green + Space + VerticalLine
                    + AnsiColor.Reset + NewLine
                ));
                Stripped = Stripped.substring(LineWidth);
            }
            int Padding = Math.max(0, LineWidth - Stripped.length());
            Builder.append(Indent(
                AnsiColor.Green + VerticalLine + Space
                + AnsiColor.White + Stripped + Space.repeat(Padding)
                + AnsiColor.Green + Space + VerticalLine
                + AnsiColor.Reset + NewLine
            ));
        }

        return Builder.append(Indent(Bottom)).toString();
    }
}
