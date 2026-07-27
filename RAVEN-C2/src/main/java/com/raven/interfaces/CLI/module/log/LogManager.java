package com.raven.interfaces.CLI.module.log;

import com.raven.interfaces.CLI.module.terminal.TerminalRenderer;
import com.raven.utils.AnsiColor;
import com.raven.core.output.Logger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LogManager {

    private static final DateTimeFormatter TimestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<String>     Entries;
    private final int              MaxEntries;
    private final TerminalRenderer Renderer;

    public LogManager(int MaxEntries, TerminalRenderer Renderer) {
        this.MaxEntries = MaxEntries;
        this.Renderer   = Renderer;
        this.Entries    = new CopyOnWriteArrayList<>();
    }

    public void Add(String Message, boolean PrintNow) {
        String Timestamp = LocalDateTime.now().format(TimestampFormat);
        String Entry     = "[" + Timestamp + "]" + Message;
        Entries.add(Entry);
        if (Entries.size() > MaxEntries) Entries.remove(0);
        if (PrintNow) Logger.Info(Entry);
    }

    public int Count() {
        return Entries.size();
    }

    public void Show() {
        System.out.println(Renderer.Box("RECENT LOGS"));
        System.out.println();
        if (Entries.isEmpty()) {
            Logger.Info(Renderer.Indent("  no logs") + "\n");
            return;
        }
        int Start = Math.max(0, Entries.size() - 25);
        for (int Index = Start; Index < Entries.size(); Index++) {
            Logger.Info(Renderer.Indent(Entries.get(Index)));
        }
        System.out.println();
    }
}
