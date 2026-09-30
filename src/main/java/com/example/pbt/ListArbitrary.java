package com.example.pbt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * List generator built from an element {@link Arbitrary}, with configurable
 * size bounds. Shrinking removes contiguous chunks of elements (starting with
 * the largest chunk) and then shrinks individual elements in place.
 */
public final class ListArbitrary<T> implements Arbitrary<List<T>> {

    static final double EDGE_PROBABILITY = 0.15;

    private final Arbitrary<T> elementArbitrary;
    private final int minSize;
    private final int maxSize;

    ListArbitrary(Arbitrary<T> elementArbitrary, int minSize, int maxSize) {
        if (minSize < 0 || minSize > maxSize) {
            throw new IllegalArgumentException("need 0 <= minSize <= maxSize");
        }
        this.elementArbitrary = elementArbitrary;
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    @Override
    public Shrinkable<List<T>> generate(RandomSource random) {
        int size;
        if (random.nextBoolean(EDGE_PROBABILITY)) {
            size = random.nextBoolean(0.5) ? minSize : maxSize;
        } else {
            size = random.nextInt(minSize, maxSize);
        }
        List<Shrinkable<T>> elements = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            elements.add(elementArbitrary.generate(random));
        }
        return new ListShrinkable<>(elements, minSize);
    }

    static final class ListShrinkable<T> implements Shrinkable<List<T>> {
        private final List<Shrinkable<T>> elements;
        private final int minSize;

        ListShrinkable(List<Shrinkable<T>> elements, int minSize) {
            this.elements = elements;
            this.minSize = minSize;
        }

        @Override
        public List<T> value() {
            List<T> result = new ArrayList<>(elements.size());
            for (Shrinkable<T> element : elements) {
                result.add(element.value());
            }
            return result;
        }

        @Override
        public Stream<Shrinkable<List<T>>> shrink() {
            List<Stream<Shrinkable<List<T>>>> groups = new ArrayList<>();
            groups.add(removals());
            for (int i = 0; i < elements.size(); i++) {
                int index = i;
                groups.add(elements.get(index).shrink()
                        .map(shrunk -> replaceElement(index, shrunk)));
            }
            return groups.stream().flatMap(s -> s);
        }

        private Stream<Shrinkable<List<T>>> removals() {
            List<Shrinkable<List<T>>> result = new ArrayList<>();
            int removable = elements.size() - minSize;
            for (int chunk = removable; chunk >= 1; chunk /= 2) {
                for (int start = 0; start + chunk <= elements.size(); start++) {
                    List<Shrinkable<T>> smaller = new ArrayList<>(elements);
                    smaller.subList(start, start + chunk).clear();
                    result.add(new ListShrinkable<>(smaller, minSize));
                }
            }
            return result.stream();
        }

        private Shrinkable<List<T>> replaceElement(int index, Shrinkable<T> shrunk) {
            List<Shrinkable<T>> replaced = new ArrayList<>(elements);
            replaced.set(index, shrunk);
            return new ListShrinkable<>(replaced, minSize);
        }
    }
}
