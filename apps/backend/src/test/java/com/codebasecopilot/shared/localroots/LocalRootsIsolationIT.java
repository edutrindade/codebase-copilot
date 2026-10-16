package com.codebasecopilot.shared.localroots;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.IntegrationTestWithoutLocalRoots;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

// The developer's config/application.yml and PROJECTS_ROOT must not affect tests (D-029).
class LocalRootsIsolationIT {

    private static final Path TEST_ROOTS = Path.of("src/test/resources/local-roots");

    @Nested
    @IntegrationTest
    class WithTestRoots {

        @Autowired private LocalRoots roots;
        @Autowired private ConfigurableEnvironment environment;

        @Test
        void seesOnlyTheRootsDeclaredByTests() {
            assertThat(roots.list())
                    .containsExactly(
                            new LocalRoot(
                                    "default",
                                    "Projetos",
                                    TEST_ROOTS.resolve("default").toAbsolutePath()),
                            new LocalRoot(
                                    "work",
                                    "Trabalho",
                                    TEST_ROOTS.resolve("work").toAbsolutePath()));
        }

        @Test
        void doesNotLoadLocalConfigDirectory() {
            assertThat(
                            StreamSupport.stream(
                                            environment.getPropertySources().spliterator(), false)
                                    .map(PropertySource::getName))
                    .noneMatch(name -> name.contains("optional:file:./config/"))
                    .noneMatch(name -> name.contains("config/application.yml"));
        }
    }

    @Nested
    @IntegrationTestWithoutLocalRoots
    class WithoutRoots {

        @Autowired private LocalRoots roots;

        @Test
        void hasNoRootsEvenIfTheDeveloperConfiguredSome() {
            assertThat(roots.isEmpty()).isTrue();
        }
    }
}
