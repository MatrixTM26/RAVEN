package com.raven.core.output;

import com.raven.utils.RavenConstants;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventLog {

    private final List<String> Entries;
    private final int MaxEntries;

    public EventLog(int MaxEntries) {
        this.MaxEntries = MaxEntries;
        this.Entries    = new CopyOnWriteArrayList<>();
    }

    public void Add(String Level, String Message, boolean PrintNow) {
        String Entry = "[" + LocalDateTime.now().format(RavenConstants.TimestampFmt) + "] [" + Level + "] " + Message;
        Entries.add(Entry);
        if (Entries.size() > MaxEntries) Entries.remove(0);
        if (PrintNow) {
            switch (Level) {
                case "WARN"  -> Logger.Warn(Entry);
                case "ERROR" -> Logger.Error(Entry);
                default      -> Logger.Info(Entry);
            }
        }
    }

    public void Add(String Level, String Message) {
        Add(Level, Message, false);
    }

    public void Add(String Message, boolean PrintNow) {
        Add("INFO", Message, PrintNow);
    }

    public void Add(String Message) {
        Add("INFO", Message, false);
    }

    public int Count() {
        return Entries.size();
    }

    public List<String> GetAll() {
        return new ArrayList<>(Entries);
    }

    public List<String> GetLast(int Count) {
        List<String> All   = GetAll();
        int StartIndex     = Math.max(0, All.size() - Count);
        return All.subList(StartIndex, All.size());
    }
}
