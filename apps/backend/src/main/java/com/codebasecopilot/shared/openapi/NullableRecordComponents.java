package com.codebasecopilot.shared.openapi;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

// jspecify's @Nullable is a type-use annotation, which swagger does not read: without this,
// a nullable record component is published as non-null and the generated client types lie.
@SuppressWarnings({"rawtypes", "unchecked"})
class NullableRecordComponents implements ModelConverter {

    private static final String REF_PREFIX = "#/components/schemas/";

    @Override
    public @Nullable Schema resolve(
            AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema schema = chain.next().resolve(type, context, chain);
        Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
        if (schema == null || !raw.isRecord()) {
            return schema;
        }
        Schema model =
                schema.get$ref() == null
                        ? schema
                        : context.getDefinedModels()
                                .get(schema.get$ref().substring(REF_PREFIX.length()));
        if (model == null || model.getProperties() == null) {
            return schema;
        }
        Map<String, Schema> properties = model.getProperties();
        for (RecordComponent component : raw.getRecordComponents()) {
            Schema property = properties.get(component.getName());
            if (property != null
                    && component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                properties.put(component.getName(), nullable(property));
            }
        }
        return schema;
    }

    // A nullable $ref is "the referenced object or null"; see OpenApiConfiguration.nullableRef.
    private static Schema nullable(Schema property) {
        if (property.get$ref() != null) {
            return new Schema<>()
                    .description(property.getDescription())
                    .oneOf(
                            List.of(
                                    new Schema<>().$ref(property.get$ref()),
                                    new Schema<>().type("null").types(Set.of("null"))));
        }
        if (property.getOneOf() != null) {
            return property;
        }
        Set<String> types = new LinkedHashSet<>();
        if (property.getTypes() != null) {
            types.addAll(property.getTypes());
        } else if (property.getType() != null) {
            types.add(property.getType());
        }
        if (types.isEmpty()) {
            return property;
        }
        types.add("null");
        property.setTypes(types);
        return property;
    }
}
