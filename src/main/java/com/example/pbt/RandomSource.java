package com.example.pbt;

import java.util.List;

/**
 * Deterministic pseudo-random source based on the SplitMix64 algorithm.
 *
 * <p>Unlike {@link java.util.Random} (whose algorithm is a JDK implementation
 * detail), this generator is fully owned by the framework. Two runs constructed
 * with the same seed always observe the identical sequence of outputs, on every
 * JDK.
 */
public final class RandomSource {

    private long state;

    public RandomSource(long seed) {
        this.state = seed;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform non-negative integer in {@code [0, bound)}. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        long m = (nextLong() & Long.MAX_VALUE) % bound;
        return (int) m;
    }

    /** Uniform integer in the closed interval {@code [min, max]}. */
    public int nextInt(int min, int max) {
        long range = (long) max - (long) min + 1L;
        return min + (int) ((nextLong() & Long.MAX_VALUE) % range);
    }

    /** True with the given probability in {@code [0, 1]}. */
    public boolean nextBoolean(double probability) {
        double p = (nextLong() >>> 11) * 0x1.0p-53;
        return p < probability;
    }

    public <T> T choose(T[] options) {
        return options[nextInt(options.length)];
    }

    public <T> T choose(List<T> options) {
        return options.get(nextInt(options.size()));
    }
}
