package com.example.gsb.pt;

import java.util.function.Predicate;

/**
 * A property under test: an assertion executed for every generated value.
 *
 * <p>A property passes when {@link #verify(Object)} returns normally and fails
 * when it throws (e.g. an AssertJ/JUnit assertion error).
 */
@FunctionalInterface
public interface Property<T> {

    void verify(T value) throws Throwable;

    /** Adapts a boolean predicate into a property: {@code false} is a failure. */
    static <T> Property<T> of(Predicate<? super T> predicate) {
        return value -> {
            if (!predicate.test(value)) {
                throw new AssertionError("Property predicate returned false for: " + value);
            }
        };
    }
}
