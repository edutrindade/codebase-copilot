package com.codebasecopilot.answering;

import com.codebasecopilot.answering.AnswerResponses.AnswerResponse;
import com.codebasecopilot.history.ConversationAnswers;
import com.codebasecopilot.history.StoredConversation;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Conversas", description = "Histórico de perguntas e respostas do projeto.")
class ConversationController {

    private final AnswerReads reads;
    private final AnswerResponses responses;

    ConversationController(AnswerReads reads, AnswerResponses responses) {
        this.reads = reads;
        this.responses = responses;
    }

    @GetMapping("/projects/{projectId}/conversations")
    @ApiErrors(ErrorCode.PROJECT_NOT_FOUND)
    @Operation(
            operationId = "listConversations",
            summary = "Lista as conversas do projeto",
            description = "A conversa com a pergunta mais recente vem primeiro.")
    PageResponse<ConversationSummaryResponse> list(
            @PathVariable UUID projectId, @Valid @ParameterObject PageRequestParams params) {
        return reads.conversations(projectId, params).map(ConversationController::summary);
    }

    @GetMapping("/conversations/{conversationId}")
    @ApiErrors(ErrorCode.CONVERSATION_NOT_FOUND)
    @Operation(
            operationId = "getConversation",
            summary = "Busca uma conversa com as perguntas e respostas",
            description =
                    "Respostas na ordem das perguntas, no formato de GET /answers/{answerId},"
                            + " inclusive as FAILED e as ainda PENDING.")
    ConversationResponse get(@PathVariable UUID conversationId) {
        ConversationAnswers found = reads.conversation(conversationId);
        return new ConversationResponse(
                summary(found.conversation()),
                found.answers().stream().map(responses::answer).toList());
    }

    private static ConversationSummaryResponse summary(StoredConversation conversation) {
        return new ConversationSummaryResponse(
                conversation.id(),
                conversation.projectId(),
                conversation.title(),
                conversation.questionCount(),
                conversation.createdAt(),
                conversation.updatedAt());
    }

    record ConversationSummaryResponse(
            UUID id,
            UUID projectId,
            @Schema(description = "Início da primeira pergunta, numa linha.") String title,
            int questionCount,
            Instant createdAt,
            @Schema(description = "Momento da pergunta mais recente.") Instant updatedAt) {}

    record ConversationResponse(
            ConversationSummaryResponse conversation, List<AnswerResponse> answers) {}
}
