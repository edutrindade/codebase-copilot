package com.codebasecopilot.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.DatabaseCleaner;
import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class WorkspaceRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00.123456Z");

    @Autowired private WorkspaceRepository repository;
    @Autowired private JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.truncateAll(jdbc);
    }

    @Test
    void insertsAndFindsById() {
        Workspace workspace = workspace("0192f5c4-0000-7000-8000-000000000001", T0);

        repository.insert(workspace);

        assertThat(repository.findById(workspace.id())).contains(workspace);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
        assertThat(repository.existsById(workspace.id())).isTrue();
        assertThat(repository.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void ordersByCreatedAtThenIdDescending() {
        Workspace oldest = workspace("0192f5c4-0000-7000-8000-000000000009", T0);
        Workspace tieLow = workspace("0192f5c4-0000-7000-8000-000000000001", T0.plusSeconds(1));
        Workspace tieHigh = workspace("0192f5c4-0000-7000-8000-000000000002", T0.plusSeconds(1));
        Workspace newest = workspace("0192f5c4-0000-7000-8000-000000000003", T0.plusSeconds(2));
        for (Workspace w : new Workspace[] {tieLow, oldest, newest, tieHigh}) {
            repository.insert(w);
        }

        assertThat(repository.findPage(new PageRequestParams(0, 20)))
                .containsExactly(newest, tieHigh, tieLow, oldest);
        assertThat(repository.findPage(new PageRequestParams(1, 3))).containsExactly(oldest);
        assertThat(repository.findPage(new PageRequestParams(2, 3))).isEmpty();
        assertThat(repository.count()).isEqualTo(4);
    }

    @Test
    void deleteReportsWhetherRowExisted() {
        Workspace workspace = workspace("0192f5c4-0000-7000-8000-000000000001", T0);
        repository.insert(workspace);

        assertThat(repository.deleteById(workspace.id())).isTrue();
        assertThat(repository.deleteById(workspace.id())).isFalse();
        assertThat(repository.findById(workspace.id())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void databaseRejectsInvalidNameLength(int length) {
        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                """
                                                INSERT INTO workspaces (id, name, created_at, updated_at)
                                                VALUES (:id, :name, :now, :now)
                                                """)
                                        .param("id", UUID.randomUUID())
                                        .param("name", "a".repeat(length))
                                        .param("now", OffsetDateTime.now())
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("workspaces_name_length");
    }

    private static Workspace workspace(String id, Instant createdAt) {
        return new Workspace(UUID.fromString(id), "Workspace " + id, createdAt, createdAt);
    }
}
