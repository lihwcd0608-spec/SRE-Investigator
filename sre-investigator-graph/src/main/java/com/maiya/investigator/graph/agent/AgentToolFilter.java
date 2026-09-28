package com.maiya.investigator.graph.agent;

import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import java.util.ArrayList;
import java.util.List;

public final class AgentToolFilter {
    private AgentToolFilter() {
    }

    public static List<String> profiles(List<String> current, AgentDefinition agent, ToolKind kind) {
        List<String> copy = new ArrayList<>();
        if (current != null) {
            for (String item : current) {
                if (item != null && !item.isBlank() && !copy.contains(item)) {
                    copy.add(item);
                }
            }
        }
        if (agent == null || agent.allows(kind)) {
            return copy;
        }
        return new ArrayList<>();
    }

    public static List<ToolBinding> bindings(List<ToolBinding> current, AgentDefinition agent) {
        List<ToolBinding> copy = new ArrayList<>();
        if (current == null) {
            return copy;
        }
        for (ToolBinding item : current) {
            if (item == null) {
                continue;
            }
            if (agent == null || agent.allows(item.toolKind())) {
                copy.add(item);
            }
        }
        return copy;
    }

    public static List<String> stageProfiles(List<String> loaded, AgentFlow.Node node, ToolKind kind) {
        List<String> copy = new ArrayList<>();
        if (loaded != null) {
            for (String item : loaded) {
                if (item != null && !item.isBlank() && !copy.contains(item)) {
                    copy.add(item);
                }
            }
        }
        if (node == null || !node.usesKind(kind)) {
            return new ArrayList<>();
        }
        List<String> wanted = node.profilesForKind(kind);
        if (wanted.isEmpty()) {
            return copy;
        }
        List<String> matched = new ArrayList<>();
        for (String item : copy) {
            for (String profile : wanted) {
                if (item.equalsIgnoreCase(profile) && !matched.contains(item)) {
                    matched.add(item);
                }
            }
        }
        return matched;
    }
}
