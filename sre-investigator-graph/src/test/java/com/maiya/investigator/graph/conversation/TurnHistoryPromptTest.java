package com.maiya.investigator.graph.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.conversation.ConversationMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TurnHistoryPromptTest {
    @Test
    void firstTurnKeepsQuestionUnchanged() {
        assertEquals("这个类怎么设计的", TurnHistoryPrompt.append("这个类怎么设计的", null, List.of()));
    }

    @Test
    void laterTurnIncludesRecentTurnsAndMemory() {
        UUID conversationId = UUID.randomUUID();
        String prompt = TurnHistoryPrompt.append(
                "那入口在哪",
                "用户在看 RelatedHotDramaRecommend",
                List.of(
                        new ConversationMessage(UUID.randomUUID(), conversationId, "user", "梳理一下这个类", null, Instant.now()),
                        new ConversationMessage(UUID.randomUUID(), conversationId, "assistant", "这是推荐编排入口", null, Instant.now())
                )
        );
        assertTrue(prompt.contains("同一会话的后续提问"));
        assertTrue(prompt.contains("会话记忆"));
        assertTrue(prompt.contains("RelatedHotDramaRecommend"));
        assertTrue(prompt.contains("最近对话"));
        assertTrue(prompt.contains("用户："));
        assertTrue(prompt.contains("助手："));
        assertTrue(prompt.contains("当前问题"));
        assertTrue(prompt.contains("那入口在哪"));
    }

    @Test
    void includesProjectMemory() {
        String prompt = TurnHistoryPrompt.append("继续", null, List.of(), List.of("表 t_order 走支付回调"));
        assertTrue(prompt.contains("项目记忆"));
        assertTrue(prompt.contains("t_order"));
    }

    @Test
    void clipsLongHistory() {
        String longText = "x".repeat(TurnHistoryPrompt.MAX_MESSAGE_CHARS + 80);
        ConversationMessage message = new ConversationMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "assistant",
                longText,
                null,
                Instant.now()
        );
        String prompt = TurnHistoryPrompt.append("继续", null, List.of(message));
        assertTrue(prompt.contains("已截断"));
        assertFalse(prompt.contains(longText));
    }
}
