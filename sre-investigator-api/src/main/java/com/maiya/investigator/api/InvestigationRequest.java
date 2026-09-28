package com.maiya.investigator.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record InvestigationRequest(
        @NotNull UUID projectId,
        @NotNull Environment environment,
        @NotBlank String question,
        ConversationMode mode,
        String provider,
        String model,
        List<ConversationAttachment> attachments,
        UUID conversationId,
        UUID continueFromRunId,
        String route,
        UUID repositoryId,
        String baseRef,
        String headRef,
        String skillSlug,
        String mcpProfile,
        String agentSlug,
        String ruleSlug
) {
    public InvestigationRequest(
            UUID projectId,
            Environment environment,
            String question,
            ConversationMode mode,
            String provider,
            String model,
            List<ConversationAttachment> attachments,
            UUID conversationId,
            UUID continueFromRunId,
            String route
    ) {
        this(projectId, environment, question, mode, provider, model, attachments, conversationId, continueFromRunId, route, null, null, null, null, null, null, null);
    }

    public InvestigationRequest(
            UUID projectId,
            Environment environment,
            String question,
            ConversationMode mode,
            String provider,
            String model,
            List<ConversationAttachment> attachments,
            UUID conversationId,
            UUID continueFromRunId,
            String route,
            UUID repositoryId,
            String baseRef,
            String headRef
    ) {
        this(projectId, environment, question, mode, provider, model, attachments, conversationId, continueFromRunId, route, repositoryId, baseRef, headRef, null, null, null, null);
    }

    public ConversationMode modeOrDefault() {
        return mode == null ? ConversationMode.INVESTIGATE : mode;
    }

    public List<ConversationAttachment> attachmentsOrEmpty() {
        return attachments == null ? List.of() : attachments;
    }
}
