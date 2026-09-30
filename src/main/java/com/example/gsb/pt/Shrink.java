package com.example.gsb.pt;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal numeric shrinking primitives shared by the built-in generators.
 */
final class Shrink {

    private Shrink() {
    }

    /**
     * Integer candidates between {@code value} and {@code target}, ordered from the
     * most aggressive shrink ({@code target} itself) to the least aggressive
     * (one step away from {@code value}).
     *
     * <p>Every candidate is strictly closer to {@code target} than {@code value};
     * there are at most {@code O(log |value-target|)} of them.
     */
    static List<Long> towards(long target, long value) {
        List<Long> candidates = new ArrayList<>();
        if (value == target) {
            return candidates;
        }
        candidates.add(target);
        long step = value - target;
        while (Math.abs(step) > 1) {
            step /= 2;
            long candidate = value - step;
            if (candidate != value && (candidates.isEmpty()
                    || candidates.get(candidates.size() - 1) != candidate)) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }
}
