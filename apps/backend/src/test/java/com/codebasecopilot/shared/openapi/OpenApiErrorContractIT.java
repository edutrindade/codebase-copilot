package com.codebasecopilot.shared.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.codebasecopilot.IntegrationTest;
import com.codebasecopilot.shared.error.ErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class OpenApiErrorContractIT {

    private static final String WORKSPACE = "$.paths['/api/v1/workspaces/{workspaceId}']";
    private static final String WORKSPACES = "$.paths['/api/v1/workspaces']";
    private static final String PROBLEM_REF = "#/components/schemas/Problem";

    @Autowired private MockMvcTester mvc;

    @Test
    void declaredDomainErrorsArePublishedAsProblems() {
        MvcTestResult docs = docs();

        assertThat(docs)
                .bodyJson()
                .extractingPath(WORKSPACE + ".get.responses['404'].description")
                .asString()
                .contains("WORKSPACE_NOT_FOUND");
        assertThat(docs)
                .bodyJson()
                .extractingPath(
                        WORKSPACE
                                + ".get.responses['404'].content['application/problem+json']"
                                + ".schema.$ref")
                .isEqualTo(PROBLEM_REF);
    }

    @Test
    void genericErrorsAreDerivedFromRequestShape() {
        MvcTestResult docs = docs();

        assertThat(docs)
                .bodyJson()
                .extractingPath(WORKSPACES + ".post.responses")
                .asMap()
                .containsKeys("201", "400", "415", "500");
        assertThat(docs)
                .bodyJson()
                .extractingPath(WORKSPACES + ".post.responses['400'].description")
                .asString()
                .contains("MALFORMED_REQUEST", "VALIDATION_FAILED");
        assertThat(docs)
                .bodyJson()
                .extractingPath(WORKSPACES + ".get.responses")
                .asMap()
                .containsOnlyKeys("200", "400", "500");
    }

    @Test
    void problemSchemaListsEveryErrorCode() {
        assertThat(docs())
                .bodyJson()
                .extractingPath("$.components.schemas.Problem.properties.code.enum")
                .isEqualTo(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
    }

    @Test
    void responseFieldsAreRequired() {
        assertThat(docs())
                .bodyJson()
                .extractingPath("$.components.schemas.WorkspaceResponse.required")
                .asInstanceOf(InstanceOfAssertFactories.list(String.class))
                .containsExactlyInAnyOrder("id", "name", "createdAt");
    }

    @Test
    void polymorphicSourceHasDiscriminatorMapping() {
        assertThat(docs())
                .bodyJson()
                .extractingPath("$.components.schemas.SourceResponse.discriminator.mapping")
                .isEqualTo(
                        Map.of(
                                "LOCAL_FOLDER", "#/components/schemas/LocalFolderSourceResponse",
                                "ZIP", "#/components/schemas/ZipSourceResponse"));
    }

    @Test
    void subtypeFieldsAreRequiredAndNullableRefsUseOneOf() {
        MvcTestResult docs = docs();

        assertThat(docs)
                .bodyJson()
                .extractingPath("$.components.schemas.LocalFolderSourceResponse.allOf[1].required")
                .asInstanceOf(InstanceOfAssertFactories.list(String.class))
                .containsExactlyInAnyOrder("rootKey", "relativePath");
        assertThat(docs)
                .bodyJson()
                .extractingPath(
                        "$.components.schemas.ZipSourceResponse.allOf[1].properties.currentImport")
                .isEqualTo(
                        Map.of(
                                "oneOf",
                                List.of(
                                        Map.of(
                                                "$ref",
                                                "#/components/schemas/CurrentImportResponse"),
                                        Map.of("type", "null"))));
    }

    private MvcTestResult docs() {
        return mvc.get().uri("/v3/api-docs").exchange();
    }
}
