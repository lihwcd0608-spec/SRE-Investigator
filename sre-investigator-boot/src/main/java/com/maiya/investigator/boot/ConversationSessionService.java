package com.maiya.investigator.boot;

import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.api.InvestigationRequest;
import com.maiya.investigator.boot.auth.AuthPrincipal;
import com.maiya.investigator.boot.auth.ForbiddenException;
import com.maiya.investigator.domain.InvestigationRun;
import com.maiya.investigator.domain.ProjectRepository;
import com.maiya.investigator.domain.conversation.ConversationMessage;
import com.maiya.investigator.domain.conversation.ConversationOrigin;
import com.maiya.investigator.domain.conversation.ConversationStore;
import com.maiya.investigator.domain.conversation.ConversationThread;
import com.maiya.investigator.domain.memory.ConversationWindowCache;
import com.maiya.investigator.domain.memory.MemoryFactSink;
import com.maiya.investigator.domain.memory.MemoryFactStore;
import com.maiya.investigator.domain.memory.ProjectMemory;
import com.maiya.investigator.graph.AgentProperties;
import com.maiya.investigator.graph.conversation.ContinuationPrompt;
import com.maiya.investigator.graph.conversation.ConversationMemory;
import com.maiya.investigator.graph.conversation.ConversationMemoryCompactor;
import com.maiya.investigator.graph.conversation.TurnHistoryPrompt;
import com.maiya.investigator.infra.memory.MemoryProperties;
import com.maiya.investigator.mcp.NamedMcpFocus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConversationSessionService {
    private static final Logger log = LoggerFactory.getLogger(ConversationSessionService.class);
    private final ProjectRepository projects;
    private final ConversationStore conversations;
    private final MemoryProperties memory;
    private final ConversationWindowCache window;
    private final ProjectMemory projectMemory;
    private final MemoryFactSink factSink;
    private final MemoryFactStore facts;
    private final AgentProperties agent;

    public ConversationSessionService(ProjectRepository projects, ConversationStore conversations) {
        this(
                projects,
                conversations,
                new MemoryProperties(),
                ConversationWindowCache.noop(),
                ProjectMemory.noop(),
                MemoryFactSink.noop(),
                null,
                new AgentProperties()
        );
    }

    @Autowired
    public ConversationSessionService(
            ProjectRepository projects,
            ConversationStore conversations,
            MemoryProperties memory,
            ConversationWindowCache window,
            ProjectMemory projectMemory,
            MemoryFactSink factSink,
            MemoryFactStore facts,
            AgentProperties agent
    ) {
        this.projects = projects;
        this.conversations = conversations;
        this.memory = memory == null ? new MemoryProperties() : memory;
        this.window = window == null ? ConversationWindowCache.noop() : window;
        this.projectMemory = projectMemory == null ? ProjectMemory.noop() : projectMemory;
        this.factSink = factSink == null ? MemoryFactSink.noop() : factSink;
        this.facts = facts;
        this.agent = agent == null ? new AgentProperties() : agent;
    }

    public int maxUserTurns() {
        return agent.getMaxUserTurns();
    }

    public List<ConversationSummary> list(UUID projectId) {
        return list(projectId, null);
    }

    public List<ConversationSummary> list(UUID projectId, AuthPrincipal actor) {
        requireProject(projectId);
        List<ConversationSummary> items = new ArrayList<>();
        for (ConversationThread thread : conversations.listByProject(projectId)) {
            if (thread.origin() != ConversationOrigin.CONVERSATION) {
                continue;
            }
            if (!canRead(actor, thread)) {
                continue;
            }
            items.add(toSummary(thread));
        }
        return items;
    }

    public List<ConversationSummary> listReviews(UUID projectId) {
        return listReviews(projectId, null);
    }

    public List<ConversationSummary> listReviews(UUID projectId, AuthPrincipal actor) {
        requireProject(projectId);
        List<ConversationSummary> items = new ArrayList<>();
        for (ConversationThread thread : conversations.listByProjectAndOrigin(projectId, ConversationOrigin.REVIEW)) {
            if (!canRead(actor, thread)) {
                continue;
            }
            items.add(toSummary(thread));
        }
        return items;
    }

    public ConversationDetail get(UUID conversationId) {
        return get(conversationId, null);
    }

    public ConversationDetail get(UUID conversationId, AuthPrincipal actor) {
        ConversationThread thread = requireReadable(requireThread(conversationId), actor);
        if (thread.origin() == ConversationOrigin.AUTOMATION) {
            throw new IllegalArgumentException("这是定时执行记录，请从定时页查看");
        }
        return toDetail(thread, conversations.messages(conversationId));
    }

    public ConversationDetail getReview(UUID conversationId) {
        return getReview(conversationId, null);
    }

    public ConversationDetail getReview(UUID conversationId, AuthPrincipal actor) {
        ConversationThread thread = requireReadable(requireThread(conversationId), actor);
        if (thread.origin() != ConversationOrigin.REVIEW) {
            throw new IllegalArgumentException("这不是代码审查记录");
        }
        return toDetail(thread, conversations.messages(conversationId));
    }

    public ConversationThread startThread(UUID projectId, Environment environment, ConversationMode mode, String title) {
        return startThread(projectId, environment, mode, ConversationOrigin.CONVERSATION, title);
    }

    public ConversationThread startThread(
            UUID projectId,
            Environment environment,
            ConversationMode mode,
            ConversationOrigin origin,
            String title
    ) {
        return startThread(projectId, environment, mode, origin, title, null);
    }

    public ConversationThread startThread(
            UUID projectId,
            Environment environment,
            ConversationMode mode,
            ConversationOrigin origin,
            String title,
            UUID createdByUserId
    ) {
        requireProject(projectId);
        return conversations.save(ConversationThread.start(projectId, environment, mode, origin, title, createdByUserId));
    }

    public ConversationThread getThread(UUID conversationId) {
        return requireThread(conversationId);
    }

    public PreparedTurn prepare(InvestigationRequest request, InvestigationRun prior) {
        return prepare(request, prior, null);
    }

    public PreparedTurn prepare(InvestigationRequest request, InvestigationRun prior, AuthPrincipal actor) {
        ConversationThread thread = resolveThread(request, prior, actor);
        if (!thread.projectId().equals(request.projectId())) {
            throw new IllegalArgumentException("对话不属于当前项目");
        }
        boolean continuation = request.continueFromRunId() != null;
        long userTurns = conversations.userTurnCount(thread.id());
        if (!continuation && userTurns >= maxUserTurns()) {
            throw new IllegalArgumentException("本对话已达 " + maxUserTurns() + " 轮上限，请点「新对话」再继续。");
        }
        String question = request.question();
        if (continuation && prior != null) {
            question = ContinuationPrompt.append(question, prior.question(), prior.report());
        }
        List<ConversationMessage> recent = recentWindow(thread.id());
        List<String> projectFacts = projectMemory.recall(thread.projectId(), question, memory.getLtmMaxChars());
        String memorySummary = ConversationMemory.current(thread.memorySummary());
        String blocks = TurnHistoryPrompt.blocks(memorySummary, recent, projectFacts);
        String mcpProfile = NamedMcpFocus.requestedProfile(
                request.question(),
                request.mcpProfile(),
                userContents(recent)
        ).orElse(null);
        return new PreparedTurn(
                thread,
                TurnHistoryPrompt.append(question, memorySummary, recent, projectFacts),
                question,
                blocks,
                continuation,
                (int) (continuation ? userTurns : userTurns + 1),
                mcpProfile
        );
    }

    public void recordUser(ConversationThread thread, String content, UUID runId) {
        conversations.saveMessage(ConversationMessage.user(thread.id(), content, runId));
        conversations.save(currentThread(thread).touched());
    }

    public void recordAssistant(ConversationThread thread, String content, UUID runId) {
        conversations.saveMessage(ConversationMessage.assistant(thread.id(), content == null ? "" : content, runId));
        ConversationThread current = currentThread(thread);
        List<ConversationMessage> recent = conversations.recentMessages(thread.id(), memory.recentMessageLimit());
        String user = lastUser(recent);
        String summary = ConversationMemoryCompactor.compact(
                current.memorySummary(),
                user,
                content,
                memory.getSummaryMaxChars()
        );
        ConversationThread updated = conversations.save(current.withMemorySummary(summary));
        // STM 缓存与长期记忆/向量库写入都是增强项，失败只记日志，绝不影响本轮回复落库。
        try {
            window.put(thread.id(), recent);
        } catch (RuntimeException exception) {
            log.warn("会话窗口缓存写入失败，忽略：{}", exception.getMessage());
        }
        try {
            factSink.afterTurn(updated, user, content);
        } catch (RuntimeException exception) {
            log.warn("长期记忆投递失败，忽略：{}", exception.getMessage());
        }
    }

    private ConversationThread currentThread(ConversationThread thread) {
        return conversations.findById(thread.id()).orElse(thread);
    }

    public List<ConversationMessage> messages(UUID conversationId) {
        return conversations.messages(conversationId);
    }

    public long userTurns(UUID conversationId) {
        return conversations.userTurnCount(conversationId);
    }

    /** 会话仍存在且未达轮次上限时可续用。飞书按人续会话时用它判断是否要新开。 */
    public boolean reusable(UUID conversationId) {
        if (conversationId == null) {
            return false;
        }
        return conversations.findById(conversationId)
                .map(thread -> conversations.userTurnCount(thread.id()) < maxUserTurns())
                .orElse(false);
    }

    public void deleteByProjectId(UUID projectId) {
        conversations.deleteByProjectId(projectId);
        if (facts != null) {
            facts.deleteByProjectId(projectId);
        }
    }

    private List<ConversationMessage> recentWindow(UUID conversationId) {
        return window.get(conversationId).orElseGet(() -> {
            List<ConversationMessage> recent = conversations.recentMessages(conversationId, memory.recentMessageLimit());
            window.put(conversationId, recent);
            return recent;
        });
    }

    private static String lastUser(List<ConversationMessage> recent) {
        if (recent == null) {
            return "";
        }
        for (int index = recent.size() - 1; index >= 0; index--) {
            if ("user".equals(recent.get(index).role())) {
                return recent.get(index).content();
            }
        }
        return "";
    }

    private static List<String> userContents(List<ConversationMessage> recent) {
        List<String> texts = new ArrayList<>();
        if (recent == null) {
            return texts;
        }
        for (ConversationMessage message : recent) {
            if ("user".equals(message.role()) && message.content() != null && !message.content().isBlank()) {
                texts.add(message.content());
            }
        }
        return texts;
    }

    public boolean canAccessRun(UUID runId, AuthPrincipal actor) {
        if (actor == null) {
            return true;
        }
        return conversations.findByRunId(runId)
                .flatMap(message -> conversations.findById(message.conversationId()))
                .map(thread -> canRead(actor, thread))
                .orElseGet(actor::superAdmin);
    }

    public void requireAccessToRun(UUID runId, AuthPrincipal actor) {
        if (!canAccessRun(runId, actor)) {
            throw new ForbiddenException("没有权限查看该对话");
        }
    }

    private ConversationThread resolveThread(InvestigationRequest request, InvestigationRun prior, AuthPrincipal actor) {
        if (request.conversationId() != null) {
            return requireReadable(requireThread(request.conversationId()), actor);
        }
        if (request.continueFromRunId() != null) {
            return conversations.findByRunId(request.continueFromRunId())
                    .flatMap(message -> conversations.findById(message.conversationId()))
                    .map(thread -> requireReadable(thread, actor))
                    .orElseGet(() -> seedFromPrior(request, prior, actor));
        }
        return conversations.save(ConversationThread.start(
                request.projectId(),
                request.environment(),
                request.modeOrDefault(),
                originOf(request),
                titleOf(request.question()),
                ownerId(actor)
        ));
    }

    private ConversationThread seedFromPrior(InvestigationRequest request, InvestigationRun prior, AuthPrincipal actor) {
        // 无主旧 run 无法回填作者，普通用户不能借 continueFromRunId 复制别人的报告
        if (actor != null && !actor.superAdmin()) {
            throw new ForbiddenException("没有权限查看该对话");
        }
        if (prior == null) {
            throw new IllegalArgumentException("继续排查的记录不存在");
        }
        ConversationThread thread = conversations.save(ConversationThread.start(
                request.projectId(),
                prior.environment(),
                prior.mode(),
                originOf(request),
                titleOf(prior.question()),
                ownerId(actor)
        ));
        conversations.saveMessage(new ConversationMessage(
                UUID.randomUUID(),
                thread.id(),
                "user",
                prior.question(),
                prior.id(),
                prior.createdAt()
        ));
        conversations.saveMessage(new ConversationMessage(
                UUID.randomUUID(),
                thread.id(),
                "assistant",
                prior.report() == null ? "" : prior.report(),
                prior.id(),
                prior.updatedAt() == null ? Instant.now() : prior.updatedAt()
        ));
        return thread;
    }

    private static ConversationOrigin originOf(InvestigationRequest request) {
        if (request.agentSlug() != null && !request.agentSlug().isBlank()) {
            return ConversationOrigin.AGENT;
        }
        return request.modeOrDefault() == ConversationMode.REVIEW
                ? ConversationOrigin.REVIEW
                : ConversationOrigin.CONVERSATION;
    }

    private ConversationThread requireThread(UUID conversationId) {
        return conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在：" + conversationId));
    }

    private static ConversationThread requireReadable(ConversationThread thread, AuthPrincipal actor) {
        if (!canRead(actor, thread)) {
            throw new ForbiddenException("没有权限查看该对话");
        }
        return thread;
    }

    // 未传 actor 的单测 / 内部调用不强制 ACL；非超管只能看自己的会话，无主历史仅超管可见
    private static boolean canRead(AuthPrincipal actor, ConversationThread thread) {
        if (actor == null) {
            return true;
        }
        if (actor.superAdmin()) {
            return true;
        }
        return actor.userId() != null && actor.userId().equals(thread.createdByUserId());
    }

    private static UUID ownerId(AuthPrincipal actor) {
        return actor == null ? null : actor.userId();
    }

    private void requireProject(UUID projectId) {
        projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + projectId));
    }

    private ConversationSummary toSummary(ConversationThread thread) {
        return new ConversationSummary(
                thread.id(),
                thread.projectId(),
                thread.environment(),
                thread.mode(),
                thread.title(),
                thread.createdAt(),
                thread.updatedAt(),
                conversations.userTurnCount(thread.id()),
                maxUserTurns()
        );
    }

    private ConversationDetail toDetail(ConversationThread thread, List<ConversationMessage> messages) {
        return new ConversationDetail(
                thread.id(),
                thread.projectId(),
                thread.environment(),
                thread.mode(),
                thread.title(),
                thread.memorySummary(),
                thread.createdAt(),
                thread.updatedAt(),
                conversations.userTurnCount(thread.id()),
                maxUserTurns(),
                messages
        );
    }

    static String titleOf(String question) {
        if (question == null || question.isBlank()) {
            return "新对话";
        }
        String compact = question.replaceAll("\\s+", " ").trim();
        return compact.length() > 80 ? compact.substring(0, 80) + "…" : compact;
    }

    public record PreparedTurn(
            ConversationThread thread,
            String agentQuestion,
            String currentQuestion,
            String memoryBlocks,
            boolean continuation,
            int turnIndex,
            String mcpProfile
    ) {
        public PreparedTurn(ConversationThread thread, String agentQuestion, boolean continuation, int turnIndex) {
            this(thread, agentQuestion, "", "", continuation, turnIndex, null);
        }
    }

    public record ConversationSummary(
            UUID id,
            UUID projectId,
            Environment environment,
            ConversationMode mode,
            String title,
            Instant createdAt,
            Instant updatedAt,
            long userTurns,
            int maxTurns
    ) {
    }

    public record ConversationDetail(
            UUID id,
            UUID projectId,
            Environment environment,
            ConversationMode mode,
            String title,
            String memorySummary,
            Instant createdAt,
            Instant updatedAt,
            long userTurns,
            int maxTurns,
            List<ConversationMessage> messages
    ) {
    }
}
