package com.codebasecopilot.importjob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ImportConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ImportConfiguration.class);

    @Test
    void failsWhenMultipartLimitIsBelowZipLimit() {
        runner.withPropertyValues(
                        "app.import.max-zip-bytes=2MB",
                        "spring.servlet.multipart.max-file-size=2MB",
                        "spring.servlet.multipart.max-request-size=1MB")
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining(
                                                "spring.servlet.multipart.max-request-size"));
    }

    @Test
    void failsWithSpringDefaultMultipartLimit() {
        runner.withPropertyValues("app.import.max-zip-bytes=2MB")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startsWhenLimitsMatchOrMultipartIsUnlimited() {
        runner.withPropertyValues(
                        "app.import.max-zip-bytes=2MB",
                        "spring.servlet.multipart.max-file-size=2MB",
                        "spring.servlet.multipart.max-request-size=-1")
                .run(context -> assertThat(context).hasSingleBean(ZipExtractor.class));
    }
}
