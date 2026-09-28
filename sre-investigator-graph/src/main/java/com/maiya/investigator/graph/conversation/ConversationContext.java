package com.maiya.investigator.graph.conversation;

import com.maiya.investigator.api.ConversationAttachment;
import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import java.util.List;
import java.util.UUID;

public record ConversationContext(
        UUID projectId,
        Environment environment,
        ConversationMode mode,
        String question,
        String memoryPrompt,
        String provider,
        String model,
        List<ConversationAttachment> attachments,
        String route,
        UUID repositoryId,
        String baseRef,
        String headRef,
        String skillSlug,
        String mcpProfile,
        String agentSlug,
        String ruleSlug
) {
}
