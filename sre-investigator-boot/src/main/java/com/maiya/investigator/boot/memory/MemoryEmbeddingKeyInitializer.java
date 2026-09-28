package com.maiya.investigator.boot.memory;

import com.maiya.investigator.boot.model.ModelProperties;
import com.maiya.investigator.infra.memory.MemoryProperties;
import org.springframework.stereotype.Component;

@Component
public class MemoryEmbeddingKeyInitializer {
    public MemoryEmbeddingKeyInitializer(MemoryProperties memory, ModelProperties model) {
        if (memory.getQdrant().getEmbeddingApiKey() == null || memory.getQdrant().getEmbeddingApiKey().isBlank()) {
            memory.getQdrant().setEmbeddingApiKey(model.getDashscope().getApiKey());
        }
    }
}
