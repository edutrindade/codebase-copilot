package com.codebasecopilot.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// A project created between the count and the DELETE must yield 409, never 500 (D-031).
@IntegrationTest
class WorkspaceDeleteRaceIT {

    @Autowired private MockMvcTester mvc;
    @Autowired private JdbcClient jdbc;

    @MockitoBean private WorkspaceProjectCounter counter;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void restrictForeignKeyIsTranslatedToConflict() {
        UUID workspaceId = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO workspaces (id, name, created_at, updated_at)
                        VALUES (:id, 'W', now(), now())
                        """)
                .param("id", workspaceId)
                .update();
        jdbc.sql(
                        """
                        INSERT INTO projects (id, workspace_id, name, source_type, created_at,
                                              updated_at)
                        VALUES (:id, :workspaceId, 'P', 'ZIP', now(), now())
                        """)
                .param("id", UUID.randomUUID())
                .param("workspaceId", workspaceId)
                .update();
        // The count misses the concurrent project, so only the foreign key can stop the delete.
        when(counter.countByWorkspace(any())).thenReturn(0L);

        assertThat(mvc.delete().uri("/api/v1/workspaces/" + workspaceId))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {"code": "WORKSPACE_NOT_EMPTY", "title": "Workspace não está vazio"}
                        """);
        assertThat(
                        jdbc.sql("SELECT count(*) FROM workspaces WHERE id = :id")
                                .param("id", workspaceId)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }
}
