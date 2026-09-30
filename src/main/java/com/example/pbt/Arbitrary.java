package com.example.pbt;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A strategy that turns randomness into a {@link Shrinkable} value.
 *
 * <p>Instances are immutable and composable through {@link #map(Function)},
 * {@link #filter(Predicate)} and {@link #flatMap(Function)}.
 */
@FunctionalInterface
public interface Arbitrary<T> {

    Shrinkable<T> generate(RandomSource random);

    default <U> Arbitrary<U> map(Function<T, U> mapper) {
        Arbitrary<T> parent = this;
        return random -> parent.generate(random).map(mapper);
    }

    /**
     * Keeps only values matching the predicate. Filtering re-generates rather
     * than shrinking; after {@link #FILTER_MAX_TRIES} discards generation
     * fails fast with {@link TooManyFilteredValuesException}.
     */
    default Arbitrary<T> filter(Predicate<T> predicate) {
        Arbitrary<T> parent = this;
        return random -> {
            for (int attempts = 0; attempts < FILTER_MAX_TRIES; attempts++) {
                Shrinkable<T> candidate = parent.generate(random);
                if (predicate.test(candidate.value())) {
                    return candidate;
                }
            }
            throw new TooManyFilteredValuesException(FILTER_MAX_TRIES);
        };
    }

    default <U> Arbitrary<U> flatMap(Function<T, Arbitrary<U>> mapper) {
        return random -> mapper.apply(this.generate(random).value()).generate(random);
    }

    int FILTER_MAX_TRIES = 10_000;

    final class TooManyFilteredValuesException extends RuntimeException {
        TooManyFilteredValuesException(int attempts) {
            super("filter() discarded more than " + attempts
                    + " generated values in a row; the predicate is likely too restrictive");
        }
    }
}
