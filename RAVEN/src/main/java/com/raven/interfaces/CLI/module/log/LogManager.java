package com.raven.interfaces.CLI.module.log;

import com.raven.utils.TerminalHelper;
import com.raven.core.output.EventLog;
import com.raven.core.output.Logger;
import java.util.List;

public final class LogManager {

    private final EventLog Log;

    public LogManager(int MaxEntries) {
        this.Log = new EventLog(MaxEntries);
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
        System.out.println(TerminalHelper.Box("RECENT LOGS"));
        System.out.println();
        List<String> Last = Log.GetLast(25);
        if (Last.isEmpty()) {
            Logger.Info(TerminalHelper.Indent("no logs") + "\n");
            return;
        }
        for (String Entry : Last) {
            Logger.Info(TerminalHelper.Indent(Entry));
        }
        System.out.println();
    }
}
