package com.codebasecopilot.answering;

import com.codebasecopilot.answering.AnswerResponses.AnswerResponse;
import com.codebasecopilot.answering.AnswerResponses.EvidenceDetailResponse;
import com.codebasecopilot.history.StoredAnswer;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Respostas",
        description = "Perguntas sobre o código do projeto, respondidas com evidências citadas.")
class AnswerController {

    private final AnswerService service;
    private final AnswerReads reads;
    private final AnswerResponses responses;

    AnswerController(AnswerService service, AnswerReads reads, AnswerResponses responses) {
        this.service = service;
        this.reads = reads;
        this.responses = responses;
    }

    @PostMapping("/projects/{projectId}/questions")
    @ApiErrors({
        ErrorCode.PROJECT_NOT_FOUND,
        ErrorCode.CONVERSATION_NOT_FOUND,
        ErrorCode.SNAPSHOT_NOT_FOUND,
        ErrorCode.SNAPSHOT_NOT_SEARCHABLE,
        ErrorCode.SNAPSHOT_LEXICAL_MISMATCH,
        ErrorCode.EMBEDDINGS_NOT_AVAILABLE,
        ErrorCode.SNAPSHOT_EMBEDDING_MISMATCH,
        ErrorCode.GENERATION_NOT_AVAILABLE
    })
    @Operation(
            operationId = "askQuestion",
            summary = "Responde a uma pergunta com evidências do código",
            description =
                    "Síncrono: responde quando a geração termina. Sem snapshotId, usa o snapshot"
                            + " ativo; sem conversationId, abre uma conversa. A pergunta e os"
                            + " trechos escolhidos como evidência são enviados ao provedor de"
                            + " geração configurado (e a pergunta ao de embeddings, nos modos"
                            + " VECTOR e HYBRID). Depois que a pergunta é registrada, falhas dos"
                            + " provedores não viram erro HTTP: a resposta é criada com status"
                            + " FAILED e error.code, e a pergunta fica no histórico.")
    ResponseEntity<AnswerResponse> ask(
            @PathVariable UUID projectId, @RequestBody QuestionRequest request) {
        OptionsRequest options = request.options();
        StoredAnswer answer =
                service.ask(
                        projectId,
                        new AnswerService.Question(
                                request.question(),
                                request.snapshotId(),
                                request.conversationId(),
                                options == null ? null : options.maxEvidence(),
                                options == null ? null : options.retrievalMode()));
        return ResponseEntity.created(URI.create("/api/v1/answers/" + answer.id()))
                .body(responses.answer(answer));
    }

    @GetMapping("/answers/{answerId}")
    @ApiErrors(ErrorCode.ANSWER_NOT_FOUND)
    @Operation(
            operationId = "getAnswer",
            summary = "Busca uma resposta com as evidências",
            description =
                    "Mesmo formato da resposta de POST /questions. Uma resposta ainda em geração"
                            + " vem com status PENDING.")
    AnswerResponse get(@PathVariable UUID answerId) {
        return responses.answer(reads.answer(answerId));
    }

    @GetMapping("/projects/{projectId}/evidence/{evidenceId}")
    @ApiErrors({ErrorCode.PROJECT_NOT_FOUND, ErrorCode.EVIDENCE_NOT_FOUND})
    @Operation(
            operationId = "getEvidence",
            summary = "Busca uma evidência como foi enviada ao modelo",
            description =
                    "Devolve a cópia guardada com a resposta, mesmo depois da poda do snapshot"
                            + " (snapshotStatus PRUNED).")
    EvidenceDetailResponse evidence(@PathVariable UUID projectId, @PathVariable UUID evidenceId) {
        AnswerReads.EvidenceView view = reads.evidence(projectId, evidenceId);
        return AnswerResponses.evidenceDetail(view.evidence(), view.snapshotAvailable());
    }

    // retrievalMode is a string so an unknown value is a VALIDATION_FAILED on the field, not a
    // malformed body.
    record QuestionRequest(
            @Schema(minLength = 1, maxLength = AnswerService.MAX_QUESTION_CHARS)
                    @Nullable String question,
            @Schema(nullable = true, description = "Ausente: snapshot ativo do projeto.")
                    @Nullable UUID snapshotId,
            @Schema(nullable = true, description = "Ausente: abre uma conversa nova.")
                    @Nullable UUID conversationId,
            @Schema(nullable = true) @Nullable OptionsRequest options) {}

    record OptionsRequest(
            @Schema(
                            minimum = "1",
                            maximum = "" + AnswerService.MAX_EVIDENCE,
                            defaultValue = "" + AnswerService.DEFAULT_MAX_EVIDENCE,
                            nullable = true,
                            description = "Máximo de trechos enviados ao modelo como evidência.")
                    @Nullable Integer maxEvidence,
            @Schema(
                            allowableValues = {"LEXICAL", "VECTOR", "HYBRID"},
                            defaultValue = "HYBRID",
                            nullable = true,
                            description =
                                    "Busca usada para escolher as evidências. LEXICAL não usa o"
                                            + " provedor de embeddings.")
                    @Nullable String retrievalMode) {}
}
