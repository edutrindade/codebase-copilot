package com.codebasecopilot.workspace;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// Implemented by the project module so workspace never depends on project (D-031).
public interface WorkspaceProjectCounter {

    long countByWorkspace(UUID workspaceId);

    /** Workspaces without projects may be absent from the result. */
    Map<UUID, Long> countByWorkspaces(Collection<UUID> workspaceIds);
}
