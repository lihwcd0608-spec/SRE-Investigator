package com.maiya.investigator.boot;

import com.maiya.investigator.domain.model.SettingStore;
import com.maiya.investigator.graph.AgentProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class AgentBudgetService implements ApplicationRunner {
    static final String TURNS_KEY = "agent.max-user-turns";
    static final String TOOLS_KEY = "agent.max-tool-calls";

    private final AgentProperties agent;
    private final SettingStore store;
    private final int yamlUserTurns;
    private final int yamlToolCalls;

    public AgentBudgetService(AgentProperties agent, SettingStore store) {
        this.agent = agent;
        this.store = store;
        this.yamlUserTurns = agent.getMaxUserTurns();
        this.yamlToolCalls = agent.getMaxToolCalls();
    }

    @Override
    public void run(ApplicationArguments args) {
        applyStored();
    }

    public Snapshot snapshot() {
        applyStored();
        return new Snapshot(agent.getMaxUserTurns(), agent.getMaxToolCalls(), yamlUserTurns, yamlToolCalls);
    }

    public Snapshot update(Integer maxUserTurns, Integer maxToolCalls) {
        if (maxUserTurns != null) {
            store.put(TURNS_KEY, String.valueOf(Math.max(1, maxUserTurns)));
        }
        if (maxToolCalls != null) {
            store.put(TOOLS_KEY, String.valueOf(Math.max(1, maxToolCalls)));
        }
        applyStored();
        return snapshot();
    }

    private void applyStored() {
        agent.setMaxUserTurns(readInt(TURNS_KEY, yamlUserTurns));
        agent.setMaxToolCalls(readInt(TOOLS_KEY, yamlToolCalls));
    }

    private int readInt(String key, int fallback) {
        return store.get(key)
                .map(value -> {
                    try {
                        return Integer.parseInt(value.trim());
                    } catch (NumberFormatException ignored) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    public record Snapshot(int maxUserTurns, int maxToolCalls, int defaultUserTurns, int defaultToolCalls) {
    }
}
