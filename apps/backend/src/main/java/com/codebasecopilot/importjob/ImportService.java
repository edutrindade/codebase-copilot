package com.codebasecopilot.importjob;

import com.codebasecopilot.project.ProjectService;
import com.codebasecopilot.project.SourceType;
import com.codebasecopilot.shared.IdGenerator;
import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.error.FieldViolation;
import com.codebasecopilot.shared.pagination.PageRequestParams;
import com.codebasecopilot.shared.pagination.PageResponse;
import com.codebasecopilot.shared.persistence.ConstraintViolations;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
class ImportService {

    private final ImportJobRepository repository;
    private final ImportFiles files;
    private final ImportWorker worker;
    private final ProjectService projects;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final long maxZipBytes;

    ImportService(
            ImportJobRepository repository,
            ImportFiles files,
            ImportWorker worker,
            ProjectService projects,
            IdGenerator idGenerator,
            Clock clock,
            ImportProperties properties) {
        this.repository = repository;
        this.files = files;
        this.worker = worker;
        this.projects = projects;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.maxZipBytes = properties.maxZipBytes().toBytes();
    }

    // The multipart limit already answers 413 before this method; the size check is defense in
    // depth. The unique index makes the busy check safe under concurrent uploads (D-028).
    @Transactional
    ImportJob startZipImport(UUID projectId, @Nullable MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    List.of(new FieldViolation("file", "validation.file.required")));
        }
        if (file.getSize() > maxZipBytes) {
            throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE);
        }
        if (projects.get(projectId).project().sourceType() != SourceType.ZIP) {
            throw new ApiException(ErrorCode.PROJECT_SOURCE_MISMATCH);
        }
        UUID id = idGenerator.newId();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        try {
            repository.insertPending(id, projectId, file.getSize(), now);
        } catch (DataIntegrityViolationException ex) {
            if (ConstraintViolations.violates(ex, ImportJobRepository.ACTIVE_UNIQUE)) {
                throw new ApiException(ErrorCode.PROJECT_BUSY);
            }
            if (ConstraintViolations.violates(ex, ImportJobRepository.PROJECT_FK)) {
                throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
            }
            throw ex;
        }
        try {
            files.storeUpload(id, file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        worker.submit(id);
                    }
                });
        return new ImportJob(
                id, projectId, ImportStatus.PENDING, file.getSize(), null, null, null, now, null);
    }

    @Transactional(readOnly = true)
    ImportJob get(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.IMPORT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    PageResponse<ImportJob> list(UUID projectId, PageRequestParams params) {
        projects.get(projectId);
        return PageResponse.of(
                repository.findPageByProject(projectId, params),
                params,
                repository.countByProject(projectId));
    }
}
