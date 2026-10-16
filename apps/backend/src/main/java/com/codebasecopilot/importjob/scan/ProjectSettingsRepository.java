package com.codebasecopilot.importjob.scan;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProjectSettingsRepository {

    static final String PROJECT_FK = "project_settings_project_id_fkey";

    private final JdbcClient jdbc;

    ProjectSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ProjectSettings> find(UUID projectId) {
        return jdbc.sql(
                        """
                        SELECT project_id, exclude_patterns, disabled_default_rules,
                               max_file_bytes, max_project_bytes, settings_version, updated_at
                        FROM project_settings
                        WHERE project_id = :projectId
                        """)
                .param("projectId", projectId)
                .query(ProjectSettingsRepository::map)
                .optional();
    }

    // Every save increments the version, which Etapa 3 compares to decide on reindexing.
    ProjectSettings upsert(
            UUID projectId,
            List<String> excludePatterns,
            List<String> disabledDefaultRules,
            @Nullable Long maxFileBytes,
            @Nullable Long maxProjectBytes,
            Instant now) {
        return jdbc.sql(
                        """
                        INSERT INTO project_settings (project_id, exclude_patterns,
                                                      disabled_default_rules, max_file_bytes,
                                                      max_project_bytes, settings_version,
                                                      updated_at)
                        VALUES (:projectId, :patterns, :disabled, :maxFileBytes,
                                :maxProjectBytes, 1, :now)
                        ON CONFLICT (project_id) DO UPDATE
                        SET exclude_patterns = EXCLUDED.exclude_patterns,
                            disabled_default_rules = EXCLUDED.disabled_default_rules,
                            max_file_bytes = EXCLUDED.max_file_bytes,
                            max_project_bytes = EXCLUDED.max_project_bytes,
                            settings_version = project_settings.settings_version + 1,
                            updated_at = EXCLUDED.updated_at
                        RETURNING project_id, exclude_patterns, disabled_default_rules,
                                  max_file_bytes, max_project_bytes, settings_version,
                                  updated_at
                        """)
                .param("projectId", projectId)
                .param("patterns", excludePatterns.toArray(String[]::new))
                .param("disabled", disabledDefaultRules.toArray(String[]::new))
                .param("maxFileBytes", maxFileBytes)
                .param("maxProjectBytes", maxProjectBytes)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .query(ProjectSettingsRepository::map)
                .single();
    }

    private static ProjectSettings map(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectSettings(
                rs.getObject("project_id", UUID.class),
                strings(rs.getArray("exclude_patterns")),
                strings(rs.getArray("disabled_default_rules")),
                rs.getObject("max_file_bytes", Long.class),
                rs.getObject("max_project_bytes", Long.class),
                rs.getInt("settings_version"),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static List<String> strings(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }
}
