package com.example.gsb.pt;

import java.util.Random;
import java.util.function.Function;

/**
 * Run configuration: how many tries to execute, which seed to use, and an optional
 * classifier used for category statistics.
 *
 * @param tries      number of generated inputs to check (must be positive)
 * @param seed       random seed; equal seeds reproduce the exact same run
 * @param classifier maps an input to a category label ({@code null} => "uncategorized")
 */
public record CheckConfig<T>(int tries, long seed, Function<? super T, String> classifier) {

    public CheckConfig {
        if (tries <= 0) {
            throw new IllegalArgumentException("tries must be positive");
        }
    }

    /** 100 tries with a random seed (recorded in the result for later reproduction). */
    public static <T> CheckConfig<T> defaults() {
        return new CheckConfig<>(100, new Random().nextLong(), null);
    }

    public static <T> CheckConfig<T> of(int tries, long seed) {
        return new CheckConfig<>(tries, seed, null);
    }

    public CheckConfig<T> withClassifier(Function<? super T, String> classifier) {
        return new CheckConfig<>(tries, seed, classifier);
    }

    public CheckConfig<T> withTries(int tries) {
        return new CheckConfig<>(tries, seed, classifier);
    }

    public CheckConfig<T> withSeed(long seed) {
        return new CheckConfig<>(tries, seed, classifier);
    }
}
