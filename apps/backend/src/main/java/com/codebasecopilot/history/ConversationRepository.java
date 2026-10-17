package com.codebasecopilot.history;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ConversationRepository {

    static final String PROJECT_FK = "conversations_project_id_fkey";

    enum Role {
        USER,
        ASSISTANT
    }

    private final JdbcClient jdbc;

    ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID id, UUID projectId, String title, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO conversations (id, project_id, title, created_at, updated_at)
                        VALUES (:id, :projectId, :title, :now, :now)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .param("title", title)
                .param("now", Timestamps.of(now))
                .update();
    }

    // False when the conversation does not exist or belongs to another project.
    boolean touch(UUID id, UUID projectId, Instant now) {
        return jdbc.sql(
                                """
                                UPDATE conversations SET updated_at = :now
                                WHERE id = :id AND project_id = :projectId
                                """)
                        .param("id", id)
                        .param("projectId", projectId)
                        .param("now", Timestamps.of(now))
                        .update()
                == 1;
    }

    boolean exists(UUID id, UUID projectId) {
        return jdbc.sql(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM conversations WHERE id = :id AND project_id = :projectId)
                        """)
                .param("id", id)
                .param("projectId", projectId)
                .query(Boolean.class)
                .single();
    }

    void insertMessage(UUID id, UUID conversationId, Role role, String content, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO messages (id, conversation_id, role, content, created_at)
                        VALUES (:id, :conversationId, :role, :content, :now)
                        """)
                .param("id", id)
                .param("conversationId", conversationId)
                .param("role", role.name())
                .param("content", content)
                .param("now", Timestamps.of(now))
                .update();
    }
}
