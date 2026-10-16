package com.codebasecopilot.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.shared.UuidV7Generator;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ErrorHandlingTest.ErrorTestController.class)
@Import({ErrorHandlingTest.ErrorTestController.class, UuidV7Generator.class})
@ExtendWith(OutputCaptureExtension.class)
class ErrorHandlingTest {

    private static final String SECRET = "SECRET-MARKER-7f3a";

    @Autowired private MockMvcTester mvc;

    @Test
    void validationErrorListsFieldsWithoutRejectedValues() {
        MvcTestResult result =
                mvc.post()
                        .uri("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name": " ", "count": -5, "label": "%s"}
                                """
                                        .formatted(SECRET))
                        .exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Requisição inválida");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/test/errors/body");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(
                        List.of(
                                Map.of("field", "count", "message", "Deve ser maior ou igual a 0."),
                                Map.of(
                                        "field",
                                        "label",
                                        "message",
                                        "Deve ter no máximo 5 caracteres."),
                                Map.of("field", "name", "message", "O campo é obrigatório.")));
        assertThat(body(result)).doesNotContain(SECRET).doesNotContain("-5");
    }

    @Test
    void unreadableJsonIsMalformedRequest() {
        MvcTestResult result =
                mvc.post()
                        .uri("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": ")
                        .exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
    }

    @Test
    void wrongJsonTypeIsMalformedRequestWithoutEchoingValue() {
        MvcTestResult result =
                mvc.post()
                        .uri("/test/errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"ok\", \"count\": \"%s\"}".formatted(SECRET))
                        .exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
        assertThat(body(result)).doesNotContain(SECRET);
    }

    @Test
    void malformedPathIdIsValidationFailureOnThatParameter() {
        MvcTestResult result = mvc.get().uri("/test/errors/items/{id}", SECRET).exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(
                        List.of(Map.of("field", "id", "message", "O valor informado é inválido.")));
        // "instance" echoes the request path by design (RFC 9457); nothing else may carry it.
        assertThat(body(result).replace("\"instance\":\"/test/errors/items/" + SECRET + "\"", ""))
                .doesNotContain(SECRET);
    }

    @Test
    void missingRequiredParameterIsValidationFailure() {
        MvcTestResult result = mvc.get().uri("/test/errors/search").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(List.of(Map.of("field", "q", "message", "O campo é obrigatório.")));
    }

    @Test
    void outOfRangePaginationIsRejected() {
        MvcTestResult result = mvc.get().uri("/test/errors/page?page=-1&size=101").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(
                        List.of(
                                Map.of("field", "page", "message", "Deve ser maior ou igual a 0."),
                                Map.of(
                                        "field",
                                        "size",
                                        "message",
                                        "Deve ser menor ou igual a 100.")));
    }

    @Test
    void nonNumericPaginationIsRejected() {
        MvcTestResult result = mvc.get().uri("/test/errors/page?size=abc").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(
                        List.of(
                                Map.of(
                                        "field",
                                        "size",
                                        "message",
                                        "O valor informado é inválido.")));
    }

    @Test
    void paginationDefaultsApply() {
        assertThat(mvc.get().uri("/test/errors/page"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"page\": 0, \"size\": 20}");
    }

    @Test
    void apiExceptionResolvesViolationMessages() {
        MvcTestResult result = mvc.get().uri("/test/errors/api").exchange();

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(
                        List.of(
                                Map.of(
                                        "field",
                                        "relativePath",
                                        "message",
                                        "O valor informado é inválido.")));
    }

    @Test
    void unknownRouteIsResourceNotFound() {
        assertProblem(
                mvc.get().uri("/test/errors/missing").exchange(),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() {
        MvcTestResult result = mvc.post().uri("/test/errors/boom").exchange();

        assertProblem(result, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
        assertThat(result).hasHeader(HttpHeaders.ALLOW, "GET");
    }

    @Test
    void unsupportedContentTypeIsRejected() {
        MvcTestResult result =
                mvc.post()
                        .uri("/test/errors/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name")
                        .exchange();

        assertProblem(result, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unexpectedErrorHidesDetailsAndLogsRequestId(CapturedOutput output) {
        MvcTestResult result = mvc.get().uri("/test/errors/boom").exchange();

        assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(result)
                .bodyJson()
                .isStrictlyEqualTo(
                        """
                        {
                          "type": "about:blank",
                          "title": "Erro interno",
                          "status": 500,
                          "detail": "Ocorreu um erro inesperado. Informe o requestId ao reportar o problema.",
                          "instance": "/test/errors/boom",
                          "code": "INTERNAL_ERROR",
                          "requestId": "%s"
                        }
                        """
                                .formatted(requestId(result)));
        assertThat(body(result)).doesNotContain(SECRET).doesNotContain("IllegalStateException");
        assertThat(output).contains(requestId(result)).contains(SECRET);
    }

    @Test
    void successfulResponseCarriesServerGeneratedRequestId() {
        MvcTestResult result =
                mvc.get()
                        .uri("/test/errors/ok")
                        .header(RequestIdFilter.HEADER, "client-supplied")
                        .exchange();

        assertThat(result).hasStatusOk();
        String requestId = requestId(result);
        assertThat(requestId).isNotEqualTo("client-supplied");
        assertThat(UUID.fromString(requestId).version()).isEqualTo(7);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result)
                .hasStatus(status)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo(code);
        assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo(requestId(result));
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
    }

    private static String requestId(MvcTestResult result) {
        String requestId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(requestId).isNotBlank();
        return requestId;
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    record TestBody(
            @NotBlank(message = "{validation.required}") String name,
            @Min(value = 0, message = "{validation.min}") Integer count,
            @Size(max = 5, message = "{validation.size.max}") String label) {}

    @RestController
    @RequestMapping("/test/errors")
    static class ErrorTestController {

        @PostMapping("/body")
        TestBody body(@Valid @RequestBody TestBody body) {
            return body;
        }

        @GetMapping("/items/{id}")
        UUID item(@PathVariable UUID id) {
            return id;
        }

        @GetMapping("/search")
        String search(@RequestParam String q) {
            return q;
        }

        @GetMapping("/page")
        PageRequestParams page(@Valid @ParameterObject PageRequestParams params) {
            return params;
        }

        @GetMapping("/api")
        void api() {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("relativePath", "validation.invalid-value")));
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException(SECRET);
        }

        @GetMapping("/ok")
        String ok() {
            return "ok";
        }
    }
}
