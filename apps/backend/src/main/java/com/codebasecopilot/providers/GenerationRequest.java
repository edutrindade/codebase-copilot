package com.codebasecopilot.providers;

import java.util.Map;

// instructions go as the system message and input as the user message. The output must be a
// JSON document matching schema, a strict JSON Schema (all properties required, no additional
// properties).
public record GenerationRequest(String instructions, String input, OutputSchema schema) {

    public record OutputSchema(String name, Map<String, Object> jsonSchema) {

        public OutputSchema {
            jsonSchema = Map.copyOf(jsonSchema);
        }
    }
}
