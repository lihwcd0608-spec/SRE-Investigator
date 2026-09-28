package com.maiya.investigator.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.maiya.investigator.graph.AgentProperties;
import com.maiya.investigator.infra.InMemorySettingStore;
import org.junit.jupiter.api.Test;

class AgentBudgetServiceTest {
    @Test
    void pageOverrideBeatsYamlDefault() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxUserTurns(10);
        properties.setMaxToolCalls(100);
        AgentBudgetService service = new AgentBudgetService(properties, new InMemorySettingStore());

        service.update(20, 50);

        assertEquals(20, properties.getMaxUserTurns());
        assertEquals(50, properties.getMaxToolCalls());
        assertEquals(10, service.snapshot().defaultUserTurns());
        assertEquals(100, service.snapshot().defaultToolCalls());
    }
}
