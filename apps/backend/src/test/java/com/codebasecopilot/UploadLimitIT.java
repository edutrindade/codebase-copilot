package com.codebasecopilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

// The only extra context of Etapa 2: MockMvc does not apply the multipart limits (D-028).
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            TestProperties.CONFIG_LOCATION,
            TestProperties.NO_PROJECTS_ROOT,
            TestProperties.MAX_ZIP_BYTES,
            TestProperties.EMBEDDING_PROVIDER,
            TestProperties.NO_EMBEDDING_API_KEY,
            TestProperties.GENERATION_PROVIDER,
            TestProperties.NO_GENERATION_API_KEY,
            TestProperties.NO_GENERATION_PRICE_INPUT,
            TestProperties.NO_GENERATION_PRICE_OUTPUT
        })
@ContextConfiguration(initializers = TestFileSystemInitializer.class)
@Import(TestcontainersConfiguration.class)
class UploadLimitIT {

    @LocalServerPort private int port;

    @Test
    void multipartLimitAnswersUploadTooLarge() throws Exception {
        String boundary = "boundary-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(
                ("--"
                                + boundary
                                + "\r\n"
                                + "Content-Disposition: form-data; name=\"file\";"
                                + " filename=\"big.zip\"\r\n"
                                + "Content-Type: application/zip\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        body.writeBytes(new byte[80 * 1024]);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:%d/api/v1/projects/%s/imports/zip"
                                                                .formatted(
                                                                        port, UUID.randomUUID())))
                                        .header(
                                                "Content-Type",
                                                "multipart/form-data; boundary=" + boundary)
                                        .POST(
                                                HttpRequest.BodyPublishers.ofByteArray(
                                                        body.toByteArray()))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(
                        type -> assertThat(type).startsWith("application/problem+json"));
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("UPLOAD_TOO_LARGE");
    }
}
