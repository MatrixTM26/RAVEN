package com.raven.interfaces.CLI.module.log;

import com.raven.core.output.EventLog;
import com.raven.core.output.Logger;
import com.raven.interfaces.CLI.module.terminal.TerminalRenderer;
import java.util.List;

public final class LogManager {

    private final EventLog Log;
    private final TerminalRenderer Renderer;

    public LogManager(int MaxEntries, TerminalRenderer Renderer) {
        this.Log = new EventLog(MaxEntries);
        this.Renderer = Renderer;
    }

    public void Add(String Message, boolean PrintNow) {
        Log.Add(Message, PrintNow);
    }

    public void Add(String Message) {
        Log.Add(Message, false);
    }

    public int Count() {
        return Log.Count();
    }

    public EventLog GetEventLog() {
        return Log;
    }

    public void Show() {
        System.out.println(Renderer.Box("RECENT LOGS"));
        System.out.println();
        List<String> Last = Log.GetLast(25);
        if (Last.isEmpty()) {
            Logger.Info(Renderer.Indent("no logs") + "\n");
            return;
        }
        for (String Entry : Last) {
            Logger.Info(Renderer.Indent(Entry));
        }
        System.out.println();
    }
}
