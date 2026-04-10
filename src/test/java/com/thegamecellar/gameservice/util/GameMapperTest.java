package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.rawg.RawgGameDto;
import com.thegamecellar.gameservice.model.dto.rawg.RawgNamedEntity;
import com.thegamecellar.gameservice.model.dto.rawg.RawgPlatformEntry;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.GameGenre;
import com.thegamecellar.gameservice.model.entity.GamePlatform;
import com.thegamecellar.gameservice.model.entity.GameTag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class
GameMapperTest {

    // --- toEntity ---

    @Test
    void shouldMapRawgDtoToEntity() {
        RawgGameDto dto = buildDto();

        Game entity = GameMapper.toEntity(dto);

        assertThat(entity.getRawgId()).isEqualTo(3328);
        assertThat(entity.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        assertThat(entity.getRating()).isEqualByComparingTo(new BigDecimal("4.66"));
    }

    @Test
    void shouldJoinMultipleDevelopersWithComma() {
        RawgGameDto dto = new RawgGameDto();
        dto.setId(1);
        dto.setName("Game");

        RawgNamedEntity dev1 = new RawgNamedEntity();
        dev1.setName("Studio A");
        RawgNamedEntity dev2 = new RawgNamedEntity();
        dev2.setName("Studio B");
        dto.setDevelopers(List.of(dev1, dev2));

        Game entity = GameMapper.toEntity(dto);

        assertThat(entity.getDevelopers()).isEqualTo("Studio A,Studio B");
    }

    @Test
    void shouldHandleNullDevelopers() {
        RawgGameDto dto = new RawgGameDto();
        dto.setId(1);
        dto.setName("Game");
        dto.setDevelopers(null);

        Game entity = GameMapper.toEntity(dto);

        assertThat(entity.getDevelopers()).isNull();
    }

    // --- toGenreEntities ---

    @Test
    void shouldMapGenresToEntities() {
        RawgGameDto dto = buildDto();
        Game game = new Game();

        List<GameGenre> genres = GameMapper.toGenreEntities(dto, game);

        assertThat(genres).hasSize(1);
        assertThat(genres.get(0).getGenreName()).isEqualTo("RPG");
        assertThat(genres.get(0).getGame()).isSameAs(game);
    }

    @Test
    void shouldReturnEmptyListForNullGenres() {
        RawgGameDto dto = new RawgGameDto();
        dto.setGenres(null);

        List<GameGenre> genres = GameMapper.toGenreEntities(dto, new Game());

        assertThat(genres).isEmpty();
    }

    // --- toTagEntities ---

    @Test
    void shouldMapTagsToEntities() {
        RawgGameDto dto = buildDto();
        Game game = new Game();

        List<GameTag> tags = GameMapper.toTagEntities(dto, game);

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).getTagName()).isEqualTo("Story Rich");
        assertThat(tags.get(0).getGame()).isSameAs(game);
    }

    @Test
    void shouldReturnEmptyListForNullTags() {
        RawgGameDto dto = new RawgGameDto();
        dto.setTags(null);

        List<GameTag> tags = GameMapper.toTagEntities(dto, new Game());

        assertThat(tags).isEmpty();
    }

    // --- toPlatformEntities ---

    @Test
    void shouldSkipPlatformEntriesWithNullPlatform() {
        RawgGameDto dto = new RawgGameDto();
        RawgPlatformEntry entry = new RawgPlatformEntry();
        entry.setPlatform(null); // null inner platform
        dto.setPlatforms(List.of(entry));

        List<GamePlatform> platforms = GameMapper.toPlatformEntities(dto, new Game());

        assertThat(platforms).isEmpty();
    }

    // --- toResponse (from entity) ---

    @Test
    void shouldMapEntityToResponse() {
        GameTag tag = new GameTag();
        tag.setTagName("Story Rich");

        GameGenre genre = new GameGenre();
        genre.setGenreName("RPG");

        GamePlatform platform = new GamePlatform();
        platform.setPlatformName("PC");

        Game game = Game.builder()
                .rawgId(3328)
                .name("The Witcher 3: Wild Hunt")
                .rating(new BigDecimal("4.66"))
                .developers("CD PROJEKT RED")
                .tags(new ArrayList<>(List.of(tag)))
                .genres(new ArrayList<>(List.of(genre)))
                .platforms(new ArrayList<>(List.of(platform)))
                .build();

        GameResponse response = GameMapper.toResponse(game);

        assertThat(response.getRawgId()).isEqualTo(3328);
        assertThat(response.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        assertThat(response.getGenres()).containsExactly("RPG");
        assertThat(response.getPlatforms()).containsExactly("PC");
        assertThat(response.getTags()).containsExactly("Story Rich");
        assertThat(response.getDevelopers()).containsExactly("CD PROJEKT RED");
        assertThat(response.getMoods()).contains("Story-driven");
    }

    @Test
    void shouldReturnEmptyDevelopersListWhenDevelopersIsNull() {
        Game game = Game.builder()
                .rawgId(1)
                .name("Game")
                .developers(null)
                .genres(new ArrayList<>())
                .platforms(new ArrayList<>())
                .tags(new ArrayList<>())
                .build();

        GameResponse response = GameMapper.toResponse(game);

        assertThat(response.getDevelopers()).isEmpty();
    }

    // --- toResponseFromRawg ---

    @Test
    void shouldMapRawgDtoDirectlyToResponse() {
        RawgGameDto dto = buildDto();

        GameResponse response = GameMapper.toResponseFromRawg(dto);

        assertThat(response.getRawgId()).isEqualTo(3328);
        assertThat(response.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        assertThat(response.getGenres()).containsExactly("RPG");
        assertThat(response.getTags()).containsExactly("Story Rich");
        assertThat(response.getMoods()).contains("Story-driven");
    }

    @Test
    void shouldHandleNullFieldsInRawgDtoResponse() {
        RawgGameDto dto = new RawgGameDto();
        dto.setId(1);
        dto.setName("Minimal Game");

        GameResponse response = GameMapper.toResponseFromRawg(dto);

        assertThat(response.getGenres()).isEmpty();
        assertThat(response.getPlatforms()).isEmpty();
        assertThat(response.getTags()).isEmpty();
        assertThat(response.getDevelopers()).isEmpty();
        assertThat(response.getMoods()).isEmpty();
    }

    // --- helpers ---

    private RawgGameDto buildDto() {
        RawgNamedEntity genre = new RawgNamedEntity();
        genre.setName("RPG");

        RawgNamedEntity tag = new RawgNamedEntity();
        tag.setName("Story Rich");

        RawgNamedEntity dev = new RawgNamedEntity();
        dev.setName("CD PROJEKT RED");

        RawgGameDto dto = new RawgGameDto();
        dto.setId(3328);
        dto.setName("The Witcher 3: Wild Hunt");
        dto.setRating(new BigDecimal("4.66"));
        dto.setGenres(List.of(genre));
        dto.setTags(List.of(tag));
        dto.setDevelopers(List.of(dev));
        dto.setPlatforms(List.of());

        return dto;
    }
}