package com.maiya.investigator.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgentPropertiesTest {
    @Test
    void limitsAreAtLeastOne() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxToolCalls(0);
        properties.setMaxUserTurns(-3);
        assertEquals(1, properties.getMaxToolCalls());
        assertEquals(1, properties.getMaxUserTurns());
    }
}
