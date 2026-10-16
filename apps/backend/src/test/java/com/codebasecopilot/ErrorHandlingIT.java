package com.codebasecopilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.shared.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingIT {

    @Autowired private MockMvcTester mvc;

    @Test
    void unknownApiRouteIsProblemWithRequestId() {
        MvcTestResult result = mvc.get().uri("/api/v1/does-not-exist").exchange();

        String requestId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(result)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                                {
                                  "code": "RESOURCE_NOT_FOUND",
                                  "title": "Recurso não encontrado",
                                  "requestId": "%s"
                                }
                                """
                                .formatted(requestId));
    }

    @Test
    void successfulResponsesCarryRequestId() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .containsHeader(RequestIdFilter.HEADER);
    }
}
