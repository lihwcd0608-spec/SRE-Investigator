package com.maiya.investigator.graph.conversation;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.graph.InvestigationAgent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AskConversationStrategy implements ConversationStrategy {
    private static final Logger log = LoggerFactory.getLogger(AskConversationStrategy.class);
    private final InvestigationAgent agent;

    public AskConversationStrategy(InvestigationAgent agent) {
        this.agent = agent;
    }

    @Override
    public ConversationMode mode() {
        return ConversationMode.ASK;
    }

    @Override
    public ConversationOutcome execute(ConversationContext context) {
        log.info("策略 ASK project={} env={}", context.projectId(), context.environment());
        String question = context.question() == null ? "" : context.question();
        if (context.memoryPrompt() != null && !context.memoryPrompt().isBlank()) {
            question = context.memoryPrompt() + "## 当前问题\n" + question;
        }
        InvestigationAgent.AgentResult result = agent.ask(
                AttachmentPrompt.append(question, context.attachments()),
                context.environment().code(),
                context.provider(),
                context.model(),
                context.projectId() == null ? null : context.projectId().toString()
        );
        return new ConversationOutcome(result.content(), result.modelUsed(), "ask", Map.of(
                "modelUsed", result.modelUsed(),
                "availableTools", result.availableTools(),
                "usedToolCalls", result.usedToolCalls(),
                "budgetExhausted", result.budgetExhausted(),
                "maxToolCalls", result.maxToolCalls()
        ));
    }
}
