package com.codebasecopilot.history;

import com.codebasecopilot.history.ConversationRepository.Role;
import com.codebasecopilot.shared.IdGenerator;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import com.codebasecopilot.shared.persistence.ConstraintViolations;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Each write is a short transaction: provider calls happen between start and complete, never
// inside one. Question and answer texts are stored here, never logged (F-07).
@Service
public class AnswerHistory {

    static final int MAX_TITLE_LENGTH = 100;

    private final ConversationRepository conversations;
    private final AnswerRepository answers;
    private final IdGenerator ids;
    private final Clock clock;

    AnswerHistory(
            ConversationRepository conversations,
            AnswerRepository answers,
            IdGenerator ids,
            Clock clock) {
        this.conversations = conversations;
        this.answers = answers;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public PendingAnswer start(NewQuestion question) {
        Instant now = now();
        UUID conversationId = question.conversationId();
        if (conversationId == null) {
            conversationId = ids.newId();
            try {
                conversations.insert(
                        conversationId, question.projectId(), title(question.question()), now);
            } catch (DataIntegrityViolationException ex) {
                if (ConstraintViolations.violates(ex, ConversationRepository.PROJECT_FK)) {
                    throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
                }
                throw ex;
            }
        } else if (!conversations.touch(conversationId, question.projectId(), now)) {
            throw new ApiException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        UUID questionMessageId = ids.newId();
        conversations.insertMessage(
                questionMessageId, conversationId, Role.USER, question.question(), now);
        UUID answerId = ids.newId();
        answers.insertPending(answerId, questionMessageId, question, now);
        return new PendingAnswer(answerId, conversationId, questionMessageId);
    }

    // False when the answer is no longer PENDING: recovered as interrupted, or removed with its
    // project while the provider was answering.
    @Transactional
    public boolean complete(UUID answerId, AnswerOutcome outcome) {
        Optional<UUID> conversationId = answers.lockPendingConversation(answerId);
        if (conversationId.isEmpty()) {
            return false;
        }
        Instant now = now();
        UUID answerMessageId = null;
        if (outcome.text() != null) {
            answerMessageId = ids.newId();
            conversations.insertMessage(
                    answerMessageId, conversationId.get(), Role.ASSISTANT, outcome.text(), now);
        }
        answers.finish(answerId, answerMessageId, outcome, now);
        for (AnswerEvidence evidence : outcome.evidence()) {
            answers.insertEvidence(ids.newId(), answerId, evidence);
        }
        return true;
    }

    @Transactional
    public int failPendingCreatedBefore(Instant cutoff, String errorCode) {
        return answers.failPendingCreatedBefore(cutoff, errorCode, now());
    }

    // Checks a request before anything is persisted; start checks again.
    @Transactional(readOnly = true)
    public void requireConversation(UUID projectId, UUID conversationId) {
        if (!conversations.exists(conversationId, projectId)) {
            throw new ApiException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public Optional<StoredAnswer> findAnswer(UUID answerId) {
        return answers.findById(answerId);
    }

    // Empty when the evidence does not exist or belongs to another project.
    @Transactional(readOnly = true)
    public Optional<StoredEvidence> findEvidence(UUID projectId, UUID evidenceId) {
        return answers.findEvidence(projectId, evidenceId);
    }

    // Most recently active first (D-030).
    @Transactional(readOnly = true)
    public PageResponse<StoredConversation> listConversations(
            UUID projectId, PageRequestParams params) {
        return PageResponse.of(
                conversations.findPage(projectId, params),
                params,
                conversations.countByProject(projectId));
    }

    @Transactional(readOnly = true)
    public Optional<ConversationAnswers> findConversation(UUID conversationId) {
        return conversations
                .find(conversationId)
                .map(c -> new ConversationAnswers(c, answers.findByConversation(conversationId)));
    }

    // The start of the first question on one line, so conversation lists stay readable.
    static String title(String question) {
        String line = question.strip().replaceAll("\\s+", " ");
        if (line.codePointCount(0, line.length()) <= MAX_TITLE_LENGTH) {
            return line;
        }
        return line.substring(0, line.offsetByCodePoints(0, MAX_TITLE_LENGTH)).stripTrailing();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
