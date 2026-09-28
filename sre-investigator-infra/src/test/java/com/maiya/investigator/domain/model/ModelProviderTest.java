package com.maiya.investigator.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModelProviderTest {
    @Test
    void parsesKnownProviders() {
        assertEquals(ModelProvider.DASHSCOPE, ModelProvider.from("dashscope"));
        assertEquals(ModelProvider.OPENAI, ModelProvider.from("OpenAI"));
        assertEquals(ModelProvider.OLLAMA, ModelProvider.from("ollama"));
    }

    @Test
    void rejectsUnknownProvider() {
        assertThrows(IllegalArgumentException.class, () -> ModelProvider.from("unknown-vendor"));
    }

    @Test
    void ollamaIsReadyWithoutApiKey() {
        ResolvedModelSettings settings = new ResolvedModelSettings(
                ModelProvider.OLLAMA,
                "qwen2.5",
                "",
                "http://localhost:11434",
                true
        );
        assertTrue(settings.available());
        assertTrue(settings.credentialsReady());
        assertFalse(new ResolvedModelSettings(ModelProvider.OPENAI, "gpt-4o-mini", "", "https://api.openai.com", true).available());
        assertFalse(new ResolvedModelSettings(ModelProvider.OPENAI, "gpt-4o-mini", "", "https://api.openai.com", true).credentialsReady());
        assertTrue(new ResolvedModelSettings(ModelProvider.OPENAI, "", "sk-test", "https://api.deepseek.com", true).credentialsReady());
    }
}
