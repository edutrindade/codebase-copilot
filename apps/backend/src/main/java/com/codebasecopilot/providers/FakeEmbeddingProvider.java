package com.codebasecopilot.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Deterministic vectors for tests and demos (D-019): each term is hashed into one dimension and
// the counts are normalized, so texts sharing terms end up close. Tokens are the term count.
final class FakeEmbeddingProvider implements EmbeddingProvider {

    static final String NAME = "fake";

    private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}_]+");

    private final ModelInfo model;

    FakeEmbeddingProvider(String model, int dimensions) {
        this.model = new ModelInfo(NAME, model, dimensions);
    }

    @Override
    public EmbeddingResult embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        long tokens = 0;
        for (String text : texts) {
            double[] counts = new double[model.dimensions()];
            Matcher terms = TERM.matcher(text.toLowerCase(Locale.ROOT));
            int found = 0;
            while (terms.find()) {
                counts[dimension(terms.group())]++;
                found++;
            }
            if (found == 0) {
                counts[0] = 1;
            }
            vectors.add(normalize(counts));
            tokens += found;
        }
        return new EmbeddingResult(vectors, tokens);
    }

    @Override
    public ModelInfo model() {
        return model;
    }

    // String.hashCode is specified, so vectors are stable across JVMs; the mix spreads it.
    private int dimension(String term) {
        int h = term.hashCode();
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return Math.floorMod(h, model.dimensions());
    }

    private static float[] normalize(double[] counts) {
        double sum = 0;
        for (double count : counts) {
            sum += count * count;
        }
        double norm = Math.sqrt(sum);
        float[] vector = new float[counts.length];
        for (int i = 0; i < counts.length; i++) {
            vector[i] = (float) (counts[i] / norm);
        }
        return vector;
    }
}
