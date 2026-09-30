package com.example.pbt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * String generator with configurable character range and length bounds.
 *
 * <p>Edge cases (empty string, min/max length, strings made of the boundary
 * characters) are injected with elevated probability.
 */
public final class StringArbitrary implements Arbitrary<String> {

    static final double EDGE_PROBABILITY = 0.15;

    private final char minChar;
    private final char maxChar;
    private final int minLength;
    private final int maxLength;

    StringArbitrary(char minChar, char maxChar, int minLength, int maxLength) {
        if (minChar > maxChar) {
            throw new IllegalArgumentException("minChar must be <= maxChar");
        }
        if (minLength < 0 || minLength > maxLength) {
            throw new IllegalArgumentException("need 0 <= minLength <= maxLength");
        }
        this.minChar = minChar;
        this.maxChar = maxChar;
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    public Shrinkable<String> generate(RandomSource random) {
        if (random.nextBoolean(EDGE_PROBABILITY)) {
            String edge = edgeCase(random);
            if (edge != null) {
                return new StringShrinkable(edge);
            }
        }
        int length = random.nextInt(minLength, maxLength);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) random.nextInt(minChar, maxChar));
        }
        return new StringShrinkable(sb.toString());
    }

    private String edgeCase(RandomSource random) {
        List<String> edges = new ArrayList<>();
        if (minLength == 0) {
            edges.add("");
        }
        edges.add(repeat(minChar, minLength));
        edges.add(repeat(maxChar, maxLength));
        edges.add(repeat(minChar, maxLength));
        edges.add(repeat(maxChar, minLength));
        return edges.isEmpty() ? null : random.choose(edges);
    }

    private static String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }

    final class StringShrinkable implements Shrinkable<String> {
        private final String value;

        StringShrinkable(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        /**
         * Shrinks in two phases, smallest first:
         * <ol>
         *   <li>shorten towards {@code minLength} (halve the excess, then the
         *       exact minimum-length prefix);</li>
         *   <li>simplify individual characters towards {@code minChar} with
         *       steps 1, 2, 4, ... per position.</li>
         * </ol>
         */
        @Override
        public Stream<Shrinkable<String>> shrink() {
            List<Shrinkable<String>> candidates = new ArrayList<>();
            int length = value.length();
            int excess = length - minLength;
            for (int cut = excess / 2; cut >= 1; cut /= 2) {
                candidates.add(new StringShrinkable(value.substring(0, length - cut)));
            }
            if (excess > 0) {
                candidates.add(new StringShrinkable(value.substring(0, minLength)));
            }
            for (int i = 0; i < length; i++) {
                char c = value.charAt(i);
                for (int step = 1; step < c - minChar; step *= 2) {
                    candidates.add(new StringShrinkable(replace(i, (char) (c - step))));
                }
                if (c != minChar) {
                    candidates.add(new StringShrinkable(replace(i, minChar)));
                }
            }
            return candidates.stream();
        }

        private String replace(int index, char c) {
            return value.substring(0, index) + c + value.substring(index + 1);
        }
    }
}
