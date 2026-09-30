package com.example.gsb.pt;

import java.util.List;
import java.util.Random;

/**
 * Deterministic randomness source.
 *
 * <p>Every value a generator draws flows through this class, so a {@link RandomSource}
 * constructed with the same {@code seed} always produces the exact same value sequence.
 */
public final class RandomSource {

    private final Random random;

    public RandomSource(long seed) {
        this.random = new Random(seed);
    }

    /** Uniformly distributed integer in [{@code minInclusive}, {@code maxInclusive}]. */
    public int nextInt(int minInclusive, int maxInclusive) {
        if (minInclusive > maxInclusive) {
            throw new IllegalArgumentException("minInclusive must be <= maxInclusive");
        }
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    /** Uniformly distributed long in [{@code minInclusive}, {@code maxExclusive}). */
    public long nextLong(long minInclusive, long maxExclusive) {
        if (minInclusive >= maxExclusive) {
            throw new IllegalArgumentException("minInclusive must be < maxExclusive");
        }
        return minInclusive + random.nextLong(maxExclusive - minInclusive);
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    /** An independent seed, e.g. for generators derived via {@code flatMap}. */
    public long nextSeed() {
        return random.nextLong();
    }

    public <T> T choose(List<T> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("cannot choose from an empty list");
        }
        return options.get(random.nextInt(options.size()));
    }
}
