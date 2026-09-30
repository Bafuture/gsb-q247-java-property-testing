package com.example.pbt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeedReproducibilityTest {

    @Test
    void sameSeedProducesTheSameRandomSequence() {
        RandomSource first = new RandomSource(2024L);
        RandomSource second = new RandomSource(2024L);
        for (int i = 0; i < 10_000; i++) {
            assertThat(second.nextLong()).isEqualTo(first.nextLong());
        }
    }

    @Test
    void sameSeedReproducesTheEntireRunIncludingShrinking() {
        Arbitrary<Integer> arbitrary = Arbitraries.integers().between(-100, 100).build();

        PropertyResult<Integer> run1 = PropertyCheck.forAll(arbitrary)
                .check(i -> i % 17 != 0)
                .withTries(150)
                .withSeed(777L)
                .run();

        PropertyResult<Integer> run2 = PropertyCheck.forAll(arbitrary)
                .check(i -> i % 17 != 0)
                .withTries(150)
                .withSeed(777L)
                .run();

        assertThat(run2.statistics()).usingRecursiveComparison()
                .isEqualTo(run1.statistics());
        assertThat(run2.falsifiedValues()).isEqualTo(run1.falsifiedValues());
        assertThat(run2.counterexamples()).usingRecursiveComparison()
                .isEqualTo(run1.counterexamples());
    }

    @Test
    void sameSeedReproducesGeneratedInputsThroughClassification() {
        Arbitrary<Integer> arbitrary = Arbitraries.integers().between(-10, 10).build();

        PropertyResult<Integer> run1 = PropertyCheck.forAll(arbitrary)
                .check(i -> i * i <= 100)
                .withTries(300)
                .withSeed(31L)
                .collect("value", i -> i)
                .run();

        PropertyResult<Integer> run2 = PropertyCheck.forAll(arbitrary)
                .check(i -> i * i <= 100)
                .withTries(300)
                .withSeed(31L)
                .collect("value", i -> i)
                .run();

        List<String> distribution1 = new ArrayList<>(run1.statistics()
                .classification().keySet());
        List<String> distribution2 = new ArrayList<>(run2.statistics()
                .classification().keySet());
        assertThat(distribution2).isEqualTo(distribution1);
        assertThat(run2.statistics().classification())
                .isEqualTo(run1.statistics().classification());
    }

    @Test
    void everyRunReportsTheSeedItUsed() {
        PropertyResult<Integer> result = PropertyCheck.forAll(
                        Arbitraries.integers().build())
                .check(i -> i == i)
                .withSeed(555L)
                .run();
        assertThat(result.statistics().seed()).isEqualTo(555L);
    }
}
