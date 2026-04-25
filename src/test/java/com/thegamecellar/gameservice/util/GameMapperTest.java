package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbCoverDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbNamedEntityDto;
import com.thegamecellar.gameservice.model.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameMapperTest {

    // ── IGDB → entity ────────────────────────────────────────────────────────

    @Test
    void shouldMapIgdbDtoToEntity() {
        IgdbGameDto dto = buildIgdbDto();

        Game entity = GameMapper.toEntity(dto);

        assertThat(entity.getIgdbId()).isEqualTo(1942);
        assertThat(entity.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        assertThat(entity.getDescription()).isEqualTo("A story-driven RPG.");
        assertThat(entity.getRating()).isEqualByComparingTo(new BigDecimal("4.70"));
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

    @Test
    void shouldMapIgdbGenresToEntities() {
        IgdbGameDto dto = buildIgdbDto();
        Game game = new Game();

        List<GameGenre> genres = GameMapper.toGenreEntities(dto, game);

        assertThat(genres).hasSize(1);
        assertThat(genres.get(0).getGenreName()).isEqualTo("Role-playing (RPG)");
        assertThat(genres.get(0).getGame()).isSameAs(game);
    }

    @Test
    void shouldMapIgdbKeywordsToTagEntities() {
        IgdbGameDto dto = buildIgdbDto();
        Game game = new Game();

        List<GameTag> tags = GameMapper.toTagEntities(dto, game);

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).getTagName()).isEqualTo("story rich");
        assertThat(tags.get(0).getGame()).isSameAs(game);
    }

    @Test
    void shouldMapIgdbThemesToEntities() {
        IgdbGameDto dto = buildIgdbDto();
        Game game = new Game();

        List<GameTheme> themes = GameMapper.toThemeEntities(dto, game);

        assertThat(themes).hasSize(1);
        assertThat(themes.get(0).getThemeName()).isEqualTo("Fantasy");
        assertThat(themes.get(0).getGame()).isSameAs(game);
    }

    @Test
    void shouldReturnEmptyListForNullIgdbCollections() {
        IgdbGameDto dto = new IgdbGameDto();

        assertThat(GameMapper.toGenreEntities(dto, new Game())).isEmpty();
        assertThat(GameMapper.toTagEntities(dto, new Game())).isEmpty();
        assertThat(GameMapper.toThemeEntities(dto, new Game())).isEmpty();
        assertThat(GameMapper.toPlatformEntities(dto, new Game())).isEmpty();
    }

    // ── entity → response ────────────────────────────────────────────────────

    @Test
    void shouldMapEntityToResponse() {
        GameTag tag = new GameTag();
        tag.setTagName("Story Rich");

        GameGenre genre = new GameGenre();
        genre.setGenreName("RPG");

        GamePlatform platform = new GamePlatform();
        platform.setPlatformName("PC");

        GameTheme theme = new GameTheme();
        theme.setThemeName("Fantasy");

        Game game = Game.builder()
                .igdbId(1942)
                .name("The Witcher 3: Wild Hunt")
                .rating(new BigDecimal("4.66"))
                .coverImageId("abc123")
                .developers("CD PROJEKT RED")
                .tags(new ArrayList<>(List.of(tag)))
                .genres(new ArrayList<>(List.of(genre)))
                .platforms(new ArrayList<>(List.of(platform)))
                .themes(new ArrayList<>(List.of(theme)))
                .build();

        GameResponse response = GameMapper.toResponse(game);

        assertThat(response.getIgdbId()).isEqualTo(1942);
        assertThat(response.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        assertThat(response.getGenres()).containsExactly("RPG");
        assertThat(response.getPlatforms()).containsExactly("PC");
        assertThat(response.getTags()).containsExactly("Story Rich");
        assertThat(response.getThemes()).containsExactly("Fantasy");
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
                .genres(new ArrayList<>())
                .platforms(new ArrayList<>())
                .tags(new ArrayList<>())
                .themes(new ArrayList<>())
                .build();

        GameResponse response = GameMapper.toResponse(game);

        assertThat(response.getDevelopers()).isEmpty();
    }

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
