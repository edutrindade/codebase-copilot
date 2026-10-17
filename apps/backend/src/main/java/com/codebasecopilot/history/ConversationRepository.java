package com.codebasecopilot.history;

import com.codebasecopilot.shared.pagination.PageRequestParams;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    private static final String CONVERSATION_COLUMNS =
            """
            SELECT c.id, c.project_id, c.title, c.created_at, c.updated_at,
                   (SELECT count(*) FROM messages m
                    WHERE m.conversation_id = c.id AND m.role = 'USER') AS question_count
            FROM conversations c
            """;

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

    // Messages, answers and their evidence go with it (ON DELETE CASCADE).
    boolean delete(UUID id) {
        return jdbc.sql("DELETE FROM conversations WHERE id = :id").param("id", id).update() == 1;
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

    List<StoredConversation> findPage(UUID projectId, PageRequestParams params) {
        return jdbc.sql(
                        CONVERSATION_COLUMNS
                                + """
                                WHERE c.project_id = :projectId
                                ORDER BY c.updated_at DESC, c.id DESC
                                LIMIT :limit OFFSET :offset
                                """)
                .param("projectId", projectId)
                .param("limit", params.size())
                .param("offset", params.offset())
                .query(ConversationRepository::map)
                .list();
    }

    long countByProject(UUID projectId) {
        return jdbc.sql("SELECT count(*) FROM conversations WHERE project_id = :projectId")
                .param("projectId", projectId)
                .query(Long.class)
                .single();
    }

    Optional<StoredConversation> find(UUID id) {
        return jdbc.sql(CONVERSATION_COLUMNS + "WHERE c.id = :id")
                .param("id", id)
                .query(ConversationRepository::map)
                .optional();
    }

    private static StoredConversation map(ResultSet rs, int rowNum) throws SQLException {
        return new StoredConversation(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("title"),
                rs.getInt("question_count"),
                Objects.requireNonNull(Timestamps.read(rs, "created_at")),
                Objects.requireNonNull(Timestamps.read(rs, "updated_at")));
    }
}
