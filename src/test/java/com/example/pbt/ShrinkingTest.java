package com.example.pbt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShrinkingTest {

    @Test
    void integerPropertyShrinksToTheBoundaryValue() {
        PropertyResult<Integer> result = PropertyCheck
                .forAll(Arbitraries.integers().between(0, 100).build())
                .check(i -> i < 50)
                .withTries(200)
                .withSeed(42L)
                .run();

        assertThat(result.isSuccessful()).isFalse();
        // Every collected counterexample must be the true minimum: 50.
        assertThat(result.falsifiedValues()).containsOnly(50);
        assertThat(result.statistics().shrinkSteps()).isGreaterThan(0);
        // The shrunk value still falsifies the property.
        assertThat(result.falsifiedValues().get(0)).isEqualTo(50);
    }

    @Test
    void stringPropertyShrinksToShortestSimplestString() {
        PropertyResult<String> result = PropertyCheck
                .forAll(Arbitraries.strings().between('a', 'z').ofMaxLength(10).build())
                .check(s -> s.length() < 3)
                .withTries(200)
                .withSeed(42L)
                .run();

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.falsifiedValues()).containsOnly("aaa");
    }

    @Test
    void listPropertyShrinksToSmallestListOfSmallestElements() {
        PropertyResult<List<Integer>> result = PropertyCheck
                .forAll(Arbitraries
                        .lists(Arbitraries.integers().between(0, 100).build())
                        .between(0, 10)
                        .build())
                .check(list -> list.size() < 3)
                .withTries(200)
                .withSeed(42L)
                .run();

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.falsifiedValues()).containsOnly(List.of(0, 0, 0));
    }

    @Test
    void shrunkCounterexampleKeepsTheOriginalFailingSample() {
        PropertyResult<Integer> result = PropertyCheck
                .forAll(Arbitraries.integers().between(0, 100).build())
                .check(i -> i < 50)
                .withTries(200)
                .withSeed(42L)
                .run();

        PropertyResult.Counterexample<Integer> first = result.counterexamples().get(0);
        assertThat(first.original()).isGreaterThanOrEqualTo(50);
        assertThat(first.shrunk()).isEqualTo(50);
        assertThat(first.shrinkSteps()).isGreaterThan(0);
    }
}
