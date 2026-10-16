package com.codebasecopilot.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codebasecopilot.importjob.scan.ProjectSources;
import com.codebasecopilot.importjob.scan.ScanResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndexRunnerTest {

    private static final List<ProjectSources.SourceFile> ONE_FILE =
            List.of(new ProjectSources.SourceFile("a.txt", Path.of("a.txt"), 10, "text"));

    @Test
    void truncatedScanIsRefused() {
        assertCode(scan(true, 10, 100, ONE_FILE), IndexErrorCode.SCAN_LIMIT_REACHED);
    }

    @Test
    void projectAboveSizeLimitIsRefused() {
        assertCode(scan(false, 101, 100, ONE_FILE), IndexErrorCode.PROJECT_SIZE_LIMIT_EXCEEDED);
    }

    @Test
    void projectWithoutIncludedFilesIsRefused() {
        assertCode(scan(false, 0, 100, List.of()), IndexErrorCode.NO_INCLUDED_FILES);
    }

    @Test
    void projectAtTheLimitIsAccepted() {
        assertThatCode(() -> IndexRunner.check(scan(false, 100, 100, ONE_FILE)))
                .doesNotThrowAnyException();
    }

    private static ProjectSources.SourceScan scan(
            boolean truncated,
            long includedBytes,
            long maxProjectBytes,
            List<ProjectSources.SourceFile> files) {
        ScanResult result =
                new ScanResult(
                        truncated,
                        new ScanResult.Totals(
                                files.size(), files.size(), includedBytes, 0, 0, 0, 0),
                        List.of(),
                        List.of(),
                        List.of());
        return new ProjectSources.SourceScan(0, 1024, maxProjectBytes, result, files);
    }

    private static void assertCode(ProjectSources.SourceScan scan, IndexErrorCode code) {
        assertThatThrownBy(() -> IndexRunner.check(scan))
                .isInstanceOfSatisfying(
                        IndexFailure.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
