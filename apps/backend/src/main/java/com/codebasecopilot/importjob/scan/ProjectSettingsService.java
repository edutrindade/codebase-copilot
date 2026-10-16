package com.codebasecopilot.importjob.scan;

import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import com.codebasecopilot.shared.persistence.ConstraintViolations;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Custom exclusions and limits per project (D-036); SENSITIVE and VCS rules always apply.
@Service
class ProjectSettingsService {

    static final int MAX_PATTERNS = 100;
    static final long MIN_LIMIT_BYTES = 1024;

    private final ProjectSettingsRepository repository;
    private final ProjectService projects;
    private final InventoryProperties properties;
    private final Clock clock;

    ProjectSettingsService(
            ProjectSettingsRepository repository,
            ProjectService projects,
            InventoryProperties properties,
            Clock clock) {
        this.repository = repository;
        this.projects = projects;
        this.properties = properties;
        this.clock = clock;
    }

    record Update(
            @Nullable List<@Nullable String> excludePatterns,
            @Nullable List<@Nullable String> disabledDefaultRules,
            @Nullable Long maxFileBytes,
            @Nullable Long maxProjectBytes) {}

    // The rules, patterns and limits a scan of the project uses.
    record Effective(
            ProjectSettings settings,
            List<ExclusionRule> rules,
            List<PathPattern> patterns,
            long maxFileBytes,
            long maxProjectBytes) {}

    @Transactional(readOnly = true)
    ProjectSettings get(UUID projectId) {
        projects.get(projectId);
        return find(projectId);
    }

    // Same precedence as project creation: the whole body is validated before the lookup.
    @Transactional
    ProjectSettings update(UUID projectId, Update update) {
        List<FieldViolation> violations = new ArrayList<>();
        List<String> patterns = patterns(update.excludePatterns(), violations);
        List<String> disabled = disabledRules(update.disabledDefaultRules(), violations);
        checkLimit(
                "maxFileBytes",
                update.maxFileBytes(),
                properties.maxFileBytesLimit().toBytes(),
                violations);
        checkLimit(
                "maxProjectBytes",
                update.maxProjectBytes(),
                properties.maxProjectBytesLimit().toBytes(),
                violations);
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, violations);
        }
        projects.get(projectId);
        try {
            return repository.upsert(
                    projectId,
                    patterns,
                    disabled,
                    update.maxFileBytes(),
                    update.maxProjectBytes(),
                    clock.instant().truncatedTo(ChronoUnit.MICROS));
        } catch (DataIntegrityViolationException ex) {
            if (ConstraintViolations.violates(ex, ProjectSettingsRepository.PROJECT_FK)) {
                throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    Effective effective(UUID projectId) {
        ProjectSettings settings = find(projectId);
        Set<String> disabled = Set.copyOf(settings.disabledDefaultRules());
        return new Effective(
                settings,
                DefaultExclusionRules.ALL.stream()
                        .filter(rule -> !rule.overridable() || !disabled.contains(rule.id()))
                        .toList(),
                settings.excludePatterns().stream().map(PathPattern::compile).toList(),
                Optional.ofNullable(settings.maxFileBytes())
                        .orElse(properties.maxFileBytes().toBytes()),
                Optional.ofNullable(settings.maxProjectBytes())
                        .orElse(properties.maxProjectBytes().toBytes()));
    }

    long defaultMaxFileBytes() {
        return properties.maxFileBytes().toBytes();
    }

    long defaultMaxProjectBytes() {
        return properties.maxProjectBytes().toBytes();
    }

    private ProjectSettings find(UUID projectId) {
        return repository.find(projectId).orElseGet(() -> ProjectSettings.defaults(projectId));
    }

    private static List<String> patterns(
            @Nullable List<@Nullable String> raw, List<FieldViolation> violations) {
        if (raw == null) {
            return List.of();
        }
        if (raw.size() > MAX_PATTERNS) {
            violations.add(
                    new FieldViolation("excludePatterns", "validation.settings.too-many-patterns"));
            return List.of();
        }
        Set<String> patterns = new LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String pattern = raw.get(i);
            String field = "excludePatterns[" + i + "]";
            if (pattern == null) {
                violations.add(new FieldViolation(field, "validation.required"));
                continue;
            }
            try {
                PathPattern.compile(pattern);
                patterns.add(pattern);
            } catch (IllegalArgumentException e) {
                violations.add(new FieldViolation(field, "validation.settings.invalid-pattern"));
            }
        }
        return List.copyOf(patterns);
    }

    private static List<String> disabledRules(
            @Nullable List<@Nullable String> raw, List<FieldViolation> violations) {
        if (raw == null) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String id = raw.get(i);
            String field = "disabledDefaultRules[" + i + "]";
            Optional<ExclusionRule> rule =
                    id == null ? Optional.empty() : DefaultExclusionRules.find(id);
            if (id == null) {
                violations.add(new FieldViolation(field, "validation.required"));
            } else if (rule.isEmpty()) {
                violations.add(new FieldViolation(field, "validation.settings.unknown-rule"));
            } else if (!rule.get().overridable()) {
                violations.add(
                        new FieldViolation(field, "validation.settings.rule-not-overridable"));
            } else {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private static void checkLimit(
            String field, @Nullable Long value, long max, List<FieldViolation> violations) {
        if (value != null && (value < MIN_LIMIT_BYTES || value > max)) {
            violations.add(new FieldViolation(field, "validation.settings.limit-out-of-range"));
        }
    }
}
