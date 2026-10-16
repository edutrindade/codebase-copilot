package com.codebasecopilot.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageRequestParamsTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void appliesDefaults() {
        PageRequestParams params = new PageRequestParams(null, null);

        assertThat(params.page()).isZero();
        assertThat(params.size()).isEqualTo(20);
    }

    @Test
    void computesOffsetWithoutOverflow() {
        assertThat(new PageRequestParams(3, 20).offset()).isEqualTo(60);
        assertThat(new PageRequestParams(Integer.MAX_VALUE, 100).offset())
                .isEqualTo(Integer.MAX_VALUE * 100L);
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "0, 100", "5, 20"})
    void acceptsValuesInRange(int page, int size) {
        assertThat(invalidFields(new PageRequestParams(page, size))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"-1, 20, page", "0, 0, size", "0, 101, size"})
    void rejectsValuesOutOfRange(int page, int size, String field) {
        assertThat(invalidFields(new PageRequestParams(page, size))).containsExactly(field);
    }

    private static Set<String> invalidFields(PageRequestParams params) {
        return VALIDATOR.validate(params).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
