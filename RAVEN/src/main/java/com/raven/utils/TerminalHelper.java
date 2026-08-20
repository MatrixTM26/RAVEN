package com.raven.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class TerminalHelper {

    private static final int DefaultWidth = 100;
    private static final int MinWidth     = 80;

    private static final AtomicInteger CachedWidth = new AtomicInteger(-1);
    private static volatile Thread     PollerThread;

    private TerminalHelper() {}

    public static void StartPoller() {
        if (PollerThread != null && PollerThread.isAlive()) return;
        PollerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                CachedWidth.set(Detect());
                try {
                    Thread.sleep(250);
                } catch (InterruptedException Exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "TerminalWidthPoller");
        PollerThread.setDaemon(true);
        PollerThread.start();
    }

    public static int GetWidth() {
        String ColumnsEnv = System.getenv("COLUMNS");
        if (ColumnsEnv != null && !ColumnsEnv.isBlank()) {
            try {
                return Math.max(MinWidth, Integer.parseInt(ColumnsEnv.trim()));
            } catch (NumberFormatException Ignored) {}
        }
        if (PollerThread == null || !PollerThread.isAlive()) StartPoller();
        int Cached = CachedWidth.get();
        if (Cached > 0) return Cached;
        int Detected = Detect();
        CachedWidth.set(Detected);
        return Detected;
    }

    public static void Clear() {
        try {
            if (IsWindows()) new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            else             new ProcessBuilder("clear").inheritIO().start().waitFor();
        } catch (Exception Ignored) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        }
    }

    public static String OutputBox(String Output) {
        int Width     = Math.max(34, ContentWidth());
        int Inner     = Math.max(0, Width - 2);
        int LineWidth = Math.max(1, Inner - 2);
        int LabelFill = Math.max(0, Inner - 9);
        String Newline = "\n";
        String Indent  = "  ";
        String Top    = AnsiColor.Green + "┌─ Output " + "─".repeat(LabelFill) + "┐" + AnsiColor.Reset;
        String Bottom = AnsiColor.Green + "└" + "─".repeat(Inner) + "┘" + AnsiColor.Reset;
        StringBuilder Builder = new StringBuilder(Indent + Top + Newline);
        for (String RawLine : Output.split(Newline, -1)) {
            String Clean = RawLine.replaceAll("\u001B\\[[;\\d?]*[A-Za-z]|\u001B[=>]|\r", "");
            while (Clean.length() > LineWidth) {
                Builder.append(Indent).append(AnsiColor.Green).append("│ ")
                       .append(AnsiColor.White).append(Clean, 0, LineWidth)
                       .append(AnsiColor.Green).append(" │").append(AnsiColor.Reset).append(Newline);
                Clean = Clean.substring(LineWidth);
            }
            int Padding = Math.max(0, LineWidth - Clean.length());
            Builder.append(Indent).append(AnsiColor.Green).append("│ ")
                   .append(AnsiColor.White).append(Clean).append(" ".repeat(Padding))
                   .append(AnsiColor.Green).append(" │").append(AnsiColor.Reset).append(Newline);
        }
        return Builder.append(Indent).append(Bottom).toString();
    }

    public static String Box(String Title) {
        int Width        = ContentWidth();
        int Inner        = Math.max(0, Width - 2);
        int PaddingLeft  = Math.max(0, (Inner - Title.length()) / 2);
        int PaddingRight = Math.max(0, Inner - PaddingLeft - Title.length());
        String Newline = "\n";
        String Indent  = "  ";
        String Top    = AnsiColor.White + "┌" + "─".repeat(Inner) + "┐" + AnsiColor.Reset;
        String Middle = AnsiColor.White + "│" + " ".repeat(PaddingLeft) + AnsiColor.Green + Title + " ".repeat(PaddingRight) + AnsiColor.White + "│" + AnsiColor.Reset;
        String Bottom = AnsiColor.White + "└" + "─".repeat(Inner) + "┘" + AnsiColor.Reset;
        return Newline + Indent + Top + Newline + Indent + Middle + Newline + Indent + Bottom;
    }

    public static String Divider() {
        return "  " + AnsiColor.White + "─".repeat(ContentWidth()) + AnsiColor.Reset;
    }

    public static String Truncate(String Text, int MaxLength) {
        return Text != null && Text.length() > MaxLength ? Text.substring(0, MaxLength - 1) + "…" : Text != null ? Text : "";
    }

    public static int ContentWidth() {
        return Math.max(36, GetWidth() - 4);
    }

    public static String Indent(String Text) {
        return "  " + Text;
    }

    private static boolean IsWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static int Detect() {
        if (IsWindows()) {
            Integer Width = DetectWindowsViaPowerShell();
            if (Width != null) return Width;
            Width = DetectWindowsViaModeCon();
            return Width != null ? Width : DefaultWidth;
        }
        Integer Width = DetectUnixViaStty();
        if (Width != null) return Width;
        Width = DetectUnixViaTput();
        return Width != null ? Width : DefaultWidth;
    }

    private static Integer DetectUnixViaStty() {
        try {
            ProcessBuilder ProcessSetup = new ProcessBuilder("sh", "-c", "stty size < /dev/tty");
            ProcessSetup.redirectErrorStream(true);
            Process DetectionProcess = ProcessSetup.start();
            String Output = new String(DetectionProcess.getInputStream().readAllBytes()).trim();
            DetectionProcess.waitFor();
            String[] Parts = Output.split("\\s+");
            if (Parts.length >= 2) return Math.max(MinWidth, Integer.parseInt(Parts[1]));
        } catch (Exception Ignored) {}
        return null;
    }

    private static Integer DetectUnixViaTput() {
        try {
            ProcessBuilder ProcessSetup = new ProcessBuilder("sh", "-c", "tput cols < /dev/tty");
            ProcessSetup.redirectErrorStream(true);
            Process DetectionProcess = ProcessSetup.start();
            String Output = new String(DetectionProcess.getInputStream().readAllBytes()).trim();
            DetectionProcess.waitFor();
            if (!Output.isBlank()) return Math.max(MinWidth, Integer.parseInt(Output));
        } catch (Exception Ignored) {}
        return null;
    }

    private static Integer DetectWindowsViaPowerShell() {
        try {
            ProcessBuilder ProcessSetup = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "[Console]::WindowWidth"
            );
            ProcessSetup.redirectErrorStream(true);
            ProcessSetup.redirectInput(ProcessBuilder.Redirect.INHERIT);
            Process DetectionProcess = ProcessSetup.start();
            String Output = new String(DetectionProcess.getInputStream().readAllBytes()).trim();
            boolean Finished = DetectionProcess.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!Finished) { DetectionProcess.destroyForcibly(); return null; }
            if (!Output.isBlank()) {
                String Digits = Output.replaceAll("\\D+", "");
                if (!Digits.isBlank()) {
                    int Width = Integer.parseInt(Digits);
                    if (Width >= 20 && Width <= 1000) return Math.max(MinWidth, Width);
                }
            }
        } catch (Exception Ignored) {}
        return null;
    }

    private static Integer DetectWindowsViaModeCon() {
        try {
            ProcessBuilder ProcessSetup = new ProcessBuilder("cmd.exe", "/c", "mode con");
            ProcessSetup.redirectErrorStream(true);
            Process DetectionProcess = ProcessSetup.start();
            String Output = new String(DetectionProcess.getInputStream().readAllBytes());
            DetectionProcess.waitFor(1500, TimeUnit.MILLISECONDS);
            for (String Line : Output.split("\\r?\\n")) {
                String Lower = Line.toLowerCase();
                if (Lower.contains("column") || Lower.contains("kolom")) {
                    String Digits = Line.replaceAll("[^0-9]", "");
                    if (!Digits.isBlank()) {
                        int Width = Integer.parseInt(Digits);
                        if (Width >= 20 && Width <= 1000) return Math.max(MinWidth, Width);
                    }
                }
            }
        } catch (Exception Ignored) {}
        return null;
    }
}
