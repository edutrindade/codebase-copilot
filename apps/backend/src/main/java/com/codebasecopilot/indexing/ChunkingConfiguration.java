package com.codebasecopilot.indexing;

import com.codebasecopilot.indexing.content.LineChunker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Chunking of new snapshots; only the retrieval evaluation replaces it (P3 of Etapa 8).
@Configuration(proxyBeanMethods = false)
class ChunkingConfiguration {

    @Bean
    LineChunker.Parameters chunking() {
        return LineChunker.V1;
    }
}
