package com.example.gsb.pt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A generator of random values that also knows how to shrink them.
 *
 * <p>Built-in generators:
 * <ul>
 *   <li>{@link #ints(int, int)} / {@link #longs(long, long)} / {@link #booleans()}</li>
 *   <li>{@link #strings(int, int, char, char)} with configurable length and character range</li>
 *   <li>{@link #lists(Gen, int, int)} and {@link #sets(Gen, int, int)} collections</li>
 *   <li>combinators {@link #constant(Object)}, {@link #oneOf(Gen[])},
 *       {@link #map(Function)}, {@link #flatMap(Function)}, {@link #combine(Gen, Gen, BiFunction)}</li>
 * </ul>
 *
 * <p>With a 10% probability the numeric/string/list generators draw a boundary value
 * (min/max of the configured range, or 0 when in range) so edge cases are exercised
 * on every run while remaining fully seed-reproducible.
 */
@FunctionalInterface
public interface Gen<T> {

    /** Generates a shrinkable value from deterministic randomness. */
    Shrinkable<T> generate(RandomSource rng);

    // ------------------------------------------------------------------
    // Combinators
    // ------------------------------------------------------------------

    /** Transforms generated values; shrink candidates are transformed as well. */
    default <R> Gen<R> map(Function<? super T, ? extends R> mapper) {
        return rng -> mapShrinkable(generate(rng), mapper);
    }

    /**
     * Generates a value and then a dependent generator from it. Shrinking explores
     * the inner generator's candidates first and then re-derives the inner
     * generator from shrunk outer values (with the same captured seed, so this
     * remains deterministic).
     */
    default <R> Gen<R> flatMap(Function<? super T, Gen<R>> mapper) {
        return rng -> {
            Shrinkable<T> outer = generate(rng);
            long innerSeed = rng.nextSeed();
            return bindShrinkable(outer, mapper, innerSeed);
        };
    }

    /** Combines two generators; shrinking alternates shrinking either side. */
    static <A, B, R> Gen<R> combine(Gen<A> first, Gen<B> second,
                                    BiFunction<? super A, ? super B, ? extends R> combiner) {
        return rng -> zipShrinkable(first.generate(rng), second.generate(rng), combiner);
    }

    /** Picks one of the alternative generators uniformly at random. */
    @SafeVarargs
    static <T> Gen<T> oneOf(Gen<? extends T>... alternatives) {
        if (alternatives.length == 0) {
            throw new IllegalArgumentException("oneOf requires at least one generator");
        }
        List<Gen<? extends T>> options = List.of(alternatives);
        return rng -> uncheckedGenerate(options.get(rng.nextInt(0, options.size() - 1)), rng);
    }

    @SuppressWarnings("unchecked")
    private static <T> Shrinkable<T> uncheckedGenerate(Gen<? extends T> generator, RandomSource rng) {
        return (Shrinkable<T>) generator.generate(rng);
    }

    /** Uniformly picks one of the given constant values. */
    @SafeVarargs
    static <T> Gen<T> oneOfValues(T... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("oneOfValues requires at least one value");
        }
        List<T> options = List.of(values);
        return rng -> Shrinkable.unshrinkable(rng.choose(options));
    }

    /** Always generates the same value (which cannot be shrunk). */
    static <T> Gen<T> constant(T value) {
        return rng -> Shrinkable.unshrinkable(value);
    }

    // ------------------------------------------------------------------
    // Numbers
    // ------------------------------------------------------------------

    /** Uniform integers in [{@code min}, {@code max}], shrinking towards 0 (or the nearest bound). */
    static Gen<Integer> ints(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        long target = shrinkTarget(min, max);
        List<Integer> edges = intEdges(min, max);
        return rng -> {
            int value = rng.nextDouble() < 0.1 ? rng.choose(edges) : rng.nextInt(min, max);
            return Shrinkable.of(value,
                    v -> Shrink.towards(target, v).stream().map(Long::intValue));
        };
    }

    /** Uniform longs in [{@code min}, {@code max}], shrinking towards 0 (or the nearest bound). */
    static Gen<Long> longs(long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
        long target = shrinkTarget(min, max);
        List<Long> edges = longEdges(min, max);
        return rng -> {
            long value = rng.nextDouble() < 0.1
                    ? rng.choose(edges)
                    : rng.nextLong(min, max + 1);
            return Shrinkable.of(value, v -> Shrink.towards(target, v).stream());
        };
    }

    /** Booleans, shrinking {@code true} to {@code false}. */
    static Gen<Boolean> booleans() {
        return rng -> Shrinkable.of(rng.nextBoolean(),
                value -> value ? Stream.of(false) : Stream.empty());
    }

    // ------------------------------------------------------------------
    // Strings
    // ------------------------------------------------------------------

    /** Lowercase ASCII strings of length 0..20. */
    static Gen<String> strings() {
        return strings(0, 20, 'a', 'z');
    }

    /** Lowercase ASCII strings with configurable length range. */
    static Gen<String> strings(int minLength, int maxLength) {
        return strings(minLength, maxLength, 'a', 'z');
    }

    /**
     * Strings with configurable length and character ranges. Shrinks by removing
     * characters (towards {@code minLength}) and by moving characters towards
     * {@code minChar}.
     */
    static Gen<String> strings(int minLength, int maxLength, char minChar, char maxChar) {
        if (minLength < 0 || minLength > maxLength || minChar > maxChar) {
            throw new IllegalArgumentException("invalid string bounds");
        }
        return rng -> {
            int length = rng.nextDouble() < 0.1
                    ? rng.choose(List.of(minLength, maxLength))
                    : rng.nextInt(minLength, maxLength);
            List<Shrinkable<Character>> characters = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                char value = (char) rng.nextInt(minChar, maxChar);
                characters.add(charShrinkable(value, minChar));
            }
            return mapShrinkable(listShrinkable(characters, minLength),
                    list -> list.stream().map(String::valueOf).collect(Collectors.joining()));
        };
    }

    // ------------------------------------------------------------------
    // Collections
    // ------------------------------------------------------------------

    /** Lists sized [{@code minSize}, {@code maxSize}]; shrinks by removing elements then shrinking them. */
    static <E> Gen<List<E>> lists(Gen<E> elementGen, int minSize, int maxSize) {
        if (minSize < 0 || minSize > maxSize) {
            throw new IllegalArgumentException("invalid size bounds");
        }
        return rng -> {
            int size = rng.nextDouble() < 0.1
                    ? rng.choose(List.of(minSize, maxSize))
                    : rng.nextInt(minSize, maxSize);
            List<Shrinkable<E>> elements = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                elements.add(elementGen.generate(rng));
            }
            return listShrinkable(elements, minSize);
        };
    }

    /**
     * Sets sized up to [{@code minSize}, {@code maxSize}]; duplicate draws are
     * deduplicated, so the resulting size may be below {@code minSize}.
     */
    static <E> Gen<Set<E>> sets(Gen<E> elementGen, int minSize, int maxSize) {
        return lists(elementGen, minSize, maxSize).map(LinkedHashSet::new);
    }

    // ------------------------------------------------------------------
    // Internal shrink-tree machinery
    // ------------------------------------------------------------------

    private static long shrinkTarget(long min, long max) {
        if (min <= 0 && 0 <= max) {
            return 0;
        }
        return min > 0 ? min : max;
    }

    private static List<Integer> intEdges(int min, int max) {
        LinkedHashSet<Integer> edges = new LinkedHashSet<>();
        edges.add(min);
        edges.add(max);
        if (min <= 0 && 0 <= max) {
            edges.add(0);
        }
        if ((long) min + 1 <= max) {
            edges.add(min + 1);
        }
        if ((long) max - 1 >= min) {
            edges.add(max - 1);
        }
        return List.copyOf(edges);
    }

    private static List<Long> longEdges(long min, long max) {
        LinkedHashSet<Long> edges = new LinkedHashSet<>();
        edges.add(min);
        edges.add(max);
        if (min <= 0 && 0 <= max) {
            edges.add(0L);
        }
        if (min + 1 <= max) {
            edges.add(min + 1);
        }
        if (max - 1 >= min) {
            edges.add(max - 1);
        }
        return List.copyOf(edges);
    }

    private static Shrinkable<Character> charShrinkable(char value, char target) {
        return Shrinkable.of(value,
                v -> Shrink.towards(target, v).stream().map(candidate -> (char) candidate.longValue()));
    }

    private static <T, R> Shrinkable<R> mapShrinkable(
            Shrinkable<T> source, Function<? super T, ? extends R> mapper) {
        return new Shrinkable<>() {
            @Override
            public R value() {
                return mapper.apply(source.value());
            }

            @Override
            public Stream<Shrinkable<R>> shrinks() {
                return source.shrinks().map(child -> mapShrinkable(child, mapper));
            }
        };
    }

    private static <A, B, R> Shrinkable<R> zipShrinkable(
            Shrinkable<A> first, Shrinkable<B> second,
            BiFunction<? super A, ? super B, ? extends R> combiner) {
        return new Shrinkable<>() {
            @Override
            public R value() {
                return combiner.apply(first.value(), second.value());
            }

            @Override
            public Stream<Shrinkable<R>> shrinks() {
                Stream<Shrinkable<R>> shrinkFirst =
                        first.shrinks().map(a -> zipShrinkable(a, second, combiner));
                Stream<Shrinkable<R>> shrinkSecond =
                        second.shrinks().map(b -> zipShrinkable(first, b, combiner));
                return Stream.concat(shrinkFirst, shrinkSecond);
            }
        };
    }

    private static <T, R> Shrinkable<R> bindShrinkable(
            Shrinkable<T> outer, Function<? super T, Gen<R>> mapper, long innerSeed) {
        Shrinkable<R> inner = mapper.apply(outer.value()).generate(new RandomSource(innerSeed));
        return bindInnerShrinkable(outer, inner, mapper, innerSeed);
    }

    private static <T, R> Shrinkable<R> bindInnerShrinkable(
            Shrinkable<T> outer, Shrinkable<R> inner,
            Function<? super T, Gen<R>> mapper, long innerSeed) {
        return new Shrinkable<>() {
            @Override
            public R value() {
                return inner.value();
            }

            @Override
            public Stream<Shrinkable<R>> shrinks() {
                Stream<Shrinkable<R>> innerShrinks =
                        inner.shrinks().map(child -> bindInnerShrinkable(outer, child, mapper, innerSeed));
                Stream<Shrinkable<R>> outerShrinks =
                        outer.shrinks().map(child -> bindShrinkable(child, mapper, innerSeed));
                return Stream.concat(innerShrinks, outerShrinks);
            }
        };
    }

    /**
     * Shrink tree for lists: first shrink the size towards {@code minSize} (prefix
     * truncations plus removal of each single element), then shrink each element
     * while keeping the others fixed.
     */
    private static <E> Shrinkable<List<E>> listShrinkable(List<Shrinkable<E>> elements, int minSize) {
        List<Shrinkable<E>> snapshot = List.copyOf(elements);
        return new Shrinkable<>() {
            @Override
            public List<E> value() {
                List<E> values = new ArrayList<>(snapshot.size());
                for (Shrinkable<E> element : snapshot) {
                    values.add(element.value());
                }
                return values;
            }

            @Override
            public Stream<Shrinkable<List<E>>> shrinks() {
                List<Shrinkable<List<E>>> candidates = new ArrayList<>();
                int size = snapshot.size();

                for (long newSize : Shrink.towards(minSize, size)) {
                    candidates.add(listShrinkable(snapshot.subList(0, (int) newSize), minSize));
                }
                if (size > minSize) {
                    for (int index = 0; index < size; index++) {
                        List<Shrinkable<E>> smaller = new ArrayList<>(snapshot);
                        smaller.remove(index);
                        candidates.add(listShrinkable(smaller, minSize));
                    }
                }
                for (int index = 0; index < size; index++) {
                    int position = index;
                    snapshot.get(position).shrinks().forEach(shrunkElement -> {
                        List<Shrinkable<E>> replaced = new ArrayList<>(snapshot);
                        replaced.set(position, shrunkElement);
                        candidates.add(listShrinkable(replaced, minSize));
                    });
                }
                return candidates.stream();
            }
        };
    }
}
