package com.maiya.investigator.boot.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.maiya.investigator.domain.InvestigatorProject;
import com.maiya.investigator.domain.auth.AuthUser;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.infra.InMemoryAuthStore;
import com.maiya.investigator.infra.InMemoryProjectRepository;
import com.maiya.investigator.infra.audit.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class AuthRbacTest {
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private InMemoryAuthStore store;
    private InMemoryProjectRepository projects;
    private AuthAdminService admin;
    private AccessGuard access;
    private AuthService auth;
    private FakeFeishu feishu;
    private AuthUser adminUser;

    @BeforeEach
    void setUp() {
        store = new InMemoryAuthStore();
        projects = new InMemoryProjectRepository();
        admin = new AuthAdminService(store, projects, encoder);
        access = new AccessGuard(projects);
        feishu = new FakeFeishu();
        auth = new AuthService(
                store,
                encoder,
                feishu,
                new HttpSessionSecurityContextRepository(),
                mock(AuditTrail.class)
        );
        Instant now = Instant.now();
        adminUser = store.saveUser(new AuthUser(
                UUID.randomUUID(),
                "admin",
                "管理员",
                null,
                encoder.encode("secret"),
                AuthUser.ENABLED,
                now,
                now
        ));
        store.setUserRoles(adminUser.id(), List.of(InMemoryAuthStore.SUPER_ADMIN_ID));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passwordLoginAcceptsValidCredentials() {
        AuthUser user = auth.authenticatePassword("admin", "secret");
        assertEquals(adminUser.id(), user.id());
        assertThrows(UnauthorizedException.class, () -> auth.authenticatePassword("admin", "wrong"));
        assertThrows(UnauthorizedException.class, () -> auth.authenticatePassword("nobody", "secret"));
    }

    @Test
    void viewerIsDeniedAdminMenus() {
        AuthUser viewer = viewer("alice");
        asUser(viewer);
        assertThrows(ForbiddenException.class, () -> access.requirePermission(Permissions.USERS));
        assertThrows(ForbiddenException.class, () -> access.requirePermission(Permissions.ROLES));
        assertThrows(ForbiddenException.class, () -> access.requirePermission(Permissions.USAGE));
        access.requirePermission(Permissions.PROJECTS);
        access.requirePermission(Permissions.INVESTIGATE);
    }

    @Test
    void superAdminHasUsageMenu() {
        asUser(adminUser);
        access.requirePermission(Permissions.USAGE);
    }

    @Test
    void operatorHasAgentPlazaMenu() {
        Instant now = Instant.now();
        AuthUser operator = store.saveUser(new AuthUser(
                UUID.randomUUID(),
                "plaza-ops",
                "运维",
                null,
                encoder.encode("pass"),
                AuthUser.ENABLED,
                now,
                now
        ));
        store.setUserRoles(operator.id(), List.of(InMemoryAuthStore.OPERATOR_ID));
        asUser(operator);
        access.requirePermission(Permissions.AGENTS);
        asUser(viewer("cara"));
        assertThrows(ForbiddenException.class, () -> access.requirePermission(Permissions.AGENTS));
    }

    @Test
    void projectMenuSeesEveryProject() {
        InvestigatorProject first = projects.save(InvestigatorProject.create("可见", "visible", "generic"));
        InvestigatorProject second = projects.save(InvestigatorProject.create("隐藏", "hidden", "generic"));
        AuthUser viewer = viewer("bob");

        asUser(viewer);
        List<InvestigatorProject> mine = access.visibleProjects();
        assertEquals(2, mine.size());
        access.requireProject(first.id());
        access.requireProject(second.id());

        asUser(adminUser);
        assertEquals(2, access.visibleProjects().size());
        access.requireProject(second.id());
    }

    @Test
    void feishuCallbackCreatesViewerWithoutProjects() {
        feishu.profile = new FeishuOAuthClient.FeishuProfile("ou_open_1", "飞书用户", "user@example.com");
        AuthUser created = auth.completeFeishu("code", "state-1", "state-1");
        assertEquals("飞书用户", created.displayName());
        assertTrue(store.rolesForUser(created.id()).stream().anyMatch(role -> role.code().equals("VIEWER")));
        assertTrue(store.projectIdsForUser(created.id()).isEmpty());
        assertThrows(UnauthorizedException.class, () -> auth.completeFeishu("code", "left", "right"));
        AuthUser again = auth.completeFeishu("code", "state-2", "state-2");
        assertEquals(created.id(), again.id());
    }

    @Test
    void cannotDisableLastSuperAdmin() {
        assertThrows(IllegalArgumentException.class, () -> admin.setStatus(adminUser.id(), AuthUser.DISABLED));
        AuthAdminService.UserView operator = admin.createUser(new AuthAdminService.CreateUserRequest(
                "ops",
                "运维",
                null,
                "pass123",
                List.of(InMemoryAuthStore.OPERATOR_ID),
                List.of()
        ));
        assertTrue(operator.roleCodes().contains("OPERATOR"));
        admin.setStatus(operator.id(), AuthUser.DISABLED);
        assertEquals(AuthUser.DISABLED, store.user(operator.id()).orElseThrow().status());
    }

    private AuthUser viewer(String username) {
        Instant now = Instant.now();
        AuthUser user = store.saveUser(new AuthUser(
                UUID.randomUUID(),
                username,
                username,
                null,
                encoder.encode("pass"),
                AuthUser.ENABLED,
                now,
                now
        ));
        store.setUserRoles(user.id(), List.of(InMemoryAuthStore.VIEWER_ID));
        return user;
    }

    private void asUser(AuthUser user) {
        AuthPrincipal principal = AuthPrincipal.from(user, store);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities())
        );
    }

    private static final class FakeFeishu implements FeishuOAuthClient {
        private FeishuProfile profile = new FeishuProfile("ou_x", "飞书", "");

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public String authorizeUrl(String state) {
            return "https://open.feishu.cn/mock?state=" + state;
        }

        @Override
        public FeishuProfile redeem(String code) {
            return profile;
        }
    }

    @Test
    void operatorCannotMutateBindings() {
        Instant now = Instant.now();
        AuthUser operator = store.saveUser(new AuthUser(
                UUID.randomUUID(),
                "bind-ops",
                "运维",
                null,
                encoder.encode("pass"),
                AuthUser.ENABLED,
                now,
                now
        ));
        store.setUserRoles(operator.id(), List.of(InMemoryAuthStore.OPERATOR_ID));
        asUser(operator);
        assertThrows(ForbiddenException.class, () -> access.requirePermission(Permissions.BINDINGS));
        assertThrows(ForbiddenException.class, () -> access.requirePermission(Permissions.BINDINGS_WRITE));
        assertThrows(ForbiddenException.class, () -> access.requirePermission(Permissions.BINDINGS_DELETE));
        access.requirePermission(Permissions.PROJECTS_WRITE);
        asUser(adminUser);
        access.requirePermission(Permissions.BINDINGS_WRITE);
        access.requirePermission(Permissions.AUDIT);
    }

}
