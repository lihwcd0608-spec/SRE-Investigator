package com.maiya.investigator.domain.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthStore {
    long userCount();

    List<AuthUser> users();

    Optional<AuthUser> user(UUID id);

    Optional<AuthUser> userByUsername(String username);

    AuthUser saveUser(AuthUser user);

    void deleteUser(UUID id);

    Optional<UserIdentity> identity(String provider, String externalId);

    UserIdentity saveIdentity(UserIdentity identity);

    List<AuthRole> roles();

    Optional<AuthRole> role(UUID id);

    Optional<AuthRole> roleByCode(String code);

    AuthRole saveRole(AuthRole role);

    void deleteRole(UUID id);

    List<AuthPermission> permissions();

    List<AuthRole> rolesForUser(UUID userId);

    List<String> permissionCodesForUser(UUID userId);

    List<String> permissionCodesForRole(UUID roleId);

    void setUserRoles(UUID userId, List<UUID> roleIds);

    void setRolePermissions(UUID roleId, List<String> permissionCodes);

    List<UUID> projectIdsForUser(UUID userId);

    boolean isProjectMember(UUID userId, UUID projectId);

    void addProjectMember(UUID projectId, UUID userId);

    void setUserProjects(UUID userId, List<UUID> projectIds);

    void deleteProjectMembers(UUID projectId);

    void deleteUserMemberships(UUID userId);
}
