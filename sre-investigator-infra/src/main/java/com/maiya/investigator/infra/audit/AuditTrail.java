package com.maiya.investigator.infra.audit;

import com.maiya.investigator.infra.persistence.AuditEventEntity;
import com.maiya.investigator.infra.persistence.AuditEventJpaRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AuditTrail {
    public static final String CATEGORY_LOGIN = "LOGIN";
    public static final String CATEGORY_OP = "OP";
    public static final String CATEGORY_TOOL = "TOOL";
    public static final String CATEGORY_SYSTEM = "SYSTEM";

    private static final Logger log = LoggerFactory.getLogger(AuditTrail.class);
    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();
    private final ObjectProvider<AuditEventJpaRepository> persistentEvents;
    private volatile Supplier<ActorContext> actorResolver = () -> ActorContext.anonymous();

    public AuditTrail(ObjectProvider<AuditEventJpaRepository> persistentEvents) {
        this.persistentEvents = persistentEvents;
    }

    public void setActorResolver(Supplier<ActorContext> actorResolver) {
        this.actorResolver = actorResolver == null ? () -> ActorContext.anonymous() : actorResolver;
    }

    public void record(String action, String target, Map<String, Object> metadata) {
        record(inferCategory(action), action, target, metadata, null);
    }

    public void record(String category, String action, String target, Map<String, Object> metadata) {
        record(category, action, target, metadata, null);
    }

    public void recordLogin(
            boolean success,
            String action,
            String username,
            UUID userId,
            String clientIp,
            String message
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("username", username == null ? "" : username);
        metadata.put("success", success);
        if (message != null && !message.isBlank()) {
            metadata.put("message", message);
        }
        ActorContext actor = new ActorContext(userId, username, clientIp);
        write(CATEGORY_LOGIN, action, userId == null ? (username == null ? "-" : username) : userId.toString(), metadata, actor, success);
    }

    public void record(String category, String action, String target, Map<String, Object> metadata, Boolean success) {
        write(category, action, target, metadata == null ? Map.of() : metadata, actorResolver.get(), success);
    }

    public List<AuditEvent> recent() {
        return recent(null);
    }

    public List<AuditEvent> recent(String category) {
        AuditEventJpaRepository repository = persistentEvents.getIfAvailable();
        if (repository == null) {
            return events.stream()
                    .filter(event -> category == null || category.isBlank() || category.equalsIgnoreCase(event.category()))
                    .limit(200)
                    .toList();
        }
        List<AuditEventEntity> rows = category == null || category.isBlank()
                ? repository.findTop200ByOrderByOccurredAtDesc()
                : repository.findTop200ByCategoryOrderByOccurredAtDesc(category.trim().toUpperCase());
        return rows.stream().map(this::toEvent).toList();
    }

    private void write(
            String category,
            String action,
            String target,
            Map<String, Object> metadata,
            ActorContext actor,
            Boolean success
    ) {
        Map<String, Object> safeMeta = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> redact(String.valueOf(entry.getValue()))
                ));
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                category == null || category.isBlank() ? CATEGORY_OP : category,
                action,
                redact(target),
                actor == null ? null : actor.userId(),
                actor == null ? null : actor.username(),
                actor == null ? null : actor.clientIp(),
                success,
                safeMeta
        );
        events.add(0, event);
        while (events.size() > 500) {
            events.remove(events.size() - 1);
        }
        log.info(
                "审计 category={} action={} actor={} ip={} target={} {}",
                event.category(),
                event.action(),
                event.actorUsername(),
                event.clientIp(),
                event.target(),
                event.metadata()
        );
        persistentEvents.ifAvailable(repository -> repository.save(new AuditEventEntity(
                event.id(),
                null,
                event.category(),
                event.actorUserId(),
                event.actorUsername(),
                event.clientIp(),
                event.success(),
                event.action(),
                event.target(),
                event.metadata().toString(),
                event.occurredAt()
        )));
    }

    private AuditEvent toEvent(AuditEventEntity event) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (event.getMetadata() != null) {
            metadata.put("stored", event.getMetadata());
        }
        return new AuditEvent(
                event.getId(),
                event.getOccurredAt(),
                event.getCategory() == null ? CATEGORY_OP : event.getCategory(),
                event.getAction(),
                event.getTarget(),
                event.getActorUserId(),
                event.getActorUsername(),
                event.getClientIp(),
                event.getSuccess(),
                metadata
        );
    }

    private static String inferCategory(String action) {
        if (action == null) {
            return CATEGORY_OP;
        }
        if (action.startsWith("auth.login") || action.startsWith("auth.feishu") || action.equals("auth.logout")) {
            return CATEGORY_LOGIN;
        }
        if (action.startsWith("tool.") || action.startsWith("mcp.")) {
            return CATEGORY_TOOL;
        }
        return CATEGORY_OP;
    }

    private String redact(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("(?i)(password|token|secret|authorization)=?[^\\s,&]+", "$1=***")
                .replaceAll("(?i)redis://[^@\\s]+@", "redis://***@")
                .replaceAll("(?i)jdbc:mysql://[^@\\s]+@", "jdbc:mysql://***@");
    }

    public record ActorContext(UUID userId, String username, String clientIp) {
        public static ActorContext anonymous() {
            return new ActorContext(null, null, null);
        }
    }
}
