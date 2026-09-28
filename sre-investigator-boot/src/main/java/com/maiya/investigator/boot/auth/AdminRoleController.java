package com.maiya.investigator.boot.auth;

import com.maiya.investigator.domain.auth.AuthPermission;
import com.maiya.investigator.domain.auth.Permissions;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequirePermission(Permissions.ROLES)
public class AdminRoleController {
    private final AuthAdminService admin;

    public AdminRoleController(AuthAdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/roles")
    @RequirePermission(any = {Permissions.ROLES, Permissions.USERS})
    public List<AuthAdminService.RoleView> roles() {
        return admin.roles();
    }

    @GetMapping("/permissions")
    @RequirePermission(any = {Permissions.ROLES, Permissions.USERS})
    public List<AuthPermission> permissions() {
        return admin.permissions();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.ROLES_WRITE)
    public AuthAdminService.RoleView create(@RequestBody AuthAdminService.RoleRequest request) {
        return admin.createRole(request);
    }

    @PutMapping("/roles/{id}")
    @RequirePermission(Permissions.ROLES_WRITE)
    public AuthAdminService.RoleView update(@PathVariable UUID id, @RequestBody AuthAdminService.RoleRequest request) {
        return admin.updateRole(id, request);
    }

    @PutMapping("/roles/{id}/permissions")
    @RequirePermission(Permissions.ROLES_WRITE)
    public AuthAdminService.RoleView permissions(@PathVariable UUID id, @RequestBody PermissionsRequest request) {
        return admin.setRolePermissions(id, request.permissionCodes());
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.ROLES_WRITE)
    public void delete(@PathVariable UUID id) {
        admin.deleteRole(id);
    }

    public record PermissionsRequest(List<String> permissionCodes) {
    }
}
