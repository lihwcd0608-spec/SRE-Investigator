package com.maiya.investigator.infra.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maiya.investigator.domain.memory.MemoryFact;
import com.maiya.investigator.domain.memory.MemoryFactStore;
import com.maiya.investigator.infra.InMemoryMemoryFactStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectMemoryServiceTest {
    private ProjectMemoryService memoryOf(MemoryFactStore store) {
        MemoryProperties properties = new MemoryProperties();
        return new ProjectMemoryService(
                store,
                properties,
                new EmbeddingClient(properties),
                new QdrantMemoryIndex(properties)
        );
    }

    @Test
    void recallsRelevantProjectFactWhenQdrantIsOff() {
        InMemoryMemoryFactStore store = new InMemoryMemoryFactStore();
        UUID projectId = UUID.randomUUID();
        store.save(MemoryFact.create(projectId, "architecture", "表 t_order 走支付回调", UUID.randomUUID()));

        List<String> hits = memoryOf(store).recall(projectId, "支付回调", 800);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).contains("t_order"));
    }

    @Test
    void skipsUnrelatedFactsInsteadOfDumpingRecent() {
        InMemoryMemoryFactStore store = new InMemoryMemoryFactStore();
        UUID projectId = UUID.randomUUID();
        // 更晚写入的无关记忆不能因为「最近」而被灌进来
        store.save(MemoryFact.create(projectId, "architecture", "支付回调命中 t_order", UUID.randomUUID()));
        store.save(MemoryFact.create(projectId, "architecture", "表 t_user 保存用户资料", UUID.randomUUID()));

        List<String> hits = memoryOf(store).recall(projectId, "支付回调", 800);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).contains("t_order"));
    }

    @Test
    void returnsEmptyWhenNothingRelevant() {
        InMemoryMemoryFactStore store = new InMemoryMemoryFactStore();
        UUID projectId = UUID.randomUUID();
        store.save(MemoryFact.create(projectId, "architecture", "表 t_user 保存用户资料", UUID.randomUUID()));

        assertTrue(memoryOf(store).recall(projectId, "网关 5xx 超时", 800).isEmpty());
    }

    @Test
    void recallSwallowsBackendFailureAndDoesNotBlock() {
        MemoryFactStore throwing = new MemoryFactStore() {
            @Override
            public MemoryFact save(MemoryFact fact) {
                return fact;
            }

            @Override
            public List<MemoryFact> recentByProject(UUID projectId, int limit) {
                throw new IllegalStateException("向量库/事实库故障");
            }

            @Override
            public void deleteByProjectId(UUID projectId) {
            }
        };

        assertTrue(memoryOf(throwing).recall(UUID.randomUUID(), "支付回调", 800).isEmpty());
    }
}
