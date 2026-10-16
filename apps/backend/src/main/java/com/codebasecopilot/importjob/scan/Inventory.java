package com.codebasecopilot.importjob.scan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record Inventory(
        UUID projectId,
        Instant scannedAt,
        int settingsVersion,
        ScanResult scan,
        List<Warning> warnings) {

    record Warning(InventoryWarning code, List<UUID> relatedProjectIds) {}
}
