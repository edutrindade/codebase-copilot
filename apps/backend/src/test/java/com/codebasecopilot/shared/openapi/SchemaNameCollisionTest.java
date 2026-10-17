package com.codebasecopilot.shared.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

// springdoc names schemas by simple class name, so two different records with the same name
// silently publish only one of them in openapi.json.
class SchemaNameCollisionTest {

    @Test
    void recordsWithTheSameSimpleNameHaveTheSameShape() {
        Map<String, List<Class<?>>> bySimpleName =
                new ClassFileImporter()
                                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                                .importPackages("com.codebasecopilot")
                                .stream()
                                .filter(JavaClass::isRecord)
                                .filter(
                                        type ->
                                                type.getSimpleName().endsWith("Response")
                                                        || type.getSimpleName().endsWith("Request"))
                                .map(JavaClass::reflect)
                                .collect(Collectors.groupingBy(Class::getSimpleName));

        List<String> collisions =
                bySimpleName.entrySet().stream()
                        .filter(
                                entry ->
                                        entry.getValue().stream()
                                                        .map(SchemaNameCollisionTest::shape)
                                                        .distinct()
                                                        .count()
                                                > 1)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();

        assertThat(collisions).isEmpty();
    }

    private static List<String> shape(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(SchemaNameCollisionTest::describe)
                .toList();
    }

    private static String describe(RecordComponent component) {
        return component.getName()
                + ":"
                + component.getGenericType().getTypeName()
                + Arrays.toString(component.getAnnotations());
    }
}
