package com.maiya.investigator.domain.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuleUsageTest {
    @Test
    void mapsLegacyRoutesToUsage() {
        assertEquals(RuleUsage.INVESTIGATE, RuleUsage.normalize(null));
        assertEquals(RuleUsage.INVESTIGATE, RuleUsage.normalize(""));
        assertEquals(RuleUsage.INVESTIGATE, RuleUsage.normalize("always"));
        assertEquals(RuleUsage.INVESTIGATE, RuleUsage.normalize("http-5xx"));
        assertEquals(RuleUsage.INVESTIGATE, RuleUsage.normalize("order-payment"));
        assertEquals(RuleUsage.REVIEW, RuleUsage.normalize("code-review"));
        assertEquals(RuleUsage.REVIEW, RuleUsage.normalize("REVIEW"));
        assertEquals(RuleUsage.OTHER, RuleUsage.normalize("other"));
    }

    @Test
    void matchesNormalizedUsage() {
        assertTrue(RuleUsage.matches("code-review", "review"));
        assertTrue(RuleUsage.matches(null, "investigate"));
        assertTrue(RuleUsage.matches("http-5xx", "investigate"));
        assertTrue(RuleUsage.matches("http-5xx", "http-5xx"));
        assertTrue(RuleUsage.matches("investigate", "http-5xx"));
        assertFalse(RuleUsage.matches("order-payment", "http-5xx"));
        assertTrue(RuleUsage.matches("other:文风", "other"));
        assertFalse(RuleUsage.matches("other", "investigate"));
        assertFalse(RuleUsage.matches("readonly", null));
    }

    @Test
    void composeKeepsLegacyInvestigateScene() {
        assertEquals("investigate", RuleUsage.canonicalize(null));
        assertEquals("http-5xx", RuleUsage.canonicalize("http-5xx"));
        assertEquals("review", RuleUsage.canonicalize("code-review"));
        assertEquals("review:demo-app", RuleUsage.compose("review", "demo-app"));
        assertEquals("other:文风", RuleUsage.compose("other", "文风"));
    }
}
