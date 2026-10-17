package com.codebasecopilot.shared.openapi;

import com.codebasecopilot.shared.error.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

// Publishes error responses and response field requirements in the contract (D-022, D-023).
// swagger-models exposes raw Schema types in its API.
@SuppressWarnings({"rawtypes", "unchecked"})
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    static final String PROBLEM = "Problem";
    static final String FIELD_ERROR = "FieldError";
    static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    @Bean
    OperationCustomizer errorResponses() {
        return (operation, handlerMethod) -> {
            errorCodes(operation, handlerMethod).stream()
                    .collect(
                            Collectors.groupingBy(
                                    code -> code.status().value(),
                                    TreeMap::new,
                                    Collectors.toCollection(TreeSet::new)))
                    .forEach((status, codes) -> addProblemResponse(operation, status, codes));
            return operation;
        };
    }

    @Bean
    NullableRecordComponents nullableRecordComponents() {
        return new NullableRecordComponents();
    }

    @Bean
    OpenApiCustomizer apiInfo() {
        return openApi ->
                openApi.info(
                        new Info()
                                .title("Codebase Copilot API")
                                .version("v1")
                                .description(
                                        "API local do Codebase Copilot: workspaces, projetos,"
                                                + " importação, inventário e indexação. Erros"
                                                + " seguem o ProblemDetail (RFC 9457) com `code`"
                                                + " estável e `requestId`."));
    }

    @Bean
    OpenApiCustomizer problemSchemas() {
        return openApi -> {
            Components components = openApi.getComponents();
            components.addSchemas(FIELD_ERROR, fieldErrorSchema());
            components.addSchemas(PROBLEM, problemSchema());
            // Jackson always writes every response property (null included), so all are required,
            // also in the schemas a response embeds without a *Response name (the inventory).
            Map<String, Schema> schemas = components.getSchemas();
            responseSchemas(schemas).forEach(name -> requireAllProperties(schemas.get(name)));
        };
    }

    private static Set<String> responseSchemas(Map<String, Schema> schemas) {
        Set<String> found = new TreeSet<>();
        List<String> pending =
                new ArrayList<>(
                        schemas.keySet().stream()
                                .filter(name -> name.endsWith("Response"))
                                .toList());
        while (!pending.isEmpty()) {
            String name = pending.removeLast();
            Schema schema = schemas.get(name);
            if (schema != null && found.add(name)) {
                Set<String> refs = new TreeSet<>();
                collectRefs(schema, refs);
                pending.addAll(refs);
            }
        }
        return found;
    }

    private static void collectRefs(@Nullable Schema schema, Set<String> refs) {
        if (schema == null) {
            return;
        }
        if (schema.get$ref() != null) {
            refs.add(schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1));
        }
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(p -> collectRefs((Schema) p, refs));
        }
        collectRefs(schema.getItems(), refs);
        if (schema.getAdditionalProperties() instanceof Schema additional) {
            collectRefs(additional, refs);
        }
        for (List<Schema> group :
                Arrays.asList(schema.getAllOf(), schema.getOneOf(), schema.getAnyOf())) {
            if (group != null) {
                group.forEach(s -> collectRefs(s, refs));
            }
        }
    }

    // Subtypes of a polymorphic response declare their own fields inside allOf.
    private static void requireAllProperties(Schema<?> schema) {
        if (schema.getProperties() != null) {
            schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            schema.getProperties().replaceAll((name, property) -> nullableRef(property));
        }
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(OpenApiConfiguration::requireAllProperties);
        }
    }

    // springdoc renders a nullable $ref as {"type": "null", "$ref": ...}, which no value
    // satisfies in OpenAPI 3.1; oneOf expresses "the referenced object or null".
    private static Schema nullableRef(Schema property) {
        boolean nullable =
                "null".equals(property.getType())
                        || (property.getTypes() != null && property.getTypes().contains("null"));
        if (property.get$ref() == null || !nullable) {
            return property;
        }
        return new Schema<>()
                .oneOf(List.of(new Schema<>().$ref(property.get$ref()), typed("null")));
    }

    private static Set<ErrorCode> errorCodes(Operation operation, HandlerMethod handlerMethod) {
        Set<ErrorCode> codes = new TreeSet<>();
        codes.add(ErrorCode.INTERNAL_ERROR);
        boolean hasBody = operation.getRequestBody() != null;
        boolean hasParameters =
                operation.getParameters() != null && !operation.getParameters().isEmpty();
        if (hasBody) {
            codes.add(ErrorCode.MALFORMED_REQUEST);
            codes.add(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        if (hasBody || hasParameters) {
            codes.add(ErrorCode.VALIDATION_FAILED);
        }
        ApiErrors declared = handlerMethod.getMethodAnnotation(ApiErrors.class);
        if (declared != null) {
            codes.addAll(Arrays.asList(declared.value()));
        }
        return codes;
    }

    private static void addProblemResponse(
            Operation operation, int status, SortedSet<ErrorCode> codes) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        String description =
                "Erro (ProblemDetail). Códigos: "
                        + codes.stream().map(Enum::name).collect(Collectors.joining(", "))
                        + ".";
        operation
                .getResponses()
                .addApiResponse(
                        String.valueOf(status),
                        new ApiResponse()
                                .description(description)
                                .content(
                                        new Content()
                                                .addMediaType(
                                                        PROBLEM_MEDIA_TYPE,
                                                        new MediaType().schema(ref(PROBLEM)))));
    }

    private static Schema<?> problemSchema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("type", typed("string"));
        properties.put("title", typed("string"));
        properties.put("status", typed("integer").format("int32"));
        properties.put("detail", typed("string"));
        properties.put("instance", typed("string"));
        properties.put("code", codeSchema());
        properties.put("requestId", typed("string").format("uuid"));
        properties.put("errors", typed("array").items(ref(FIELD_ERROR)));
        return typed("object")
                .properties(properties)
                .required(
                        List.of(
                                "type",
                                "title",
                                "status",
                                "detail",
                                "instance",
                                "code",
                                "requestId"));
    }

    private static Schema<?> fieldErrorSchema() {
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("field", typed("string"));
        properties.put("message", typed("string"));
        return typed("object").properties(properties).required(List.of("field", "message"));
    }

    private static Schema<?> codeSchema() {
        Schema<String> schema = (Schema<String>) typed("string");
        schema.setEnum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        return schema;
    }

    private static Schema<?> typed(String type) {
        return new Schema<>().type(type).types(Set.of(type));
    }

    private static Schema<?> ref(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }
}
