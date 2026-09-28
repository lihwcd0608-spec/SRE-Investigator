package com.maiya.investigator.graph;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 给模型调用加"重试 + 指数退避"，扛住 dashscope/openai 等的瞬时 I/O 报错，
 * 避免一次网络抖动就让整轮排查废掉、只吐降级 stub。
 *
 * <p>说明：这里在同一线程上重试，保证 {@link AgentThoughts} 的 ThreadLocal sink 与工具进度
 * 事件不丢；超时交给各模型 HTTP 客户端自身处理。只读工具可安全重放。
 */
final class ResilientModelCall {
    private static final Logger log = LoggerFactory.getLogger(ResilientModelCall.class);

    private ResilientModelCall() {
    }

    static String content(Supplier<String> call, int maxAttempts, long backoffMs, Consumer<AgentThought> progress) {
        int attempts = Math.max(1, maxAttempts);
        long backoff = Math.max(0, backoffMs);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException exception) {
                last = exception;
                if (attempt >= attempts) {
                    break;
                }
                log.warn("模型调用第 {}/{} 次失败，将在 {}ms 后重试：{}", attempt, attempts, backoff, exception.getMessage());
                if (progress != null) {
                    progress.accept(AgentThought.of("model", "模型调用第 " + attempt + " 次失败，重试中…"));
                }
                sleep(backoff);
                backoff *= 2;
            }
        }
        throw last == null ? new IllegalStateException("模型调用失败") : last;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
