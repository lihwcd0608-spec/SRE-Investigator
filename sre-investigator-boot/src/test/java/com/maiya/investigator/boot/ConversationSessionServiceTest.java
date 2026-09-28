package com.maiya.investigator.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.api.InvestigationRequest;
import com.maiya.investigator.boot.auth.AuthPrincipal;
import com.maiya.investigator.boot.auth.ForbiddenException;
import com.maiya.investigator.domain.InvestigationRun;
import com.maiya.investigator.domain.InvestigatorProject;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.conversation.ConversationMessage;
import com.maiya.investigator.domain.conversation.ConversationOrigin;
import com.maiya.investigator.infra.InMemoryConversationStore;
import com.maiya.investigator.infra.InMemoryProjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationSessionServiceTest {
    @Test
    void firstTurnCreatesThreadAndSecondTurnReusesIt() {
        Fixture fixture = Fixture.create();
        ConversationSessionService.PreparedTurn first = fixture.service.prepare(fixture.request(null, "第一问"), null);
        fixture.service.recordUser(first.thread(), "第一问", UUID.randomUUID());
        fixture.service.recordAssistant(first.thread(), "第一答", UUID.randomUUID());

        ConversationSessionService.PreparedTurn second = fixture.service.prepare(
                fixture.request(first.thread().id(), "第二问"),
                null
        );

        assertEquals(first.thread().id(), second.thread().id());
        assertEquals(2, second.turnIndex());
        assertTrue(second.agentQuestion().contains("最近对话"));
        assertTrue(second.agentQuestion().contains("第一问"));
        assertTrue(second.agentQuestion().contains("当前问题"));
        assertTrue(second.agentQuestion().contains("第二问"));
        assertTrue(second.agentQuestion().contains("会话记忆"));
    }

    @Test
    void fourthTurnDropsFirstTurnVerbatimAndKeepsSummary() {
        Fixture fixture = Fixture.create();
        ConversationSessionService.PreparedTurn first = fixture.service.prepare(
                fixture.request(null, "第一问很独特的标记AAA"),
                null
        );
        fixture.service.recordUser(first.thread(), "第一问很独特的标记AAA", UUID.randomUUID());
        fixture.service.recordAssistant(first.thread(), "第一答结论是入口在 FooService", UUID.randomUUID());

        ConversationSessionService.PreparedTurn second = fixture.service.prepare(
                fixture.request(first.thread().id(), "第二问"),
                null
        );
        fixture.service.recordUser(first.thread(), "第二问", UUID.randomUUID());
        fixture.service.recordAssistant(first.thread(), "第二答查了表 t_order", UUID.randomUUID());

        ConversationSessionService.PreparedTurn third = fixture.service.prepare(
                fixture.request(first.thread().id(), "第三问"),
                null
        );
        assertTrue(third.agentQuestion().contains("会话记忆"));
        fixture.service.recordUser(first.thread(), "第三问", UUID.randomUUID());
        fixture.service.recordAssistant(first.thread(), "第三答", UUID.randomUUID());

        ConversationSessionService.PreparedTurn fourth = fixture.service.prepare(
                fixture.request(first.thread().id(), "第四问"),
                null
        );
        assertTrue(fourth.agentQuestion().contains("会话记忆"));
        assertTrue(fourth.agentQuestion().contains("最近对话"));
        assertFalse(fourth.agentQuestion().contains("第一问很独特的标记AAA"));
        assertTrue(fourth.agentQuestion().contains("第四问"));
    }

    @Test
    void eleventhUserTurnIsRejected() {
        Fixture fixture = Fixture.create();
        ConversationSessionService.PreparedTurn first = fixture.service.prepare(fixture.request(null, "第 1 问"), null);
        UUID conversationId = first.thread().id();
        for (int index = 1; index <= fixture.service.maxUserTurns(); index++) {
            fixture.service.recordUser(first.thread(), "第 " + index + " 问", UUID.randomUUID());
            fixture.service.recordAssistant(first.thread(), "答 " + index, UUID.randomUUID());
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.prepare(fixture.request(conversationId, "第 11 问"), null)
        );
        assertTrue(error.getMessage().contains(fixture.service.maxUserTurns() + " 轮"));
    }

    @Test
    void continuationDoesNotCreateANewUserTurn() {
        Fixture fixture = Fixture.create();
        InvestigationRun prior = InvestigationRun.start(
                fixture.project.id(),
                Environment.TEST,
                ConversationMode.INVESTIGATE,
                "预约剧没生效"
        ).complete("已确认入口类", InvestigationRun.RunStatus.NEEDS_INPUT);
        ConversationSessionService.PreparedTurn first = fixture.service.prepare(fixture.request(null, "预约剧没生效"), null);
        fixture.service.recordUser(first.thread(), "预约剧没生效", prior.id());
        fixture.service.recordAssistant(first.thread(), "已确认入口类", prior.id());

        InvestigationRequest continueRequest = new InvestigationRequest(
                fixture.project.id(),
                Environment.TEST,
                "继续看强插",
                ConversationMode.INVESTIGATE,
                null,
                null,
                null,
                first.thread().id(),
                prior.id(),
                null
        );
        ConversationSessionService.PreparedTurn continued = fixture.service.prepare(continueRequest, prior);

        assertTrue(continued.continuation());
        assertEquals(1, continued.turnIndex());
        assertTrue(continued.agentQuestion().contains("用户确认继续排查"));
        assertEquals(1, fixture.service.messages(first.thread().id()).stream().filter(message -> "user".equals(message.role())).count());
    }

    @Test
    void conversationMustBelongToCurrentProject() {
        Fixture fixture = Fixture.create();
        ConversationSessionService.PreparedTurn first = fixture.service.prepare(fixture.request(null, "问一下"), null);
        InvestigatorProject other = fixture.projects.save(InvestigatorProject.create("其他", "other-proj", "generic"));
        InvestigationRequest stolen = new InvestigationRequest(
                other.id(),
                Environment.TEST,
                "偷用会话",
                ConversationMode.ASK,
                null,
                null,
                null,
                first.thread().id(),
                null,
                null
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.prepare(stolen, null)
        );
        assertTrue(error.getMessage().contains("不属于当前项目"));
        assertFalse(fixture.service.messages(first.thread().id()).stream().map(ConversationMessage::content).toList().contains("偷用会话"));
    }

    @Test
    void followUpKeepsNamedMcpFromEarlierTurn() {
        Fixture fixture = Fixture.create();
        ConversationSessionService.PreparedTurn first = fixture.service.prepare(
                fixture.request(null, "使用 zg_mongodb_test mcp 查 ums_user_tag_attr"),
                null
        );
        fixture.service.recordUser(
                first.thread(),
                "使用 zg_mongodb_test mcp 查 ums_user_tag_attr",
                java.util.UUID.randomUUID()
        );

        ConversationSessionService.PreparedTurn follow = fixture.service.prepare(
                fixture.request(first.thread().id(), "继续"),
                null
        );
        assertEquals("zg_mongodb_test", follow.mcpProfile());
    }

    @Test
    void automationThreadsStayOutOfChatList() {
        Fixture fixture = Fixture.create();
        fixture.service.startThread(
                fixture.project.id(),
                Environment.TEST,
                ConversationMode.INVESTIGATE,
                ConversationOrigin.AUTOMATION,
                "巡检 · 定时"
        );
        fixture.service.prepare(fixture.request(null, "人手问一句"), null);

        assertEquals(1, fixture.service.list(fixture.project.id()).size());
        assertEquals("人手问一句", fixture.service.list(fixture.project.id()).get(0).title());
    }

    @Test
    void reviewThreadsStayOutOfChatList() {
        Fixture fixture = Fixture.create();
        fixture.service.prepare(fixture.request(null, "人手问一句"), null);
        fixture.service.prepare(new InvestigationRequest(
                fixture.project.id(),
                Environment.TEST,
                "审查 feature 相对 main 的差异",
                ConversationMode.REVIEW,
                null,
                null,
                null,
                null,
                null,
                "code-review"
        ), null);

        assertEquals(1, fixture.service.list(fixture.project.id()).size());
        assertEquals("人手问一句", fixture.service.list(fixture.project.id()).get(0).title());
        assertEquals(1, fixture.service.listReviews(fixture.project.id()).size());
        assertEquals(ConversationMode.REVIEW, fixture.service.listReviews(fixture.project.id()).get(0).mode());
    }

    @Test
    void agentThreadsStayOutOfChatList() {
        Fixture fixture = Fixture.create();
        fixture.service.prepare(fixture.request(null, "人手问一句"), null);
        ConversationSessionService.PreparedTurn playground = fixture.service.prepare(
                new InvestigationRequest(
                        fixture.project.id(),
                        Environment.TEST,
                        "先看当前库有哪些表",
                        ConversationMode.INVESTIGATE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "sre-incident-flow",
                        null
                ),
                null
        );

        assertEquals(ConversationOrigin.AGENT, playground.thread().origin());
        assertEquals(1, fixture.service.list(fixture.project.id()).size());
        assertEquals("人手问一句", fixture.service.list(fixture.project.id()).get(0).title());
        assertEquals(playground.thread().id(), fixture.service.get(playground.thread().id()).id());
    }

    @Test
    void viewerCannotSeeAnotherUsersConversation() {
        Fixture fixture = Fixture.create();
        AuthPrincipal alice = viewer("alice");
        AuthPrincipal bob = viewer("bob");
        ConversationSessionService.PreparedTurn aliceTurn = fixture.service.prepare(
                fixture.request(null, "alice 的问题"),
                null,
                alice
        );

        assertEquals(alice.userId(), aliceTurn.thread().createdByUserId());
        assertEquals(1, fixture.service.list(fixture.project.id(), alice).size());
        assertEquals(0, fixture.service.list(fixture.project.id(), bob).size());
        assertEquals(1, fixture.service.list(fixture.project.id(), superAdmin()).size());

        ForbiddenException getError = assertThrows(
                ForbiddenException.class,
                () -> fixture.service.get(aliceTurn.thread().id(), bob)
        );
        assertTrue(getError.getMessage().contains("没有权限查看该对话"));

        ForbiddenException prepareError = assertThrows(
                ForbiddenException.class,
                () -> fixture.service.prepare(fixture.request(aliceTurn.thread().id(), "bob 接着聊"), null, bob)
        );
        assertTrue(prepareError.getMessage().contains("没有权限查看该对话"));
        assertEquals("alice 的问题", fixture.service.get(aliceTurn.thread().id(), alice).title());
    }

    @Test
    void viewerCannotContinueAnotherUsersRun() {
        Fixture fixture = Fixture.create();
        AuthPrincipal alice = viewer("alice");
        AuthPrincipal bob = viewer("bob");
        UUID runId = UUID.randomUUID();
        ConversationSessionService.PreparedTurn aliceTurn = fixture.service.prepare(
                fixture.request(null, "alice 的问题"),
                null,
                alice
        );
        fixture.service.recordUser(aliceTurn.thread(), "alice 的问题", runId);
        fixture.service.recordAssistant(aliceTurn.thread(), "alice 的结论", runId);

        InvestigationRequest stolen = new InvestigationRequest(
                fixture.project.id(),
                Environment.TEST,
                "bob 继续",
                ConversationMode.ASK,
                null,
                null,
                null,
                null,
                runId,
                null
        );
        assertThrows(ForbiddenException.class, () -> fixture.service.prepare(stolen, null, bob));
        assertTrue(fixture.service.canAccessRun(runId, alice));
        assertFalse(fixture.service.canAccessRun(runId, bob));
        assertTrue(fixture.service.canAccessRun(runId, superAdmin()));
    }

    @Test
    void unownedHistoryIsHiddenFromViewers() {
        Fixture fixture = Fixture.create();
        fixture.service.prepare(fixture.request(null, "旧会话"), null);
        AuthPrincipal bob = viewer("bob");

        assertEquals(0, fixture.service.list(fixture.project.id(), bob).size());
        assertEquals(1, fixture.service.list(fixture.project.id(), superAdmin()).size());
        assertEquals(1, fixture.service.list(fixture.project.id()).size());

        InvestigationRun orphan = InvestigationRun.start(
                fixture.project.id(),
                Environment.TEST,
                ConversationMode.INVESTIGATE,
                "无主旧 run"
        ).complete("旧报告", InvestigationRun.RunStatus.COMPLETED);
        InvestigationRequest continueOrphan = new InvestigationRequest(
                fixture.project.id(),
                Environment.TEST,
                "接着看",
                ConversationMode.INVESTIGATE,
                null,
                null,
                null,
                null,
                orphan.id(),
                null
        );
        assertThrows(ForbiddenException.class, () -> fixture.service.prepare(continueOrphan, orphan, bob));
        assertFalse(fixture.service.canAccessRun(orphan.id(), bob));
        assertTrue(fixture.service.canAccessRun(orphan.id(), superAdmin()));
    }

    private static AuthPrincipal viewer(String name) {
        List<String> roles = new ArrayList<>();
        roles.add("VIEWER");
        List<String> permissions = new ArrayList<>();
        permissions.add(Permissions.INVESTIGATE);
        return new AuthPrincipal(UUID.randomUUID(), name, name, roles, permissions);
    }

    private static AuthPrincipal superAdmin() {
        List<String> roles = new ArrayList<>();
        roles.add(Permissions.SUPER_ADMIN);
        List<String> permissions = new ArrayList<>();
        permissions.add(Permissions.INVESTIGATE);
        return new AuthPrincipal(UUID.randomUUID(), "admin", "admin", roles, permissions);
    }

    private record Fixture(
            InMemoryProjectRepository projects,
            ConversationSessionService service,
            InvestigatorProject project
    ) {
        static Fixture create() {
            InMemoryProjectRepository projects = new InMemoryProjectRepository();
            InvestigatorProject project = projects.save(InvestigatorProject.create("Demo", "demo", "generic"));
            return new Fixture(projects, new ConversationSessionService(projects, new InMemoryConversationStore()), project);
        }

        InvestigationRequest request(UUID conversationId, String question) {
            return new InvestigationRequest(
                    project.id(),
                    Environment.TEST,
                    question,
                    ConversationMode.ASK,
                    null,
                    null,
                    null,
                    conversationId,
                    null,
                    null
            );
        }
    }
}
