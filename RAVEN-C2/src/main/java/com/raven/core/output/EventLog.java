package com.raven.core.output;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventLog {

    private static final DateTimeFormatter TimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<String> Entries;
    private final int MaxEntries;

    public EventLog(int MaxEntries) {
        this.MaxEntries = MaxEntries;
        this.Entries = new CopyOnWriteArrayList<>();
    }

    public void Add(String Message, boolean PrintNow) {
        String Entry = "[" + LocalDateTime.now().format(TimeFmt) + "] " + Message;
        Entries.add(Entry);
        if (Entries.size() > MaxEntries) Entries.remove(0);
        if (PrintNow) Logger.Info(Entry);
    }

    public void Add(String Message) {
        Add(Message, false);
    }

    public int Count() {
        return Entries.size();
    }

    public List<String> GetAll() {
        return new ArrayList<>(Entries);
    }

    public List<String> GetLast(int N) {
        List<String> All = GetAll();
        int Start = Math.max(0, All.size() - N);
        return All.subList(Start, All.size());
    }
}
