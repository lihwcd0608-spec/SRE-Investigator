package com.maiya.investigator.boot.auth;

import com.maiya.investigator.domain.auth.AuthStore;
import com.maiya.investigator.domain.auth.AuthUser;
import com.maiya.investigator.domain.auth.Permissions;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthPrincipal(
        UUID userId,
        String username,
        String displayName,
        List<String> roles,
        List<String> permissions
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static AuthPrincipal from(AuthUser user, AuthStore store) {
        return new AuthPrincipal(
                user.id(),
                user.username(),
                user.displayName(),
                store.rolesForUser(user.id()).stream().map(role -> role.code()).toList(),
                store.permissionCodesForUser(user.id())
        );
    }

    public boolean superAdmin() {
        return roles.contains(Permissions.SUPER_ADMIN);
    }

    public boolean hasPermission(String code) {
        return superAdmin() || permissions.contains(code);
    }

    public Collection<GrantedAuthority> authorities() {
        List<GrantedAuthority> granted = new ArrayList<>();
        granted.add(new SimpleGrantedAuthority("ROLE_USER"));
        roles.forEach(role -> granted.add(new SimpleGrantedAuthority("ROLE_" + role)));
        permissions.forEach(code -> granted.add(new SimpleGrantedAuthority(code)));
        return granted;
    }
}
