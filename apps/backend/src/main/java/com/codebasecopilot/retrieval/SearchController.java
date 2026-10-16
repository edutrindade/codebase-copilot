package com.codebasecopilot.retrieval;

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
            summary = "Busca trechos de um snapshot por similaridade com a pergunta",
            description =
                    "Sem snapshotId, usa o snapshot ativo. A pergunta é enviada ao provedor de"
                            + " embeddings configurado.")
    SearchResponse search(@PathVariable UUID projectId, @RequestBody SearchRequest request) {
        SearchService.Result result =
                service.search(
                        projectId,
                        new SearchService.Query(
                                request.query(), request.snapshotId(), request.limit()));
        return new SearchResponse(
                result.snapshotId(),
                new ModelResponse(
                        result.model().provider(),
                        result.model().model(),
                        result.model().dimensions()),
                result.hits().stream()
                        .map(
                                hit ->
                                        new ResultResponse(
                                                hit.chunkId(),
                                                hit.paths().getFirst(),
                                                hit.paths().subList(1, hit.paths().size()),
                                                hit.startLine(),
                                                hit.endLine(),
                                                hit.content(),
                                                hit.score()))
                        .toList());
    }

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
                    @Nullable Integer limit) {}

    record SearchResponse(UUID snapshotId, ModelResponse model, List<ResultResponse> results) {}

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
            @Schema(description = "1 − distância de cosseno; maior é mais parecido.")
                    double score) {}
}
