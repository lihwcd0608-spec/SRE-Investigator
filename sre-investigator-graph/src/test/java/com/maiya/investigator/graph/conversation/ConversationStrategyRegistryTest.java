package com.maiya.investigator.graph.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.ConversationMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationStrategyRegistryTest {
    @Test
    void listsAskAndInvestigateAsEnabled() {
        ConversationStrategyRegistry registry = new ConversationStrategyRegistry(List.of(
                new AskConversationStrategy(null),
                new InvestigateConversationStrategy(null),
                new CodeConversationStrategy()
        ));

        assertEquals(3, registry.descriptors().size());
        assertTrue(registry.descriptors().stream().anyMatch(item -> item.id().equals("ASK") && item.enabled()));
        assertTrue(registry.descriptors().stream().anyMatch(item -> item.id().equals("CODE") && !item.enabled()));
        assertEquals(ConversationMode.ASK, registry.require(ConversationMode.ASK).mode());
    }

    @Test
    void hidesReviewFromConversationPicker() {
        ConversationStrategy review = new ConversationStrategy() {
            @Override
            public ConversationMode mode() {
                return ConversationMode.REVIEW;
            }

            @Override
            public ConversationOutcome execute(ConversationContext context) {
                return null;
            }
        };
        ConversationStrategyRegistry registry = new ConversationStrategyRegistry(List.of(
                new AskConversationStrategy(null),
                new InvestigateConversationStrategy(null),
                new CodeConversationStrategy(),
                review
        ));

        assertEquals(3, registry.descriptors().size());
        assertTrue(registry.descriptors().stream().noneMatch(item -> item.id().equals("REVIEW")));
        assertEquals(ConversationMode.REVIEW, registry.require(ConversationMode.REVIEW).mode());
    }

    @Test
    void rejectsDisabledCodeMode() {
        ConversationStrategyRegistry registry = new ConversationStrategyRegistry(List.of(new CodeConversationStrategy()));
        assertThrows(IllegalStateException.class, () -> registry.require(ConversationMode.CODE));
    }
}
