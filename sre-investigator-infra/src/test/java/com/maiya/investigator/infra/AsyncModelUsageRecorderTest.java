package com.maiya.investigator.infra;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.usage.ModelUsageEvent;
import com.maiya.investigator.domain.usage.ModelUsageStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AsyncModelUsageRecorderTest {
    @Test
    void storeFailureDoesNotPropagate() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AsyncModelUsageRecorder recorder = new AsyncModelUsageRecorder(new ModelUsageStore() {
            @Override
            public ModelUsageEvent save(ModelUsageEvent event) {
                try {
                    throw new IllegalStateException("disk full");
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public List<ModelUsageEvent> listBetween(Instant fromInclusive, Instant toExclusive) {
                return List.of();
            }
        });

        assertDoesNotThrow(() -> recorder.accept(sample()));
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void writesThroughExecutor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<ModelUsageEvent> saved = new CopyOnWriteArrayList<>();
        AsyncModelUsageRecorder recorder = new AsyncModelUsageRecorder(new ModelUsageStore() {
            @Override
            public ModelUsageEvent save(ModelUsageEvent event) {
                saved.add(event);
                latch.countDown();
                return event;
            }

            @Override
            public List<ModelUsageEvent> listBetween(Instant fromInclusive, Instant toExclusive) {
                return List.copyOf(saved);
            }
        });

        recorder.accept(sample());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, saved.size());
        assertEquals("alice", saved.get(0).username());
    }

    private static ModelUsageEvent sample() {
        return ModelUsageEvent.of(
                UUID.randomUUID(),
                "alice",
                "OPERATOR",
                null,
                "dashscope",
                "qwen-plus",
                1L,
                2L,
                3L
        );
    }
}
