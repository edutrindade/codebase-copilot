package com.codebasecopilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatabaseExtensionsIT {

    @Autowired private JdbcClient jdbcClient;

    @Test
    void requiredExtensionsAreInstalled() {
        List<String> extensions =
                jdbcClient
                        .sql(
                                "SELECT extname FROM pg_extension WHERE extname IN ('vector', 'pg_trgm')")
                        .query(String.class)
                        .list();

        assertThat(extensions).containsExactlyInAnyOrder("vector", "pg_trgm");
    }
}
