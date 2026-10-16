package com.codebasecopilot.retrieval;

import com.codebasecopilot.providers.ModelInfo;
import com.codebasecopilot.retrieval.RrfFusion.RankedHit;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Busca", description = "Busca de trechos de código de um snapshot do projeto.")
class SearchController {

    private final SearchService service;

    SearchController(SearchService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/search")
    @ApiErrors({
        ErrorCode.PROJECT_NOT_FOUND,
        ErrorCode.SNAPSHOT_NOT_FOUND,
        ErrorCode.SNAPSHOT_NOT_SEARCHABLE,
        ErrorCode.SNAPSHOT_LEXICAL_MISMATCH,
        ErrorCode.EMBEDDINGS_NOT_AVAILABLE,
        ErrorCode.SNAPSHOT_EMBEDDING_MISMATCH,
        ErrorCode.EMBEDDING_PROVIDER_UNAVAILABLE,
        ErrorCode.EMBEDDING_PROVIDER_UNAUTHORIZED,
        ErrorCode.EMBEDDING_QUOTA_EXCEEDED,
        ErrorCode.EMBEDDING_REQUEST_REJECTED,
        ErrorCode.EMBEDDING_RATE_LIMITED
    })
    @Operation(
            operationId = "searchProject",
            summary = "Busca trechos de um snapshot pela pergunta",
            description =
                    "Sem snapshotId, usa o snapshot ativo. LEXICAL busca por termos e não usa o"
                            + " provedor de embeddings; VECTOR e HYBRID enviam a pergunta ao"
                            + " provedor configurado. HYBRID funde as buscas lexical, vetorial"
                            + " e por caminhos citados na pergunta.")
    SearchResponse search(@PathVariable UUID projectId, @RequestBody SearchRequest request) {
        SearchService.Result result =
                service.search(
                        projectId,
                        new SearchService.Query(
                                request.query(),
                                request.snapshotId(),
                                request.limit(),
                                request.mode()));
        ModelInfo model = result.model();
        return new SearchResponse(
                result.snapshotId(),
                result.mode(),
                model == null
                        ? null
                        : new ModelResponse(model.provider(), model.model(), model.dimensions()),
                result.lexicalVersion(),
                result.hits().stream().map(SearchController::result).toList());
    }

    private static ResultResponse result(RankedHit ranked) {
        SearchHit hit = ranked.hit();
        return new ResultResponse(
                hit.chunkId(),
                hit.paths().getFirst(),
                hit.paths().subList(1, hit.paths().size()),
                hit.startLine(),
                hit.endLine(),
                hit.content(),
                ranked.score(),
                new RanksResponse(
                        ranked.ranks().lexical(), ranked.ranks().vector(), ranked.ranks().path()));
    }

    // mode is a string so an unknown value is a VALIDATION_FAILED on the field, not a
    // malformed body.
    record SearchRequest(
            @Schema(minLength = 1, maxLength = SearchService.MAX_QUERY_CHARS)
                    @Nullable String query,
            @Schema(nullable = true, description = "Ausente: snapshot ativo do projeto.")
                    @Nullable UUID snapshotId,
            @Schema(
                            minimum = "1",
                            maximum = "" + SearchService.MAX_LIMIT,
                            defaultValue = "" + SearchService.DEFAULT_LIMIT,
                            nullable = true)
                    @Nullable Integer limit,
            @Schema(
                            allowableValues = {"LEXICAL", "VECTOR", "HYBRID"},
                            defaultValue = "HYBRID",
                            nullable = true)
                    @Nullable String mode) {}

    record SearchResponse(
            UUID snapshotId,
            SearchMode mode,
            @Schema(nullable = true, description = "null no modo LEXICAL.")
                    @Nullable ModelResponse model,
            @Schema(nullable = true, description = "Versão do índice lexical; null no modo VECTOR.")
                    @Nullable String lexicalVersion,
            List<ResultResponse> results) {}

    record ModelResponse(String provider, String name, int dimensions) {}

    record ResultResponse(
            UUID chunkId,
            @Schema(description = "Primeiro caminho em ordem lexicográfica com este conteúdo.")
                    String path,
            @Schema(description = "Demais caminhos do snapshot com o mesmo conteúdo.")
                    List<String> otherPaths,
            int startLine,
            int endLine,
            String content,
            @Schema(
                            description =
                                    "Depende do modo: 1 − distância de cosseno (VECTOR), ts_rank_cd"
                                            + " (LEXICAL) ou RRF (HYBRID). Maior é melhor; só"
                                            + " serve para ordenar dentro de uma resposta.")
                    double score,
            RanksResponse ranks) {}

    @Schema(
            description =
                    "Posição do trecho em cada busca, a partir de 1; null quando ela não o trouxe.")
    record RanksResponse(
            @Schema(nullable = true) @Nullable Integer lexical,
            @Schema(nullable = true) @Nullable Integer vector,
            @Schema(nullable = true) @Nullable Integer path) {}
}
