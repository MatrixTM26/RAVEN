package com.raven.core.output;

import com.raven.utils.AnsiColor;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class Logger {

    public enum Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private static final DateTimeFormatter TimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile Level CurrentLevel = Level.INFO;
    private static volatile boolean Verbose = false;
    private static volatile boolean FileEnabled = false;
    private static volatile String LogFilePath = "logs/raven.log";
    private static volatile int MaxEntries = 1000;
    private static final BlockingQueue<String> FileQueue = new ArrayBlockingQueue<>(4096);
    private static Thread WriterThread;

    private Logger() {}

    public static void Configure(String Level, boolean IsVerbose, boolean EnableFile, String FilePath, int MaxEnt) {
        CurrentLevel = ParseLevel(Level);
        Verbose = IsVerbose;
        FileEnabled = EnableFile;
        LogFilePath = FilePath;
        MaxEntries = MaxEnt;
        if (EnableFile) StartFileWriter(FilePath);
    }

    private static Level ParseLevel(String LogLevel) {
        try {
            return Level.valueOf(LogLevel.toUpperCase());
        } catch (Exception E) {
            return Level.INFO;
        }
    }

    private static void StartFileWriter(String Path) {
        try {
            Files.createDirectories(Paths.get(Path).getParent());
        } catch (Exception Ignored) {}
        WriterThread = new Thread(() -> {
            try (BufferedWriter StrObject = new BufferedWriter(new FileWriter(Path, true))) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        StrObject.write(FileQueue.take());
                        StrObject.newLine();
                        StrObject.flush();
                    } catch (InterruptedException E) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException E) {
                System.err.println("[Logger] File writer failed: " + E.getMessage());
            }
        }, "LogFileWriter");
        WriterThread.setDaemon(true);
        WriterThread.start();
    }

    private static String Timestamp() {
        return LocalDateTime.now().format(TimeFmt);
    }

    private static String Format(String Message, Object[] Args) {
        if (Args == null || Args.length == 0) return Message;
        try {
            return String.format(Message, Args);
        } catch (java.util.IllegalFormatException E) {
            return Message + " [FORMAT_ERROR: " + E.getMessage() + "]";
        }
    }

    private static void Emit(Level MessageLevel, String PlainTag, String ColorCode, String Message, Object[] Args) {
        if (MessageLevel.ordinal() < CurrentLevel.ordinal()) return;
        String Formatted = Format(Message, Args);
        String Times = Timestamp();
        String PlainLine = "  [" + Times + "] [" + PlainTag + "] " + Formatted;
        String ColorLine = AnsiColor.White + "  [" + ColorCode + PlainTag + AnsiColor.White + "] " + AnsiColor.Dim + Formatted + AnsiColor.Reset;
        System.out.println(ColorLine);
        if (FileEnabled) FileQueue.offer(PlainLine);
    }

    public static void Info(String Message, Object... Args) {
        Emit(Level.INFO, "INFO", AnsiColor.Cyan, Message, Args);
    }

    public static void Warn(String Message, Object... Args) {
        Emit(Level.WARN, "WARN", AnsiColor.Orange, Message, Args);
    }

    public static void Error(String Message, Object... Args) {
        Emit(Level.ERROR, "ERROR", AnsiColor.BrightRed, Message, Args);
    }

    public static void Debug(String Message, Object... Args) {
        Emit(Level.DEBUG, "DEBUG", AnsiColor.Magenta, Message, Args);
    }

    public static void Success(String Message, Object... Args) {
        Emit(Level.INFO, "OK", AnsiColor.Green, Message, Args);
    }

    public static void Verbose(String Message, Object... Args) {
        if (!Verbose) return;
        Emit(Level.VERBOSE, "TRACE", AnsiColor.Dim, Message, Args);
    }

    public static void Custom(String Text) {
        System.out.println(Text);
    }

    public static void Custom(String Text, Object... Args) {
        System.out.print(Format(Text, Args));
        System.out.flush();
    }

    public static void Custom(String Text, long DelayMs) {
        System.out.println(Text);
        if (DelayMs > 0) {
            try {
                Thread.sleep(DelayMs);
            } catch (InterruptedException Ignored) {}
        }
    }

    public static void Custom(String Text, int DelayMs) {
        Custom(Text, (long) DelayMs);
    }

    public static void Messages(String Message, Object... Args) {
        Info(Message, Args);
    }

    public static void Warnings(String Message, Object... Args) {
        Warn(Message, Args);
    }

    public static void ErrorMessage(String Message, Object... Args) {
        Error(Message, Args);
    }

    public static void Warning(String Message, Object... Args) {
        Warn(Message, Args);
    }

    public static void Shutdown() {
        if (WriterThread != null) WriterThread.interrupt();
    }
}
