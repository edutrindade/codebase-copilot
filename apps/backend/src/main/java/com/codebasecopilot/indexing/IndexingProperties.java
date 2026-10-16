package com.codebasecopilot.indexing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Defaults are hypotheses to adjust with measurements (D-040, D-044).
@ConfigurationProperties("app.indexing")
record IndexingProperties(
        @DefaultValue("1") int maxConcurrent,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("10s") Duration heartbeatInterval,
        @DefaultValue("60s") Duration leaseTimeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("100") int batchFiles,
        @DefaultValue("3") int retainedSnapshots) {}
