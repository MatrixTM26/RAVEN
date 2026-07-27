package com.raven.interfaces.CLI.module.terminal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class TerminalWidthDetector {

    private static final int DefaultWidth = 80;
    private static final int MinWidth     = 40;

    private final AtomicInteger CachedWidth = new AtomicInteger(-1);
    private Thread              PollerThread;

    public void StartPoller() {
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

    public int GetWidth() {
        String ColumnsEnvironment = System.getenv("COLUMNS");
        if (ColumnsEnvironment != null && !ColumnsEnvironment.isBlank()) {
            try {
                return Math.max(MinWidth, Integer.parseInt(ColumnsEnvironment.trim()));
            } catch (NumberFormatException Ignored) {}
        }

        if (PollerThread == null || !PollerThread.isAlive()) StartPoller();

        int Cached = CachedWidth.get();
        if (Cached > 0) return Cached;

        int Detected = Detect();
        CachedWidth.set(Detected);
        return Detected;
    }

    private int Detect() {
        String OperatingSystem = System.getProperty("os.name", "").toLowerCase();
        if (OperatingSystem.contains("win")) {
            Integer Width = DetectWindows();
            return Width != null ? Width : DefaultWidth;
        }
        Integer Width = DetectUnix();
        return Width != null ? Width : DefaultWidth;
    }

    private Integer DetectUnix() {
        Integer Width = DetectUnixViaStty();
        if (Width != null) return Width;
        return DetectUnixViaTput();
    }

    private Integer DetectUnixViaStty() {
        try {
            ProcessBuilder ProcessSetup = new ProcessBuilder("sh", "-c", "stty size < /dev/tty");
            ProcessSetup.redirectErrorStream(true);
            Process RunningProcess = ProcessSetup.start();
            String Output = new String(RunningProcess.getInputStream().readAllBytes()).trim();
            RunningProcess.waitFor();
            String[] Parts = Output.split("\\s+");
            if (Parts.length >= 2) return Math.max(MinWidth, Integer.parseInt(Parts[1]));
        } catch (Exception Ignored) {}
        return null;
    }

    private Integer DetectUnixViaTput() {
        try {
            ProcessBuilder ProcessSetup = new ProcessBuilder("sh", "-c", "tput cols < /dev/tty");
            ProcessSetup.redirectErrorStream(true);
            Process RunningProcess = ProcessSetup.start();
            String Output = new String(RunningProcess.getInputStream().readAllBytes()).trim();
            RunningProcess.waitFor();
            if (!Output.isBlank()) return Math.max(MinWidth, Integer.parseInt(Output));
        } catch (Exception Ignored) {}
        return null;
    }

    private Integer DetectWindows() {
        Integer Width = DetectWindowsViaPowerShell();
        if (Width != null) return Width;
        return DetectWindowsViaModeCon();
    }

    private Integer DetectWindowsViaPowerShell() {
        try {
            ProcessBuilder ProcessSetup = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-Command", "[Console]::WindowWidth"
            );
            ProcessSetup.redirectErrorStream(true);
            ProcessSetup.redirectInput(ProcessBuilder.Redirect.INHERIT);
            Process RunningProcess = ProcessSetup.start();
            String Output = new String(RunningProcess.getInputStream().readAllBytes()).trim();
            boolean Finished = RunningProcess.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!Finished) { RunningProcess.destroyForcibly(); return null; }
            if (!Output.isBlank()) {
                String Digits = Output.replaceAll("\\D+", "");
                if (!Digits.isBlank()) {
                    int Value = Integer.parseInt(Digits);
                    if (Value >= 20 && Value <= 1000) return Math.max(MinWidth, Value);
                }
            }
        } catch (Exception Ignored) {}
        return null;
    }

    private Integer DetectWindowsViaModeCon() {
        try {
            ProcessBuilder ProcessSetup = new ProcessBuilder("cmd.exe", "/c", "mode con");
            ProcessSetup.redirectErrorStream(true);
            Process RunningProcess = ProcessSetup.start();
            String Output = new String(RunningProcess.getInputStream().readAllBytes());
            boolean Finished = RunningProcess.waitFor(1500, TimeUnit.MILLISECONDS);
            if (!Finished) { RunningProcess.destroyForcibly(); return null; }
            for (String Line : Output.split("\\r?\\n")) {
                String Lower = Line.toLowerCase();
                if (Lower.contains("column") || Lower.contains("kolom")) {
                    String Digits = Line.replaceAll("[^0-9]", "");
                    if (!Digits.isBlank()) {
                        int Value = Integer.parseInt(Digits);
                        if (Value >= 20 && Value <= 1000) return Math.max(MinWidth, Value);
                    }
                }
            }
        } catch (Exception Ignored) {}
        return null;
    }
}
