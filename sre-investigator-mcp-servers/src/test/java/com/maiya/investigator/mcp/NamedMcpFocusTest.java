package com.maiya.investigator.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.control.ToolBinding;
import com.maiya.investigator.domain.control.ToolKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NamedMcpFocusTest {
    @Test
    void parsesNamedMcpFromQuestion() {
        Optional<String> profile = NamedMcpFocus.parseRequestedProfile(
                "查一下这个人：2009475153714520065的用户画像，使用 zg_mongodb_test mcp"
        );
        assertTrue(profile.isPresent());
        assertEquals("zg_mongodb_test", profile.get());
    }

    @Test
    void keepsNamedMcpFromPriorUserMessage() {
        Optional<String> profile = NamedMcpFocus.requestedProfile(
                "继续",
                null,
                List.of("使用 zg_mongodb_test mcp 查 ums_user_tag_attr")
        );
        assertTrue(profile.isPresent());
        assertEquals("zg_mongodb_test", profile.get());
    }

    @Test
    void resolvesFromBindings() {
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        List<ToolBinding> bindings = List.of(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.MONGODB, "zg_mongodb_test", now),
                ToolBinding.create(projectId, Environment.TEST, ToolKind.REDIS, "test-redis", now)
        );
        Optional<NamedMcpFocus> focus = NamedMcpFocus.resolve(
                bindings,
                "使用 zg_mongodb_test mcp 查用户画像",
                null,
                List.of(),
                List.of("test-redis"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("zg_mongodb_test"),
                List.of(),
                List.of(),
                List.of()
        );
        assertTrue(focus.isPresent());
        assertEquals("zg_mongodb_test", focus.get().profile());
        assertEquals(ToolKind.MONGODB, focus.get().toolKind());
    }

    @Test
    void resolvesFromExplicitProfileWithoutQuestionMention() {
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        List<ToolBinding> bindings = List.of(
                ToolBinding.create(projectId, Environment.TEST, ToolKind.MONGODB, "zg_mongodb_test", now)
        );
        Optional<NamedMcpFocus> focus = NamedMcpFocus.resolve(
                bindings,
                "说错了查这个集合：ums_user_tag_attr",
                "zg_mongodb_test",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("zg_mongodb_test"),
                List.of(),
                List.of(),
                List.of()
        );
        assertTrue(focus.isPresent());
        assertEquals(ToolKind.MONGODB, focus.get().toolKind());
    }

    @Test
    void narrowsProfilesToNamedMcpOnly() {
        NamedMcpFocus focus = new NamedMcpFocus("zg_mongodb_test", ToolKind.MONGODB);
        NamedMcpFocus.FocusedProfiles narrowed = focus.narrow(
                List.of("my_test"),
                List.of("test-redis"),
                List.of("sls-test"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("zg_mongodb_test"),
                List.of(),
                List.of(),
                List.of()
        );
        assertTrue(narrowed.mysqlProfiles().isEmpty());
        assertTrue(narrowed.redisProfiles().isEmpty());
        assertTrue(narrowed.logsProfiles().isEmpty());
        assertEquals(List.of("zg_mongodb_test"), narrowed.mongodbProfiles());
    }
}
