package com.thegamecellar.gameservice.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoodMapperTest {

    @Test
    void shouldMapTagToMood() {
        List<String> moods = MoodMapper.getMoods(List.of("Story Rich"), List.of(), List.of());

        assertThat(moods).contains("Story-driven");
    }

    @Test
    void shouldMapTagCaseInsensitively() {
        List<String> moods = MoodMapper.getMoods(List.of("story rich"), List.of(), List.of());

        assertThat(moods).contains("Story-driven");
    }

    @Test
    void shouldMapHyphenatedIgdbKeyword() {
        // IGDB keywords may use hyphens: "story-rich" should match "story rich"
        List<String> moods = MoodMapper.getMoods(List.of("story-rich"), List.of(), List.of());

        assertThat(moods).contains("Story-driven");
    }

    @Test
    void shouldMapHyphenatedSoulsLike() {
        List<String> moods = MoodMapper.getMoods(List.of("souls-like"), List.of(), List.of());

        assertThat(moods).contains("Intense", "Dark/Gritty");
    }

    @Test
    void shouldDeduplicateMoods() {
        // Both "open world" and "exploration" map to "Exploration"
        List<String> moods = MoodMapper.getMoods(List.of("open world", "exploration"), List.of(), List.of());

        long count = moods.stream().filter("Exploration"::equals).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldReturnMultipleMoodsForOneTag() {
        // "open world" maps to both "Exploration" and "Epic"
        List<String> moods = MoodMapper.getMoods(List.of("open world"), List.of(), List.of());

        assertThat(moods).contains("Exploration", "Epic");
    }

    @Test
    void shouldFallBackToGenreIfNoTagOrThemeMoodsFound() {
        List<String> moods = MoodMapper.getMoods(List.of("unknowntag"), List.of("RPG"), List.of());

        assertThat(moods).contains("Story-driven", "Exploration", "Epic");
    }

    @Test
    void shouldFallBackToIgdbGenreName() {
        List<String> moods = MoodMapper.getMoods(List.of("unknowntag"), List.of("Role-playing (RPG)"), List.of());

        assertThat(moods).contains("Story-driven", "Exploration", "Epic");
    }

    @Test
    void shouldNotFallBackToGenreIfTagMoodsWereFound() {
        // "relaxing" maps to "Chill" — genre fallback should NOT be used
        List<String> moods = MoodMapper.getMoods(List.of("relaxing"), List.of("Action"), List.of());

        assertThat(moods).doesNotContain("Intense", "Fast-paced"); // Action moods
        assertThat(moods).contains("Chill");
    }

    @Test
    void shouldNotFallBackToGenreIfThemeMoodsWereFound() {
        List<String> moods = MoodMapper.getMoods(List.of("unknowntag"), List.of("Simulation"), List.of("Fantasy"));

        assertThat(moods).doesNotContain("Chill", "Cozy", "Creative"); // Simulation genre moods
        assertThat(moods).contains("Epic", "Atmospheric");
    }

    @Test
    void shouldMapIgdbThemeToMood() {
        List<String> moods = MoodMapper.getMoods(List.of(), List.of(), List.of("Horror"));

        assertThat(moods).contains("Spooky", "Intense");
    }

    @Test
    void shouldMapMultipleThemes() {
        List<String> moods = MoodMapper.getMoods(List.of(), List.of(), List.of("Fantasy", "Survival"));

        assertThat(moods).contains("Epic", "Atmospheric", "Survival", "Intense");
    }

    @Test
    void shouldCombineTagAndThemeMoods() {
        List<String> moods = MoodMapper.getMoods(List.of("relaxing"), List.of(), List.of("Mystery"));

        assertThat(moods).contains("Chill", "Mystery");
    }

    @Test
    void shouldReturnEmptyListIfNoTagsOrGenresMatch() {
        List<String> moods = MoodMapper.getMoods(List.of("unknowntag"), List.of("UnknownGenre"), List.of());

        assertThat(moods).isEmpty();
    }

    @Test
    void shouldHandleNullTagsGracefully() {
        List<String> moods = MoodMapper.getMoods(null, List.of("RPG"), null);

        assertThat(moods).contains("Story-driven");
    }

    @Test
    void shouldHandleNullGenresGracefully() {
        List<String> moods = MoodMapper.getMoods(List.of("relaxing"), null, null);

        assertThat(moods).contains("Chill");
    }

    @Test
    void shouldHandleAllNullGracefully() {
        List<String> moods = MoodMapper.getMoods(null, null, null);

        assertThat(moods).isEmpty();
    }

    @Test
    void shouldReturnTagsForGivenMood() {
        List<String> tags = MoodMapper.getTagsForMood("Story-driven");

        assertThat(tags).contains("story rich", "choices matter", "narrative");
    }

    @Test
    void shouldReturnTagsForMoodCaseInsensitively() {
        List<String> tags = MoodMapper.getTagsForMood("story-driven");

        assertThat(tags).contains("story rich", "choices matter", "narrative");
    }

    @Test
    void shouldReturnTagsForLowercaseMood() {
        List<String> tags = MoodMapper.getTagsForMood("chill");

        assertThat(tags).contains("relaxing", "casual", "peaceful");
    }

    @Test
    void shouldReturnEmptyListForUnknownMood() {
        List<String> tags = MoodMapper.getTagsForMood("nonexistentmood");

        assertThat(tags).isEmpty();
    }

    @Test
    void shouldReturnAllMoodsSorted() {
        List<String> moods = MoodMapper.getAllMoods();

        assertThat(moods).isNotEmpty();
        assertThat(moods).contains("Chill", "Intense", "Story-driven", "Cozy", "Epic", "Spooky", "Mystery");
        assertThat(moods).isSorted();
    }

    @Test
    void shouldReturnDistinctMoodsOnly() {
        List<String> moods = MoodMapper.getAllMoods();

        long distinctCount = moods.stream().distinct().count();
        assertThat(moods).hasSize((int) distinctCount);
    }

    @Test
    void shouldIncludeThemeMoodsInGetAllMoods() {
        List<String> moods = MoodMapper.getAllMoods();

        // Theme-exclusive moods that may not appear in TAG_TO_MOODS
        assertThat(moods).contains("Spooky");
    }
}
