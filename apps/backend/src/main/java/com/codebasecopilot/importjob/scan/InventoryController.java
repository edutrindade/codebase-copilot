package com.codebasecopilot.importjob.scan;

import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Inventário",
        description = "Pré-análise dos arquivos do projeto, calculada a cada chamada.")
class InventoryController {

    private static final Locale LOCALE = Locale.of("pt", "BR");

    private final InventoryService service;
    private final MessageSource messages;

    InventoryController(InventoryService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping("/projects/{projectId}/inventory")
    @ApiErrors({
        ErrorCode.PROJECT_NOT_FOUND,
        ErrorCode.SOURCE_ROOT_NOT_CONFIGURED,
        ErrorCode.SOURCE_DIRECTORY_NOT_FOUND,
        ErrorCode.SOURCE_IS_SYMLINK,
        ErrorCode.SOURCE_NOT_READABLE,
        ErrorCode.SOURCE_NOT_IMPORTED
    })
    @Operation(
            operationId = "getInventory",
            summary = "Calcula o inventário (pré-análise) do projeto")
    InventoryResponse inventory(@PathVariable UUID projectId) {
        Inventory inventory = service.inventory(projectId);
        ScanResult scan = inventory.scan();
        ScanResult.Totals t = scan.totals();
        return new InventoryResponse(
                inventory.projectId(),
                inventory.scannedAt(),
                inventory.settingsVersion(),
                scan.truncated(),
                new InventoryTotalsResponse(
                        t.files(),
                        t.includedFiles(),
                        t.includedBytes(),
                        t.excludedFiles(),
                        t.unsupportedFiles(),
                        t.unreadableFiles(),
                        t.excludedDirectories()),
                scan.byLanguage().stream()
                        .map(l -> new LanguageCountResponse(l.language(), l.files(), l.bytes()))
                        .toList(),
                scan.byDirectory().stream()
                        .map(
                                d ->
                                        new DirectoryCountResponse(
                                                d.path(),
                                                d.includedFiles(),
                                                d.excludedFiles(),
                                                d.unsupportedFiles(),
                                                d.unreadableFiles()))
                        .toList(),
                scan.exclusions().stream()
                        .map(
                                e ->
                                        new ExclusionGroupResponse(
                                                e.reason(),
                                                e.ruleId(),
                                                e.pattern(),
                                                e.files(),
                                                e.directories(),
                                                e.examples()))
                        .toList(),
                inventory.warnings().stream()
                        .map(
                                w ->
                                        new InventoryWarningResponse(
                                                w.code().name(),
                                                messages.getMessage(
                                                        "inventory.warning." + w.code().name(),
                                                        null,
                                                        LOCALE),
                                                w.relatedProjectIds()))
                        .toList());
    }

    record InventoryResponse(
            UUID projectId,
            Instant scannedAt,
            int settingsVersion,
            boolean truncated,
            InventoryTotalsResponse totals,
            List<LanguageCountResponse> byLanguage,
            List<DirectoryCountResponse> byDirectory,
            List<ExclusionGroupResponse> exclusions,
            List<InventoryWarningResponse> warnings) {}

    record InventoryTotalsResponse(
            long files,
            long includedFiles,
            long includedBytes,
            long excludedFiles,
            long unsupportedFiles,
            long unreadableFiles,
            long excludedDirectories) {}

    record LanguageCountResponse(String language, long files, long bytes) {}

    record DirectoryCountResponse(
            String path,
            long includedFiles,
            long excludedFiles,
            long unsupportedFiles,
            long unreadableFiles) {}

    record ExclusionGroupResponse(
            ExclusionReason reason,
            @Nullable String ruleId,
            @Nullable String pattern,
            long files,
            long directories,
            List<String> examples) {}

    record InventoryWarningResponse(
            @Schema(
                            allowableValues = {
                                "CASE_VARIANT_PROJECT",
                                "SCAN_LIMIT_REACHED",
                                "PROJECT_SIZE_LIMIT_EXCEEDED",
                                "NO_INCLUDED_FILES"
                            })
                    String code,
            String message,
            List<UUID> relatedProjectIds) {}
}
