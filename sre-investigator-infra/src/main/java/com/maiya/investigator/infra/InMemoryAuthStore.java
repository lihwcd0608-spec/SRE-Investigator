package com.maiya.investigator.infra;

import com.maiya.investigator.domain.auth.AuthPermission;
import com.maiya.investigator.domain.auth.AuthRole;
import com.maiya.investigator.domain.auth.AuthStore;
import com.maiya.investigator.domain.auth.AuthUser;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.auth.UserIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryAuthStore implements AuthStore {
    public static final UUID SUPER_ADMIN_ID = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    public static final UUID OPERATOR_ID = UUID.fromString("b1000000-0000-0000-0000-000000000002");
    public static final UUID VIEWER_ID = UUID.fromString("b1000000-0000-0000-0000-000000000003");

    private final ConcurrentHashMap<UUID, AuthUser> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UserIdentity> identities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AuthRole> roles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AuthPermission> permissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<UUID>> userRoles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<String>> rolePermissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<UUID>> userProjects = new ConcurrentHashMap<>();

    public InMemoryAuthStore() {
        seed();
    }

    @Override
    public long userCount() {
        return users.size();
    }

    @Override
    public List<AuthUser> users() {
        return users.values().stream()
                .sorted(Comparator.comparing(AuthUser::username))
                .toList();
    }

    @Override
    public Optional<AuthUser> user(UUID id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public Optional<AuthUser> userByUsername(String username) {
        return users.values().stream().filter(item -> item.username().equals(username)).findFirst();
    }

    @Override
    public AuthUser saveUser(AuthUser user) {
        boolean taken = users.values().stream()
                .anyMatch(item -> item.username().equals(user.username()) && !item.id().equals(user.id()));
        if (taken) {
            throw new IllegalArgumentException("用户名已存在：" + user.username());
        }
        users.put(user.id(), user);
        return user;
    }

    @Override
    public void deleteUser(UUID id) {
        users.remove(id);
        identities.values().removeIf(item -> item.userId().equals(id));
        userRoles.remove(id);
        userProjects.remove(id);
    }

    @Override
    public Optional<UserIdentity> identity(String provider, String externalId) {
        return identities.values().stream()
                .filter(item -> item.provider().equals(provider) && item.externalId().equals(externalId))
                .findFirst();
    }

    @Override
    public UserIdentity saveIdentity(UserIdentity identity) {
        identities.put(identity.id(), identity);
        return identity;
    }

    @Override
    public List<AuthRole> roles() {
        return roles.values().stream().sorted(Comparator.comparing(AuthRole::code)).toList();
    }

    @Override
    public Optional<AuthRole> role(UUID id) {
        return Optional.ofNullable(roles.get(id));
    }

    @Override
    public Optional<AuthRole> roleByCode(String code) {
        return roles.values().stream().filter(item -> item.code().equals(code)).findFirst();
    }

    @Override
    public AuthRole saveRole(AuthRole role) {
        boolean taken = roles.values().stream()
                .anyMatch(item -> item.code().equals(role.code()) && !item.id().equals(role.id()));
        if (taken) {
            throw new IllegalArgumentException("角色编码已存在：" + role.code());
        }
        roles.put(role.id(), role);
        rolePermissions.putIfAbsent(role.id(), new CopyOnWriteArrayList<>());
        return role;
    }

    @Override
    public void deleteRole(UUID id) {
        AuthRole role = roles.get(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在：" + id);
        }
        if (role.builtin()) {
            throw new IllegalArgumentException("内置角色不可删除");
        }
        roles.remove(id);
        rolePermissions.remove(id);
        userRoles.values().forEach(list -> list.remove(id));
    }

    @Override
    public List<AuthPermission> permissions() {
        return permissions.values().stream()
                .sorted(Comparator.comparingInt(AuthPermission::sortOrder))
                .toList();
    }

    @Override
    public List<AuthRole> rolesForUser(UUID userId) {
        return userRoles.getOrDefault(userId, new CopyOnWriteArrayList<>()).stream()
                .map(roles::get)
                .filter(item -> item != null)
                .toList();
    }

    @Override
    public List<String> permissionCodesForUser(UUID userId) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (UUID roleId : userRoles.getOrDefault(userId, new CopyOnWriteArrayList<>())) {
            codes.addAll(permissionCodesForRole(roleId));
        }
        return List.copyOf(codes);
    }

    @Override
    public List<String> permissionCodesForRole(UUID roleId) {
        return List.copyOf(rolePermissions.getOrDefault(roleId, new CopyOnWriteArrayList<>()));
    }

    @Override
    public void setUserRoles(UUID userId, List<UUID> roleIds) {
        userRoles.put(userId, new CopyOnWriteArrayList<>(new LinkedHashSet<>(roleIds)));
    }

    @Override
    public void setRolePermissions(UUID roleId, List<String> permissionCodes) {
        List<String> known = permissionCodes.stream()
                .filter(code -> permissions.values().stream().anyMatch(item -> item.code().equals(code)))
                .toList();
        if (known.size() != permissionCodes.size()) {
            List<String> missing = new ArrayList<>(permissionCodes);
            missing.removeAll(known);
            throw new IllegalArgumentException("未知权限：" + String.join(", ", missing));
        }
        rolePermissions.put(roleId, new CopyOnWriteArrayList<>(new LinkedHashSet<>(known)));
    }

    @Override
    public List<UUID> projectIdsForUser(UUID userId) {
        return List.copyOf(userProjects.getOrDefault(userId, new CopyOnWriteArrayList<>()));
    }

    @Override
    public boolean isProjectMember(UUID userId, UUID projectId) {
        return userProjects.getOrDefault(userId, new CopyOnWriteArrayList<>()).contains(projectId);
    }

    @Override
    public void addProjectMember(UUID projectId, UUID userId) {
        userProjects.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>());
        if (!userProjects.get(userId).contains(projectId)) {
            userProjects.get(userId).add(projectId);
        }
    }

    @Override
    public void setUserProjects(UUID userId, List<UUID> projectIds) {
        userProjects.put(userId, new CopyOnWriteArrayList<>(new LinkedHashSet<>(projectIds)));
    }

    @Override
    public void deleteProjectMembers(UUID projectId) {
        userProjects.values().forEach(list -> list.remove(projectId));
    }

    @Override
    public void deleteUserMemberships(UUID userId) {
        userProjects.remove(userId);
    }

    private void seed() {
        addPermission("a1000000-0000-0000-0000-000000000001", Permissions.PROJECTS, "项目", "MENU", "/projects", 10);
        addPermission("a1000000-0000-0000-0000-000000000002", Permissions.REPOSITORIES, "代码仓", "MENU", "/repositories", 20);
        addPermission("a1000000-0000-0000-0000-000000000003", Permissions.BINDINGS, "工具", "MENU", "/bindings", 30);
        addPermission("a1000000-0000-0000-0000-000000000011", Permissions.AGENTS, "领域专家编排", "MENU", "/agents", 32);
        addPermission("a1000000-0000-0000-0000-000000000009", Permissions.AUTOMATIONS, "定时", "MENU", "/automations", 35);
        addPermission("a1000000-0000-0000-0000-000000000004", Permissions.INVESTIGATE, "对话", "MENU", "/investigate", 40);
        addPermission("a1000000-0000-0000-0000-000000000010", Permissions.REVIEW, "代码审查", "MENU", "/review", 45);
        addPermission("a1000000-0000-0000-0000-000000000005", Permissions.MODEL, "模型", "MENU", "/settings/model", 50);
        addPermission("a1000000-0000-0000-0000-000000000006", Permissions.USERS, "用户", "MENU", "/settings/users", 60);
        addPermission("a1000000-0000-0000-0000-000000000007", Permissions.ROLES, "角色", "MENU", "/settings/roles", 70);
        addPermission("a1000000-0000-0000-0000-000000000008", Permissions.USAGE, "用量", "MENU", "/settings/usage", 80);
        addPermission("a1000000-0000-0000-0000-000000000012", Permissions.AUDIT, "审计日志", "MENU", "/settings/audit", 90);

        addPermission("a2000000-0000-0000-0000-000000000001", Permissions.PROJECTS_WRITE, "项目-创建/修改", "ACTION", null, 110);
        addPermission("a2000000-0000-0000-0000-000000000002", Permissions.PROJECTS_DELETE, "项目-删除", "ACTION", null, 111);
        addPermission("a2000000-0000-0000-0000-000000000003", Permissions.REPOSITORIES_WRITE, "代码仓-创建/修改", "ACTION", null, 120);
        addPermission("a2000000-0000-0000-0000-000000000004", Permissions.REPOSITORIES_DELETE, "代码仓-删除", "ACTION", null, 121);
        addPermission("a2000000-0000-0000-0000-000000000005", Permissions.BINDINGS_WRITE, "工具-创建/修改", "ACTION", null, 130);
        addPermission("a2000000-0000-0000-0000-000000000006", Permissions.BINDINGS_DELETE, "工具-删除", "ACTION", null, 131);
        addPermission("a2000000-0000-0000-0000-000000000007", Permissions.AGENTS_WRITE, "专家编排-创建/修改", "ACTION", null, 140);
        addPermission("a2000000-0000-0000-0000-000000000008", Permissions.AGENTS_DELETE, "专家编排-删除", "ACTION", null, 141);
        addPermission("a2000000-0000-0000-0000-000000000009", Permissions.AUTOMATIONS_WRITE, "定时-创建/修改", "ACTION", null, 150);
        addPermission("a2000000-0000-0000-0000-000000000010", Permissions.AUTOMATIONS_DELETE, "定时-删除", "ACTION", null, 151);
        addPermission("a2000000-0000-0000-0000-000000000011", Permissions.MODEL_WRITE, "模型-修改", "ACTION", null, 160);
        addPermission("a2000000-0000-0000-0000-000000000012", Permissions.USERS_WRITE, "用户-管理", "ACTION", null, 170);
        addPermission("a2000000-0000-0000-0000-000000000013", Permissions.ROLES_WRITE, "角色-管理", "ACTION", null, 180);
        addPermission("a2000000-0000-0000-0000-000000000014", Permissions.FEISHU_WRITE, "飞书群-配置", "ACTION", null, 190);

        roles.put(SUPER_ADMIN_ID, new AuthRole(SUPER_ADMIN_ID, Permissions.SUPER_ADMIN, "超级管理员", true, "全部菜单与操作，可见全部项目"));
        roles.put(OPERATOR_ID, new AuthRole(OPERATOR_ID, "OPERATOR", "运维", true, "项目、代码仓、领域专家编排、定时、对话、审查；不可配置工具"));
        roles.put(VIEWER_ID, new AuthRole(VIEWER_ID, "VIEWER", "只读", true, "项目和对话"));

        rolePermissions.put(SUPER_ADMIN_ID, new CopyOnWriteArrayList<>(permissions.values().stream()
                .sorted(Comparator.comparingInt(AuthPermission::sortOrder))
                .map(AuthPermission::code)
                .toList()));
        rolePermissions.put(OPERATOR_ID, new CopyOnWriteArrayList<>(List.of(
                Permissions.PROJECTS, Permissions.REPOSITORIES, Permissions.AGENTS, Permissions.AUTOMATIONS,
                Permissions.INVESTIGATE, Permissions.REVIEW,
                Permissions.PROJECTS_WRITE, Permissions.PROJECTS_DELETE,
                Permissions.REPOSITORIES_WRITE, Permissions.REPOSITORIES_DELETE,
                Permissions.AGENTS_WRITE, Permissions.AGENTS_DELETE,
                Permissions.AUTOMATIONS_WRITE, Permissions.AUTOMATIONS_DELETE
        )));
        rolePermissions.put(VIEWER_ID, new CopyOnWriteArrayList<>(List.of(
                Permissions.PROJECTS, Permissions.INVESTIGATE
        )));
    }

    private void addPermission(String id, String code, String name, String type, String path, int order) {
        UUID uuid = UUID.fromString(id);
        permissions.put(uuid, new AuthPermission(uuid, code, name, type, path, order));
    }
}
