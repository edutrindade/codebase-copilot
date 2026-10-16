package com.codebasecopilot.indexing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Removing a project leaves its embeddings behind until the next completed job or this sweep
// (D-052).
@Component
class EmbeddingOrphanSweep {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingOrphanSweep.class);

    private final ChunkEmbeddingRepository embeddings;

    EmbeddingOrphanSweep(ChunkEmbeddingRepository embeddings) {
        this.embeddings = embeddings;
    }

    @EventListener(ApplicationReadyEvent.class)
    void sweep() {
        int deleted = embeddings.deleteOrphans();
        if (deleted > 0) {
            log.info("Removed {} orphan embeddings", deleted);
        }
    }
}
