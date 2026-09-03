package com.thegamecellar.gameservice.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CellarRatingBlendTest {

    private static BigDecimal blend(String memberAvg, Integer count, String igdb, int k) {
        return GameMapper.cellarRating(
                memberAvg == null ? null : new BigDecimal(memberAvg),
                count,
                igdb == null ? null : new BigDecimal(igdb),
                k);
    }

    @Test
    void oneMemberRatingBarelyMovesTheIgdbScoreAtTheDefaultPrior() {
        // (1 * 10.00 + 10 * 8.50) / 11 = 8.6363... -> 8.64
        assertThat(blend("10.00", 1, "8.50", 10)).isEqualByComparingTo("8.64");
    }

    @Test
    void aLowerPriorLetsTheSameRatingMoveTheScoreFurther() {
        // (1 * 10.00 + 3 * 8.50) / 4 = 8.875 -> 8.88
        assertThat(blend("10.00", 1, "8.50", 3)).isEqualByComparingTo("8.88");
    }

    @Test
    void manyMembersOutweighThePrior() {
        // (100 * 9.00 + 10 * 5.00) / 110 = 8.6363... -> 8.64
        assertThat(blend("9.00", 100, "5.00", 10)).isEqualByComparingTo("8.64");
    }

    @Test
    void noMemberRatingsFallsBackToTheIgdbScoreUntouched() {
        assertThat(blend(null, 0, "7.25", 10)).isEqualByComparingTo("7.25");
        assertThat(blend(null, null, "7.25", 10)).isEqualByComparingTo("7.25");
    }

    @Test
    void noIgdbScoreFallsBackToThePlainMemberAverage() {
        assertThat(blend("6.5", 4, null, 10)).isEqualByComparingTo("6.50");
    }

    @Test
    void neitherSideHasAScoreSoThereIsNoNumberToShow() {
        assertThat(blend(null, 0, null, 10)).isNull();
        assertThat(blend(null, null, null, 10)).isNull();
    }

    // A count without an average is malformed input rather than a rated game; treating it
    // as rated would divide by a member average that does not exist.
    @Test
    void aCountWithoutAnAverageIsTreatedAsUnrated() {
        assertThat(blend(null, 7, "8.00", 10)).isEqualByComparingTo("8.00");
    }
}
