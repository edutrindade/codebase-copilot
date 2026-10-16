package com.codebasecopilot.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintViolationsTest {

    private static final DataIntegrityViolationException FK_VIOLATION =
            new DataIntegrityViolationException(
                    "insert failed",
                    new SQLException(
                            "ERROR: insert or update on table \"projects\" violates foreign key"
                                    + " constraint \"projects_workspace_id_fkey\""));

    @Test
    void matchesConstraintNamedInCause() {
        assertThat(ConstraintViolations.violates(FK_VIOLATION, "projects_workspace_id_fkey"))
                .isTrue();
    }

    @Test
    void requiresExactQuotedName() {
        assertThat(ConstraintViolations.violates(FK_VIOLATION, "projects_workspace_id")).isFalse();
        assertThat(ConstraintViolations.violates(FK_VIOLATION, "projects_name_length")).isFalse();
    }
}
