package com.codebasecopilot.indexing;

// pgvector text format ("[1.0,-0.5]"), bound as a text parameter and cast in SQL.
// Float.toString never uses the default locale, so the decimal separator is always a dot.
final class VectorLiteral {

    private VectorLiteral() {}

    static String of(float[] vector) {
        if (vector.length == 0) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        StringBuilder literal = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            float component = vector[i];
            if (!Float.isFinite(component)) {
                throw new IllegalArgumentException("vector component " + i + " is not finite");
            }
            if (i > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(component));
        }
        return literal.append(']').toString();
    }
}
