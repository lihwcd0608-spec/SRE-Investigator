package com.maiya.investigator.boot;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.InvestigationRequest;
import com.maiya.investigator.boot.auth.AuthPrincipal;
import com.maiya.investigator.domain.InvestigationRun;
import com.maiya.investigator.domain.InvestigationRunStore;
import com.maiya.investigator.domain.ProjectRepository;
import com.maiya.investigator.domain.conversation.ConversationMessage;
import com.maiya.investigator.graph.AgentThought;
import com.maiya.investigator.graph.AgentThoughts;
import com.maiya.investigator.graph.conversation.ConversationContext;
import com.maiya.investigator.graph.conversation.ConversationOutcome;
import com.maiya.investigator.graph.conversation.ConversationStrategyRegistry;
import com.maiya.investigator.graph.intent.QuestionIntent;
import com.maiya.investigator.graph.intent.QuestionIntentGate;
import com.maiya.investigator.graph.usage.UsageContext;
import com.maiya.investigator.graph.usage.UsageSnapshot;
import com.maiya.investigator.infra.audit.AuditTrail;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InvestigationRunService {
    private static final Logger log = LoggerFactory.getLogger(InvestigationRunService.class);
    private final ProjectRepository projects;
    private final InvestigationRunStore runs;
    private final ConversationSessionService conversations;
    private final ConversationStrategyRegistry strategies;
    private final QuestionIntentGate intentGate;
    private final AuditTrail auditTrail;

    public InvestigationRunService(
            ProjectRepository projects,
            InvestigationRunStore runs,
            ConversationSessionService conversations,
            ConversationStrategyRegistry strategies,
            QuestionIntentGate intentGate,
            AuditTrail auditTrail
    ) {
        this.projects = projects;
        this.runs = runs;
        this.conversations = conversations;
        this.strategies = strategies;
        this.intentGate = intentGate;
        this.auditTrail = auditTrail;
    }

    public RunOutcome investigate(InvestigationRequest request) {
        return investigate(request, thought -> {
        }, null, null);
    }

    public RunOutcome investigate(InvestigationRequest request, AuthPrincipal actor) {
        return investigate(request, thought -> {
        }, null, actor);
    }

    public RunOutcome investigate(InvestigationRequest request, Consumer<AgentThought> thoughts) {
        return investigate(request, thoughts, null, null);
    }

    public RunOutcome investigate(
            InvestigationRequest request,
            Consumer<AgentThought> thoughts,
            BiConsumer<UUID, UUID> onStarted
    ) {
        return investigate(request, thoughts, onStarted, null);
    }

    public RunOutcome investigate(
            InvestigationRequest request,
            Consumer<AgentThought> thoughts,
            BiConsumer<UUID, UUID> onStarted,
            AuthPrincipal actor
    ) {
        AgentThoughts.open(thoughts);
        UsageContext.open(snapshot(actor, request.projectId()));
        try {
            return runInvestigation(request, onStarted, actor);
        } finally {
            UsageContext.close();
            AgentThoughts.close();
        }
    }

    private static UsageSnapshot snapshot(AuthPrincipal actor, UUID projectId) {
        if (actor == null) {
            return null;
        }
        return new UsageSnapshot(actor.userId(), actor.username(), actor.roles(), projectId);
    }

    private RunOutcome runInvestigation(InvestigationRequest request, BiConsumer<UUID, UUID> onStarted, AuthPrincipal actor) {
        projects.findById(request.projectId())
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + request.projectId()));
        ConversationMode mode = request.modeOrDefault();
        InvestigationRun prior = resolveContinuation(request);
        ConversationSessionService.PreparedTurn turn = conversations.prepare(request, prior, actor);
        InvestigationRun run = runs.save(
                InvestigationRun.start(request.projectId(), request.environment(), mode, request.question())
        );
        if (!turn.continuation()) {
            conversations.recordUser(turn.thread(), request.question(), run.id());
        }
        if (onStarted != null) {
            onStarted.accept(turn.thread().id(), run.id());
        }
        long started = System.currentTimeMillis();
        log.info(
                "会话落库 run={} conversation={} turn={}/{} mode={} env={} project={} question={}",
                run.id(),
                turn.thread().id(),
                turn.turnIndex(),
                conversations.maxUserTurns(),
                mode,
                request.environment(),
                request.projectId(),
                preview(request.question())
        );
        auditTrail.record("conversation.start", run.id().toString(), Map.of(
                "projectId", request.projectId(),
                "conversationId", turn.thread().id(),
                "environment", request.environment(),
                "mode", mode,
                "turnIndex", turn.turnIndex(),
                "route", request.route() == null ? "" : request.route(),
                "skillSlug", request.skillSlug() == null ? "" : request.skillSlug(),
                "ruleSlug", request.ruleSlug() == null ? "" : request.ruleSlug(),
                "agentSlug", request.agentSlug() == null ? "" : request.agentSlug()
        ));
        QuestionIntent intent = intentGate.evaluate(
                mode,
                turn.currentQuestion(),
                turn.turnIndex() > 1 || turn.continuation() || prior != null
        );
        if (!intent.proceed()) {
            log.info("问句前置过滤已拦截 run={} conversation={} question={}",
                    run.id(), turn.thread().id(), preview(turn.currentQuestion()));
            InvestigationRun completed = runs.save(run.complete(intent.reply(), InvestigationRun.RunStatus.COMPLETED));
            conversations.recordAssistant(turn.thread(), intent.reply(), completed.id());
            Map<String, Object> state = new HashMap<>();
            state.put("intentSkip", true);
            ConversationOutcome skipped = new ConversationOutcome(intent.reply(), false, "", state);
            auditTrail.record("conversation.intent.skip", completed.id().toString(), Map.of(
                    "conversationId", turn.thread().id(),
                    "mode", mode
            ));
            return toOutcome(completed, skipped, turn.thread().id());
        }
        try {
            ConversationOutcome outcome = strategies.require(mode).execute(new ConversationContext(
                    request.projectId(),
                    request.environment(),
                    mode,
                    turn.currentQuestion(),
                    turn.memoryBlocks(),
                    request.provider(),
                    request.model(),
                    request.attachmentsOrEmpty(),
                    request.route(),
                    request.repositoryId(),
                    request.baseRef(),
                    request.headRef(),
                    request.skillSlug(),
                    firstNonBlank(request.mcpProfile(), turn.mcpProfile()),
                    request.agentSlug(),
                    request.ruleSlug()
            ));
            boolean budgetExhausted = outcome.budgetExhausted();
            InvestigationRun.RunStatus status = !outcome.modelUsed() || budgetExhausted
                    ? InvestigationRun.RunStatus.NEEDS_INPUT
                    : InvestigationRun.RunStatus.COMPLETED;
            String report = budgetExhausted ? appendBudgetNotice(outcome) : outcome.report();
            InvestigationRun completed = runs.save(run.complete(report, status));
            conversations.recordAssistant(turn.thread(), report, completed.id());
            ConversationOutcome visible = new ConversationOutcome(report, outcome.modelUsed(), outcome.route(), outcome.state());
            log.info(
                    "会话完成 run={} conversation={} status={} route={} modelUsed={} tools={} used={} elapsedMs={} report={}",
                    completed.id(),
                    turn.thread().id(),
                    status,
                    outcome.route(),
                    outcome.modelUsed(),
                    outcome.state() == null ? 0 : outcome.state().getOrDefault("availableTools", 0),
                    outcome.usedToolCalls(),
                    System.currentTimeMillis() - started,
                    preview(visible.report())
            );
            auditTrail.record("conversation.complete", completed.id().toString(), Map.of(
                    "status", status,
                    "mode", mode,
                    "route", outcome.route(),
                    "conversationId", turn.thread().id(),
                    "budgetExhausted", budgetExhausted
            ));
            return toOutcome(completed, visible, turn.thread().id());
        } catch (RuntimeException exception) {
            InvestigationRun failed = runs.save(run.complete(
                    "对话执行失败：" + exception.getMessage(),
                    InvestigationRun.RunStatus.FAILED
            ));
            conversations.recordAssistant(turn.thread(), failed.report(), failed.id());
            log.error(
                    "会话失败 run={} conversation={} mode={} elapsedMs={}：{}",
                    failed.id(),
                    turn.thread().id(),
                    mode,
                    System.currentTimeMillis() - started,
                    exception.getMessage(),
                    exception
            );
            auditTrail.record("conversation.failed", failed.id().toString(), Map.of(
                    "error", exception.getClass().getSimpleName(),
                    "mode", mode,
                    "conversationId", turn.thread().id()
            ));
            throw exception;
        }
    }

    private InvestigationRun resolveContinuation(InvestigationRequest request) {
        if (request.continueFromRunId() == null) {
            return null;
        }
        InvestigationRun prior = getRun(request.continueFromRunId());
        if (!prior.projectId().equals(request.projectId())) {
            throw new IllegalArgumentException("继续排查的记录不属于当前项目");
        }
        log.info("继续排查 from={} toQuestion={}", prior.id(), preview(request.question()));
        return prior;
    }

    private RunOutcome toOutcome(InvestigationRun run, ConversationOutcome result, UUID conversationId) {
        return new RunOutcome(
                run,
                result,
                conversationId,
                (int) conversations.userTurns(conversationId),
                conversations.maxUserTurns(),
                conversations.messages(conversationId)
        );
    }

    private static String appendBudgetNotice(ConversationOutcome outcome) {
        int used = outcome.usedToolCalls();
        int max = outcome.maxToolCalls() <= 0 ? used : outcome.maxToolCalls();
        String report = outcome.report() == null ? "" : outcome.report();
        return report + "\n\n---\n只读工具已达上限（" + used + "/" + max + "）。如需继续，请在确认后接着排查。";
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "…" : compact;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    public InvestigationRun getRun(UUID id) {
        return runs.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("对话记录不存在：" + id));
    }

    public InvestigationRun getRun(UUID id, AuthPrincipal actor) {
        InvestigationRun run = getRun(id);
        conversations.requireAccessToRun(id, actor);
        return run;
    }

    public List<InvestigationRun> listByProject(UUID projectId) {
        return listByProject(projectId, null);
    }

    public List<InvestigationRun> listByProject(UUID projectId, AuthPrincipal actor) {
        projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + projectId));
        List<InvestigationRun> visible = new ArrayList<>();
        for (InvestigationRun run : runs.listByProject(projectId)) {
            if (conversations.canAccessRun(run.id(), actor)) {
                visible.add(run);
            }
        }
        return visible;
    }

    public record RunOutcome(
            InvestigationRun run,
            ConversationOutcome result,
            UUID conversationId,
            int turnIndex,
            int maxTurns,
            List<ConversationMessage> messages
    ) {
    }
}
