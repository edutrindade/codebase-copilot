package com.codebasecopilot.answering;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

final class Questions {

    private Questions() {}

    static MvcTestResult ask(MockMvcTester mvc, UUID projectId, String body) {
        return mvc.post()
                .uri("/api/v1/projects/{id}/questions", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    static DocumentContext json(MvcTestResult result) {
        return JsonPath.parse(body(result));
    }

    static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // Rows of every history table, to check that a rejected request persisted nothing.
    static int historyRows(JdbcClient jdbc) {
        return jdbc.sql(
                        """
                        SELECT (SELECT count(*) FROM conversations) + (SELECT count(*) FROM messages)
                             + (SELECT count(*) FROM answers)
                             + (SELECT count(*) FROM answer_evidence)
                        """)
                .query(Integer.class)
                .single();
    }

    static UUID projectOf(JdbcClient jdbc, UUID snapshotId) {
        return jdbc.sql("SELECT project_id FROM snapshots WHERE id = :id")
                .param("id", snapshotId)
                .query(UUID.class)
                .single();
    }
}
