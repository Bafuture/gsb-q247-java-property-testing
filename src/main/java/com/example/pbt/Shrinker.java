package com.example.pbt;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Greedy descent over the shrink tree.
 *
 * <p>Algorithm: starting from the failing value, repeatedly take the
 * <em>first</em> child (candidates are ordered smallest-first by the
 * generators) that still falsifies the property, until no child does.
 *
 * <p>Guarantees:
 * <ul>
 *   <li><b>Termination</b> — every generator produces children that are
 *       strictly smaller in a well-founded ordering (closer to zero, shorter,
 *       fewer elements), so the descent cannot loop. A {@code maxSteps} cap
 *       additionally bounds the effort.</li>
 *   <li><b>Soundness</b> — the returned value still falsifies the property
 *       (it is re-checked on every step).</li>
 *   <li><b>1-minimality</b> — no direct shrink candidate of the result
 *       falsifies the property. For monotone predicates over a single integer
 *       (e.g. {@code i -> i < k}) this coincides with the global minimum,
 *       because the candidate set always contains the immediate neighbour
 *       towards zero.</li>
 * </ul>
 */
final class Shrinker {

    record ShrinkResult<T>(T value, long steps) {
    }

    private Shrinker() {
    }

    static <T> ShrinkResult<T> shrink(Shrinkable<T> initial, Predicate<T> falsifies, long maxSteps) {
        Shrinkable<T> current = initial;
        long steps = 0;
        while (steps < maxSteps) {
            Optional<Shrinkable<T>> next = current.shrink()
                    .filter(candidate -> falsifies.test(candidate.value()))
                    .findFirst();
            if (next.isEmpty()) {
                break;
            }
            current = next.get();
            steps++;
        }
        return new ShrinkResult<>(current.value(), steps);
    }
}
