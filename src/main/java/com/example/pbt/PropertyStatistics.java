package com.example.pbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable snapshot of one property run. */
public final class PropertyStatistics {

    private final long seed;
    private final int tries;
    private final int failures;
    private final long shrinkSteps;
    private final Map<String, Long> classification;

    PropertyStatistics(long seed, int tries, int failures, long shrinkSteps,
                       Map<String, Long> classification) {
        this.seed = seed;
        this.tries = tries;
        this.failures = failures;
        this.shrinkSteps = shrinkSteps;
        this.classification = Collections.unmodifiableMap(new LinkedHashMap<>(classification));
    }

    /** Seed used for this run; re-running with it reproduces the run exactly. */
    public long seed() {
        return seed;
    }

    /** Number of generated inputs the property was evaluated against. */
    public int tries() {
        return tries;
    }

    /** Number of tries that falsified the property (before shrinking). */
    public int failures() {
        return failures;
    }

    /** Total number of successful shrink steps across all failures. */
    public long shrinkSteps() {
        return shrinkSteps;
    }

    /** Category label -> number of tries classified into it. */
    public Map<String, Long> classification() {
        return classification;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PropertyStatistics{")
                .append("seed=").append(seed)
                .append(", tries=").append(tries)
                .append(", failures=").append(failures)
                .append(", shrinkSteps=").append(shrinkSteps);
        if (!classification.isEmpty()) {
            sb.append(", classification=").append(classification);
        }
        return sb.append('}').toString();
    }
}
