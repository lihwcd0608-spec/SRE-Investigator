package com.maiya.investigator.infra;

import com.maiya.investigator.domain.usage.ModelUsageEvent;
import com.maiya.investigator.domain.usage.ModelUsageSink;
import com.maiya.investigator.domain.usage.ModelUsageStore;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AsyncModelUsageRecorder implements ModelUsageSink {
    private static final Logger log = LoggerFactory.getLogger(AsyncModelUsageRecorder.class);
    private final ModelUsageStore store;
    private final Executor executor;

    public AsyncModelUsageRecorder(ModelUsageStore store) {
        this.store = store;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "model-usage");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void accept(ModelUsageEvent event) {
        try {
            executor.execute(() -> write(event));
        } catch (RuntimeException exception) {
            log.warn("用量投递失败：{}", exception.getMessage());
        }
    }

    private void write(ModelUsageEvent event) {
        try {
            store.save(event);
        } catch (RuntimeException exception) {
            log.warn("用量写入失败：{}", exception.getMessage());
        }
    }
}
