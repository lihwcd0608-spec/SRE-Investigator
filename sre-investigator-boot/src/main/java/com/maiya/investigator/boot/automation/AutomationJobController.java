package com.maiya.investigator.boot.automation;

import com.maiya.investigator.api.AutomationExecutionDetailResponse;
import com.maiya.investigator.api.AutomationExecutionResponse;
import com.maiya.investigator.api.AutomationJobRequest;
import com.maiya.investigator.api.AutomationJobResponse;
import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.auth.Permissions;
import com.maiya.investigator.domain.automation.AutomationExecution;
import com.maiya.investigator.domain.automation.TriggerType;
import jakarta.validation.Valid;
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
@RequestMapping("/api/projects/{projectId}/automations")
@RequirePermission(Permissions.AUTOMATIONS)
public class AutomationJobController {
    private final AutomationJobService jobs;
    private final AccessGuard access;

    public AutomationJobController(AutomationJobService jobs, AccessGuard access) {
        this.jobs = jobs;
        this.access = access;
    }

    @GetMapping
    public List<AutomationJobResponse> list(@PathVariable UUID projectId) {
        access.requireProject(projectId);
        return jobs.list(projectId).stream().map(jobs::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.AUTOMATIONS_WRITE)
    public AutomationJobResponse create(
            @PathVariable UUID projectId,
            @Valid @RequestBody AutomationJobRequest request
    ) {
        access.requireProject(projectId);
        return jobs.toResponse(jobs.create(projectId, request));
    }

    @PutMapping("/{jobId}")
    @RequirePermission(Permissions.AUTOMATIONS_WRITE)
    public AutomationJobResponse update(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @Valid @RequestBody AutomationJobRequest request
    ) {
        access.requireProject(projectId);
        return jobs.toResponse(jobs.update(projectId, jobId, request));
    }

    @PatchMapping("/{jobId}")
    @RequirePermission(Permissions.AUTOMATIONS_WRITE)
    public AutomationJobResponse enable(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @RequestBody EnableBody body
    ) {
        access.requireProject(projectId);
        if (body == null || body.enabled() == null) {
            throw new IllegalArgumentException("请指定 enabled");
        }
        return jobs.toResponse(jobs.setEnabled(projectId, jobId, body.enabled()));
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.AUTOMATIONS_DELETE)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID jobId) {
        access.requireProject(projectId);
        jobs.delete(projectId, jobId);
    }

    @PostMapping("/{jobId}/run")
    @RequirePermission(Permissions.AUTOMATIONS_WRITE)
    public AutomationExecutionResponse run(@PathVariable UUID projectId, @PathVariable UUID jobId) {
        access.requireProject(projectId);
        AutomationExecution execution = jobs.run(projectId, jobId, TriggerType.MANUAL);
        if (execution == null) {
            throw new IllegalArgumentException("任务正在执行，请稍后再试");
        }
        return jobs.toExecutionResponse(execution);
    }

    @GetMapping("/{jobId}/executions")
    public List<AutomationExecutionResponse> executions(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId
    ) {
        access.requireProject(projectId);
        return jobs.executions(projectId, jobId, 30).stream().map(jobs::toExecutionResponse).toList();
    }

    @GetMapping("/{jobId}/executions/{executionId}")
    public AutomationExecutionDetailResponse execution(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @PathVariable UUID executionId
    ) {
        access.requireProject(projectId);
        return jobs.executionDetail(projectId, jobId, executionId);
    }

    public record EnableBody(Boolean enabled) {
    }
}
