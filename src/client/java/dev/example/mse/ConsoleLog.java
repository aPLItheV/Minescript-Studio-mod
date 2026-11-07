package dev.example.mse;

import java.util.ArrayList;
import java.util.List;

/** Журнал событий редактора для консоли (хранится, пока запущена игра). */
final class ConsoleLog {
    private ConsoleLog() {}

    static final int INFO = 0, OK = 1, WARN = 2, ERROR = 3;
    private static final int MAX = 500;

    record Entry(long time, int level, String text) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static synchronized void add(int level, String text) {
        ENTRIES.add(new Entry(System.currentTimeMillis(), level, text));
        while (ENTRIES.size() > MAX) ENTRIES.remove(0);
    }

    static synchronized List<Entry> snapshot() {
        return new ArrayList<>(ENTRIES);
    }

    static synchronized int size() {
        return ENTRIES.size();
    }

    static synchronized void clear() {
        ENTRIES.clear();
    }
}
