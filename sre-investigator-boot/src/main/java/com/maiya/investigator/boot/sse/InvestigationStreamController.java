package com.maiya.investigator.boot.sse;

import com.maiya.investigator.api.InvestigationRequest;
import com.maiya.investigator.boot.InvestigationRunService;
import com.maiya.investigator.boot.auth.AccessGuard;
import com.maiya.investigator.boot.auth.RequirePermission;
import com.maiya.investigator.domain.auth.Permissions;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/investigations")
public class InvestigationStreamController {
    private static final Logger log = LoggerFactory.getLogger(InvestigationStreamController.class);
    private static final long SSE_TIMEOUT_MS = 1_800_000L;
    private final InvestigationRunService runs;
    private final AccessGuard access;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "investigation-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public InvestigationStreamController(InvestigationRunService runs, AccessGuard access) {
        this.runs = runs;
        this.access = access;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission(any = {Permissions.INVESTIGATE, Permissions.REVIEW, Permissions.BINDINGS, Permissions.AGENTS})
    public SseEmitter stream(@Valid @RequestBody InvestigationRequest request, HttpServletResponse response) {
        access.requireProject(request.projectId());
        var actor = access.current();
        log.info(
                "SSE 会话开始 mode={} env={} project={} model={}/{} question={}",
                request.modeOrDefault(),
                request.environment(),
                request.projectId(),
                request.provider(),
                request.model(),
                preview(request.question())
        );
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicBoolean finished = new AtomicBoolean(false);
        Object sendLock = new Object();
        ScheduledFuture<?> heartbeat = heartbeats.scheduleAtFixedRate(() -> {
            if (finished.get()) {
                return;
            }
            try {
                send(sendLock, emitter, "heartbeat", Map.of("ok", true));
            } catch (Exception ignored) {
                finished.set(true);
            }
        }, 8, 8, TimeUnit.SECONDS);
        emitter.onCompletion(() -> {
            finished.set(true);
            heartbeat.cancel(true);
        });
        emitter.onTimeout(() -> {
            finished.set(true);
            heartbeat.cancel(true);
        });
        try {
            if (request.modeOrDefault() == com.maiya.investigator.api.ConversationMode.ASK) {
                send(sendLock, emitter, "phase", Map.of("name", "ask", "message", "正在回答问题"));
            } else if (request.route() != null && !request.route().isBlank()
                    && !"auto".equalsIgnoreCase(request.route().trim())) {
                send(sendLock, emitter, "phase", Map.of("name", "route", "message", "正在按所选方向装载上下文"));
            } else {
                send(sendLock, emitter, "phase", Map.of("name", "route", "message", "正在判断排查方向"));
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
            return emitter;
        }
        CompletableFuture.runAsync(() -> {
            try {
                InvestigationRunService.RunOutcome outcome = runs.investigate(request, thought -> {
                    if (finished.get()) {
                        return;
                    }
                    try {
                        send(sendLock, emitter, "thought", Map.of(
                                "kind", thought.kind(),
                                "message", thought.message()
                        ));
                    } catch (Exception exception) {
                        finished.set(true);
                    }
                }, (conversationId, runId) -> {
                    if (finished.get()) {
                        return;
                    }
                    try {
                        send(sendLock, emitter, "started", Map.of(
                                "conversationId", conversationId,
                                "runId", runId
                        ));
                    } catch (Exception exception) {
                        finished.set(true);
                    }
                }, actor);
                log.info("SSE 会话结束 run={} status={}", outcome.run().id(), outcome.run().status());
                Object structured = outcome.result() == null || outcome.result().state() == null
                        ? null
                        : outcome.result().state().get("reportStructured");
                if (structured != null && !finished.get()) {
                    try {
                        send(sendLock, emitter, "evidence", structured);
                    } catch (Exception ignored) {
                        // 结构化证据事件失败不影响最终 result 下发
                    }
                }
                send(sendLock, emitter, "result", outcome);
                emitter.complete();
            } catch (Exception exception) {
                log.error("SSE 会话失败 project={}：{}", request.projectId(), exception.getMessage(), exception);
                try {
                    send(sendLock, emitter, "error", Map.of(
                            "message", exception.getMessage() == null ? "对话执行失败" : exception.getMessage()
                    ));
                    emitter.complete();
                } catch (IOException ignored) {
                    emitter.complete();
                }
            } finally {
                finished.set(true);
                heartbeat.cancel(true);
            }
        });
        return emitter;
    }

    @GetMapping("/{runId}")
    @RequirePermission(any = {Permissions.INVESTIGATE, Permissions.REVIEW, Permissions.AGENTS})
    public com.maiya.investigator.domain.InvestigationRun getRun(@PathVariable UUID runId) {
        var run = runs.getRun(runId, access.current());
        access.requireProject(run.projectId());
        return run;
    }

    private static void send(Object lock, SseEmitter emitter, String event, Object data) throws IOException {
        synchronized (lock) {
            emitter.send(SseEmitter.event().name(event).data(data));
        }
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "…" : compact;
    }
}
