package com.example.pbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fluent entry point for executing properties.
 *
 * <pre>{@code
 * PropertyCheck.forAll(Arbitraries.integers().between(0, 100).build())
 *     .check(i -> i <= 50)
 *     .withTries(200)
 *     .withSeed(42L)
 *     .classify("small", i -> i < 10)
 *     .run()
 *     .assertSuccessful();
 * }</pre>
 */
public final class PropertyCheck<T> {

    public static final int DEFAULT_TRIES = 100;
    public static final long DEFAULT_MAX_SHRINKS = 1_000L;

    private final Arbitrary<T> arbitrary;
    private Predicate<T> property;
    private int tries = DEFAULT_TRIES;
    private long seed = new java.util.Random().nextLong();
    private boolean seedSet;
    private long maxShrinks = DEFAULT_MAX_SHRINKS;
    private final List<NamedClassifier<T>> classifiers = new ArrayList<>();

    private PropertyCheck(Arbitrary<T> arbitrary) {
        this.arbitrary = arbitrary;
    }

    public static <T> PropertyCheck<T> forAll(Arbitrary<T> arbitrary) {
        return new PropertyCheck<>(arbitrary);
    }

    /** Property expressed as a boolean predicate; {@code false} falsifies it. */
    public PropertyCheck<T> check(Predicate<T> predicate) {
        this.property = predicate;
        return this;
    }

    /**
     * Property expressed through assertions (e.g. AssertJ). Any thrown
     * {@link AssertionError} or {@link RuntimeException} falsifies the
     * property; other {@link Error}s propagate.
     */
    public PropertyCheck<T> checkAssert(Consumer<T> assertions) {
        this.property = value -> {
            try {
                assertions.accept(value);
                return true;
            } catch (AssertionError | RuntimeException e) {
                return false;
            }
        };
        return this;
    }

    public PropertyCheck<T> withTries(int tries) {
        if (tries <= 0) {
            throw new IllegalArgumentException("tries must be positive: " + tries);
        }
        this.tries = tries;
        return this;
    }

    /** Fixes the RNG seed; the full run becomes reproducible. */
    public PropertyCheck<T> withSeed(long seed) {
        this.seed = seed;
        this.seedSet = true;
        return this;
    }

    /** Upper bound on shrink steps per individual counterexample. */
    public PropertyCheck<T> withMaxShrinks(long maxShrinks) {
        this.maxShrinks = maxShrinks;
        return this;
    }

    /** Counts every input for which the predicate holds under the label. */
    public PropertyCheck<T> classify(String label, Predicate<T> belongsTo) {
        classifiers.add(new NamedClassifier<>(label, belongsTo));
        return this;
    }

    /**
     * Maps each input to a bucket key and counts occurrences as
     * {@code label: key} (e.g. {@code "length: 0"}, {@code "length: 1"}).
     */
    public PropertyCheck<T> collect(String label, Function<T, ?> classifier) {
        classifiers.add(new NamedClassifier<>(label, value -> true) {
            @Override
            String bucketFor(T value) {
                return label + ": " + classifier.apply(value);
            }
        });
        return this;
    }

    public PropertyResult<T> run() {
        if (property == null) {
            throw new IllegalStateException("declare the property with check(...) before run()");
        }
        RandomSource random = new RandomSource(seed);
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<T, PropertyResult.Counterexample<T>> distinctFailures = new LinkedHashMap<>();
        int failures = 0;
        long totalShrinkSteps = 0;

        for (int i = 0; i < tries; i++) {
            Shrinkable<T> shrinkable = arbitrary.generate(random);
            T value = shrinkable.value();

            classify(value, counts);

            if (!property.test(value)) {
                failures++;
                Shrinker.ShrinkResult<T> shrunk = Shrinker.shrink(shrinkable, v -> !property.test(v), maxShrinks);
                totalShrinkSteps += shrunk.steps();
                distinctFailures.putIfAbsent(shrunk.value(),
                        new PropertyResult.Counterexample<>(value, shrunk.value(), shrunk.steps()));
            }
        }

        PropertyStatistics statistics = new PropertyStatistics(
                seed, tries, failures, totalShrinkSteps, counts);
        return new PropertyResult<>(statistics, new ArrayList<>(distinctFailures.values()));
    }

    private void classify(T value, Map<String, Long> counts) {
        for (NamedClassifier<T> classifier : classifiers) {
            if (classifier.predicate.test(value)) {
                String bucket = classifier.bucketFor(value);
                counts.merge(bucket, 1L, Long::sum);
            }
        }
    }

    boolean isSeedSet() {
        return seedSet;
    }

    private static class NamedClassifier<T> {
        final String label;
        final Predicate<T> predicate;

        NamedClassifier(String label, Predicate<T> predicate) {
            this.label = label;
            this.predicate = predicate;
        }

        String bucketFor(T value) {
            return label;
        }
    }
}
