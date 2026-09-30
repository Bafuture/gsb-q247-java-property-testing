package com.example.pbt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of a property run.
 *
 * <p>All falsifying inputs are kept (failure collection), shrunk to local
 * minima and de-duplicated by {@code equals}. The original (unshrunk) sample
 * that produced each counterexample is retained for diagnosis.
 */
public final class PropertyResult<T> {

    /** A single collected falsification. */
    public static final class Counterexample<T> {
        private final T original;
        private final T shrunk;
        private final long shrinkSteps;

        Counterexample(T original, T shrunk, long shrinkSteps) {
            this.original = original;
            this.shrunk = shrunk;
            this.shrinkSteps = shrinkSteps;
        }

        public T original() {
            return original;
        }

        public T shrunk() {
            return shrunk;
        }

        public long shrinkSteps() {
            return shrinkSteps;
        }

        @Override
        public String toString() {
            return "Counterexample{original=" + original
                    + ", shrunk=" + shrunk
                    + ", shrinkSteps=" + shrinkSteps + '}';
        }
    }

    private final PropertyStatistics statistics;
    private final List<Counterexample<T>> counterexamples;

    PropertyResult(PropertyStatistics statistics, List<Counterexample<T>> counterexamples) {
        this.statistics = statistics;
        this.counterexamples = Collections.unmodifiableList(new ArrayList<>(counterexamples));
    }

    public boolean isSuccessful() {
        return statistics.failures() == 0;
    }

    public PropertyStatistics statistics() {
        return statistics;
    }

    /** Distinct, already-shrunk counterexamples found during the whole run. */
    public List<Counterexample<T>> counterexamples() {
        return counterexamples;
    }

    /** Distinct shrunk falsifying values, in first-seen order. */
    public List<T> falsifiedValues() {
        List<T> values = new ArrayList<>();
        for (Counterexample<T> counterexample : counterexamples) {
            values.add(counterexample.shrunk());
        }
        return values;
    }

    public PropertyResult<T> assertSuccessful() {
        if (!isSuccessful()) {
            throw new AssertionError(buildFailureReport());
        }
        return this;
    }

    private String buildFailureReport() {
        StringBuilder sb = new StringBuilder("Property falsified ")
                .append(statistics.failures())
                .append(" time(s); ")
                .append(counterexamples.size())
                .append(" distinct counterexample(s) after shrinking:\n");
        for (Counterexample<T> counterexample : counterexamples) {
            sb.append("  ").append(counterexample).append('\n');
        }
        sb.append(statistics);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "PropertyResult{" + statistics
                + ", counterexamples=" + counterexamples + '}';
    }
}
