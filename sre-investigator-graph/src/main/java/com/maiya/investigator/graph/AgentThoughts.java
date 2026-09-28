package com.maiya.investigator.graph;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class AgentThoughts {
    private static final Consumer<AgentThought> NOOP = thought -> {
    };
    private static final ThreadLocal<Consumer<AgentThought>> SINK = new InheritableThreadLocal<>();
    private static final ThreadLocal<Integer> TOKEN = new InheritableThreadLocal<>();
    private static final ConcurrentHashMap<Integer, Consumer<AgentThought>> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicInteger SEQ = new AtomicInteger();

    private AgentThoughts() {
    }

    public static void open(Consumer<AgentThought> sink) {
        Consumer<AgentThought> safe = sink == null ? NOOP : thought -> {
            try {
                sink.accept(thought);
            } catch (RuntimeException ignored) {
                // SSE 断开不应打断工具调用。
            }
        };
        int token = SEQ.incrementAndGet();
        TOKEN.set(token);
        SINK.set(safe);
        ACTIVE.put(token, safe);
    }

    public static void close() {
        Integer token = TOKEN.get();
        if (token != null) {
            ACTIVE.remove(token);
        }
        TOKEN.remove();
        SINK.remove();
    }

    public static Consumer<AgentThought> sink() {
        Consumer<AgentThought> local = SINK.get();
        if (local != null) {
            return local;
        }
        if (ACTIVE.size() == 1) {
            return ACTIVE.values().iterator().next();
        }
        return NOOP;
    }

    public static void emit(String kind, String message) {
        String text = clip(message, 160);
        if (text.isBlank()) {
            return;
        }
        sink().accept(AgentThought.of(kind, text));
    }

    static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= max) {
            return compact;
        }
        return compact.substring(0, max) + "…";
    }
}
