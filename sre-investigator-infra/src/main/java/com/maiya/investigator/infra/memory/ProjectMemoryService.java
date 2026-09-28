package com.maiya.investigator.infra.memory;

import com.maiya.investigator.domain.memory.MemoryFact;
import com.maiya.investigator.domain.memory.MemoryFactStore;
import com.maiya.investigator.domain.memory.ProjectMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProjectMemoryService implements ProjectMemory {
    private static final Logger log = LoggerFactory.getLogger(ProjectMemoryService.class);
    // 关键词回退时先取一批候选，再按与问题的词面重合度筛，最多注入几条，避免灌噪声。
    private static final int CANDIDATE_LIMIT = 50;
    private static final int RELEVANT_LIMIT = 5;
    private final MemoryFactStore facts;
    private final MemoryProperties properties;
    private final EmbeddingClient embeddings;
    private final QdrantMemoryIndex qdrant;

    public ProjectMemoryService(
            MemoryFactStore facts,
            MemoryProperties properties,
            EmbeddingClient embeddings,
            QdrantMemoryIndex qdrant
    ) {
        this.facts = facts;
        this.properties = properties;
        this.embeddings = embeddings;
        this.qdrant = qdrant;
    }

    @Override
    public List<String> recall(UUID projectId, String query, int maxChars) {
        // 记忆是增强项：向量库/embedding/事实库任一读失败都只跳过记忆注入，绝不阻断本轮排查。
        try {
            return doRecall(projectId, query, maxChars);
        } catch (Exception exception) {
            log.warn("项目记忆检索失败，本轮跳过记忆注入：{}", exception.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> doRecall(UUID projectId, String query, int maxChars) {
        int budget = maxChars <= 0 ? properties.getLtmMaxChars() : maxChars;
        List<String> hits = new ArrayList<>();
        if (qdrant.enabled()) {
            hits = qdrant.search(projectId, embeddings.embed(query == null ? "" : query), RELEVANT_LIMIT);
        }
        if (hits.isEmpty()) {
            // Qdrant 未启用或未命中：按与当前问题的词面相关度过滤项目事实，无相关则不注入，避免跨会话噪声。
            List<String> candidates = facts.recentByProject(projectId, CANDIDATE_LIMIT).stream()
                    .map(MemoryFact::content)
                    .toList();
            hits = MemoryRelevance.filter(query, candidates, RELEVANT_LIMIT);
        }
        List<String> clipped = new ArrayList<>();
        int used = 0;
        for (String hit : hits) {
            if (hit == null || hit.isBlank()) {
                continue;
            }
            String text = hit.trim();
            if (used + text.length() > budget) {
                break;
            }
            clipped.add(text);
            used += text.length();
        }
        return clipped;
    }
}
