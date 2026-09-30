package com.example.pbt;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A generated value together with a (lazy) tree of strictly smaller candidates.
 *
 * <p>The children returned by {@link #shrink()} must be "smaller" than the
 * parent according to some well-founded ordering. That guarantee is what makes
 * the shrink process terminate.
 */
public interface Shrinkable<T> {

    T value();

    Stream<Shrinkable<T>> shrink();

    /** Lifts a deterministic transformation over the whole shrink tree. */
    default <U> Shrinkable<U> map(Function<T, U> mapper) {
        Shrinkable<T> parent = this;
        return new Shrinkable<>() {
            @Override
            public U value() {
                return mapper.apply(parent.value());
            }

            @Override
            public Stream<Shrinkable<U>> shrink() {
                return parent.shrink().map(child -> child.map(mapper));
            }
        };
    }

    static <T> Shrinkable<T> unshrinkable(T value) {
        return new Shrinkable<>() {
            @Override
            public T value() {
                return value;
            }

            @Override
            public Stream<Shrinkable<T>> shrink() {
                return Stream.empty();
            }
        };
    }
}
