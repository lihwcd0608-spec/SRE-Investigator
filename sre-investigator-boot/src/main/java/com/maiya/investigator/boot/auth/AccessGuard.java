package com.maiya.investigator.boot.auth;

import com.maiya.investigator.domain.InvestigatorProject;
import com.maiya.investigator.domain.ProjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {
    private final ProjectRepository projects;

    public AccessGuard(ProjectRepository projects) {
        this.projects = projects;
    }

    public AuthPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new UnauthorizedException("未登录");
        }
        return principal;
    }

    public void requirePermission(String code) {
        if (!current().hasPermission(code)) {
            throw new ForbiddenException("没有权限");
        }
    }

    public void requireAny(String... codes) {
        AuthPrincipal principal = current();
        for (String code : codes) {
            if (principal.hasPermission(code)) {
                return;
            }
        }
        throw new ForbiddenException("没有权限");
    }

    public InvestigatorProject requireProject(UUID projectId) {
        current();
        return projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + projectId));
    }

    public List<InvestigatorProject> visibleProjects() {
        current();
        return new ArrayList<>(projects.findAll());
    }
}
