package com.charlie.ikibaho.issue.rank;

import com.charlie.ikibaho.issue.internal.rank.LexoRank;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure algorithm, so these are plain unit tests -- no Spring, no database.
 *
 * The property that matters throughout: the string produced must sort strictly
 * between its neighbours under plain lexicographic comparison, because that is
 * the only comparison the database will do.
 */
class LexoRankTest {

    @Test
    void placesARankBetweenTwoNeighbours() {
        String mid = LexoRank.between("a", "c");

        assertThat(mid).isGreaterThan("a").isLessThan("c");
    }

    @Test
    void growsALetterWhenNeighboursAreAdjacent() {
        // There is no single character between "a" and "b", so the rank must
        // lengthen rather than fail.
        String mid = LexoRank.between("a", "b");

        assertThat(mid).isGreaterThan("a").isLessThan("b");
        assertThat(mid).hasSizeGreaterThan(1);
    }

    @Test
    void survivesRepeatedInsertionIntoTheSameGap() {
        String low = "a";
        String high = "b";

        // Every drop into the same spot halves the remaining room. Fifty of them
        // is far past what a person would do and must still hold the ordering.
        String previous = high;
        for (int i = 0; i < 50; i++) {
            String mid = LexoRank.between(low, previous);
            assertThat(mid).isGreaterThan(low).isLessThan(previous);
            previous = mid;
        }
    }

    @Test
    void appendsAfterTheLastRank() {
        String last = LexoRank.after("zzz");

        assertThat(last).isGreaterThan("zzz");
    }

    @Test
    void prependsBeforeTheFirstRank() {
        String first = LexoRank.before("0i");

        assertThat(first).isLessThan("0i");
    }

    @Test
    void neverProducesARankThatNothingCanPrecede() {
        // A rank of all-minimum digits has nothing below it, so the generator must
        // not create one -- otherwise the next "move to top" is impossible.
        List<String> generated = new ArrayList<>();
        String rank = LexoRank.initial();
        for (int i = 0; i < 40; i++) {
            rank = LexoRank.before(rank);
            generated.add(rank);
        }

        assertThat(generated).allSatisfy(r ->
                assertThat(r.chars().anyMatch(c -> c != '0'))
                        .as("rank %s is all zeros", r)
                        .isTrue());
    }

    @Test
    void refusesBoundsThatAreOutOfOrder() {
        assertThatThrownBy(() -> LexoRank.between("c", "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of order");

        assertThatThrownBy(() -> LexoRank.between("a", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesARankBelowTheFloor() {
        assertThatThrownBy(() -> LexoRank.before("0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No rank exists before");
    }

    @Test
    void rejectsCharactersOutsideBase36() {
        assertThatThrownBy(() -> LexoRank.between("A", "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base 36");
    }

    @Test
    void seedsAnExistingListInOrderAndEvenlyApart() {
        List<String> ranks = LexoRank.evenlySpaced(200);

        assertThat(ranks).hasSize(200);
        assertThat(ranks).isSorted();
        assertThat(ranks).doesNotHaveDuplicates();

        // Evenly spaced means every gap still has room to insert into.
        for (int i = 1; i < ranks.size(); i++) {
            String mid = LexoRank.between(ranks.get(i - 1), ranks.get(i));
            assertThat(mid).isGreaterThan(ranks.get(i - 1)).isLessThan(ranks.get(i));
        }
    }

    @Test
    void seedingHandlesTheEmptyAndSingleCases() {
        assertThat(LexoRank.evenlySpaced(0)).isEmpty();
        assertThat(LexoRank.evenlySpaced(1)).hasSize(1);
    }

    @Test
    void aFreshListStartsInTheMiddleSoItCanGrowBothWays() {
        String initial = LexoRank.initial();

        assertThat(LexoRank.before(initial)).isLessThan(initial);
        assertThat(LexoRank.after(initial)).isGreaterThan(initial);
    }

    @Test
    void flagsRanksThatHaveGrownLongEnoughToRebalance() {
        assertThat(LexoRank.isDegenerate("i")).isFalse();
        assertThat(LexoRank.isDegenerate("aaaaaaaaaaaa")).isTrue();
    }
}
