package com.maiya.investigator.infra.persistence;

import com.maiya.investigator.api.Environment;
import com.maiya.investigator.domain.automation.AutomationExecution;
import com.maiya.investigator.domain.automation.AutomationJob;
import com.maiya.investigator.domain.automation.AutomationJobStore;
import com.maiya.investigator.domain.automation.PayloadKind;
import com.maiya.investigator.domain.automation.ScheduleKind;
import com.maiya.investigator.domain.automation.SessionTarget;
import com.maiya.investigator.domain.automation.TriggerType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mysql")
public class MysqlAutomationJobStore implements AutomationJobStore {
    private final AutomationJobJpaRepository jobs;
    private final AutomationExecutionJpaRepository executions;

    public MysqlAutomationJobStore(AutomationJobJpaRepository jobs, AutomationExecutionJpaRepository executions) {
        this.jobs = jobs;
        this.executions = executions;
    }

    @Override
    public AutomationJob save(AutomationJob job) {
        return toDomain(jobs.save(toEntity(job)));
    }

    @Override
    public Optional<AutomationJob> findById(UUID id) {
        return jobs.findById(id).map(MysqlAutomationJobStore::toDomain);
    }

    @Override
    public List<AutomationJob> listByProject(UUID projectId) {
        return listByProjects(List.of(projectId));
    }

    @Override
    public List<AutomationJob> listByProjects(List<UUID> projectIds) {
        List<AutomationJob> found = new java.util.ArrayList<>();
        if (projectIds == null || projectIds.isEmpty()) {
            return found;
        }
        for (AutomationJobEntity entity : jobs.findByProjectIdInOrderByUpdatedAtDesc(projectIds)) {
            found.add(toDomain(entity));
        }
        return found;
    }

    @Override
    public List<AutomationJob> due(Instant now) {
        return jobs.findByEnabledTrueAndNextRunAtLessThanEqual(now).stream()
                .map(MysqlAutomationJobStore::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        executions.deleteByJobId(id);
        jobs.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteByProjectId(UUID projectId) {
        List<UUID> ids = jobs.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(AutomationJobEntity::getId)
                .toList();
        if (!ids.isEmpty()) {
            executions.deleteByJobIdIn(ids);
        }
        jobs.deleteByProjectId(projectId);
    }

    @Override
    public AutomationExecution saveExecution(AutomationExecution execution) {
        return toExecution(executions.save(toExecutionEntity(execution)));
    }

    @Override
    public Optional<AutomationExecution> findExecution(UUID id) {
        return executions.findById(id).map(MysqlAutomationJobStore::toExecution);
    }

    @Override
    public List<AutomationExecution> executions(UUID jobId, int limit) {
        return executions.findByJobIdOrderByStartedAtDesc(jobId).stream()
                .limit(Math.max(1, limit))
                .map(MysqlAutomationJobStore::toExecution)
                .toList();
    }

    private static AutomationJobEntity toEntity(AutomationJob job) {
        return new AutomationJobEntity(
                job.id(),
                job.projectId(),
                job.name(),
                job.environment().code(),
                job.enabled(),
                job.scheduleKind().name(),
                job.scheduleExpr(),
                job.timezone(),
                job.payloadKind().name(),
                job.sessionTarget().name(),
                job.prompt(),
                job.route(),
                job.conversationId(),
                job.deleteAfterRun(),
                job.timeoutSeconds(),
                job.lastRunAt(),
                job.nextRunAt(),
                job.lastStatus(),
                job.consecutiveErrors(),
                job.runningExecutionId(),
                job.runningSince(),
                job.createdAt(),
                job.updatedAt()
        );
    }

    private static AutomationJob toDomain(AutomationJobEntity entity) {
        return new AutomationJob(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                Environment.from(entity.getEnvironment()),
                entity.isEnabled(),
                ScheduleKind.valueOf(entity.getScheduleKind()),
                entity.getScheduleExpr(),
                entity.getTimezone(),
                PayloadKind.valueOf(entity.getPayloadKind()),
                SessionTarget.valueOf(entity.getSessionTarget()),
                entity.getPrompt(),
                entity.getRoute(),
                entity.getConversationId(),
                entity.isDeleteAfterRun(),
                entity.getTimeoutSeconds(),
                entity.getLastRunAt(),
                entity.getNextRunAt(),
                entity.getLastStatus(),
                entity.getConsecutiveErrors(),
                entity.getRunningExecutionId(),
                entity.getRunningSince(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static AutomationExecutionEntity toExecutionEntity(AutomationExecution execution) {
        return new AutomationExecutionEntity(
                execution.id(),
                execution.jobId(),
                execution.runId(),
                execution.conversationId(),
                execution.triggerType().name(),
                execution.status(),
                clip(execution.summary(), 400),
                execution.errorMessage(),
                execution.startedAt(),
                execution.finishedAt()
        );
    }

    private static AutomationExecution toExecution(AutomationExecutionEntity entity) {
        return new AutomationExecution(
                entity.getId(),
                entity.getJobId(),
                entity.getRunId(),
                entity.getConversationId(),
                TriggerType.valueOf(entity.getTriggerType()),
                entity.getStatus(),
                entity.getSummary(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }

    private static String clip(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
