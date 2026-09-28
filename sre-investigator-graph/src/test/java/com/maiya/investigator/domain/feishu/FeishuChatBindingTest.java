package com.maiya.investigator.domain.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.Environment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeishuChatBindingTest {
    @Test
    void emptyAllowedFallsBackToDefault() {
        List<Environment> allowed = FeishuChatBinding.normalizeAllowed(Environment.TEST, new ArrayList<>());
        assertEquals(1, allowed.size());
        assertEquals(Environment.TEST, allowed.get(0));
    }

    @Test
    void defaultInsertedWhenMissingFromAllowed() {
        List<Environment> requested = new ArrayList<>();
        requested.add(Environment.PROD);
        List<Environment> allowed = FeishuChatBinding.normalizeAllowed(Environment.TEST, requested);
        assertEquals(Environment.TEST, allowed.get(0));
        assertTrue(allowed.contains(Environment.PROD));
    }

    @Test
    void resolveUsesDefaultWhenQuestionOmitsEnvironment() {
        List<Environment> requested = new ArrayList<>();
        requested.add(Environment.TEST);
        requested.add(Environment.PROD);
        FeishuChatBinding binding = FeishuChatBinding.create(
                UUID.randomUUID(),
                "oc_test",
                "机器人测试",
                Environment.TEST,
                requested,
                "",
                null
        );
        assertEquals(Environment.TEST, binding.resolveEnvironment(null));
        assertEquals(Environment.PROD, binding.resolveEnvironment(Environment.PROD));
    }

    @Test
    void resolveRejectsEnvironmentNotAllowed() {
        List<Environment> requested = new ArrayList<>();
        requested.add(Environment.TEST);
        FeishuChatBinding binding = FeishuChatBinding.create(
                UUID.randomUUID(),
                "oc_test",
                "机器人测试",
                Environment.TEST,
                requested,
                "",
                null
        );
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> binding.resolveEnvironment(Environment.PROD)
        );
        assertTrue(error.getMessage().contains("prod"));
    }

    @Test
    void splitAllowedReadsCommaList() {
        List<Environment> allowed = FeishuChatBinding.splitAllowed("test,prod", Environment.TEST);
        assertEquals(2, allowed.size());
        assertTrue(allowed.contains(Environment.TEST));
        assertTrue(allowed.contains(Environment.PROD));
        assertEquals("test,prod", FeishuChatBinding.joinAllowed(allowed));
    }
}
