package com.example.pbt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Integer generator over a configurable closed interval.
 *
 * <p>Most draws are uniform, but ~15% are forced "edge cases" (the bounds,
 * zero and {@code +/-1} when inside the range) so boundary defects surface
 * quickly instead of waiting for chance.
 */
public final class IntegerArbitrary implements Arbitrary<Integer> {

    static final double EDGE_PROBABILITY = 0.15;

    private final int min;
    private final int max;
    private final List<Integer> edgeCases;

    public IntegerArbitrary(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
        }
        this.min = min;
        this.max = max;
        this.edgeCases = buildEdgeCases(min, max);
    }

    private static List<Integer> buildEdgeCases(int min, int max) {
        List<Integer> edges = new ArrayList<>();
        for (int candidate : new int[] {min, max, 0, 1, -1, min + 1, max - 1}) {
            if (candidate >= min && candidate <= max && !edges.contains(candidate)) {
                edges.add(candidate);
            }
        }
        return edges;
    }

    @Override
    public Shrinkable<Integer> generate(RandomSource random) {
        int value = random.nextBoolean(EDGE_PROBABILITY) && !edgeCases.isEmpty()
                ? random.choose(edgeCases)
                : random.nextInt(min, max);
        return new IntegerShrinkable(value, min, max);
    }

    static int clampToRange(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class IntegerShrinkable implements Shrinkable<Integer> {
        private final int value;
        private final int min;
        private final int max;

        IntegerShrinkable(int value, int min, int max) {
            this.value = value;
            this.min = min;
            this.max = max;
        }

        @Override
        public Integer value() {
            return value;
        }

        /**
         * Classic QuickCheck shrink: towards zero by steps 1, 2, 4, 8, ...
         * (capped by the range), with the clamped zero appended last.
         * Every child is strictly closer to zero and the step of size 1 is
         * always present, which gives the greedy shrinker strong locality.
         */
        @Override
        public Stream<Shrinkable<Integer>> shrink() {
            int target = clampToRange(0, min, max);
            if (value == target) {
                return Stream.empty();
            }
            int sign = Integer.signum(value - target);
            List<Integer> candidates = new ArrayList<>();
            long step = 1;
            long room = Math.abs((long) value - target);
            while (step < room) {
                candidates.add(value - sign * (int) step);
                step *= 2;
            }
            if (!candidates.contains(target)) {
                candidates.add(target);
            }
            return candidates.stream().map(v -> new IntegerShrinkable(v, min, max));
        }
    }
}
