package com.example.gsb.pt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GenTest {

    @Test
    void intsStayWithinConfiguredRange() {
        Gen<Integer> gen = Gen.ints(-5, 7);
        for (int value : sample(gen, 1L, 500)) {
            assertThat(value).isBetween(-5, 7);
        }
    }

    @Test
    void intsHitBoundaryValues() {
        Set<Integer> seen = new HashSet<>(sample(Gen.ints(-5, 7), 42L, 500));
        assertThat(seen).contains(-5, 7, 0);
    }

    @Test
    void sameSeedProducesIdenticalSequence() {
        Gen<String> gen = Gen.strings(0, 12);
        assertThat(sample(gen, 123L, 100)).isEqualTo(sample(gen, 123L, 100));
    }

    @Test
    void differentSeedsProduceDifferentSequences() {
        Gen<Integer> gen = Gen.ints(-1000, 1000);
        assertThat(sample(gen, 1L, 100)).isNotEqualTo(sample(gen, 2L, 100));
    }

    @Test
    void stringsRespectLengthAndCharacterBounds() {
        Gen<String> gen = Gen.strings(2, 6, 'a', 'f');
        for (String value : sample(gen, 7L, 300)) {
            assertThat(value).hasSizeBetween(2, 6);
            assertThat(value).matches("[a-f]+");
        }
    }

    @Test
    void stringsHitBoundaryLengthsIncludingEmpty() {
        Set<String> values = new HashSet<>(sample(Gen.strings(0, 8, 'a', 'c'), 9L, 300));
        assertThat(values).contains("");
        assertThat(values).anySatisfy(s -> assertThat(s).hasSize(8));
    }

    @Test
    void listsRespectSizeBoundsAndElementBounds() {
        Gen<List<Integer>> gen = Gen.lists(Gen.ints(-3, 3), 1, 5);
        for (List<Integer> value : sample(gen, 11L, 300)) {
            assertThat(value).hasSizeBetween(1, 5);
            assertThat(value).allSatisfy(element -> assertThat(element).isBetween(-3, 3));
        }
    }

    @Test
    void listsHitBoundarySizes() {
        List<List<Integer>> values = sample(Gen.lists(Gen.ints(0, 1), 1, 4), 5L, 300);
        assertThat(values).anySatisfy(list -> assertThat(list).hasSize(1));
        assertThat(values).anySatisfy(list -> assertThat(list).hasSize(4));
    }

    @Test
    void setsContainOnlyUniqueElements() {
        Gen<Set<Integer>> gen = Gen.sets(Gen.ints(0, 5), 0, 4);
        for (Set<Integer> value : sample(gen, 13L, 200)) {
            assertThat(value).hasSizeLessThanOrEqualTo(4);
            assertThat(value).allSatisfy(element -> assertThat(element).isBetween(0, 5));
        }
    }

    @Test
    void mapTransformsValuesAndBounds() {
        Gen<Integer> even = Gen.ints(0, 25).map(x -> x * 2);
        for (Integer value : sample(even, 17L, 200)) {
            assertThat(value).isEven().isBetween(0, 50);
        }
    }

    @Test
    void combineJoinsTwoGenerators() {
        record Point(int x, int y) {
        }
        Gen<Point> points = Gen.combine(Gen.ints(0, 4), Gen.ints(5, 9), Point::new);
        for (Point point : sample(points, 19L, 200)) {
            assertThat(point.x()).isBetween(0, 4);
            assertThat(point.y()).isBetween(5, 9);
        }
    }

    @Test
    void oneOfAndConstantStayWithinTheirAlternatives() {
        Gen<String> words = Gen.oneOf(Gen.constant("a"), Gen.constant("b"));
        Set<String> seen = new HashSet<>(sample(words, 23L, 100));
        assertThat(seen).containsExactlyInAnyOrder("a", "b");
        assertThat(sample(Gen.constant("x"), 1L, 10)).containsOnly("x");
    }

    @Test
    void flatMapBuildsDependentGenerators() {
        Gen<List<Integer>> dependent =
                Gen.ints(1, 4).flatMap(size -> Gen.lists(Gen.ints(0, 9), size, size));
        for (List<Integer> value : sample(dependent, 77L, 100)) {
            assertThat(value).hasSizeBetween(1, 4);
            assertThat(value).allSatisfy(element -> assertThat(element).isBetween(0, 9));
        }
    }

    @Test
    void shrinkTreeOfIntegerMovesTowardsTarget() {
        List<Integer> candidates = Shrink.towards(0, 100).stream().map(Long::intValue).toList();
        assertThat(candidates).startsWith(0).isSorted();
        assertThat(candidates).allSatisfy(value -> assertThat(value).isBetween(0, 99));
        List<Integer> negatives = Shrink.towards(0, -10).stream().map(Long::intValue).toList();
        assertThat(negatives).startsWith(0).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
    }

    private static <T> List<T> sample(Gen<T> gen, long seed, int count) {
        RandomSource rng = new RandomSource(seed);
        List<T> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(gen.generate(rng).value());
        }
        return values;
    }
}
