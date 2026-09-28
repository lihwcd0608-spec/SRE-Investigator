package com.maiya.investigator.domain.feishu;

import com.maiya.investigator.api.Environment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FeishuChatBinding(
        UUID id,
        UUID projectId,
        String chatId,
        String chatName,
        Environment environment,
        List<Environment> allowedEnvironments,
        String defaultRoute,
        UUID defaultRepositoryId,
        UUID lastConversationId,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public FeishuChatBinding {
        allowedEnvironments = new ArrayList<>(normalizeAllowed(environment, allowedEnvironments));
    }

    public boolean allows(Environment env) {
        return env != null && allowedEnvironments.contains(env);
    }

    public String allowedLabel() {
        List<String> codes = new ArrayList<>();
        for (Environment item : allowedEnvironments) {
            codes.add(item.code());
        }
        return String.join(" / ", codes);
    }

    public Environment resolveEnvironment(Environment requested) {
        Environment wanted = requested == null ? environment : requested;
        if (!allows(wanted)) {
            throw new IllegalArgumentException(
                    "这个群未开放 " + wanted.code() + "。可用环境：" + allowedLabel()
                            + "。请写成：prod环境 问题描述"
            );
        }
        return wanted;
    }

    public FeishuChatBinding withLastConversationId(UUID conversationId) {
        return new FeishuChatBinding(
                id,
                projectId,
                chatId,
                chatName,
                environment,
                allowedEnvironments,
                defaultRoute,
                defaultRepositoryId,
                conversationId,
                enabled,
                createdAt,
                Instant.now()
        );
    }

    public FeishuChatBinding clearedConversation() {
        return withLastConversationId(null);
    }

    public static FeishuChatBinding create(
            UUID projectId,
            String chatId,
            String chatName,
            Environment environment,
            List<Environment> allowedEnvironments,
            String defaultRoute,
            UUID defaultRepositoryId
    ) {
        Instant now = Instant.now();
        return new FeishuChatBinding(
                UUID.randomUUID(),
                projectId,
                chatId.trim(),
                chatName == null ? "" : chatName.trim(),
                environment,
                allowedEnvironments,
                defaultRoute == null ? "" : defaultRoute.trim(),
                defaultRepositoryId,
                null,
                true,
                now,
                now
        );
    }

    public static List<Environment> normalizeAllowed(Environment defaultEnv, List<Environment> requested) {
        Environment fallback = defaultEnv == null ? Environment.TEST : defaultEnv;
        List<Environment> found = new ArrayList<>();
        if (requested != null) {
            for (Environment item : requested) {
                if (item != null && !found.contains(item)) {
                    found.add(item);
                }
            }
        }
        if (found.isEmpty()) {
            found.add(fallback);
        } else if (!found.contains(fallback)) {
            found.add(0, fallback);
        }
        return found;
    }

    public static String joinAllowed(List<Environment> environments) {
        List<String> codes = new ArrayList<>();
        if (environments == null) {
            return "";
        }
        for (Environment item : environments) {
            if (item != null && !codes.contains(item.code())) {
                codes.add(item.code());
            }
        }
        return String.join(",", codes);
    }

    public static List<Environment> splitAllowed(String raw, Environment fallback) {
        List<Environment> found = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                if (part.isBlank()) {
                    continue;
                }
                Environment env = Environment.from(part.trim());
                if (!found.contains(env)) {
                    found.add(env);
                }
            }
        }
        return normalizeAllowed(fallback, found);
    }
}
