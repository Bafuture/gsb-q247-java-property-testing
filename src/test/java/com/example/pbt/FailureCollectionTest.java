package com.example.pbt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FailureCollectionTest {

    @Test
    void keepsAllDistinctShrunkCounterexamplesFromOneRun() {
        // Fails on the two disjoint bands [-8, -5] and [5, 8], which are away
        // from the shrink target zero. Each failing sample converges to the
        // inner boundary of its band: -5 or 5.
        PropertyResult<Integer> result = PropertyCheck
                .forAll(Arbitraries.integers().between(-10, 10).build())
                .check(i -> {
                    int abs = Math.abs(i);
                    return abs < 5 || abs > 8;
                })
                .withTries(300)
                .withSeed(2024L)
                .run();

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.falsifiedValues()).containsExactlyInAnyOrder(5, -5);
        assertThat(result.counterexamples()).hasSize(2);
        // Many raw failures, only two distinct after shrinking+dedup.
        assertThat(result.statistics().failures()).isGreaterThan(2);
    }

    @Test
    void duplicateCounterexamplesAreDeduplicated() {
        // Only one failing value exists (5), and edge-case injection hits it
        // repeatedly: raw failures must collapse to a single counterexample.
        PropertyResult<Integer> result = PropertyCheck
                .forAll(Arbitraries.integers().between(5, 5).build())
                .check(i -> i < 5)
                .withTries(50)
                .withSeed(1L)
                .run();

        assertThat(result.statistics().failures()).isEqualTo(50);
        assertThat(result.counterexamples()).hasSize(1);
        assertThat(result.falsifiedValues()).containsExactly(5);
    }

    @Test
    void failuresDoNotStopTheRun() {
        PropertyResult<Integer> result = PropertyCheck
                .forAll(Arbitraries.integers().between(-10, 10).build())
                .check(i -> i != 0)
                .withTries(100)
                .withSeed(13L)
                .run();

        assertThat(result.statistics().tries()).isEqualTo(100);
        assertThat(result.statistics().failures()).isPositive();
        assertThat(result.falsifiedValues()).containsExactly(0);
    }

    @Test
    void assertionErrorsAreCollectedAsFailures() {
        PropertyResult<List<Integer>> result = PropertyCheck
                .forAll(Arbitraries
                        .lists(Arbitraries.integers().between(0, 10).build())
                        .between(0, 6)
                        .build())
                .checkAssert(list -> org.assertj.core.api.Assertions.assertThat(list)
                        .hasSizeLessThan(3))
                .withTries(200)
                .withSeed(13L)
                .run();

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.falsifiedValues()).containsOnly(List.of(0, 0, 0));
    }
}
