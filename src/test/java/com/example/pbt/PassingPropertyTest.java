package com.example.pbt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PassingPropertyTest {

    @Test
    void reversingAListTwiceYieldsTheOriginalList() {
        Arbitrary<List<Integer>> lists = Arbitraries
                .lists(Arbitraries.integers().between(-50, 50).build())
                .between(0, 20)
                .build();

        PropertyResult<List<Integer>> result = PropertyCheck.forAll(lists)
                .check(list -> {
                    List<Integer> reversed = new ArrayList<>(list);
                    Collections.reverse(reversed);
                    Collections.reverse(reversed);
                    return reversed.equals(list);
                })
                .withTries(200)
                .withSeed(1234L)
                .run()
                .assertSuccessful();

        assertThat(result.statistics().tries()).isEqualTo(200);
        assertThat(result.statistics().failures()).isZero();
        assertThat(result.statistics().shrinkSteps()).isZero();
        assertThat(result.counterexamples()).isEmpty();
    }

    @Test
    void assertionStylePropertiesAreSupported() {
        PropertyCheck.forAll(Arbitraries.integers().between(-1000, 1000).build())
                .checkAssert(i -> assertThat(i * i).isGreaterThanOrEqualTo(0))
                .withTries(100)
                .withSeed(7L)
                .run()
                .assertSuccessful();
    }

    @Test
    void combinedAndMappedGeneratorsWork() {
        record Point(int x, int y) {
        }

        Arbitrary<Point> points = Arbitraries
                .combine(
                        Arbitraries.integers().between(-10, 10).build(),
                        Arbitraries.integers().between(-10, 10).build())
                .as(Point::new);

        PropertyCheck.forAll(points)
                .check(p -> Math.abs(p.x()) <= 10 && Math.abs(p.y()) <= 10)
                .withTries(100)
                .withSeed(99L)
                .run()
                .assertSuccessful();
    }

    @Test
    void filteredGeneratorsOnlyProduceMatchingValues() {
        Arbitrary<Integer> evens = Arbitraries.integers().between(0, 100).build()
                .filter(i -> i % 2 == 0);

        PropertyCheck.forAll(evens)
                .check(i -> i % 2 == 0)
                .withTries(50)
                .withSeed(5L)
                .run()
                .assertSuccessful();
    }
}
