package com.codebasecopilot.workspace;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("workspaces")
public record Workspace(@Id UUID id, String name, Instant createdAt, Instant updatedAt) {}
