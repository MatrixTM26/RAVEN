package com.raven.core.output;

import com.raven.utils.AnsiColor;
import java.io.*;
import java.nio.file.*;
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

    private static void Emit(Level MessageLevel, String PlainTag, String ColorCode, String Message) {
        if (MessageLevel.ordinal() < CurrentLevel.ordinal()) return;
        String Times = Timestamp();
        String PlainLine = "  [" + Times + "] [" + PlainTag + "] " + Message;
        String ColorLine = AnsiColor.White + "  [" + ColorCode + PlainTag + AnsiColor.White + "] " + AnsiColor.Dim + Message + AnsiColor.Reset;
        System.out.println(ColorLine);
        if (FileEnabled) FileQueue.offer(PlainLine);
    }

    public static void Info(String Message) {
        Emit(Level.INFO, "INFO", AnsiColor.Cyan, Message);
    }

    public static void Warn(String Message) {
        Emit(Level.WARN, "WARN", AnsiColor.Orange, Message);
    }

    public static void Error(String Message) {
        Emit(Level.ERROR, "ERROR", AnsiColor.BrightRed, Message);
    }

    public static void Debug(String Message) {
        Emit(Level.DEBUG, "DEBUG", AnsiColor.Magenta, Message);
    }

    public static void Success(String Message) {
        Emit(Level.INFO, "OK", AnsiColor.Green, Message);
    }

    public static void Custom(String Text) {
        System.out.println(Text);
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

    public static void Verbose(String Message) {
        if (!Verbose) return;
        Emit(Level.VERBOSE, "TRACE", AnsiColor.Dim, Message);
    }

    public static void Messages(String Message) {
        Info(Message);
    }

    public static void Warnings(String Message) {
        Warn(Message);
    }

    public static void ErrorMessage(String Message) {
        Error(Message);
    }

    public static void Warning(String Message) {
        Warn(Message);
    }

    public static void Shutdown() {
        if (WriterThread != null) WriterThread.interrupt();
    }
}
