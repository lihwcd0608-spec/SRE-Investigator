package com.maiya.investigator.graph;

import com.maiya.investigator.domain.control.ToolKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 单轮只读排查的入参聚合对象，替代 InvestigationAgent.investigate 的一长串重载。
 * 数据类工具的 profile 统一按 ToolKind 归档，避免散落的 allowedXxxProfiles 参数。
 */
public record InvestigateCommand(
        String question,
        String route,
        String environment,
        boolean codeBound,
        List<String> skills,
        List<String> rules,
        String provider,
        String model,
        String projectId,
        List<String> toolCatalog,
        String memoryPrompt,
        Map<ToolKind, List<String>> profiles,
        String pinnedMcpProfile,
        UUID repositoryId,
        String agentSystemPrompt,
        int toolCallLimit
) {
    public InvestigateCommand {
        skills = copy(skills);
        rules = copy(rules);
        toolCatalog = copy(toolCatalog);
        profiles = copyProfiles(profiles);
    }

    // 取某类数据工具本轮可用的 profile 列表，永远返回可变空列表兜底
    public List<String> profiles(ToolKind kind) {
        List<String> value = profiles.get(kind);
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<String> copy(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String item : raw) {
            if (item != null && !item.isBlank() && !out.contains(item)) {
                out.add(item);
            }
        }
        return out;
    }

    private static Map<ToolKind, List<String>> copyProfiles(Map<ToolKind, List<String>> raw) {
        Map<ToolKind, List<String>> out = new EnumMap<>(ToolKind.class);
        if (raw == null) {
            return out;
        }
        for (Map.Entry<ToolKind, List<String>> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            List<String> value = copy(entry.getValue());
            if (!value.isEmpty()) {
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    public static final class Builder {
        private String question = "";
        private String route = "";
        private String environment = "test";
        private boolean codeBound;
        private List<String> skills = new ArrayList<>();
        private List<String> rules = new ArrayList<>();
        private String provider = "";
        private String model = "";
        private String projectId = "";
        private List<String> toolCatalog = new ArrayList<>();
        private String memoryPrompt = "";
        private final Map<ToolKind, List<String>> profiles = new EnumMap<>(ToolKind.class);
        private String pinnedMcpProfile = "";
        private UUID repositoryId;
        private String agentSystemPrompt = "";
        private int toolCallLimit = 1;

        public Builder question(String value) {
            this.question = value;
            return this;
        }

        public Builder route(String value) {
            this.route = value;
            return this;
        }

        public Builder environment(String value) {
            this.environment = value;
            return this;
        }

        public Builder codeBound(boolean value) {
            this.codeBound = value;
            return this;
        }

        public Builder skills(List<String> value) {
            this.skills = value;
            return this;
        }

        public Builder rules(List<String> value) {
            this.rules = value;
            return this;
        }

        public Builder provider(String value) {
            this.provider = value;
            return this;
        }

        public Builder model(String value) {
            this.model = value;
            return this;
        }

        public Builder projectId(String value) {
            this.projectId = value;
            return this;
        }

        public Builder toolCatalog(List<String> value) {
            this.toolCatalog = value;
            return this;
        }

        public Builder memoryPrompt(String value) {
            this.memoryPrompt = value;
            return this;
        }

        public Builder profile(ToolKind kind, List<String> value) {
            if (kind != null && value != null && !value.isEmpty()) {
                this.profiles.put(kind, value);
            }
            return this;
        }

        public Builder pinnedMcpProfile(String value) {
            this.pinnedMcpProfile = value;
            return this;
        }

        public Builder repositoryId(UUID value) {
            this.repositoryId = value;
            return this;
        }

        public Builder agentSystemPrompt(String value) {
            this.agentSystemPrompt = value;
            return this;
        }

        public Builder toolCallLimit(int value) {
            this.toolCallLimit = value;
            return this;
        }

        public InvestigateCommand build() {
            return new InvestigateCommand(
                    question,
                    route,
                    environment,
                    codeBound,
                    skills,
                    rules,
                    provider,
                    model,
                    projectId,
                    toolCatalog,
                    memoryPrompt,
                    profiles,
                    pinnedMcpProfile,
                    repositoryId,
                    agentSystemPrompt,
                    toolCallLimit
            );
        }
    }
}
