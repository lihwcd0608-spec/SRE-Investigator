package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.domain.auth.AuthPermission;
import com.maiya.investigator.domain.auth.AuthRole;
import com.maiya.investigator.domain.auth.AuthStore;
import com.maiya.investigator.domain.auth.AuthUser;
import com.maiya.investigator.domain.auth.UserIdentity;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mysql")
public class MysqlAuthStore implements AuthStore {
    private final AuthUserJpaRepository users;
    private final UserIdentityJpaRepository identities;
    private final AuthRoleJpaRepository roles;
    private final AuthPermissionJpaRepository permissions;
    private final UserRoleJpaRepository userRoles;
    private final RolePermissionJpaRepository rolePermissions;
    private final ProjectMemberJpaRepository members;

    public MysqlAuthStore(
            AuthUserJpaRepository users,
            UserIdentityJpaRepository identities,
            AuthRoleJpaRepository roles,
            AuthPermissionJpaRepository permissions,
            UserRoleJpaRepository userRoles,
            RolePermissionJpaRepository rolePermissions,
            ProjectMemberJpaRepository members
    ) {
        this.users = users;
        this.identities = identities;
        this.roles = roles;
        this.permissions = permissions;
        this.userRoles = userRoles;
        this.rolePermissions = rolePermissions;
        this.members = members;
    }

    @Override
    public long userCount() {
        return users.count();
    }

    @Override
    public List<AuthUser> users() {
        return users.findAll().stream().map(MysqlAuthStore::toUser).toList();
    }

    @Override
    public Optional<AuthUser> user(UUID id) {
        return users.findById(id).map(MysqlAuthStore::toUser);
    }

    @Override
    public Optional<AuthUser> userByUsername(String username) {
        return users.findByUsername(username).map(MysqlAuthStore::toUser);
    }

    @Override
    public AuthUser saveUser(AuthUser user) {
        return toUser(users.save(new AuthUserEntity(
                user.id(),
                user.username(),
                user.displayName(),
                user.email(),
                user.passwordHash(),
                user.status(),
                user.createdAt(),
                user.updatedAt()
        )));
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        identities.deleteByUserId(id);
        userRoles.deleteByUserId(id);
        members.deleteByUserId(id);
        users.deleteById(id);
    }

    @Override
    public Optional<UserIdentity> identity(String provider, String externalId) {
        return identities.findByProviderAndExternalId(provider, externalId)
                .map(item -> new UserIdentity(item.getId(), item.getUserId(), item.getProvider(), item.getExternalId()));
    }

    @Override
    public UserIdentity saveIdentity(UserIdentity identity) {
        UserIdentityEntity saved = identities.save(new UserIdentityEntity(
                identity.id(),
                identity.userId(),
                identity.provider(),
                identity.externalId()
        ));
        return new UserIdentity(saved.getId(), saved.getUserId(), saved.getProvider(), saved.getExternalId());
    }

    @Override
    public List<AuthRole> roles() {
        return roles.findAll().stream().map(MysqlAuthStore::toRole).toList();
    }

    @Override
    public Optional<AuthRole> role(UUID id) {
        return roles.findById(id).map(MysqlAuthStore::toRole);
    }

    @Override
    public Optional<AuthRole> roleByCode(String code) {
        return roles.findByCode(code).map(MysqlAuthStore::toRole);
    }

    @Override
    public AuthRole saveRole(AuthRole role) {
        return toRole(roles.save(new AuthRoleEntity(
                role.id(),
                role.code(),
                role.name(),
                role.builtin(),
                role.description()
        )));
    }

    @Override
    @Transactional
    public void deleteRole(UUID id) {
        rolePermissions.deleteByRoleId(id);
        userRoles.deleteByRoleId(id);
        roles.deleteById(id);
    }

    @Override
    public List<AuthPermission> permissions() {
        return permissions.findAllByOrderBySortOrderAsc().stream()
                .map(item -> new AuthPermission(
                        item.getId(),
                        item.getCode(),
                        item.getName(),
                        item.getType(),
                        item.getMenuPath(),
                        item.getSortOrder()
                ))
                .toList();
    }

    @Override
    public List<AuthRole> rolesForUser(UUID userId) {
        return userRoles.findByUserId(userId).stream()
                .map(UserRoleEntity::getRoleId)
                .map(roles::findById)
                .flatMap(Optional::stream)
                .map(MysqlAuthStore::toRole)
                .toList();
    }

    @Override
    public List<String> permissionCodesForUser(UUID userId) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (UserRoleEntity link : userRoles.findByUserId(userId)) {
            codes.addAll(permissionCodesForRole(link.getRoleId()));
        }
        return List.copyOf(codes);
    }

    @Override
    public List<String> permissionCodesForRole(UUID roleId) {
        return rolePermissions.findByRoleId(roleId).stream()
                .map(RolePermissionEntity::getPermissionId)
                .map(permissions::findById)
                .flatMap(Optional::stream)
                .map(AuthPermissionEntity::getCode)
                .toList();
    }

    @Override
    @Transactional
    public void setUserRoles(UUID userId, List<UUID> roleIds) {
        userRoles.deleteByUserId(userId);
        for (UUID roleId : roleIds) {
            userRoles.save(new UserRoleEntity(userId, roleId));
        }
    }

    @Override
    @Transactional
    public void setRolePermissions(UUID roleId, List<String> permissionCodes) {
        rolePermissions.deleteByRoleId(roleId);
        for (String code : permissionCodes) {
            AuthPermissionEntity permission = permissions.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("未知权限：" + code));
            rolePermissions.save(new RolePermissionEntity(roleId, permission.getId()));
        }
    }

    @Override
    public List<UUID> projectIdsForUser(UUID userId) {
        return members.findByUserId(userId).stream().map(ProjectMemberEntity::getProjectId).toList();
    }

    @Override
    public boolean isProjectMember(UUID userId, UUID projectId) {
        return members.existsByProjectIdAndUserId(projectId, userId);
    }

    @Override
    public void addProjectMember(UUID projectId, UUID userId) {
        if (members.existsByProjectIdAndUserId(projectId, userId)) {
            return;
        }
        members.save(new ProjectMemberEntity(projectId, userId, Instant.now()));
    }

    @Override
    @Transactional
    public void setUserProjects(UUID userId, List<UUID> projectIds) {
        members.deleteByUserId(userId);
        Instant now = Instant.now();
        for (UUID projectId : projectIds) {
            members.save(new ProjectMemberEntity(projectId, userId, now));
        }
    }

    @Override
    @Transactional
    public void deleteProjectMembers(UUID projectId) {
        members.deleteByProjectId(projectId);
    }

    @Override
    @Transactional
    public void deleteUserMemberships(UUID userId) {
        members.deleteByUserId(userId);
    }

    private static AuthUser toUser(AuthUserEntity entity) {
        return new AuthUser(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static AuthRole toRole(AuthRoleEntity entity) {
        return new AuthRole(entity.getId(), entity.getCode(), entity.getName(), entity.isBuiltin(), entity.getDescription());
    }
}
