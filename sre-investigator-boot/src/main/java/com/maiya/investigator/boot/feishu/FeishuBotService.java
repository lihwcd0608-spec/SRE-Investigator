package com.maiya.investigator.boot.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maiya.investigator.api.ConversationMode;
import com.maiya.investigator.api.Environment;
import com.maiya.investigator.api.InvestigationRequest;
import com.maiya.investigator.boot.ConversationSessionService;
import com.maiya.investigator.boot.InvestigationRunService;
import com.maiya.investigator.boot.auth.AuthPrincipal;
import com.maiya.investigator.boot.auth.AuthProperties;
import com.maiya.investigator.domain.auth.AuthStore;
import com.maiya.investigator.domain.auth.AuthUser;
import com.maiya.investigator.domain.control.ControlPlaneStore;
import com.maiya.investigator.domain.control.RepositoryConfig;
import com.maiya.investigator.domain.feishu.FeishuChatBinding;
import com.maiya.investigator.domain.feishu.FeishuChatBindingStore;
import com.maiya.investigator.domain.feishu.FeishuThread;
import com.maiya.investigator.domain.feishu.FeishuThreadStore;
import com.maiya.investigator.graph.intent.QuestionIntent;
import com.maiya.investigator.graph.intent.QuestionIntentGate;
import com.maiya.investigator.infra.audit.AuditTrail;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeishuBotService {
    private static final Logger log = LoggerFactory.getLogger(FeishuBotService.class);
    // 同一发送人的消息串行处理，避免连发时会话指针互相踩踏；不同人并行不受影响。
    private static final int LOCK_STRIPES = 64;
    private static final long LOCK_WAIT_SECONDS = 120;

    private final AuthProperties properties;
    private final AuthStore authStore;
    private final FeishuChatBindingStore bindings;
    private final FeishuThreadStore threads;
    private final ConversationSessionService sessions;
    private final ControlPlaneStore controlPlane;
    private final InvestigationRunService investigations;
    private final FeishuAppClient feishu;
    private final AuditTrail auditTrail;
    private final QuestionIntentGate intentGate;
    private final ObjectMapper mapper;
    private final ReentrantLock[] senderLocks = createLocks();
    private final ExecutorService workers = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "feishu-bot");
        thread.setDaemon(true);
        return thread;
    });

    public FeishuBotService(
            AuthProperties properties,
            AuthStore authStore,
            FeishuChatBindingStore bindings,
            FeishuThreadStore threads,
            ConversationSessionService sessions,
            ControlPlaneStore controlPlane,
            InvestigationRunService investigations,
            FeishuAppClient feishu,
            AuditTrail auditTrail,
            QuestionIntentGate intentGate,
            ObjectMapper mapper
    ) {
        this.properties = properties;
        this.authStore = authStore;
        this.bindings = bindings;
        this.threads = threads;
        this.sessions = sessions;
        this.controlPlane = controlPlane;
        this.investigations = investigations;
        this.feishu = feishu;
        this.auditTrail = auditTrail;
        this.intentGate = intentGate;
        this.mapper = mapper;
    }

    private static ReentrantLock[] createLocks() {
        ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }

    @PostConstruct
    void logStartup() {
        AuthProperties.Feishu config = properties.getFeishu();
        log.info(
                "飞书 Bot 配置 botEnabled={} appIdConfigured={} appId={} verificationTokenConfigured={} "
                        + "encryptKeyConfigured={} serviceUsername={} botReady={}",
                config.isBotEnabled(),
                configured(config.getAppId()),
                maskId(config.getAppId()),
                configured(config.getVerificationToken()),
                configured(config.getEncryptKey()),
                config.getServiceUsername(),
                enabled()
        );
        if (config.isBotEnabled() && !enabled()) {
            log.warn("飞书 bot-enabled=true，但 app-id/app-secret 不完整，事件会被忽略");
        }
        if (configured(config.getEncryptKey())) {
            log.warn("飞书 encrypt-key 已配置，但当前实现不解密。开放平台请关闭事件加密，并把 encrypt-key 留空");
        }
    }

    public boolean enabled() {
        return feishu.botConfigured();
    }

    public void handleEvent(JsonNode payload) {
        if (!enabled()) {
            log.warn("飞书 handleEvent 被调用，但 Bot 未就绪，已忽略");
            return;
        }
        String type = text(payload, "type");
        if ("url_verification".equals(type)) {
            log.info("飞书 handleEvent 收到 URL 校验，业务侧跳过");
            return;
        }
        JsonNode header = payload.path("header");
        String eventType = text(header, "event_type");
        if (eventType.isBlank()) {
            eventType = text(payload.path("event"), "type");
        }
        if (!"im.message.receive_v1".equals(eventType)) {
            log.info("飞书事件已忽略：不是收消息 eventType={}", dash(eventType));
            return;
        }
        verifyToken(firstNonBlank(text(header, "token"), text(payload, "token")));
        JsonNode event = payload.path("event");
        JsonNode message = event.path("message");
        String chatType = text(message, "chat_type");
        String chatId = text(message, "chat_id");
        String senderId = text(event.path("sender").path("sender_id"), "open_id");
        String messageId = text(message, "message_id");
        String messageType = text(message, "message_type");
        JsonNode contentNode = message.path("content");
        String content = extractText(contentNode);
        int mentions = mentionCount(message.path("mentions"));
        log.info(
                "飞书开始解析消息 chatType={} chatId={} messageId={} messageType={} contentNode={} "
                        + "mentionCount={} mentions={} rawText={}",
                dash(chatType),
                dash(chatId),
                dash(messageId),
                dash(messageType),
                contentNode == null || contentNode.isMissingNode() ? "missing" : contentNode.getNodeType(),
                mentions,
                mentionSummary(message.path("mentions")),
                FeishuText.preview(content, 400)
        );
        if (!"group".equals(chatType)) {
            log.info("飞书事件已忽略：不是群聊 chatType={} chatId={} messageId={}", dash(chatType), dash(chatId), dash(messageId));
            return;
        }
        if (mentions <= 0) {
            log.info("飞书群消息未 @机器人，已忽略 chat={} message={} messageType={} rawText={}",
                    dash(chatId), dash(messageId), dash(messageType), FeishuText.preview(content, 400));
            return;
        }
        if (chatId.isBlank() || messageId.isBlank()) {
            log.warn("飞书事件缺 chat_id 或 message_id chatId={} messageId={} messageType={}",
                    dash(chatId), dash(messageId), dash(messageType));
            return;
        }
        log.info("飞书群消息入队 chat={} sender={} message={} text={}",
                chatId, dash(senderId), messageId, FeishuText.preview(content, 400));
        workers.execute(() -> processMessage(chatId, senderId, messageId, content));
    }

    private static String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        return right == null ? "" : right;
    }

    private void processMessage(String chatId, String senderId, String messageId, String rawText) {
        ReentrantLock lock = senderLocks[Math.floorMod((chatId + '\u0000' + senderId).hashCode(), LOCK_STRIPES)];
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("飞书消息等待处理被中断 chat={} sender={} message={}", chatId, dash(senderId), messageId);
            return;
        }
        if (!locked) {
            log.warn("飞书同一发送人处理繁忙，已放弃 chat={} sender={} message={}", chatId, dash(senderId), messageId);
            try {
                reply(messageId, "上一个问题还在处理中，请等它回复后再 @我。");
            } catch (Exception replyException) {
                log.warn("飞书繁忙回执发送失败 message={}：{}", messageId, replyException.getMessage());
            }
            return;
        }
        try {
            doProcessMessage(chatId, senderId, messageId, rawText);
        } finally {
            lock.unlock();
        }
    }

    private void doProcessMessage(String chatId, String senderId, String messageId, String rawText) {
        long started = System.currentTimeMillis();
        try {
            FeishuMessageParser.ParsedMessage parsed = FeishuMessageParser.parse(rawText);
            log.info(
                    "飞书解析结果 chat={} message={} mode={} env={} reset={} question={} base={} head={} raw={}",
                    chatId,
                    messageId,
                    parsed.mode(),
                    parsed.environment() == null ? "-" : parsed.environment().code(),
                    parsed.resetThread(),
                    FeishuText.preview(parsed.question(), 400),
                    dash(parsed.baseRef()),
                    dash(parsed.headRef()),
                    FeishuText.preview(rawText, 400)
            );
            FeishuChatBinding binding = bindings.findByChatId(chatId).orElse(null);
            if (binding == null || !binding.enabled()) {
                log.warn(
                        "飞书群未绑定或已停用 chat={} message={} bindingFound={} enabled={}，将回复引导文案",
                        chatId,
                        messageId,
                        binding != null,
                        binding != null && binding.enabled()
                );
                reply(messageId, "这个群还没有绑定项目。请到控制台「飞书群」页配置 chat_id → 项目/环境。");
                return;
            }
            UUID resumeConversationId = parsed.mode() == ConversationMode.REVIEW
                    ? null
                    : resolveConversation(chatId, senderId);
            log.info(
                    "飞书群绑定 chat={} sender={} name={} enabled={} project={} defaultEnv={} allowed={} route={} repo={} resumeConversation={}",
                    chatId,
                    dash(senderId),
                    dash(binding.chatName()),
                    binding.enabled(),
                    binding.projectId(),
                    binding.environment().code(),
                    binding.allowedLabel(),
                    dash(binding.defaultRoute()),
                    binding.defaultRepositoryId() == null ? "-" : binding.defaultRepositoryId(),
                    resumeConversationId == null ? "-" : resumeConversationId
            );
            if (parsed.resetThread()) {
                if (hasSender(senderId)) {
                    threads.clear(chatId, senderId);
                }
                log.info("飞书已重置会话 chat={} sender={} message={}", chatId, dash(senderId), messageId);
                reply(messageId, "已重置你在本群的会话上下文。下次 @我 会开启新对话。");
                return;
            }
            if (parsed.mode() == ConversationMode.REVIEW) {
                if (binding.defaultRepositoryId() == null && (parsed.baseRef() == null || parsed.headRef() == null)) {
                    log.warn("飞书审查缺少代码仓和分支 chat={} message={} repo={} base={} head={}",
                            chatId, messageId, binding.defaultRepositoryId(), parsed.baseRef(), parsed.headRef());
                    reply(messageId, "代码审查需要绑定默认代码仓，或发送：审查 main..feature/xxx 重点看什么");
                    return;
                }
                if (parsed.baseRef() == null || parsed.headRef() == null) {
                    log.warn("飞书审查缺少分支 chat={} message={} base={} head={}",
                            chatId, messageId, parsed.baseRef(), parsed.headRef());
                    reply(messageId, "请指定两个分支，例如：审查 release..feature/xxx，或：拉取 feature/xxx 和 release 分支做代码 review");
                    return;
                }
            } else if (parsed.question().isBlank()) {
                log.info("飞书问句为空 chat={} message={} raw={}", chatId, messageId, FeishuText.preview(rawText, 400));
                reply(messageId, "请 @我 后描述要排查的问题。");
                return;
            }
            QuestionIntent intent = intentGate.evaluate(parsed.mode(), parsed.question(), resumeConversationId != null);
            if (!intent.proceed()) {
                log.info("飞书问句前置过滤 chat={} message={} reply={}",
                        chatId, messageId, FeishuText.preview(intent.reply(), 400));
                reply(messageId, intent.reply());
                return;
            }
            Environment runEnv;
            try {
                runEnv = binding.resolveEnvironment(parsed.environment());
            } catch (IllegalArgumentException exception) {
                log.warn(
                        "飞书环境不在允许列表 chat={} message={} requested={} allowed={}：{}",
                        chatId,
                        messageId,
                        parsed.environment() == null ? "-" : parsed.environment().code(),
                        binding.allowedLabel(),
                        exception.getMessage()
                );
                reply(messageId, exception.getMessage());
                return;
            }
            String ack = parsed.mode() == ConversationMode.REVIEW ? "收到，开始代码审查…" : "收到，开始排查…";
            reply(messageId, ack);
            AuthPrincipal actor = botPrincipal();
            InvestigationRequest request = toRequest(binding, parsed, runEnv, resumeConversationId);
            log.info(
                    "飞书开始排查 actor={} roles={} project={} env={} mode={} route={} repo={} base={} head={} "
                            + "conversation={} question={}",
                    actor.username(),
                    actor.roles(),
                    request.projectId(),
                    request.environment(),
                    request.mode(),
                    dash(request.route()),
                    request.repositoryId() == null ? "-" : request.repositoryId(),
                    dash(request.baseRef()),
                    dash(request.headRef()),
                    request.conversationId() == null ? "-" : request.conversationId(),
                    FeishuText.preview(request.question(), 400)
            );
            InvestigationRunService.RunOutcome outcome = investigations.investigate(request, thought -> {
            }, null, actor);
            if (hasSender(senderId)) {
                threads.upsert(chatId, senderId, outcome.conversationId(), Instant.now());
            }
            auditTrail.record("feishu.bot.reply", chatId, Map.of(
                    "projectId", binding.projectId(),
                    "mode", parsed.mode().name(),
                    "runId", outcome.run().id()
            ));
            String report = formatOutcome(outcome);
            log.info(
                    "飞书排查完成 chat={} message={} run={} conversation={} status={} elapsedMs={} reportChars={} report={}",
                    chatId,
                    messageId,
                    outcome.run().id(),
                    outcome.conversationId(),
                    outcome.run().status(),
                    System.currentTimeMillis() - started,
                    report.length(),
                    FeishuText.preview(report, 400)
            );
            reply(messageId, report);
            log.info("飞书结果已回贴 chat={} message={} elapsedMs={}",
                    chatId, messageId, System.currentTimeMillis() - started);
        } catch (Exception exception) {
            log.error("飞书群消息处理失败 chat={} message={} elapsedMs={}：{}",
                    chatId, messageId, System.currentTimeMillis() - started, exception.getMessage(), exception);
            try {
                reply(messageId, "处理失败：" + exception.getMessage());
            } catch (Exception replyException) {
                log.error("飞书失败回执也发送失败 chat={} message={}：{}",
                        chatId, messageId, replyException.getMessage(), replyException);
            }
        }
    }

    private void reply(String messageId, String text) {
        log.info("飞书准备回复 message={} chars={} text={}", messageId, text == null ? 0 : text.length(), FeishuText.preview(text, 400));
        feishu.replyText(messageId, text);
        log.info("飞书回复成功 message={} chars={}", messageId, text == null ? 0 : text.length());
    }

    private InvestigationRequest toRequest(
            FeishuChatBinding binding,
            FeishuMessageParser.ParsedMessage parsed,
            Environment environment,
            UUID conversationId
    ) {
        UUID repositoryId = binding.defaultRepositoryId();
        String route = binding.defaultRoute();
        if (parsed.mode() == ConversationMode.REVIEW) {
            if (repositoryId == null) {
                List<RepositoryConfig> repos = controlPlane.repositories(binding.projectId());
                if (repos.isEmpty()) {
                    throw new IllegalArgumentException("项目还没有代码仓，无法审查");
                }
                repositoryId = repos.get(0).id();
            }
            if (route == null || route.isBlank()) {
                route = "code-review";
            }
            return new InvestigationRequest(
                    binding.projectId(),
                    environment,
                    parsed.question(),
                    ConversationMode.REVIEW,
                    null,
                    null,
                    null,
                    conversationId,
                    null,
                    route,
                    repositoryId,
                    parsed.baseRef(),
                    parsed.headRef()
            );
        }
        return new InvestigationRequest(
                binding.projectId(),
                environment,
                parsed.question(),
                ConversationMode.INVESTIGATE,
                null,
                null,
                null,
                conversationId,
                null,
                route == null || route.isBlank() ? null : route,
                null,
                null,
                null
        );
    }

    /**
     * 按发送人续会话：同一人在闲置超时内、且会话仍可续用时，接着上一条会话；
     * 否则返回 null 由 InvestigationRunService 新开。缺发送人时一律新开，避免匿名共用。
     */
    private UUID resolveConversation(String chatId, String senderId) {
        if (!hasSender(senderId)) {
            return null;
        }
        return threads.find(chatId, senderId)
                .filter(thread -> withinTtl(thread.lastActiveAt()))
                .map(FeishuThread::conversationId)
                .filter(sessions::reusable)
                .orElse(null);
    }

    private boolean withinTtl(Instant lastActiveAt) {
        int ttlMinutes = properties.getFeishu().getSessionTtlMinutes();
        if (ttlMinutes <= 0) {
            return true;
        }
        return lastActiveAt != null
                && Duration.between(lastActiveAt, Instant.now()).toMinutes() < ttlMinutes;
    }

    private static boolean hasSender(String senderId) {
        return senderId != null && !senderId.isBlank();
    }

    private AuthPrincipal botPrincipal() {
        String username = properties.getFeishu().getServiceUsername();
        AuthUser user = authStore.userByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "未找到飞书 Bot 服务账号「" + username + "」。请在用户管理创建并分配项目权限。"
                ));
        if (!user.enabled()) {
            throw new IllegalStateException("飞书 Bot 服务账号已禁用：" + username);
        }
        AuthPrincipal actor = AuthPrincipal.from(user, authStore);
        log.info("飞书服务账号 username={} enabled={} roles={} permissions={}",
                actor.username(), user.enabled(), actor.roles(), actor.permissions());
        return actor;
    }

    private static String formatOutcome(InvestigationRunService.RunOutcome outcome) {
        String report = outcome.result().report();
        if (report == null || report.isBlank()) {
            return "已完成，但没有生成报告。";
        }
        return report;
    }

    private void verifyToken(String token) {
        String expected = properties.getFeishu().getVerificationToken();
        if (expected == null || expected.isBlank()) {
            log.info("飞书 verification-token 未配置，跳过校验 tokenPresent={}", token != null && !token.isBlank());
            return;
        }
        if (!expected.equals(token)) {
            log.error("飞书事件 token 不匹配 expectedConfigured=true incomingPresent={}",
                    token != null && !token.isBlank());
            throw new IllegalArgumentException("飞书事件 token 不匹配");
        }
        log.info("飞书事件 token 校验通过");
    }

    private String extractText(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode()) {
            return "";
        }
        if (contentNode.isTextual()) {
            try {
                JsonNode parsed = mapper.readTree(contentNode.asText());
                return text(parsed, "text");
            } catch (Exception exception) {
                log.warn("飞书消息 content 不是 JSON 文本，按原样使用：{}", exception.getMessage());
                return contentNode.asText("");
            }
        }
        return text(contentNode, "text");
    }

    private static int mentionCount(JsonNode mentions) {
        if (mentions == null || !mentions.isArray()) {
            return 0;
        }
        return mentions.size();
    }

    private static String mentionSummary(JsonNode mentions) {
        if (mentions == null || !mentions.isArray() || mentions.isEmpty()) {
            return "无";
        }
        List<String> items = new ArrayList<>();
        for (JsonNode mention : mentions) {
            items.add(dash(text(mention, "key")) + "/" + dash(text(mention, "name"))
                    + "/" + dash(text(mention.path("id"), "open_id")));
        }
        return String.join(", ", items);
    }

    private static boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    private static String maskId(String value) {
        if (!configured(value)) {
            return "-";
        }
        if (value.length() <= 8) {
            return value.charAt(0) + "***";
        }
        return value.substring(0, 6) + "***" + value.substring(value.length() - 4);
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }
}
