package com.example.gsb.pt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertyCheckerTest {

    private record Pair(int a, int b) {
    }

    @Test
    void passingPropertyIsReportedAsSuccess() {
        PropertyResult<Pair> result = PropertyChecker.check(
                Gen.combine(Gen.ints(-1000, 1000), Gen.ints(-1000, 1000), Pair::new),
                Property.of(pair -> pair.a() + pair.b() == pair.b() + pair.a()),
                CheckConfig.of(100, 1L));

        result.assertPassed();
        assertThat(result.passed()).isTrue();
        assertThat(result.tries()).isEqualTo(100);
        assertThat(result.failures()).isZero();
        assertThat(result.shrinkSteps()).isZero();
        assertThat(result.counterexamples()).isEmpty();
    }

    @Test
    void failingIntegerPropertyShrinksToMinimalCounterexample() {
        PropertyResult<Integer> result = PropertyChecker.check(
                Gen.ints(0, 1000),
                Property.of(x -> x < 10),
                CheckConfig.of(200, 7L));

        assertThat(result.passed()).isFalse();
        assertThat(result.counterexamples()).containsExactly(10);
        assertThat(result.firstCounterexample()).contains(10);
        assertThat(result.failures()).isPositive();
        assertThat(result.shrinkSteps()).isPositive();
        assertThat(result.failureMessages()).containsKey(10);
    }

    @Test
    void failingStringPropertyShrinksToShortestSimplestString() {
        PropertyResult<String> result = PropertyChecker.check(
                Gen.strings(0, 12, 'a', 'z'),
                Property.of(value -> value.length() < 3),
                CheckConfig.of(200, 3L));

        assertThat(result.counterexamples()).containsExactly("aaa");
        assertThat(result.shrinkSteps()).isPositive();
    }

    @Test
    void failingListPropertyShrinksElementsAndSize() {
        PropertyResult<List<Integer>> result = PropertyChecker.check(
                Gen.lists(Gen.ints(-10, 10), 0, 8),
                Property.of(list -> list.size() < 3),
                CheckConfig.of(200, 21L));

        assertThat(result.counterexamples()).containsExactly(List.of(0, 0, 0));
    }

    @Test
    void sameSeedReproducesTheEntireRun() {
        CheckConfig<Integer> config = CheckConfig.<Integer>of(200, 99L)
                .withClassifier(x -> x < 50 ? "small" : "large");
        Property<Integer> property = Property.of(x ->
                x < 10 || (x > 19 && x < 80) || x > 89);

        PropertyResult<Integer> first = PropertyChecker.check(Gen.ints(0, 100), property, config);
        PropertyResult<Integer> second = PropertyChecker.check(Gen.ints(0, 100), property, config);

        assertThat(second.counterexamples()).isEqualTo(first.counterexamples());
        assertThat(second.failures()).isEqualTo(first.failures());
        assertThat(second.shrinkSteps()).isEqualTo(first.shrinkSteps());
        assertThat(second.categories()).isEqualTo(first.categories());
    }

    @Test
    void classificationDistributesEveryTryAcrossCategories() {
        PropertyResult<Integer> result = PropertyChecker.check(
                Gen.ints(0, 99),
                Property.of(x -> true),
                CheckConfig.<Integer>of(300, 11L).withClassifier(x -> x < 50 ? "small" : "large"));

        long total = result.categories().values().stream().mapToLong(Long::longValue).sum();
        assertThat(total).isEqualTo(300);
        assertThat(result.categories()).containsOnlyKeys("small", "large");
        assertThat(result.categories().get("small")).isPositive();
        assertThat(result.categories().get("large")).isPositive();
        assertThat(result.passed()).isTrue();
    }

    @Test
    void runsWithoutClassifierAreMarkedUncategorized() {
        PropertyResult<Integer> result = PropertyChecker.check(
                Gen.ints(0, 9), Property.of(x -> true), CheckConfig.of(50, 4L));
        assertThat(result.categories()).isEqualTo(Map.of("uncategorized", 50L));
    }

    @Test
    void multipleCounterexamplesAreCollectedAndDeduplicated() {
        // Property fails exactly on [10,19] and [80,89]; greedy shrinking lands in two local minima.
        PropertyResult<Integer> result = PropertyChecker.forAll(
                Gen.ints(0, 100),
                x -> x < 10 || (x > 19 && x < 80) || x > 89,
                CheckConfig.of(300, 5L));

        assertThat(result.counterexamples()).containsExactlyInAnyOrder(10, 80);
        assertThat(result.failures()).isGreaterThanOrEqualTo(2);
        // Many raw failures collapse onto the same two distinct shrunk counterexamples.
        assertThat(result.failures()).isGreaterThan(result.counterexamples().size());
    }

    @Test
    void statisticsCoverTriesFailuresShrinkStepsAndDistribution() {
        PropertyResult<Integer> result = PropertyChecker.forAll(
                Gen.ints(0, 100),
                x -> x < 50,
                CheckConfig.<Integer>of(100, 13L)
                        .withClassifier(x -> x < 25 ? "quarter" : "rest"));

        assertThat(result.tries()).isEqualTo(100);
        assertThat(result.failures()).isBetween(1, 99);
        assertThat(result.shrinkSteps()).isPositive();
        assertThat(result.counterexamples()).containsExactly(50);
        assertThat(result.categories().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(100);
        assertThat(result.report())
                .contains("Property FAILED")
                .contains("100 tries")
                .contains("seed=13")
                .contains("50");
    }

    @Test
    void assertPassedThrowsWithReportWhenPropertyFails() {
        PropertyResult<Integer> result = PropertyChecker.check(
                Gen.ints(0, 10), Property.of(x -> x < 0), CheckConfig.of(10, 2L));
        assertThatThrownBy(result::assertPassed)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Property FAILED");
    }
}
