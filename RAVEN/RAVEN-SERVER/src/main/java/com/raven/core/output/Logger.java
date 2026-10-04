package com.raven.core.output;

import com.raven.utils.AnsiColor;
import com.raven.utils.RavenConstants;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class Logger {

    public enum Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    private static volatile Level   CurrentLevel = Level.INFO;
    private static volatile boolean Verbose      = false;
    private static volatile boolean FileEnabled  = false;
    private static volatile String  LogFilePath  = "logs/raven.log";
    private static volatile int     MaxEntries   = 1000;

    private static final BlockingQueue<String> FileQueue = new ArrayBlockingQueue<>(4096);
    private static Thread WriterThread;

    private Logger() {}

    public static void Configure(String LevelName, boolean IsVerbose, boolean EnableFile, String FilePath, int MaxEnt) {
        CurrentLevel = ParseLevel(LevelName);
        Verbose      = IsVerbose;
        FileEnabled  = EnableFile;
        LogFilePath  = FilePath;
        MaxEntries   = MaxEnt;
        if (EnableFile) StartFileWriter(FilePath);
    }

    private static Level ParseLevel(String LevelName) {
        try {
            return Level.valueOf(LevelName.toUpperCase());
        } catch (Exception Ignored) {
            return Level.INFO;
        }
    }

    private static void StartFileWriter(String Path) {
        java.nio.file.Path Parent = Paths.get(Path).getParent();
        if (Parent != null) {
            try { Files.createDirectories(Parent); } catch (Exception Ignored) {}
        }
        WriterThread = new Thread(() -> {
            try (BufferedWriter Writer = new BufferedWriter(new FileWriter(Path, true))) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Writer.write(FileQueue.take());
                        Writer.newLine();
                        Writer.flush();
                    } catch (InterruptedException InterruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException WriteException) {
                PromptManager.PrintLine(
                    AnsiColor.White + "[" + AnsiColor.Red + "ERROR" + AnsiColor.White + "] " +
                    "Log file writer failed: " + WriteException.getMessage() + AnsiColor.Reset);
            }
        }, "LogFileWriter");
        WriterThread.setDaemon(true);
        WriterThread.start();
    }

    private static String Timestamp() {
        return LocalDateTime.now().format(RavenConstants.TimestampFmt);
    }

    private static String Format(String Message, Object... Args) {
        if (Args == null || Args.length == 0) return Message.replace("%n", System.lineSeparator());
        try {
            return String.format(Message, Args);
        } catch (java.util.IllegalFormatException FormatException) {
            return Message + " [FORMAT_ERROR: " + FormatException.getMessage() + "]";
        }
    }

    private static void Emit(Level MessageLevel, String PlainTag, String LevelColor, String Message, Object[] Args) {
        if (MessageLevel.ordinal() < CurrentLevel.ordinal()) return;
        String Formatted  = Format(Message, Args);
        String ColorLine  = AnsiColor.White + "[" + LevelColor + PlainTag + AnsiColor.White + "] " + Formatted + AnsiColor.Reset;
        String PlainLine  = "[" + Timestamp() + "] [" + PlainTag + "] " + Formatted;
        PromptManager.PrintLine(ColorLine);
        if (FileEnabled) FileQueue.offer(PlainLine);
    }

    public static void Info(String Message, Object... Args) {
        Emit(Level.INFO, "INFO", AnsiColor.Blue, Message, Args);
    }

    public static void Warn(String Message, Object... Args) {
        Emit(Level.WARN, "WARN", AnsiColor.Yellow, Message, Args);
    }

    public static void Error(String Message, Object... Args) {
        Emit(Level.ERROR, "ERROR", AnsiColor.Red, Message, Args);
    }

    public static void Debug(String Message, Object... Args) {
        Emit(Level.DEBUG, "DEBUG", AnsiColor.Magenta, Message, Args);
    }

    public static void Success(String Message, Object... Args) {
        Emit(Level.INFO, "OK", AnsiColor.Green, Message, Args);
    }

    public static void Ok(String Message, Object... Args) {
        Emit(Level.INFO, "OK", AnsiColor.Green, Message, Args);
    }

    public static void Verbose(String Message, Object... Args) {
        if (!Verbose) return;
        Emit(Level.VERBOSE, "TRACE", AnsiColor.Dim, Message, Args);
    }

    public static void Custom(String Text) {
        System.out.print(Format(Text));
        System.out.flush();
    }

    public static void Custom(String Text, Object... Args) {
        System.out.print(Format(Text, Args));
        System.out.flush();
    }

    public static void Custom(String Text, long DelayMs) {
        System.out.print(Format(Text));
        System.out.flush();
        if (DelayMs > 0) {
            try { Thread.sleep(DelayMs); }
            catch (InterruptedException InterruptedException) { Thread.currentThread().interrupt(); }
        }
    }

    public static void Custom(String Text, int DelayMs) {
        Custom(Text, (long) DelayMs);
    }

    public static void Shutdown() {
        if (WriterThread != null) WriterThread.interrupt();
    }
}
