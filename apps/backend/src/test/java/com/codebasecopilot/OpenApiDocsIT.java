package com.codebasecopilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsIT {

        private static final Path CONTRACT = Path.of("openapi.json");

        @Autowired
        private MockMvcTester mvc;

        @Test
        void apiDocsAreServed() {
                assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.OK);
        }

        @Test
        void apiDocsMatchVersionedContract() throws IOException {
                String generated = normalize(
                                mvc.get()
                                                .uri("/v3/api-docs")
                                                .exchange()
                                                .getResponse()
                                                .getContentAsString());

                if (Boolean.getBoolean("openapi.update")) {
                        Files.writeString(CONTRACT, generated);
                        return;
                }

                assertThat(CONTRACT)
                                .as(
                                                "Missing %s; run ./mvnw verify -Dopenapi.update=true",
                                                CONTRACT.toAbsolutePath())
                                .exists();
                assertThat(generated)
                                .as(
                                                "OpenAPI contract changed; run ./mvnw verify -Dopenapi.update=true and"
                                                                + " commit openapi.json")
                                .isEqualTo(normalize(Files.readString(CONTRACT)));
        }

        private static String normalize(String json) {
                return json.replace("\r\n", "\n").strip() + "\n";
        }
}
