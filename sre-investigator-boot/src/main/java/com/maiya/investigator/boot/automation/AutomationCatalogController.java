package com.maiya.investigator.boot.automation;

import com.maiya.investigator.api.AutomationExecutionDetailResponse;
import com.maiya.investigator.api.AutomationJobResponse;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.InvestigatorProject;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.automation.AutomationJob;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automations")
@RequirePermission(Permissions.AUTOMATIONS)
public class AutomationCatalogController {
    private final AutomationJobService jobs;
    private final AccessGuard access;

    public AutomationCatalogController(AutomationJobService jobs, AccessGuard access) {
        this.jobs = jobs;
        this.access = access;
    }

    @GetMapping
    public List<AutomationJobResponse> list(@RequestParam String environment) {
        Environment current = Environment.from(environment);
        List<UUID> projectIds = new ArrayList<>();
        for (InvestigatorProject project : access.visibleProjects()) {
            projectIds.add(project.id());
        }
        List<AutomationJobResponse> items = new ArrayList<>();
        for (AutomationJob job : jobs.listVisible(projectIds, current)) {
            items.add(jobs.toResponse(job));
        }
        return items;
    }

    @GetMapping("/{jobId}/executions/{executionId}")
    public AutomationExecutionDetailResponse execution(
            @PathVariable UUID jobId,
            @PathVariable UUID executionId
    ) {
        AutomationJob job = jobs.get(jobId);
        access.requireProject(job.projectId());
        return jobs.executionDetail(job.projectId(), jobId, executionId);
    }
}
