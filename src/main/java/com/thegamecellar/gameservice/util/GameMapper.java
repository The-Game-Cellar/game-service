package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.rawg.RawgGameDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.GameGenre;
import com.thegamecellar.gameservice.model.entity.GamePlatform;
import com.thegamecellar.gameservice.model.entity.GameTag;

import java.util.Collections;
import java.util.List;

public class GameMapper {

    public static Game toEntity(RawgGameDto dto) {
        Game game = new Game();
        game.setRawgId(dto.getId());
        game.setName(dto.getName());
        game.setDescription(dto.getDescriptionRaw());
        game.setRating(dto.getRating());
        game.setBackgroundImage(dto.getBackgroundImage());
        game.setReleased(dto.getReleased());

        if (dto.getEsrbRating() != null) {
            game.setEsrbRating(dto.getEsrbRating().getName());
        }
        if (dto.getDevelopers() != null) {
            String devNames = dto.getDevelopers().stream()
                    .map(d -> d.getName())
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
            game.setDevelopers(devNames);
        }

        return game;
    }

    public static List<GameGenre> toGenreEntities(RawgGameDto dto, Game game) {
        if (dto.getGenres() == null) return Collections.emptyList();
        return dto.getGenres().stream()
                .map(g -> {
                    GameGenre genre = new GameGenre();
                    genre.setGame(game);
                    genre.setGenreName(g.getName());
                    return genre;
                })
                .toList();
    }

    public static List<GamePlatform> toPlatformEntities(RawgGameDto dto, Game game) {
        if (dto.getPlatforms() == null) return Collections.emptyList();
        return dto.getPlatforms().stream()
                .filter(entry -> entry.getPlatform() != null)
                .map(entry -> {
                    GamePlatform platform = new GamePlatform();
                    platform.setGame(game);
                    platform.setPlatformName(entry.getPlatform().getName());
                    return platform;
                })
                .toList();
    }

    public static List<GameTag> toTagEntities(RawgGameDto dto, Game game) {
        if (dto.getTags() == null) return Collections.emptyList();
        return dto.getTags().stream()
                .map(t -> {
                    GameTag tag = new GameTag();
                    tag.setGame(game);
                    tag.setTagName(t.getName());
                    return tag;
                })
                .toList();
    }

    public static GameResponse toResponse(Game game) {
        List<String> genres = game.getGenres().stream()
                .map(GameGenre::getGenreName)
                .toList();

        List<String> platforms = game.getPlatforms().stream()
                .map(GamePlatform::getPlatformName)
                .toList();

        List<String> developers = game.getDevelopers() != null
                ? List.of(game.getDevelopers().split(","))
                : Collections.emptyList();

        List<String> tags = game.getTags().stream()
                .map(GameTag::getTagName)
                .toList();

        List<String> moods = MoodMapper.getMoods(tags, genres);

        return GameResponse.builder()
                .rawgId(game.getRawgId())
                .name(game.getName())
                .description(game.getDescription())
                .rating(game.getRating())
                .backgroundImage(game.getBackgroundImage())
                .released(game.getReleased())
                .esrbRating(game.getEsrbRating())
                .genres(genres)
                .platforms(platforms)
                .developers(developers)
                .tags(tags)
                .moods(moods)
                .build();
    }

    public static GameResponse toResponseFromRawg(RawgGameDto dto) {
        List<String> genres = dto.getGenres() != null
                ? dto.getGenres().stream().map(g -> g.getName()).toList()
                : Collections.emptyList();

        List<String> platforms = dto.getPlatforms() != null
                ? dto.getPlatforms().stream()
                        .filter(e -> e.getPlatform() != null)
                        .map(e -> e.getPlatform().getName())
                        .toList()
                : Collections.emptyList();

        List<String> developers = dto.getDevelopers() != null
                ? dto.getDevelopers().stream().map(d -> d.getName()).toList()
                : Collections.emptyList();

        List<String> tags = dto.getTags() != null
                ? dto.getTags().stream().map(t -> t.getName()).toList()
                : Collections.emptyList();

        List<String> moods = MoodMapper.getMoods(tags, genres);

        return GameResponse.builder()
                .rawgId(dto.getId())
                .name(dto.getName())
                .description(dto.getDescriptionRaw())
                .rating(dto.getRating())
                .backgroundImage(dto.getBackgroundImage())
                .released(dto.getReleased())
                .esrbRating(dto.getEsrbRating() != null ? dto.getEsrbRating().getName() : null)
                .genres(genres)
                .platforms(platforms)
                .developers(developers)
                .tags(tags)
                .moods(moods)
                .build();
    }
}