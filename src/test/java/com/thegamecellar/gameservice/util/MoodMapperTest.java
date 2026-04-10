package com.thegamecellar.gameservice.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoodMapperTest {

    // --- getMoods: tag-based ---

    @Test
    void shouldMapTagToMood() {
        List<String> moods = MoodMapper.getMoods(List.of("Story Rich"), List.of());

        assertThat(moods).contains("Story-driven");
    }

    @Test
    void shouldMapTagCaseInsensitively() {
        List<String> moods = MoodMapper.getMoods(List.of("story rich"), List.of());

        assertThat(moods).contains("Story-driven");
    }

    @Test
    void shouldDeduplicateMoods() {
        // Both "open world" and "exploration" map to "Exploration"
        List<String> moods = MoodMapper.getMoods(List.of("open world", "exploration"), List.of());

        long count = moods.stream().filter("Exploration"::equals).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldReturnMultipleMoodsForOneTag() {
        // "open world" maps to both "Exploration" and "Epic"
        List<String> moods = MoodMapper.getMoods(List.of("open world"), List.of());

        assertThat(moods).contains("Exploration", "Epic");
    }

    // --- getMoods: genre fallback ---

    @Test
    void shouldFallBackToGenreIfNoTagMoodsFound() {
        List<String> moods = MoodMapper.getMoods(List.of("unknowntag"), List.of("RPG"));

        assertThat(moods).contains("Story-driven", "Exploration", "Epic");
    }

    @Test
    void shouldNotFallBackToGenreIfTagMoodsWereFound() {
        // "relaxing" maps to "Chill" — genre fallback should NOT be used
        List<String> moods = MoodMapper.getMoods(List.of("relaxing"), List.of("Action"));

        assertThat(moods).doesNotContain("Intense", "Fast-paced"); // Action moods
        assertThat(moods).contains("Chill");
    }

    @Test
    void shouldReturnEmptyListIfNoTagsOrGenresMatch() {
        List<String> moods = MoodMapper.getMoods(List.of("unknowntag"), List.of("UnknownGenre"));

        assertThat(moods).isEmpty();
    }

    @Test
    void shouldHandleNullTagsGracefully() {
        List<String> moods = MoodMapper.getMoods(null, List.of("RPG"));

        assertThat(moods).contains("Story-driven");
    }

    @Test
    void shouldHandleNullGenresGracefully() {
        List<String> moods = MoodMapper.getMoods(List.of("relaxing"), null);

        assertThat(moods).contains("Chill");
    }

    @Test
    void shouldHandleBothNullGracefully() {
        List<String> moods = MoodMapper.getMoods(null, null);

        assertThat(moods).isEmpty();
    }

    // --- getTagsForMood ---

    @Test
    void shouldReturnTagsForGivenMood() {
        List<String> tags = MoodMapper.getTagsForMood("Story-driven");

        assertThat(tags).contains("story rich", "choices matter", "narrative");
    }

    @Test
    void shouldReturnEmptyListForUnknownMood() {
        List<String> tags = MoodMapper.getTagsForMood("nonexistentmood");

        assertThat(tags).isEmpty();
    }

    // --- getAllMoods ---

    @Test
    void shouldReturnAllMoodsSorted() {
        List<String> moods = MoodMapper.getAllMoods();

        assertThat(moods).isNotEmpty();
        assertThat(moods).contains("Chill", "Intense", "Story-driven", "Cozy", "Epic");
        assertThat(moods).isSorted();
    }

    @Test
    void shouldReturnDistinctMoodsOnly() {
        List<String> moods = MoodMapper.getAllMoods();

        long distinctCount = moods.stream().distinct().count();
        assertThat(moods).hasSize((int) distinctCount);
    }
}