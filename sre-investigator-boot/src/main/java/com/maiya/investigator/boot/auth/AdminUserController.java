package com.maiya.investigator.boot.auth;

import com.maiya.investigator.domain.InvestigatorProject;
import com.maiya.investigator.domain.auth.Permissions;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequirePermission(Permissions.USERS)
public class AdminUserController {
    private final AuthAdminService admin;

    public AdminUserController(AuthAdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/users")
    public List<AuthAdminService.UserView> users() {
        return admin.users();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.USERS_WRITE)
    public AuthAdminService.UserView create(@RequestBody AuthAdminService.CreateUserRequest request) {
        return admin.createUser(request);
    }

    @PutMapping("/users/{id}")
    @RequirePermission(Permissions.USERS_WRITE)
    public AuthAdminService.UserView update(@PathVariable UUID id, @RequestBody AuthAdminService.UpdateUserRequest request) {
        return admin.updateUser(id, request);
    }

    @PatchMapping("/users/{id}/status")
    @RequirePermission(Permissions.USERS_WRITE)
    public AuthAdminService.UserView status(@PathVariable UUID id, @RequestBody StatusRequest request) {
        return admin.setStatus(id, request.status());
    }

    @PutMapping("/users/{id}/roles")
    @RequirePermission(Permissions.USERS_WRITE)
    public AuthAdminService.UserView roles(@PathVariable UUID id, @RequestBody IdsRequest request) {
        return admin.setRoles(id, request.roleIds());
    }

    @PutMapping("/users/{id}/projects")
    @RequirePermission(Permissions.USERS_WRITE)
    public AuthAdminService.UserView projects(@PathVariable UUID id, @RequestBody ProjectsRequest request) {
        return admin.setProjects(id, request.projectIds());
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.USERS_WRITE)
    public void delete(@PathVariable UUID id) {
        admin.deleteUser(id);
    }

    @GetMapping("/projects")
    public List<InvestigatorProject> projects() {
        return admin.allProjects();
    }

    public record StatusRequest(String status) {
    }

    public record IdsRequest(List<UUID> roleIds) {
    }

    public record ProjectsRequest(List<UUID> projectIds) {
    }
}
