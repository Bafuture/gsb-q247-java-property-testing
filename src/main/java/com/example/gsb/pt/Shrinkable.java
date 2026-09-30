package com.example.gsb.pt;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A generated value together with its lazily computed <em>shrink tree</em>.
 *
 * <p>{@link #shrinks()} returns direct shrink candidates: values "smaller" than
 * {@link #value()} according to a well-founded ordering of the producing generator.
 * Each candidate is itself a {@code Shrinkable}, so shrinking can continue recursively.
 */
public interface Shrinkable<T> {

    T value();

    /** Direct, strictly smaller shrink candidates; the stream is computed lazily. */
    Stream<Shrinkable<T>> shrinks();

    /**
     * Creates a shrinkable whose shrink candidates are produced by {@code shrinkFunction}
     * applied to each value; the same function is reused recursively on every candidate.
     */
    static <T> Shrinkable<T> of(T value, Function<? super T, Stream<T>> shrinkFunction) {
        return new Shrinkable<>() {
            @Override
            public T value() {
                return value;
            }

            @Override
            public Stream<Shrinkable<T>> shrinks() {
                return shrinkFunction.apply(value).map(candidate -> of(candidate, shrinkFunction));
            }
        };
    }

    /** A value that cannot be shrunk (a leaf of the shrink tree). */
    static <T> Shrinkable<T> unshrinkable(T value) {
        return of(value, ignored -> Stream.empty());
    }
}
