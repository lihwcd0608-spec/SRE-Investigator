package com.maiya.investigator.boot.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.AutomationJobRequest;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.boot.ConversationSessionService;
import com.maiya.investigator.domain.InvestigatorProject;
import com.maiya.investigator.domain.automation.AutomationExecution;
import com.maiya.investigator.domain.automation.AutomationJob;
import com.maiya.investigator.domain.automation.TriggerType;
import com.maiya.investigator.infra.InMemoryAutomationJobStore;
import com.maiya.investigator.infra.InMemoryConversationStore;
import com.maiya.investigator.infra.InMemoryControlPlaneStore;
import com.maiya.investigator.infra.InMemoryInvestigationRunStore;
import com.maiya.investigator.infra.InMemoryProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationJobServiceTest {
    @Test
    void everyScheduleDoesNotFireOnSave() {
        Fixture fixture = Fixture.create();
        Instant before = Instant.now();
        AutomationJob job = fixture.service.create(fixture.project.id(), request(
                "K8s 只读巡检",
                "EVERY",
                "15m",
                "K8S_INSPECT",
                null
        ));
        assertTrue(job.nextRunAt().isAfter(before));
        assertTrue(fixture.store.due(before).isEmpty());
        assertEquals("K8S_INSPECT", job.payloadKind().name());
    }

    @Test
    void inspectWithoutBindingFailsAndKeepsHistory() {
        Fixture fixture = Fixture.create();
        AutomationJob job = fixture.service.create(fixture.project.id(), request(
                "巡检",
                "EVERY",
                "15m",
                "K8S_INSPECT",
                null
        ));
        AutomationExecution execution = fixture.service.run(fixture.project.id(), job.id(), TriggerType.MANUAL);
        assertEquals("FAILED", execution.status());
        AutomationJob after = fixture.store.findById(job.id()).orElseThrow();
        assertEquals("FAILED", after.lastStatus());
        assertEquals(1, after.consecutiveErrors());
        assertFalse(after.isRunning(Instant.now()));
        assertEquals(1, fixture.service.executions(fixture.project.id(), job.id(), 10).size());
    }

    @Test
    void inspectSuccessDisablesOneShot() {
        Fixture fixture = Fixture.healthy();
        AutomationJob job = fixture.service.create(fixture.project.id(), request(
                "一次巡检",
                "AT",
                Instant.now().plusSeconds(3600).toString(),
                "K8S_INSPECT",
                null
        ));
        AutomationExecution execution = fixture.service.run(fixture.project.id(), job.id(), TriggerType.MANUAL);
        assertEquals("OK", execution.status());
        assertNotNull(execution.conversationId());
        AutomationJob after = fixture.store.findById(job.id()).orElseThrow();
        assertFalse(after.enabled());
        assertEquals("OK", after.lastStatus());
        assertTrue(fixture.conversations.list(fixture.project.id()).isEmpty());
        var detail = fixture.service.executionDetail(fixture.project.id(), job.id(), execution.id());
        assertEquals(execution.id(), detail.execution().id());
        assertFalse(detail.messages().isEmpty());
        assertEquals("assistant", detail.messages().get(detail.messages().size() - 1).role());
        IllegalArgumentException hidden = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.conversations.get(execution.conversationId())
        );
        assertTrue(hidden.getMessage().contains("定时"));
    }

    @Test
    void listVisibleKeepsOnlyCurrentEnvironment() {
        Fixture fixture = Fixture.create();
        fixture.service.create(fixture.project.id(), request("test 巡检", "EVERY", "15m", "K8S_INSPECT", null));
        AutomationJobRequest pre = new AutomationJobRequest(
                "pre 巡检",
                Environment.PRE,
                true,
                "EVERY",
                "15m",
                "Asia/Shanghai",
                "K8S_INSPECT",
                "ISOLATED",
                null,
                null,
                null,
                false,
                600
        );
        fixture.service.create(fixture.project.id(), pre);
        List<AutomationJob> testJobs = fixture.service.listVisible(List.of(fixture.project.id()), Environment.TEST);
        List<AutomationJob> preJobs = fixture.service.listVisible(List.of(fixture.project.id()), Environment.PRE);
        assertEquals(1, testJobs.size());
        assertEquals("test 巡检", testJobs.get(0).name());
        assertEquals(1, preJobs.size());
        assertEquals("pre 巡检", preJobs.get(0).name());
        assertTrue(fixture.service.listVisible(List.of(), Environment.TEST).isEmpty());
    }

    @Test
    void agentTurnRequiresPrompt() {
        Fixture fixture = Fixture.create();
        assertThrows(IllegalArgumentException.class, () -> fixture.service.create(fixture.project.id(), request(
                "模型轮",
                "EVERY",
                "1h",
                "AGENT_TURN",
                ""
        )));
    }

    private static AutomationJobRequest request(
            String name,
            String scheduleKind,
            String expr,
            String payload,
            String prompt
    ) {
        return new AutomationJobRequest(
                name,
                Environment.TEST,
                true,
                scheduleKind,
                expr,
                "Asia/Shanghai",
                payload,
                "ISOLATED",
                prompt,
                null,
                null,
                false,
                600
        );
    }

    private record Fixture(
            InMemoryProjectRepository projects,
            InvestigatorProject project,
            InMemoryAutomationJobStore store,
            ConversationSessionService conversations,
            AutomationJobService service
    ) {
        static Fixture create() {
            return create(new K8sInspectionScanner(new InMemoryControlPlaneStore(), null));
        }

        static Fixture healthy() {
            return create(new K8sInspectionScanner(new InMemoryControlPlaneStore(), null) {
                @Override
                public Report scan(UUID projectId, Environment environment) {
                    return new Report("# 正常", false, 0, "未见异常");
                }
            });
        }

        private static Fixture create(K8sInspectionScanner scanner) {
            InMemoryProjectRepository projects = new InMemoryProjectRepository();
            InvestigatorProject project = projects.save(InvestigatorProject.create("Demo", "demo", "generic"));
            InMemoryAutomationJobStore store = new InMemoryAutomationJobStore();
            ConversationSessionService conversations = new ConversationSessionService(
                    projects,
                    new InMemoryConversationStore()
            );
            AutomationJobService service = new AutomationJobService(
                    store,
                    conversations,
                    new InMemoryInvestigationRunStore(),
                    null,
                    scanner
            );
            return new Fixture(projects, project, store, conversations, service);
        }
    }
}
