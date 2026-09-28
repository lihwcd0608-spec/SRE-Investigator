package com.maiya.investigator.graph.conversation;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.graph.InvestigationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InvestigateConversationStrategy implements ConversationStrategy {
    private static final Logger log = LoggerFactory.getLogger(InvestigateConversationStrategy.class);
    private final InvestigationWorkflow workflow;

    public InvestigateConversationStrategy(InvestigationWorkflow workflow) {
        this.workflow = workflow;
    }

    @Override
    public ConversationMode mode() {
        return ConversationMode.INVESTIGATE;
    }

    @Override
    public ConversationOutcome execute(ConversationContext context) {
        log.info("策略 INVESTIGATE project={} env={}", context.projectId(), context.environment());
        InvestigationWorkflow.InvestigationResult result = workflow.investigate(
                context.projectId(),
                AttachmentPrompt.append(context.question(), context.attachments()),
                context.environment().code(),
                context.provider(),
                context.model(),
                context.route(),
                context.memoryPrompt(),
                context.mcpProfile(),
                context.repositoryId(),
                context.agentSlug(),
                context.skillSlug(),
                context.ruleSlug()
        );
        boolean modelUsed = Boolean.TRUE.equals(result.state().get("modelUsed"));
        return new ConversationOutcome(result.report(), modelUsed, result.route(), result.state());
    }
}
