package com.maiya.investigator.boot.auth;

import com.maiya.investigator.domain.ProjectRepository;
import com.maiya.investigator.domain.auth.AuthPermission;
import com.maiya.investigator.domain.auth.AuthRole;
import com.maiya.investigator.domain.auth.AuthStore;
import com.maiya.investigator.domain.auth.AuthUser;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.auth.UserIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthAdminService {
    private final AuthStore store;
    private final ProjectRepository projects;
    private final PasswordEncoder passwords;

    public AuthAdminService(AuthStore store, ProjectRepository projects, PasswordEncoder passwords) {
        this.store = store;
        this.projects = projects;
        this.passwords = passwords;
    }

    public List<UserView> users() {
        return store.users().stream().map(this::toView).toList();
    }

    public UserView createUser(CreateUserRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("请填写用户名");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("请填写密码");
        }
        String username = request.username().trim();
        if (store.userByUsername(username).isPresent()) {
            throw new IllegalArgumentException("用户名已存在：" + username);
        }
        Instant now = Instant.now();
        AuthUser user = store.saveUser(new AuthUser(
                UUID.randomUUID(),
                username,
                blank(request.displayName()) ? username : request.displayName().trim(),
                blank(request.email()) ? null : request.email().trim(),
                passwords.encode(request.password()),
                AuthUser.ENABLED,
                now,
                now
        ));
        store.saveIdentity(new UserIdentity(UUID.randomUUID(), user.id(), UserIdentity.PASSWORD, username));
        store.setUserRoles(user.id(), resolveRoleIds(request.roleIds()));
        store.setUserProjects(user.id(), resolveProjectIds(request.projectIds()));
        return toView(user);
    }

    public UserView updateUser(UUID id, UpdateUserRequest request) {
        AuthUser current = requireUser(id);
        Instant now = Instant.now();
        String hash = current.passwordHash();
        if (!blank(request.password())) {
            hash = passwords.encode(request.password());
        }
        AuthUser saved = store.saveUser(new AuthUser(
                current.id(),
                current.username(),
                blank(request.displayName()) ? current.displayName() : request.displayName().trim(),
                request.email() == null ? current.email() : blankToNull(request.email()),
                hash,
                current.status(),
                current.createdAt(),
                now
        ));
        return toView(saved);
    }

    public UserView setStatus(UUID id, String status) {
        AuthUser current = requireUser(id);
        String next = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!AuthUser.ENABLED.equals(next) && !AuthUser.DISABLED.equals(next)) {
            throw new IllegalArgumentException("状态只能是 ENABLED 或 DISABLED");
        }
        if (AuthUser.DISABLED.equals(next)) {
            ensureNotLastSuperAdmin(id);
        }
        return toView(store.saveUser(current.withDetails(current.displayName(), current.email(), next, Instant.now())));
    }

    public UserView setRoles(UUID id, List<UUID> roleIds) {
        requireUser(id);
        List<UUID> next = resolveRoleIds(roleIds);
        if (!next.contains(superAdminRoleId()) && isLastSuperAdmin(id)) {
            throw new IllegalArgumentException("不能移除最后一名超级管理员");
        }
        store.setUserRoles(id, next);
        return toView(requireUser(id));
    }

    public UserView setProjects(UUID id, List<UUID> projectIds) {
        requireUser(id);
        store.setUserProjects(id, resolveProjectIds(projectIds));
        return toView(requireUser(id));
    }

    public void deleteUser(UUID id) {
        requireUser(id);
        ensureNotLastSuperAdmin(id);
        store.deleteUser(id);
    }

    public List<RoleView> roles() {
        return store.roles().stream().map(this::toRoleView).toList();
    }

    public List<AuthPermission> permissions() {
        return store.permissions();
    }

    public RoleView createRole(RoleRequest request) {
        String code = requireCode(request.code());
        if (store.roleByCode(code).isPresent()) {
            throw new IllegalArgumentException("角色编码已存在：" + code);
        }
        AuthRole role = store.saveRole(new AuthRole(
                UUID.randomUUID(),
                code,
                blank(request.name()) ? code : request.name().trim(),
                false,
                request.description()
        ));
        store.setRolePermissions(role.id(), request.permissionCodes() == null ? List.of() : request.permissionCodes());
        return toRoleView(role);
    }

    public RoleView updateRole(UUID id, RoleRequest request) {
        AuthRole current = store.role(id).orElseThrow(() -> new IllegalArgumentException("角色不存在：" + id));
        AuthRole saved = store.saveRole(new AuthRole(
                current.id(),
                current.code(),
                blank(request.name()) ? current.name() : request.name().trim(),
                current.builtin(),
                request.description() == null ? current.description() : request.description()
        ));
        if (request.permissionCodes() != null) {
            store.setRolePermissions(id, request.permissionCodes());
        }
        return toRoleView(saved);
    }

    public void deleteRole(UUID id) {
        AuthRole role = store.role(id).orElseThrow(() -> new IllegalArgumentException("角色不存在：" + id));
        if (role.builtin()) {
            throw new IllegalArgumentException("内置角色不可删除");
        }
        store.deleteRole(id);
    }

    public RoleView setRolePermissions(UUID id, List<String> permissionCodes) {
        store.role(id).orElseThrow(() -> new IllegalArgumentException("角色不存在：" + id));
        store.setRolePermissions(id, permissionCodes == null ? List.of() : permissionCodes);
        return toRoleView(store.role(id).orElseThrow());
    }

    public List<com.maiya.investigator.domain.InvestigatorProject> allProjects() {
        return projects.findAll();
    }

    private UserView toView(AuthUser user) {
        List<AuthRole> roles = store.rolesForUser(user.id());
        return new UserView(
                user.id(),
                user.username(),
                user.displayName(),
                user.email(),
                user.status(),
                user.createdAt(),
                roles.stream().map(AuthRole::id).toList(),
                roles.stream().map(AuthRole::code).toList(),
                store.projectIdsForUser(user.id())
        );
    }

    private RoleView toRoleView(AuthRole role) {
        return new RoleView(
                role.id(),
                role.code(),
                role.name(),
                role.builtin(),
                role.description(),
                store.permissionCodesForRole(role.id())
        );
    }

    private AuthUser requireUser(UUID id) {
        return store.user(id).orElseThrow(() -> new IllegalArgumentException("用户不存在：" + id));
    }

    private List<UUID> resolveRoleIds(List<UUID> roleIds) {
        if (roleIds == null) {
            return List.of();
        }
        return roleIds.stream()
                .peek(id -> store.role(id).orElseThrow(() -> new IllegalArgumentException("角色不存在：" + id)))
                .toList();
    }

    private List<UUID> resolveProjectIds(List<UUID> projectIds) {
        if (projectIds == null) {
            return List.of();
        }
        return projectIds.stream()
                .peek(id -> projects.findById(id).orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id)))
                .toList();
    }

    private void ensureNotLastSuperAdmin(UUID userId) {
        if (isLastSuperAdmin(userId)) {
            throw new IllegalArgumentException("不能移除最后一名超级管理员");
        }
    }

    private boolean isLastSuperAdmin(UUID userId) {
        UUID superId = superAdminRoleId();
        List<AuthUser> supers = store.users().stream()
                .filter(AuthUser::enabled)
                .filter(user -> store.rolesForUser(user.id()).stream().anyMatch(role -> role.id().equals(superId)))
                .toList();
        return supers.size() == 1 && supers.get(0).id().equals(userId);
    }

    private UUID superAdminRoleId() {
        return store.roleByCode(Permissions.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("缺少 SUPER_ADMIN 角色"))
                .id();
    }

    private static String requireCode(String code) {
        if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_]{1,39}")) {
            throw new IllegalArgumentException("角色编码需为字母开头的标识");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    public record UserView(
            UUID id,
            String username,
            String displayName,
            String email,
            String status,
            Instant createdAt,
            List<UUID> roleIds,
            List<String> roleCodes,
            List<UUID> projectIds
    ) {
    }

    public record CreateUserRequest(
            String username,
            String displayName,
            String email,
            String password,
            List<UUID> roleIds,
            List<UUID> projectIds
    ) {
    }

    public record UpdateUserRequest(String displayName, String email, String password) {
    }

    public record RoleRequest(String code, String name, String description, List<String> permissionCodes) {
    }

    public record RoleView(
            UUID id,
            String code,
            String name,
            boolean builtin,
            String description,
            List<String> permissionCodes
    ) {
    }
}
