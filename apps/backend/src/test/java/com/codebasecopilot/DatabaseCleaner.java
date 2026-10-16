package com.codebasecopilot;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class DatabaseCleaner {

    private DatabaseCleaner() {}

    public static void truncateAll(JdbcClient jdbc) {
        List<String> tables =
                jdbc.sql(
                                """
                                SELECT quote_ident(tablename)
                                FROM pg_tables
                                WHERE schemaname = 'public'
                                  AND tablename <> 'flyway_schema_history'
                                """)
                        .query(String.class)
                        .list();
        if (!tables.isEmpty()) {
            jdbc.sql("TRUNCATE " + String.join(", ", tables) + " CASCADE").update();
        }
    }
}
