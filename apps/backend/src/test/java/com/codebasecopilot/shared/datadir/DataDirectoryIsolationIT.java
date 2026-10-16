package com.codebasecopilot.shared.datadir;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.IntegrationTestWithoutLocalRoots;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Tests must never write to the developer's ~/.codebase-copilot or DATA_DIR (D-028).
class DataDirectoryIsolationIT {

    @Nested
    @IntegrationTest
    class WithTestRoots {

        @Autowired private DataDirectory dataDir;

        @Test
        void usesTemporaryDirectory() throws IOException {
            assertTemporary(dataDir);
        }
    }

    @Nested
    @IntegrationTestWithoutLocalRoots
    class WithoutRoots {

        @Autowired private DataDirectory dataDir;

        @Test
        void usesTemporaryDirectory() throws IOException {
            assertTemporary(dataDir);
        }
    }

    private static void assertTemporary(DataDirectory dataDir) throws IOException {
        assertThat(dataDir.importsDir())
                .startsWith(Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
                .isDirectory();
    }
}
