package com.example.gsb.pt;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Property checking engine.
 *
 * <p>For every try it generates a value, classifies it, runs the property, and on
 * failure greedily shrinks the value along its shrink tree. A single run never
 * stops at the first failure: all {@code tries} are executed and every distinct
 * shrunk counterexample is collected.
 */
public final class PropertyChecker {

    private static final String UNCATEGORIZED = "uncategorized";

    private PropertyChecker() {
    }

    public static <T> PropertyResult<T> check(Gen<T> generator, Property<T> property) {
        return check(generator, property, CheckConfig.defaults());
    }

    public static <T> PropertyResult<T> check(Gen<T> generator, Property<T> property,
                                              CheckConfig<T> config) {
        RandomSource rng = new RandomSource(config.seed());
        int failures = 0;
        int shrinkSteps = 0;
        Map<String, Long> categories = new TreeMap<>();
        LinkedHashSet<T> counterexamples = new LinkedHashSet<>();
        Map<T, String> failureMessages = new LinkedHashMap<>();

        for (int i = 0; i < config.tries(); i++) {
            Shrinkable<T> shrinkable = generator.generate(rng);
            T value = shrinkable.value();
            classify(config, value, categories);

            if (run(property, value) == null) {
                continue;
            }
            failures++;
            ShrinkOutcome<T> outcome = shrink(shrinkable, property);
            shrinkSteps += outcome.steps();
            if (counterexamples.add(outcome.value())) {
                Throwable error = run(property, outcome.value());
                failureMessages.put(outcome.value(), error == null ? null : String.valueOf(error.getMessage()));
            }
        }

        return new PropertyResult<>(config.seed(), config.tries(), failures, shrinkSteps,
                categories, counterexamples.stream().toList(), failureMessages);
    }

    /** Convenience overload for boolean-predicate properties. */
    public static <T> PropertyResult<T> forAll(Gen<T> generator, Predicate<? super T> predicate,
                                               CheckConfig<T> config) {
        return check(generator, Property.of(predicate), config);
    }

    private static <T> void classify(CheckConfig<T> config, T value, Map<String, Long> categories) {
        String category = UNCATEGORIZED;
        if (config.classifier() != null) {
            String labeled = config.classifier().apply(value);
            if (labeled != null && !labeled.isEmpty()) {
                category = labeled;
            }
        }
        categories.merge(category, 1L, Long::sum);
    }

    private static <T> Throwable run(Property<T> property, T value) {
        try {
            property.verify(value);
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    /**
     * Greedy descent over the shrink tree: at the current value, walk the direct
     * shrink candidates in order and move to the first one that still fails.
     * Repeat until no direct candidate fails.
     */
    private static <T> ShrinkOutcome<T> shrink(Shrinkable<T> start, Property<T> property) {
        Shrinkable<T> current = start;
        int steps = 0;
        while (true) {
            Optional<Shrinkable<T>> nextFailing = current.shrinks()
                    .filter(candidate -> run(property, candidate.value()) != null)
                    .findFirst();
            if (nextFailing.isEmpty()) {
                return new ShrinkOutcome<>(current.value(), steps);
            }
            current = nextFailing.get();
            steps++;
        }
    }

    private record ShrinkOutcome<T>(T value, int steps) {
    }
}
