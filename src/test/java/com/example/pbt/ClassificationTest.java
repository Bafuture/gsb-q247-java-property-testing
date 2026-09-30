package com.example.pbt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClassificationTest {

    @Test
    void classifyPartitionsAllTriesIntoExhaustiveCategories() {
        PropertyResult<Integer> result = PropertyCheck
                .forAll(Arbitraries.integers().between(-100, 100).build())
                .check(i -> i * i >= 0)
                .withTries(500)
                .withSeed(2024L)
                .classify("negative", i -> i < 0)
                .classify("zero", i -> i == 0)
                .classify("positive", i -> i > 0)
                .run()
                .assertSuccessful();

        var distribution = result.statistics().classification();
        assertThat(distribution).containsOnlyKeys("negative", "zero", "positive");
        assertThat(distribution.get("negative")).isPositive();
        assertThat(distribution.get("zero")).isPositive();
        assertThat(distribution.get("positive")).isPositive();
        long total = distribution.values().stream().mapToLong(Long::longValue).sum();
        assertThat(total).isEqualTo(500);
    }

    @Test
    void collectCountsPerDerivedBucket() {
        PropertyResult<Integer> result = PropertyCheck
                .forAll(Arbitraries.integers().between(0, 99).build())
                .check(i -> i / 100 == 0)
                .withTries(300)
                .withSeed(88L)
                .collect("decade", i -> i / 10)
                .run()
                .assertSuccessful();

        var distribution = result.statistics().classification();
        assertThat(distribution).hasSize(10);
        long total = distribution.values().stream().mapToLong(Long::longValue).sum();
        assertThat(total).isEqualTo(300);
    }

    @Test
    void nonExhaustiveClassificationsSimplyCountLessThanAllTries() {
        PropertyResult<Integer> result = PropertyCheck
                .forAll(Arbitraries.integers().between(-100, 100).build())
                .check(i -> true)
                .withTries(200)
                .withSeed(9L)
                .classify("multipleOf10", i -> i % 10 == 0 && i != 0)
                .run()
                .assertSuccessful();

        long counted = result.statistics().classification().values().stream()
                .mapToLong(Long::longValue).sum();
        assertThat(counted).isLessThan(200).isPositive();
    }
}
