package com.maiya.investigator.graph.agent;

import com.maiya.investigator.domain.control.ToolKind;
import java.util.ArrayList;
import java.util.List;

public record AgentDefinition(
        String slug,
        String name,
        String systemPrompt,
        List<ToolKind> allowedToolKinds,
        List<String> skillSlugs,
        List<String> starters,
        AgentFlow flow
) {
    public static final String DEFAULT_SLUG = "sre-investigate";

    public AgentDefinition(
            String slug,
            String name,
            String systemPrompt,
            List<ToolKind> allowedToolKinds,
            List<String> skillSlugs,
            List<String> starters
    ) {
        this(slug, name, systemPrompt, allowedToolKinds, skillSlugs, starters, AgentFlow.empty());
    }

    public AgentDefinition {
        slug = slug == null ? "" : slug.trim();
        name = name == null ? slug : name.trim();
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        allowedToolKinds = copyKinds(allowedToolKinds);
        skillSlugs = copyStrings(skillSlugs);
        starters = copyStrings(starters);
        flow = flow == null ? AgentFlow.empty() : flow;
    }

    public boolean specialized() {
        return !slug.isBlank() && !DEFAULT_SLUG.equalsIgnoreCase(slug);
    }

    public boolean hasFlow() {
        return flow != null && !flow.isEmpty();
    }

    public boolean allows(ToolKind kind) {
        if (!specialized()) {
            return true;
        }
        if (kind == null) {
            return false;
        }
        for (ToolKind item : allowedToolKinds) {
            if (item == kind) {
                return true;
            }
        }
        if (flow != null) {
            for (AgentFlow.Node node : flow.nodes()) {
                if (node.usesKind(kind)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<ToolKind> copyKinds(List<ToolKind> raw) {
        List<ToolKind> copy = new ArrayList<>();
        if (raw == null) {
            return copy;
        }
        for (ToolKind item : raw) {
            if (item != null && !copy.contains(item)) {
                copy.add(item);
            }
        }
        return copy;
    }

    private static List<String> copyStrings(List<String> raw) {
        List<String> copy = new ArrayList<>();
        if (raw == null) {
            return copy;
        }
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String value = item.trim();
            if (!copy.contains(value)) {
                copy.add(value);
            }
        }
        return copy;
    }
}
