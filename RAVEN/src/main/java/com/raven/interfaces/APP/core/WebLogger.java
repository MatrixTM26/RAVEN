package com.raven.interfaces.APP.core;

import com.raven.core.database.TeamDatabase;
import com.raven.utils.RavenConstants;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WebLogger {

    
    private final List<String> Entries;
    private final int MaxEntries;
    private final TeamDatabase Database;

    public WebLogger(int MaxEntries, TeamDatabase Database) {
        this.MaxEntries = MaxEntries;
        this.Database = Database;
        this.Entries = new CopyOnWriteArrayList<>();
    }

    public void Add(String Message) {
        String Entry = "  [" + LocalDateTime.now().format(RavenConstants.ChatTimeFmt) + "] " + Message;
        Entries.add(Entry);
        if (Entries.size() > MaxEntries) Entries.remove(0);
        Database.SaveLog(Entry);
    }

    public List<String> GetAll() {
        return new ArrayList<>(Entries);
    }

    public void Clear() {
        Entries.clear();
    }
}
