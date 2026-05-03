package com.thegamecellar.gameservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgeRatingMapperTest {

    @Test
    void shouldMapEsrbCategoryToBody() {
        assertThat(AgeRatingMapper.body(1)).isEqualTo("ESRB");
    }

    @Test
    void shouldMapPegiCategoryToBody() {
        assertThat(AgeRatingMapper.body(2)).isEqualTo("PEGI");
    }

    @Test
    void shouldReturnNullForUnsupportedBody() {
        assertThat(AgeRatingMapper.body(3)).isNull(); // CERO
        assertThat(AgeRatingMapper.body(7)).isNull(); // ACB
    }

    @Test
    void shouldReturnNullForNullCategoryBody() {
        assertThat(AgeRatingMapper.body(null)).isNull();
    }

    @Test
    void shouldMapPegiSixteenLabel() {
        assertThat(AgeRatingMapper.label(2, 4)).isEqualTo("16");
    }

    @Test
    void shouldMapPegiEighteenLabel() {
        assertThat(AgeRatingMapper.label(2, 5)).isEqualTo("18");
    }

    @Test
    void shouldMapEsrbMatureLabel() {
        assertThat(AgeRatingMapper.label(1, 11)).isEqualTo("M");
    }

    @Test
    void shouldMapEsrbTeenLabel() {
        assertThat(AgeRatingMapper.label(1, 10)).isEqualTo("T");
    }

    @Test
    void shouldReturnNullForUnknownRating() {
        assertThat(AgeRatingMapper.label(2, 99)).isNull();
        assertThat(AgeRatingMapper.label(1, 99)).isNull();
    }

    @Test
    void shouldReturnNullForUnsupportedCategoryLabel() {
        assertThat(AgeRatingMapper.label(3, 1)).isNull(); // CERO
    }

    @Test
    void shouldReturnNullForNullCategoryOrRating() {
        assertThat(AgeRatingMapper.label(null, 4)).isNull();
        assertThat(AgeRatingMapper.label(2, null)).isNull();
    }
}
