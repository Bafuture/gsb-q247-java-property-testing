package com.example.gsb.pt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outcome of one property run: statistics and all distinct shrunk counterexamples.
 */
public final class PropertyResult<T> {

    private final long seed;
    private final int tries;
    private final int failures;
    private final int shrinkSteps;
    private final Map<String, Long> categories;
    private final List<T> counterexamples;
    private final Map<T, String> failureMessages;

    PropertyResult(long seed, int tries, int failures, int shrinkSteps,
                   Map<String, Long> categories, List<T> counterexamples,
                   Map<T, String> failureMessages) {
        this.seed = seed;
        this.tries = tries;
        this.failures = failures;
        this.shrinkSteps = shrinkSteps;
        this.categories = Map.copyOf(categories);
        this.counterexamples = List.copyOf(counterexamples);
        this.failureMessages = Map.copyOf(failureMessages);
    }

    public long seed() {
        return seed;
    }

    /** Number of inputs actually checked. */
    public int tries() {
        return tries;
    }

    /** Number of generated inputs for which the property threw. */
    public int failures() {
        return failures;
    }

    /** Total number of successful shrink moves across all failing inputs. */
    public int shrinkSteps() {
        return shrinkSteps;
    }

    /** Category name -> number of generated inputs in that category. */
    public Map<String, Long> categories() {
        return categories;
    }

    /** All distinct shrunk counterexamples, in first-discovery order. */
    public List<T> counterexamples() {
        return counterexamples;
    }

    /** Failure message recorded for each distinct counterexample. */
    public Map<T, String> failureMessages() {
        return failureMessages;
    }

    public boolean passed() {
        return failures == 0;
    }

    public Optional<T> firstCounterexample() {
        return counterexamples.isEmpty() ? Optional.empty() : Optional.of(counterexamples.get(0));
    }

    /** Throws an {@link AssertionError} carrying the full report when the property failed. */
    public void assertPassed() {
        if (!passed()) {
            throw new AssertionError(report());
        }
    }

    public String report() {
        StringBuilder builder = new StringBuilder();
        if (passed()) {
            builder.append("Property PASSED: ").append(tries)
                    .append(" tries, 0 failures (seed=").append(seed).append(')');
        } else {
            builder.append("Property FAILED: ").append(failures).append(" of ").append(tries)
                    .append(" tries failed (seed=").append(seed)
                    .append(", shrink steps=").append(shrinkSteps).append(")\n")
                    .append("counterexamples (").append(counterexamples.size())
                    .append(" distinct): ").append(counterexamples);
        }
        builder.append("\ncategories: ").append(categories);
        return builder.toString();
    }

    @Override
    public String toString() {
        return report();
    }
}
