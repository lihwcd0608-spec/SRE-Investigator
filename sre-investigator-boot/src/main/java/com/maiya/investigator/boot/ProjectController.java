package com.maiya.investigator.boot;

import com.maiya.investigator.api.InvestigationRequest;
import com.maiya.investigator.api.ProjectRequest;
import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.InvestigationRun;
import com.maiya.investigator.domain.InvestigationRunStore;
import com.maiya.investigator.domain.InvestigatorProject;
import com.maiya.investigator.domain.ProjectRepository;
import com.maiya.investigator.domain.auth.AuthStore;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.automation.AutomationJobStore;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.feishu.FeishuChatBindingStore;
import com.maiya.investigator.infra.audit.AuditTrail;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api")
public class ProjectController {
    private final ProjectRepository projects;
    private final ControlPlaneStore controlPlane;
    private final InvestigationRunStore runStore;
    private final ConversationSessionService conversations;
    private final InvestigationRunService runs;
    private final AuditTrail auditTrail;
    private final AccessGuard access;
    private final AuthStore authStore;
    private final AutomationJobStore automations;
    private final FeishuChatBindingStore feishuChats;

    public ProjectController(
            ProjectRepository projects,
            ControlPlaneStore controlPlane,
            InvestigationRunStore runStore,
            ConversationSessionService conversations,
            InvestigationRunService runs,
            AuditTrail auditTrail,
            AccessGuard access,
            AuthStore authStore,
            AutomationJobStore automations,
            FeishuChatBindingStore feishuChats
    ) {
        this.projects = projects;
        this.controlPlane = controlPlane;
        this.runStore = runStore;
        this.conversations = conversations;
        this.runs = runs;
        this.auditTrail = auditTrail;
        this.access = access;
        this.authStore = authStore;
        this.automations = automations;
        this.feishuChats = feishuChats;
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("ok", "java-spring-ai-alibaba-graph");
    }

    @GetMapping("/projects")
    @RequirePermission(any = {
            Permissions.PROJECTS,
            Permissions.REPOSITORIES,
            Permissions.BINDINGS,
            Permissions.AGENTS,
            Permissions.AUTOMATIONS,
            Permissions.INVESTIGATE,
            Permissions.REVIEW
    })
    public List<InvestigatorProject> listProjects() {
        return access.visibleProjects();
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.PROJECTS_WRITE)
    public InvestigatorProject createProject(@Valid @RequestBody ProjectRequest request) {
        InvestigatorProject project = projects.save(
                InvestigatorProject.create(request.name(), request.slug(), request.topology())
        );
        authStore.addProjectMember(project.id(), access.current().userId());
        auditTrail.record("project.create", project.id().toString(), Map.of(
                "name", project.name(),
                "slug", project.slug()
        ));
        return project;
    }

    @PutMapping("/projects/{projectId}")
    @RequirePermission(Permissions.PROJECTS_WRITE)
    public InvestigatorProject updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectRequest request
    ) {
        InvestigatorProject current = requireProject(projectId);
        InvestigatorProject saved = projects.save(current.withDetails(request.name(), request.slug(), request.topology()));
        auditTrail.record("project.update", projectId.toString(), Map.of(
                "name", saved.name(),
                "slug", saved.slug()
        ));
        return saved;
    }

    @DeleteMapping("/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.PROJECTS_DELETE)
    public void deleteProject(@PathVariable UUID projectId) {
        InvestigatorProject current = requireProject(projectId);
        automations.deleteByProjectId(projectId);
        feishuChats.deleteByProjectId(projectId);
        controlPlane.deleteByProjectId(projectId);
        conversations.deleteByProjectId(projectId);
        runStore.deleteByProjectId(projectId);
        authStore.deleteProjectMembers(projectId);
        projects.delete(projectId);
        auditTrail.record("project.delete", projectId.toString(), Map.of(
                "name", current.name(),
                "slug", current.slug()
        ));
    }

    @GetMapping("/projects/{projectId}/investigations")
    @RequirePermission(Permissions.INVESTIGATE)
    public List<InvestigationRun> listInvestigations(@PathVariable UUID projectId) {
        access.requireProject(projectId);
        return runs.listByProject(projectId, access.current());
    }

    @PostMapping("/investigations")
    @RequirePermission(Permissions.INVESTIGATE)
    public InvestigationRunService.RunOutcome investigate(
            @Valid @RequestBody InvestigationRequest request
    ) {
        access.requireProject(request.projectId());
        return runs.investigate(request, access.current());
    }

    private InvestigatorProject requireProject(UUID projectId) {
        return access.requireProject(projectId);
    }

    record Health(String status, String runtime) {
    }
}
