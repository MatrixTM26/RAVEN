package com.raven.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class TerminalHelper {

    private static final int DEFAULT_WIDTH = 80;
    private static final int MIN_WIDTH = 40;

    private static final AtomicInteger CachedWidth = new AtomicInteger(-1);
    private static volatile Thread PollerThread;

    private TerminalHelper() {}

    public static void StartPoller() {
        if (PollerThread != null && PollerThread.isAlive()) return;
        PollerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                CachedWidth.set(Detect());
                try {
                    Thread.sleep(250);
                } catch (InterruptedException Ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "TerminalWidthPoller");
        PollerThread.setDaemon(true);
        PollerThread.start();
    }

    public static int GetWidth() {
        String Env = System.getenv("COLUMNS");
        if (Env != null && !Env.isBlank()) {
            try {
                return Math.max(MIN_WIDTH, Integer.parseInt(Env.trim()));
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
            else new ProcessBuilder("clear").inheritIO().start().waitFor();
        } catch (Exception Ignored) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
        }
    }

    public static String OutputBox(String Output) {
        int Width = Math.max(34, ContentWidth());
        int Inner = Math.max(0, Width - 2);
        int LineWidth = Math.max(1, Inner - 2);
        int LabelFill = Math.max(0, Inner - 9);
        String Nl = "\n";
        String Indent = "  ";
        String Top = AnsiColor.Green + "┌─ Output " + "─".repeat(LabelFill) + "┐" + AnsiColor.Reset;
        String Bottom = AnsiColor.Green + "└" + "─".repeat(Inner) + "┘" + AnsiColor.Reset;
        StringBuilder Sb = new StringBuilder(Indent + Top + Nl);
        for (String Line : Output.split(Nl, -1)) {
            String S = Line.replaceAll("\u001B\\[[;\\d?]*[A-Za-z]|\u001B[=>]|\r", "");
            while (S.length() > LineWidth) {
                Sb.append(Indent).append(AnsiColor.Green).append("│ ").append(AnsiColor.White).append(S, 0, LineWidth).append(AnsiColor.Green).append(" │").append(AnsiColor.Reset).append(Nl);
                S = S.substring(LineWidth);
            }
            int Pad = Math.max(0, LineWidth - S.length());
            Sb.append(Indent).append(AnsiColor.Green).append("│ ").append(AnsiColor.White).append(S).append(" ".repeat(Pad)).append(AnsiColor.Green).append(" │").append(AnsiColor.Reset).append(Nl);
        }
        return Sb.append(Indent).append(Bottom).toString();
    }

    public static String Box(String Title) {
        int Width = ContentWidth();
        int Inner = Math.max(0, Width - 2);
        int PaddingLeft = Math.max(0, (Inner - Title.length()) / 2);
        int PaddingRight = Math.max(0, Inner - PaddingLeft - Title.length());
        String NL = "\n";
        String Indent = "  ";
        String Top = AnsiColor.White + "┌" + "─".repeat(Inner) + "┐" + AnsiColor.Reset;
        String Middle = AnsiColor.White + "│" + " ".repeat(PaddingLeft) + AnsiColor.Green + Title + " ".repeat(PaddingRight) + AnsiColor.White + "│" + AnsiColor.Reset;
        String Bottom = AnsiColor.White + "└" + "─".repeat(Inner) + "┘" + AnsiColor.Reset;
        return NL + Indent + Top + NL + Indent + Middle + NL + Indent + Bottom;
    }

    public static String Divider() {
        return "  " + AnsiColor.White + "─".repeat(ContentWidth()) + AnsiColor.Reset;
    }

    public static String Truncate(String S, int N) {
        return S != null && S.length() > N ? S.substring(0, N - 1) + "…" : S != null ? S : "";
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
        Integer W = IsWindows() ? DetectWindows() : DetectUnix();
        return W != null ? W : DEFAULT_WIDTH;
    }

    private static Integer DetectUnix() {
        try {
            Process P = new ProcessBuilder("sh", "-c", "stty size < /dev/tty").redirectErrorStream(true).start();
            String Out = new String(P.getInputStream().readAllBytes()).trim();
            P.waitFor();
            String[] Parts = Out.split("\\s+");
            if (Parts.length >= 2) return Math.max(MIN_WIDTH, Integer.parseInt(Parts[1]));
        } catch (Exception Ignored) {}
        try {
            Process P = new ProcessBuilder("sh", "-c", "tput cols < /dev/tty").redirectErrorStream(true).start();
            String Out = new String(P.getInputStream().readAllBytes()).trim();
            P.waitFor();
            if (!Out.isBlank()) return Math.max(MIN_WIDTH, Integer.parseInt(Out));
        } catch (Exception Ignored) {}
        return null;
    }

    private static Integer DetectWindows() {
        try {
            ProcessBuilder Pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "[Console]::WindowWidth");
            Pb.redirectErrorStream(true);
            Pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            Process P = Pb.start();
            String Out = new String(P.getInputStream().readAllBytes()).trim();
            boolean Timeout = P.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!Timeout) {
                P.destroyForcibly();
            } else if (!Out.isBlank()) {
                String Digits = Out.replaceAll("\\D+", "");
                if (!Digits.isBlank()) {
                    int V = Integer.parseInt(Digits);
                    if (V >= 20 && V <= 1000) return Math.max(MIN_WIDTH, V);
                }
            }
        } catch (Exception Ignored) {}
        try {
            Process P = new ProcessBuilder("cmd.exe", "/c", "mode con").redirectErrorStream(true).start();
            String Out = new String(P.getInputStream().readAllBytes());
            P.waitFor(1500, TimeUnit.MILLISECONDS);
            for (String L : Out.split("\\r?\\n")) {
                String Lower = L.toLowerCase();
                if (Lower.contains("column") || Lower.contains("kolom")) {
                    String Digits = L.replaceAll("[^0-9]", "");
                    if (!Digits.isBlank()) {
                        int V = Integer.parseInt(Digits);
                        if (V >= 20 && V <= 1000) return Math.max(MIN_WIDTH, V);
                    }
                }
            }
        } catch (Exception Ignored) {}
        return null;
    }
}
