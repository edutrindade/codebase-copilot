package com.codebasecopilot.indexing;

import com.codebasecopilot.codeanalysis.IdentifierTokenizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Fills the lexical rows of the snapshot's chunks that lack the current tokenizer version
// (D-056). Logs carry counts only.
@Component
class LexicalStage {

    private static final Logger log = LoggerFactory.getLogger(LexicalStage.class);

    private final ChunkLexicalRepository lexical;
    private final TransactionTemplate transactions;
    private final IndexingProperties properties;

    LexicalStage(
            ChunkLexicalRepository lexical,
            TransactionTemplate transactions,
            IndexingProperties properties) {
        this.lexical = lexical;
        this.transactions = transactions;
        this.properties = properties;
    }

    void run(Claim claim, Heartbeat heartbeat) {
        List<UUID> missing =
                lexical.missingChunkIds(claim.snapshotId(), IdentifierTokenizer.VERSION);
        heartbeat.stage(IndexStage.LEXICAL, missing.size(), 0);
        int batchSize = properties.lexicalBatchChunks();
        long start = System.nanoTime();
        for (int from = 0; from < missing.size(); from += batchSize) {
            List<UUID> ids = missing.subList(from, Math.min(from + batchSize, missing.size()));
            Map<UUID, String> texts = lexical.chunkTexts(ids);
            List<ChunkLexicalRepository.NewLexical> rows = new ArrayList<>(ids.size());
            for (UUID id : ids) {
                List<String> terms =
                        IdentifierTokenizer.identifierTerms(Objects.requireNonNull(texts.get(id)));
                rows.add(new ChunkLexicalRepository.NewLexical(id, String.join(" ", terms)));
            }
            int completed = from + ids.size();
            transactions.executeWithoutResult(
                    tx -> {
                        heartbeat.progress(completed);
                        lexical.insert(IdentifierTokenizer.VERSION, rows);
                    });
        }
        log.info(
                "Index job {} built the lexical index of {} chunks in {} ms",
                claim.jobId(),
                missing.size(),
                (System.nanoTime() - start) / 1_000_000);
    }
}
