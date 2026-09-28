package com.maiya.investigator.graph.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.maiya.investigator.domain.model.ModelProvider;
import com.maiya.investigator.domain.model.ResolvedModelSettings;
import com.maiya.investigator.domain.usage.ModelUsageEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

class UsageRecordingChatModelTest {
    @AfterEach
    void tearDown() {
        UsageContext.close();
    }

    @Test
    void sinkFailureDoesNotChangeResponse() {
        ChatResponse response = emptyResponse();
        UsageRecordingChatModel model = new UsageRecordingChatModel(
                new FixedChatModel(response),
                settings(),
                event -> {
                    throw new IllegalStateException("disk full");
                }
        );
        UsageContext.open(snapshot());

        assertSame(response, model.call(new Prompt("hello")));
    }

    @Test
    void recordsCallWhenUsageMissing() {
        List<ModelUsageEvent> captured = new ArrayList<>();
        ChatResponse response = new ChatResponse(
                List.of(),
                ChatResponseMetadata.builder().usage(new NullUsage()).build()
        );
        UsageRecordingChatModel model = new UsageRecordingChatModel(new FixedChatModel(response), settings(), captured::add);
        UsageContext.open(snapshot());

        model.call(new Prompt("hello"));

        assertEquals(1, captured.size());
        assertEquals(1, captured.get(0).callCount());
        assertNull(captured.get(0).promptTokens());
        assertNull(captured.get(0).completionTokens());
        assertNull(captured.get(0).totalTokens());
        assertEquals("dashscope", captured.get(0).provider());
        assertEquals("qwen-plus", captured.get(0).model());
        assertEquals("alice", captured.get(0).username());
        assertEquals("OPERATOR,VIEWER", captured.get(0).roleCodes());
    }

    @Test
    void recordsTokenUsageWhenPresent() {
        List<ModelUsageEvent> captured = new ArrayList<>();
        ChatResponse response = new ChatResponse(
                List.of(),
                ChatResponseMetadata.builder().usage(new DefaultUsage(11, 7, 18)).build()
        );
        UsageRecordingChatModel model = new UsageRecordingChatModel(new FixedChatModel(response), settings(), captured::add);
        UsageContext.open(snapshot());

        model.call(new Prompt("hello"));

        assertEquals(11L, captured.get(0).promptTokens());
        assertEquals(7L, captured.get(0).completionTokens());
        assertEquals(18L, captured.get(0).totalTokens());
    }

    @Test
    void skipsWhenContextClosed() {
        List<ModelUsageEvent> captured = new ArrayList<>();
        UsageRecordingChatModel model = new UsageRecordingChatModel(
                new FixedChatModel(emptyResponse()),
                settings(),
                captured::add
        );

        model.call(new Prompt("probe"));

        assertEquals(0, captured.size());
    }

    private static ChatResponse emptyResponse() {
        return new ChatResponse(List.of());
    }

    private static ResolvedModelSettings settings() {
        return new ResolvedModelSettings(ModelProvider.DASHSCOPE, "qwen-plus", "key", "", true);
    }

    private static UsageSnapshot snapshot() {
        return new UsageSnapshot(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "alice",
                List.of("OPERATOR", "VIEWER"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        );
    }

    private static final class FixedChatModel implements ChatModel {
        private final ChatResponse response;

        private FixedChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return response;
        }
    }

    private static final class NullUsage implements Usage {
        @Override
        public Integer getPromptTokens() {
            return null;
        }

        @Override
        public Integer getCompletionTokens() {
            return null;
        }

        @Override
        public Integer getTotalTokens() {
            return null;
        }

        @Override
        public Object getNativeUsage() {
            return null;
        }
    }
}
