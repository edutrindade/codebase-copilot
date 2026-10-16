package com.codebasecopilot.indexing;

import com.codebasecopilot.IntegrationTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// One extra context for the indexing tests that need a spy on the file reader (read failures,
// crashes), a retention of one snapshot and small batches. Declared once so subclasses share it.
@IntegrationTest
@TestPropertySource(
        properties = {"app.indexing.retained-snapshots=1", "app.indexing.batch-files=2"})
abstract class SpiedReaderContext {

    @MockitoSpyBean SourceFileReader reader;
}
