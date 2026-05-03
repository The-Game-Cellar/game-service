package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbCoverDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbNamedEntityDto;
import com.thegamecellar.gameservice.model.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GameMapperTest {

    // ── IGDB → Game entity ────────────────────────────────────────────────────

    @Test
    void shouldMapIgdbDtoToEntity() {
        IgdbGameDto dto = buildIgdbDto();

        Game entity = GameMapper.toEntity(dto);

        assertThat(entity.getIgdbId()).isEqualTo(1942);
        assertThat(entity.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        assertThat(entity.getDescription()).isEqualTo("A story-driven RPG.");
        assertThat(entity.getRating()).isEqualByComparingTo(new BigDecimal("9.40"));
        assertThat(entity.getCoverImageId()).isEqualTo("abc123");
        assertThat(entity.getReleased()).isNotNull();
    }

    @Test
    void shouldHandleNullRatingInIgdbDto() {
        IgdbGameDto dto = new IgdbGameDto();
        dto.setId(1);
        dto.setName("Game");

        Game entity = GameMapper.toEntity(dto);

        assertThat(entity.getRating()).isNull();
        assertThat(entity.getCoverImageId()).isNull();
        assertThat(entity.getReleased()).isNull();
    }

    // ── Game entity → GameResponse ────────────────────────────────────────────

    @Test
    void shouldMapEntityToResponse() {
        Tag tag = new Tag("Story Rich");
        Genre genre = new Genre("RPG");
        Platform platform = new Platform("PC");
        Theme theme = new Theme("Fantasy");

        Game game = Game.builder()
                .igdbId(1942)
                .name("The Witcher 3: Wild Hunt")
                .rating(new BigDecimal("4.66"))
                .coverImageId("abc123")
                .developers("CD PROJEKT RED")
                .tags(new HashSet<>(Set.of(tag)))
                .genres(new HashSet<>(Set.of(genre)))
                .platforms(new HashSet<>(Set.of(platform)))
                .themes(new HashSet<>(Set.of(theme)))
                .build();

        GameResponse response = GameMapper.toResponse(game);

        assertThat(response.getIgdbId()).isEqualTo(1942);
        assertThat(response.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        assertThat(response.getGenres()).containsExactlyInAnyOrder("RPG");
        assertThat(response.getPlatforms()).containsExactlyInAnyOrder("PC");
        assertThat(response.getTags()).containsExactlyInAnyOrder("Story Rich");
        assertThat(response.getThemes()).containsExactlyInAnyOrder("Fantasy");
        assertThat(response.getDevelopers()).containsExactly("CD PROJEKT RED");
        assertThat(response.getCoverImageUrl()).contains("abc123");
        assertThat(response.getMoods()).contains("Story-driven");
    }

    @Test
    void shouldReturnEmptyDevelopersListWhenDevelopersIsNull() {
        Game game = Game.builder()
                .igdbId(1)
                .name("Game")
                .developers(null)
                .genres(new HashSet<>())
                .platforms(new HashSet<>())
                .tags(new HashSet<>())
                .themes(new HashSet<>())
                .build();

        GameResponse response = GameMapper.toResponse(game);

        assertThat(response.getDevelopers()).isEmpty();
    }

    // ── IGDB DTO → GameResponse (live bypass) ─────────────────────────────────

    @Test
    void shouldMapIgdbDtoDirectlyToResponse() {
        IgdbGameDto dto = buildIgdbDto();

        GameResponse response = GameMapper.toResponseFromIgdb(dto);

        assertThat(response.getIgdbId()).isEqualTo(1942);
        assertThat(response.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        assertThat(response.getGenres()).containsExactly("Role-playing (RPG)");
        assertThat(response.getTags()).containsExactly("story rich");
        assertThat(response.getThemes()).containsExactly("Fantasy");
        assertThat(response.getCoverImageUrl()).contains("abc123");
    }

    @Test
    void shouldHandleNullFieldsInIgdbDtoResponse() {
        IgdbGameDto dto = new IgdbGameDto();
        dto.setId(1);
        dto.setName("Minimal Game");

        GameResponse response = GameMapper.toResponseFromIgdb(dto);

        assertThat(response.getGenres()).isEmpty();
        assertThat(response.getPlatforms()).isEmpty();
        assertThat(response.getTags()).isEmpty();
        assertThat(response.getThemes()).isEmpty();
        assertThat(response.getDevelopers()).isEmpty();
        assertThat(response.getMoods()).isEmpty();
        assertThat(response.getCoverImageUrl()).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private IgdbGameDto buildIgdbDto() {
        IgdbNamedEntityDto genre = new IgdbNamedEntityDto();
        genre.setId(12);
        genre.setName("Role-playing (RPG)");

        IgdbNamedEntityDto keyword = new IgdbNamedEntityDto();
        keyword.setId(10);
        keyword.setName("story rich");

        IgdbNamedEntityDto theme = new IgdbNamedEntityDto();
        theme.setId(17);
        theme.setName("Fantasy");

        IgdbNamedEntityDto platform = new IgdbNamedEntityDto();
        platform.setId(6);
        platform.setName("PC (Microsoft Windows)");

        IgdbCoverDto cover = new IgdbCoverDto();
        cover.setId(1);
        cover.setImageId("abc123");

        IgdbGameDto dto = new IgdbGameDto();
        dto.setId(1942);
        dto.setName("The Witcher 3: Wild Hunt");
        dto.setSummary("A story-driven RPG.");
        dto.setAggregatedRating(94.0);
        dto.setFirstReleaseDate(1431993600L);
        dto.setCover(cover);
        dto.setGenres(List.of(genre));
        dto.setKeywords(List.of(keyword));
        dto.setThemes(List.of(theme));
        dto.setPlatforms(List.of(platform));

        return dto;
    }
}
