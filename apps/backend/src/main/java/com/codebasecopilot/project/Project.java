package com.codebasecopilot.project;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("projects")
public record Project(
        @Id UUID id,
        UUID workspaceId,
        String name,
        SourceType sourceType,
        @Nullable String sourceRootKey,
        @Nullable String sourceRelativePath,
        @Nullable UUID currentImportJobId,
        Instant createdAt,
        Instant updatedAt) {}
