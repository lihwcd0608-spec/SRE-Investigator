package com.maiya.investigator.graph.usage;

public final class UsageContext {
    private static final ThreadLocal<UsageSnapshot> CURRENT = new ThreadLocal<>();

    private UsageContext() {
    }

    public static void open(UsageSnapshot snapshot) {
        if (snapshot == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(snapshot);
    }

    public static UsageSnapshot current() {
        return CURRENT.get();
    }

    public static void close() {
        CURRENT.remove();
    }
}
