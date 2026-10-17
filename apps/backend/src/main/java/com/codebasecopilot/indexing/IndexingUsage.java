package com.codebasecopilot.indexing;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndexingUsage {

    private final IndexJobRepository jobs;

    IndexingUsage(IndexJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public IndexUsage find(UUID projectId) {
        return jobs.usage(projectId);
    }
}
