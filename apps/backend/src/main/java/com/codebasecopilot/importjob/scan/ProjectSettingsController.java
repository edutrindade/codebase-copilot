package com.codebasecopilot.importjob.scan;

import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/settings")
@Tag(
        name = "Configurações do projeto",
        description = "Exclusões e limites usados na varredura e na indexação.")
class ProjectSettingsController {

    private final ProjectSettingsService service;

    ProjectSettingsController(ProjectSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @ApiErrors(ErrorCode.PROJECT_NOT_FOUND)
    @Operation(
            operationId = "getProjectSettings",
            summary = "Busca as exclusões e os limites do projeto")
    SettingsResponse get(@PathVariable UUID projectId) {
        return response(service.get(projectId));
    }

    // Full replacement (P6): absent lists are empty and null limits fall back to the defaults.
    @PutMapping
    @ApiErrors(ErrorCode.PROJECT_NOT_FOUND)
    @Operation(
            operationId = "updateProjectSettings",
            summary = "Substitui as exclusões e os limites do projeto")
    SettingsResponse update(
            @PathVariable UUID projectId, @RequestBody UpdateSettingsRequest request) {
        return response(
                service.update(
                        projectId,
                        new ProjectSettingsService.Update(
                                request.excludePatterns(),
                                request.disabledDefaultRules(),
                                request.maxFileBytes(),
                                request.maxProjectBytes())));
    }

    private SettingsResponse response(ProjectSettings settings) {
        Set<String> disabled = Set.copyOf(settings.disabledDefaultRules());
        return new SettingsResponse(
                settings.excludePatterns(),
                settings.disabledDefaultRules(),
                settings.maxFileBytes(),
                settings.maxProjectBytes(),
                settings.maxFileBytes() != null
                        ? settings.maxFileBytes()
                        : service.defaultMaxFileBytes(),
                settings.maxProjectBytes() != null
                        ? settings.maxProjectBytes()
                        : service.defaultMaxProjectBytes(),
                DefaultExclusionRules.ALL.stream()
                        .map(
                                rule ->
                                        new DefaultRuleResponse(
                                                rule.id(),
                                                rule.category(),
                                                rule.patterns(),
                                                rule.exceptions(),
                                                rule.overridable(),
                                                disabled.contains(rule.id())))
                        .toList(),
                settings.version(),
                settings.updatedAt());
    }

    record UpdateSettingsRequest(
            @Nullable List<@Nullable String> excludePatterns,
            @Nullable List<@Nullable String> disabledDefaultRules,
            @Schema(nullable = true) @Nullable Long maxFileBytes,
            @Schema(nullable = true) @Nullable Long maxProjectBytes) {}

    record SettingsResponse(
            List<String> excludePatterns,
            List<String> disabledDefaultRules,
            @Nullable Long maxFileBytes,
            @Nullable Long maxProjectBytes,
            long effectiveMaxFileBytes,
            long effectiveMaxProjectBytes,
            List<DefaultRuleResponse> defaultRules,
            int version,
            @Nullable Instant updatedAt) {}

    record DefaultRuleResponse(
            String id,
            ExclusionCategory category,
            List<String> patterns,
            List<String> exceptions,
            boolean overridable,
            boolean disabled) {}
}
