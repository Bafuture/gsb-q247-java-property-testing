package com.example.pbt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/** Entry point for the built-in generators and combinators. */
public final class Arbitraries {

    private Arbitraries() {
    }

    /** Integer generator, configured with {@code between(min, max)}. */
    public static IntegerBuilder integers() {
        return new IntegerBuilder();
    }

    /** String generator, configured via the returned builder. */
    public static StringBuilder strings() {
        return new StringBuilder();
    }

    /** List generator over the given element generator. */
    public static <T> ListBuilder<T> lists(Arbitrary<T> elementArbitrary) {
        return new ListBuilder<>(elementArbitrary);
    }

    /** A generator that always returns the same value (not shrinkable). */
    public static <T> Arbitrary<T> constant(T value) {
        return random -> Shrinkable.unshrinkable(value);
    }

    /** Uniform choice between fixed values. */
    @SafeVarargs
    public static <T> Arbitrary<T> oneOf(T... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("oneOf requires at least one value");
        }
        return random -> Shrinkable.unshrinkable(values[random.nextInt(values.length)]);
    }

    /** Pairs the outputs of two generators; shrinking preserves the pairing. */
    public static <A, B> Combinator2<A, B> combine(Arbitrary<A> first, Arbitrary<B> second) {
        return new Combinator2<>(first, second);
    }

    public static <A, B, C, D> Arbitrary<D> combine3(
            Arbitrary<A> first, Arbitrary<B> second, Arbitrary<C> third,
            TriFunction<A, B, C, D> combiner) {
        return random -> {
            Shrinkable<A> a = first.generate(random);
            Shrinkable<B> b = second.generate(random);
            Shrinkable<C> c = third.generate(random);
            return new TripleShrinkable<>(a, b, c).map(t -> combiner.apply(t.a, t.b, t.c));
        };
    }

    @FunctionalInterface
    public interface TriFunction<A, B, C, D> {
        D apply(A a, B b, C c);
    }

    public static final class IntegerBuilder {
        private int min = -100;
        private int max = 100;

        public IntegerBuilder between(int min, int max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public Arbitrary<Integer> build() {
            return new IntegerArbitrary(min, max);
        }
    }

    public static final class StringBuilder {
        private char minChar = 'a';
        private char maxChar = 'z';
        private int minLength = 0;
        private int maxLength = 20;

        public StringBuilder between(char minChar, char maxChar) {
            this.minChar = minChar;
            this.maxChar = maxChar;
            return this;
        }

        public StringBuilder ofMinLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public StringBuilder ofMaxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public StringBuilder ofLength(int length) {
            this.minLength = length;
            this.maxLength = length;
            return this;
        }

        public Arbitrary<String> build() {
            return new StringArbitrary(minChar, maxChar, minLength, maxLength);
        }
    }

    public static final class ListBuilder<T> {
        private final Arbitrary<T> elementArbitrary;
        private int minSize = 0;
        private int maxSize = 10;

        ListBuilder(Arbitrary<T> elementArbitrary) {
            this.elementArbitrary = elementArbitrary;
        }

        public ListBuilder<T> ofMinSize(int minSize) {
            this.minSize = minSize;
            return this;
        }

        public ListBuilder<T> ofMaxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public ListBuilder<T> ofSize(int size) {
            this.minSize = size;
            this.maxSize = size;
            return this;
        }

        public ListBuilder<T> between(int minSize, int maxSize) {
            this.minSize = minSize;
            this.maxSize = maxSize;
            return this;
        }

        public Arbitrary<List<T>> build() {
            return new ListArbitrary<>(elementArbitrary, minSize, maxSize);
        }
    }

    public static final class Combinator2<A, B> {
        private final Arbitrary<A> first;
        private final Arbitrary<B> second;

        Combinator2(Arbitrary<A> first, Arbitrary<B> second) {
            this.first = first;
            this.second = second;
        }

        public <C> Arbitrary<C> as(BiFunction<A, B, C> combiner) {
            return random -> {
                Shrinkable<A> a = first.generate(random);
                Shrinkable<B> b = second.generate(random);
                return new PairShrinkable<>(a, b).map(pair -> combiner.apply(pair.a, pair.b));
            };
        }
    }

    private record Pair<A, B>(A a, B b) {
    }

    private record Triple<A, B, C>(A a, B b, C c) {
    }

    private static final class PairShrinkable<A, B> implements Shrinkable<Pair<A, B>> {
        private final Shrinkable<A> first;
        private final Shrinkable<B> second;

        PairShrinkable(Shrinkable<A> first, Shrinkable<B> second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public Pair<A, B> value() {
            return new Pair<>(first.value(), second.value());
        }

        @Override
        public Stream<Shrinkable<Pair<A, B>>> shrink() {
            Stream<Shrinkable<Pair<A, B>>> shrinkFirst =
                    first.shrink().map(a -> new PairShrinkable<>(a, second));
            Stream<Shrinkable<Pair<A, B>>> shrinkSecond =
                    second.shrink().map(b -> new PairShrinkable<>(first, b));
            return Stream.concat(shrinkFirst, shrinkSecond);
        }
    }

    private static final class TripleShrinkable<A, B, C> implements Shrinkable<Triple<A, B, C>> {
        private final Shrinkable<A> first;
        private final Shrinkable<B> second;
        private final Shrinkable<C> third;

        TripleShrinkable(Shrinkable<A> first, Shrinkable<B> second, Shrinkable<C> third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }

        @Override
        public Triple<A, B, C> value() {
            return new Triple<>(first.value(), second.value(), third.value());
        }

        @Override
        public Stream<Shrinkable<Triple<A, B, C>>> shrink() {
            List<Shrinkable<Triple<A, B, C>>> all = new ArrayList<>();
            first.shrink().forEach(a -> all.add(new TripleShrinkable<>(a, second, third)));
            second.shrink().forEach(b -> all.add(new TripleShrinkable<>(first, b, third)));
            third.shrink().forEach(c -> all.add(new TripleShrinkable<>(first, second, c)));
            return all.stream();
        }
    }
}
