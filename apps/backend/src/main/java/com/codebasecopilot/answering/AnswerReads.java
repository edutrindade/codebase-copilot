package com.codebasecopilot.answering;

import com.codebasecopilot.history.AnswerHistory;
import com.codebasecopilot.history.ConversationAnswers;
import com.codebasecopilot.history.StoredAnswer;
import com.codebasecopilot.history.StoredConversation;
import com.codebasecopilot.history.StoredEvidence;
import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.retrieval.Retrieval;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AnswerReads {

    private final ProjectService projects;
    private final AnswerHistory history;
    private final Retrieval retrieval;

    AnswerReads(ProjectService projects, AnswerHistory history, Retrieval retrieval) {
        this.projects = projects;
        this.history = history;
        this.retrieval = retrieval;
    }

    record EvidenceView(StoredEvidence evidence, boolean snapshotAvailable) {}

    StoredAnswer answer(UUID answerId) {
        return history.findAnswer(answerId)
                .orElseThrow(() -> new ApiException(ErrorCode.ANSWER_NOT_FOUND));
    }

    // The copy is returned even after the snapshot is pruned (D-046).
    EvidenceView evidence(UUID projectId, UUID evidenceId) {
        projects.find(projectId);
        StoredEvidence evidence =
                history.findEvidence(projectId, evidenceId)
                        .orElseThrow(() -> new ApiException(ErrorCode.EVIDENCE_NOT_FOUND));
        return new EvidenceView(evidence, retrieval.snapshotAvailable(evidence.snapshotId()));
    }

    PageResponse<StoredConversation> conversations(UUID projectId, PageRequestParams params) {
        projects.find(projectId);
        return history.listConversations(projectId, params);
    }

    ConversationAnswers conversation(UUID conversationId) {
        return history.findConversation(conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONVERSATION_NOT_FOUND));
    }
}
