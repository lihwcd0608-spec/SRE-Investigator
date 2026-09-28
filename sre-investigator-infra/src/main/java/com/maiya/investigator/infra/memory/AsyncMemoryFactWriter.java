package com.maiya.investigator.infra.memory;

import com.maiya.investigator.domain.conversation.ConversationThread;
import com.maiya.investigator.domain.memory.MemoryFact;
import com.maiya.investigator.domain.memory.MemoryFactSink;
import com.maiya.investigator.domain.memory.MemoryFactStore;
import com.maiya.investigator.domain.memory.MemoryFactExtractor;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AsyncMemoryFactWriter implements MemoryFactSink {
    private static final Logger log = LoggerFactory.getLogger(AsyncMemoryFactWriter.class);
    private final MemoryFactStore facts;
    private final EmbeddingClient embeddings;
    private final QdrantMemoryIndex qdrant;
    private final Executor executor;

    public AsyncMemoryFactWriter(MemoryFactStore facts, EmbeddingClient embeddings, QdrantMemoryIndex qdrant) {
        this.facts = facts;
        this.embeddings = embeddings;
        this.qdrant = qdrant;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "memory-fact");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void afterTurn(ConversationThread thread, String user, String assistant) {
        try {
            executor.execute(() -> write(thread, user, assistant));
        } catch (RuntimeException exception) {
            log.warn("长期记忆投递失败：{}", exception.getMessage());
        }
    }

    private void write(ConversationThread thread, String user, String assistant) {
        try {
            List<MemoryFactExtractor.ExtractedFact> extracted = MemoryFactExtractor.extract(user, assistant);
            for (MemoryFactExtractor.ExtractedFact item : extracted) {
                MemoryFact saved = facts.save(MemoryFact.create(
                        thread.projectId(),
                        item.kind(),
                        item.content(),
                        thread.id()
                ));
                List<Float> vector = embeddings.embed(saved.content());
                if (!vector.isEmpty() && qdrant.enabled()) {
                    qdrant.upsert(saved, vector);
                    facts.save(saved.withQdrantPointId(saved.id()));
                }
            }
        } catch (RuntimeException exception) {
            log.warn("长期记忆写入失败：{}", exception.getMessage());
        }
    }
}
