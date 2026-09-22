package com.kbase.ai.provider.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable provider-neutral embedding result. */
public final class AiEmbeddingResult {

    private final List<Double> vector;
    private final String modelId;

    public AiEmbeddingResult(List<Double> vector) {
        this(vector, "");
    }

    public AiEmbeddingResult(List<Double> vector, String modelId) {
        Objects.requireNonNull(vector, "vector must not be null");
        if (vector.isEmpty()) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        List<Double> copy = new ArrayList<>(vector.size());
        for (Double value : vector) {
            Objects.requireNonNull(value, "vector values must not be null");
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("vector values must be finite");
            }
            copy.add(value);
        }
        this.vector = List.copyOf(copy);
        this.modelId = modelId == null ? "" : modelId;
    }

    public List<Double> vector() {
        return vector;
    }

    public String modelId() {
        return modelId;
    }

    public int dimensions() {
        return vector.size();
    }

    public double[] toArray() {
        double[] values = new double[vector.size()];
        for (int index = 0; index < vector.size(); index++) {
            values[index] = vector.get(index);
        }
        return values;
    }
}
