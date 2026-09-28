package com.maiya.investigator.boot.automation;

import com.maiya.investigator.api.AutomationExecutionDetailResponse;
import com.maiya.investigator.api.AutomationExecutionResponse;
import com.maiya.investigator.api.AutomationJobRequest;
import com.maiya.investigator.api.AutomationJobResponse;
import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.api.InvestigationRequest;
import com.maiya.investigator.boot.ConversationSessionService;
import com.maiya.investigator.boot.InvestigationRunService;
import com.maiya.investigator.domain.InvestigationRun;
import com.maiya.investigator.domain.InvestigationRunStore;
import com.maiya.investigator.domain.automation.AutomationExecution;
import com.maiya.investigator.domain.automation.AutomationJob;
import com.maiya.investigator.domain.automation.AutomationJobStore;
import com.maiya.investigator.domain.automation.PayloadKind;
import com.maiya.investigator.domain.automation.ScheduleKind;
import com.maiya.investigator.domain.automation.SessionTarget;
import com.maiya.investigator.domain.automation.TriggerType;
import com.maiya.investigator.domain.conversation.ConversationMessage;
import com.maiya.investigator.domain.conversation.ConversationOrigin;
import com.maiya.investigator.domain.conversation.ConversationThread;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AutomationJobService {
    private static final Logger log = LoggerFactory.getLogger(AutomationJobService.class);
    private final AutomationJobStore store;
    private final ConversationSessionService conversations;
    private final InvestigationRunStore runs;
    private final InvestigationRunService investigations;
    private final K8sInspectionScanner scanner;
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    public AutomationJobService(
            AutomationJobStore store,
            ConversationSessionService conversations,
            InvestigationRunStore runs,
            InvestigationRunService investigations,
            K8sInspectionScanner scanner
    ) {
        this.store = store;
        this.conversations = conversations;
        this.runs = runs;
        this.investigations = investigations;
        this.scanner = scanner;
    }

    public List<AutomationJob> list(UUID projectId) {
        return store.listByProject(projectId);
    }

    public List<AutomationJob> listVisible(List<UUID> projectIds, Environment environment) {
        List<AutomationJob> visible = new java.util.ArrayList<>();
        if (projectIds == null || projectIds.isEmpty() || environment == null) {
            return visible;
        }
        for (AutomationJob job : store.listByProjects(projectIds)) {
            if (job.environment() == environment) {
                visible.add(job);
            }
        }
        return visible;
    }

    public AutomationJob get(UUID jobId) {
        return store.findById(jobId).orElseThrow(() -> new IllegalArgumentException("定时任务不存在：" + jobId));
    }

    public AutomationJob create(UUID projectId, AutomationJobRequest request) {
        Instant now = Instant.now();
        Parsed parsed = parse(request, now);
        AutomationJob job = AutomationJob.create(
                projectId,
                parsed.name(),
                parsed.environment(),
                parsed.scheduleKind(),
                parsed.scheduleExpr(),
                parsed.timezone(),
                parsed.payloadKind(),
                parsed.sessionTarget(),
                parsed.prompt(),
                parsed.route(),
                request.conversationId(),
                Boolean.TRUE.equals(request.deleteAfterRun()),
                parsed.timeoutSeconds(),
                parsed.nextRunAt()
        );
        if (request.enabled() != null) {
            job = job.withEnabled(request.enabled());
        }
        return store.save(job);
    }

    public AutomationJob update(UUID projectId, UUID jobId, AutomationJobRequest request) {
        AutomationJob current = require(projectId, jobId);
        Instant now = Instant.now();
        Parsed parsed = parse(request, now);
        boolean scheduleChanged = current.scheduleKind() != parsed.scheduleKind()
                || !current.scheduleExpr().equals(parsed.scheduleExpr())
                || !current.timezone().equals(parsed.timezone());
        Instant next = scheduleChanged ? parsed.nextRunAt() : current.nextRunAt();
        boolean enabled = request.enabled() == null ? current.enabled() : request.enabled();
        return store.save(current.updated(
                parsed.name(),
                parsed.environment(),
                enabled,
                parsed.scheduleKind(),
                parsed.scheduleExpr(),
                parsed.timezone(),
                parsed.payloadKind(),
                parsed.sessionTarget(),
                parsed.prompt(),
                parsed.route(),
                request.conversationId() != null ? request.conversationId() : current.conversationId(),
                request.deleteAfterRun() == null ? current.deleteAfterRun() : request.deleteAfterRun(),
                parsed.timeoutSeconds(),
                next
        ));
    }

    public AutomationJob setEnabled(UUID projectId, UUID jobId, boolean enabled) {
        return store.save(require(projectId, jobId).withEnabled(enabled));
    }

    public void delete(UUID projectId, UUID jobId) {
        require(projectId, jobId);
        store.delete(jobId);
    }

    public List<AutomationExecution> executions(UUID projectId, UUID jobId, int limit) {
        require(projectId, jobId);
        return store.executions(jobId, limit);
    }

    public AutomationExecutionDetailResponse executionDetail(UUID projectId, UUID jobId, UUID executionId) {
        AutomationJob job = require(projectId, jobId);
        AutomationExecution execution = store.findExecution(executionId)
                .orElseThrow(() -> new IllegalArgumentException("执行记录不存在：" + executionId));
        if (!execution.jobId().equals(jobId)) {
            throw new IllegalArgumentException("执行记录不属于该任务");
        }
        List<AutomationExecutionDetailResponse.MessageView> messages = List.of();
        if (execution.conversationId() != null) {
            messages = conversations.messages(execution.conversationId()).stream()
                    .map(AutomationJobService::toMessageView)
                    .toList();
        }
        return new AutomationExecutionDetailResponse(
                toResponse(job),
                toExecutionResponse(execution),
                execution.conversationId(),
                messages
        );
    }

    public void runDue(Instant now) {
        for (AutomationJob job : store.due(now)) {
            try {
                run(job.projectId(), job.id(), TriggerType.SCHEDULED);
            } catch (RuntimeException exception) {
                log.warn("定时任务跳过或失败 job={}：{}", job.id(), exception.getMessage());
            }
        }
    }

    public AutomationExecution run(UUID projectId, UUID jobId, TriggerType trigger) {
        Object lock = locks.computeIfAbsent(jobId, ignored -> new Object());
        synchronized (lock) {
            return executeLocked(projectId, jobId, trigger);
        }
    }

    private AutomationExecution executeLocked(UUID projectId, UUID jobId, TriggerType trigger) {
        Instant now = Instant.now();
        AutomationJob job = require(projectId, jobId);
        if (trigger == TriggerType.SCHEDULED && !job.enabled()) {
            throw new IllegalArgumentException("任务已停用");
        }
        if (job.isRunning(now)) {
            if (trigger == TriggerType.MANUAL) {
                throw new IllegalArgumentException("任务正在执行，请稍后再试");
            }
            log.info("定时任务仍在跑，跳过重叠 job={}", jobId);
            return null;
        }
        AutomationExecution execution = store.saveExecution(AutomationExecution.start(jobId, trigger));
        store.save(job.markingRun(execution.id(), now));
        try {
            ExecutionResult result = dispatch(job);
            if (job.sessionTarget() == SessionTarget.THREAD && result.conversationId() != null
                    && (job.conversationId() == null || !job.conversationId().equals(result.conversationId()))) {
                job = store.findById(jobId).orElse(job).withConversationId(result.conversationId());
                store.save(job);
            }
            AutomationExecution completed = store.saveExecution(execution.complete(
                    result.runId(),
                    result.conversationId(),
                    result.status(),
                    result.summary(),
                    result.error()
            ));
            finish(jobId, now, result.status(), result.error() == null, job.deleteAfterRun() && job.scheduleKind() == ScheduleKind.AT);
            return completed;
        } catch (RuntimeException exception) {
            log.warn("定时任务失败 job={}：{}", jobId, exception.getMessage());
            AutomationExecution failed = store.saveExecution(execution.complete(
                    null,
                    null,
                    "FAILED",
                    "执行失败",
                    clip(exception.getMessage(), 800)
            ));
            finish(jobId, now, "FAILED", false, false);
            return failed;
        }
    }

    private ExecutionResult dispatch(AutomationJob job) {
        return switch (job.payloadKind()) {
            case K8S_INSPECT -> runInspect(job);
            case AGENT_TURN -> runAgent(job);
        };
    }

    private ExecutionResult runInspect(AutomationJob job) {
        String question = blankToDefault(job.prompt(), K8sInspectionScanner.DEFAULT_PROMPT);
        ConversationThread thread = resolveThread(job, "巡检 · " + job.name());
        InvestigationRun run = runs.save(InvestigationRun.start(
                job.projectId(),
                job.environment(),
                ConversationMode.INVESTIGATE,
                question
        ));
        conversations.recordUser(thread, question, run.id());
        try {
            K8sInspectionScanner.Report report = scanner.scan(job.projectId(), job.environment());
            runs.save(run.complete(report.markdown(), InvestigationRun.RunStatus.COMPLETED));
            conversations.recordAssistant(thread, report.markdown(), run.id());
            return new ExecutionResult(
                    run.id(),
                    thread.id(),
                    report.hasFindings() ? "FINDINGS" : "OK",
                    report.summary(),
                    null
            );
        } catch (RuntimeException exception) {
            String message = "巡检失败：" + exception.getMessage();
            runs.save(run.complete(message, InvestigationRun.RunStatus.FAILED));
            conversations.recordAssistant(thread, message, run.id());
            throw exception;
        }
    }

    private ExecutionResult runAgent(AutomationJob job) {
        String prompt = job.prompt() == null ? "" : job.prompt().trim();
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("AGENT_TURN 需要填写提示词");
        }
        ConversationThread thread = resolveThread(job, "定时 · " + job.name());
        InvestigationRunService.RunOutcome outcome = investigations.investigate(new InvestigationRequest(
                job.projectId(),
                job.environment(),
                prompt,
                ConversationMode.INVESTIGATE,
                null,
                null,
                List.of(),
                thread.id(),
                null,
                job.route()
        ));
        String status = outcome.run().status() == InvestigationRun.RunStatus.FAILED ? "FAILED" : "OK";
        return new ExecutionResult(
                outcome.run().id(),
                outcome.conversationId(),
                status,
                clip(outcome.run().report(), 200),
                outcome.run().status() == InvestigationRun.RunStatus.FAILED ? outcome.run().report() : null
        );
    }

    private ConversationThread resolveThread(AutomationJob job, String title) {
        if (job.sessionTarget() == SessionTarget.THREAD && job.conversationId() != null) {
            try {
                ConversationThread existing = conversations.getThread(job.conversationId());
                if (existing.projectId().equals(job.projectId())) {
                    return existing;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return conversations.startThread(
                job.projectId(),
                job.environment(),
                ConversationMode.INVESTIGATE,
                ConversationOrigin.AUTOMATION,
                title
        );
    }

    private void finish(UUID jobId, Instant now, String status, boolean success, boolean disableAfter) {
        AutomationJob current = store.findById(jobId).orElseThrow();
        int errors = success ? 0 : current.consecutiveErrors() + 1;
        Instant next = nextAfter(current, now, success);
        boolean disable = disableAfter && success
                || current.scheduleKind() == ScheduleKind.AT && success;
        store.save(current.finished(now, next, status, errors, disable));
    }

    private static Instant nextAfter(AutomationJob job, Instant now, boolean success) {
        if (job.scheduleKind() == ScheduleKind.AT) {
            return success ? null : now.plusSeconds(60);
        }
        return AutomationSchedules.computeNext(job.scheduleKind(), job.scheduleExpr(), job.timezone(), now);
    }

    private AutomationJob require(UUID projectId, UUID jobId) {
        AutomationJob job = store.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("定时任务不存在：" + jobId));
        if (!job.projectId().equals(projectId)) {
            throw new IllegalArgumentException("定时任务不属于当前项目");
        }
        return job;
    }

    private Parsed parse(AutomationJobRequest request, Instant now) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("请填写任务名称");
        }
        ScheduleKind scheduleKind = enumValue(ScheduleKind.class, request.scheduleKind(), "调度类型");
        PayloadKind payloadKind = enumValue(PayloadKind.class, request.payloadKind(), "载荷类型");
        SessionTarget sessionTarget = request.sessionTarget() == null || request.sessionTarget().isBlank()
                ? SessionTarget.ISOLATED
                : enumValue(SessionTarget.class, request.sessionTarget(), "会话方式");
        String expr = request.scheduleExpr().trim();
        String timezone = request.timezone() == null || request.timezone().isBlank()
                ? "Asia/Shanghai"
                : request.timezone().trim();
        AutomationSchedules.validate(scheduleKind, expr, timezone);
        Instant next = AutomationSchedules.firstRunAt(scheduleKind, expr, timezone, now);
        String prompt = request.prompt() == null ? "" : request.prompt().trim();
        if (payloadKind == PayloadKind.AGENT_TURN && prompt.isBlank()) {
            throw new IllegalArgumentException("AGENT_TURN 需要填写提示词");
        }
        if (payloadKind == PayloadKind.K8S_INSPECT && prompt.isBlank()) {
            prompt = K8sInspectionScanner.DEFAULT_PROMPT;
        }
        int timeout = request.timeoutSeconds() == null ? 600 : request.timeoutSeconds();
        return new Parsed(
                name,
                request.environment(),
                scheduleKind,
                expr,
                timezone,
                payloadKind,
                sessionTarget,
                prompt,
                blankToNull(request.route()),
                timeout,
                next
        );
    }

    public AutomationJobResponse toResponse(AutomationJob job) {
        Instant now = Instant.now();
        return new AutomationJobResponse(
                job.id(),
                job.projectId(),
                job.name(),
                job.environment(),
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
                job.isRunning(now),
                job.createdAt(),
                job.updatedAt()
        );
    }

    private static AutomationExecutionDetailResponse.MessageView toMessageView(ConversationMessage message) {
        return new AutomationExecutionDetailResponse.MessageView(
                message.id(),
                message.role(),
                message.content(),
                message.runId(),
                message.createdAt()
        );
    }

    public AutomationExecutionResponse toExecutionResponse(AutomationExecution execution) {
        return new AutomationExecutionResponse(
                execution.id(),
                execution.jobId(),
                execution.runId(),
                execution.conversationId(),
                execution.triggerType().name(),
                execution.status(),
                execution.summary(),
                execution.errorMessage(),
                execution.startedAt(),
                execution.finishedAt()
        );
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String label) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的" + label + "：" + raw);
        }
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max) + "…";
    }

    private record Parsed(
            String name,
            Environment environment,
            ScheduleKind scheduleKind,
            String scheduleExpr,
            String timezone,
            PayloadKind payloadKind,
            SessionTarget sessionTarget,
            String prompt,
            String route,
            int timeoutSeconds,
            Instant nextRunAt
    ) {
    }

    private record ExecutionResult(
            UUID runId,
            UUID conversationId,
            String status,
            String summary,
            String error
    ) {
    }
}
