package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbInvolvedCompanyDto;
import com.thegamecellar.gameservice.model.entity.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class GameMapper {

    private static final String IGDB_COVER_URL_TEMPLATE =
            "https://images.igdb.com/igdb/image/upload/t_cover_big/%s.jpg";

    // ── IGDB → entity ────────────────────────────────────────────────────────

    public static Game toEntity(IgdbGameDto dto) {
        Game game = new Game();
        game.setIgdbId(dto.getId());
        game.setName(dto.getName());
        game.setDescription(dto.getSummary());

        if (dto.getAggregatedRating() != null) {
            // IGDB scale is 0–100; normalize to 0–5
            game.setRating(BigDecimal.valueOf(dto.getAggregatedRating() / 20.0)
                    .setScale(2, RoundingMode.HALF_UP));
        }

        if (dto.getCover() != null && dto.getCover().getImageId() != null) {
            game.setCoverImageId(dto.getCover().getImageId());
        }

        if (dto.getFirstReleaseDate() != null) {
            game.setReleased(Instant.ofEpochSecond(dto.getFirstReleaseDate())
                    .atZone(ZoneId.of("UTC"))
                    .toLocalDate()
                    .toString());
        }

        if (dto.getInvolvedCompanies() != null) {
            String devNames = dto.getInvolvedCompanies().stream()
                    .filter(ic -> ic.isDeveloper() && ic.getCompany() != null)
                    .map(ic -> ic.getCompany().getName())
                    .filter(Objects::nonNull)
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
            game.setDevelopers(devNames);
        }

        return game;
    }

    public static List<GameGenre> toGenreEntities(IgdbGameDto dto, Game game) {
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

    public static List<GamePlatform> toPlatformEntities(IgdbGameDto dto, Game game) {
        if (dto.getPlatforms() == null) return Collections.emptyList();
        return dto.getPlatforms().stream()
                .map(p -> {
                    GamePlatform platform = new GamePlatform();
                    platform.setGame(game);
                    platform.setPlatformName(p.getName());
                    return platform;
                })
                .toList();
    }

    public static List<GameTag> toTagEntities(IgdbGameDto dto, Game game) {
        if (dto.getKeywords() == null) return Collections.emptyList();
        return dto.getKeywords().stream()
                .map(k -> {
                    GameTag tag = new GameTag();
                    tag.setGame(game);
                    tag.setTagName(k.getName());
                    return tag;
                })
                .toList();
    }

    public static List<GameTheme> toThemeEntities(IgdbGameDto dto, Game game) {
        if (dto.getThemes() == null) return Collections.emptyList();
        return dto.getThemes().stream()
                .map(t -> {
                    GameTheme theme = new GameTheme();
                    theme.setGame(game);
                    theme.setThemeName(t.getName());
                    return theme;
                })
                .toList();
    }

    // ── entity → response ────────────────────────────────────────────────────

    public static GameResponse toResponse(Game game) {
        List<String> genres = game.getGenres().stream()
                .map(GameGenre::getGenreName)
                .toList();
        List<String> platforms = game.getPlatforms().stream()
                .map(GamePlatform::getPlatformName)
                .toList();
        List<String> tags = game.getTags().stream()
                .map(GameTag::getTagName)
                .toList();
        List<String> themes = game.getThemes().stream()
                .map(GameTheme::getThemeName)
                .toList();
        List<String> developers = game.getDevelopers() != null
                ? List.of(game.getDevelopers().split(","))
                : Collections.emptyList();

        String coverImageUrl = game.getCoverImageId() != null
                ? String.format(IGDB_COVER_URL_TEMPLATE, game.getCoverImageId())
                : null;

        return GameResponse.builder()
                .igdbId(game.getIgdbId())
                .name(game.getName())
                .description(game.getDescription())
                .rating(game.getRating())
                .backgroundImage(game.getBackgroundImage() != null ? game.getBackgroundImage() : coverImageUrl)
                .coverImageUrl(coverImageUrl)
                .released(game.getReleased())
                .esrbRating(game.getEsrbRating())
                .genres(genres)
                .platforms(platforms)
                .developers(developers)
                .tags(tags)
                .themes(themes)
                .moods(MoodMapper.getMoods(tags, genres, themes))
                .build();
    }

    public static GameResponse toResponseFromIgdb(IgdbGameDto dto) {
        List<String> genres = dto.getGenres() != null
                ? dto.getGenres().stream().map(g -> g.getName()).toList()
                : Collections.emptyList();
        List<String> platforms = dto.getPlatforms() != null
                ? dto.getPlatforms().stream().map(p -> p.getName()).toList()
                : Collections.emptyList();
        List<String> tags = dto.getKeywords() != null
                ? dto.getKeywords().stream().map(k -> k.getName()).toList()
                : Collections.emptyList();
        List<String> themes = dto.getThemes() != null
                ? dto.getThemes().stream().map(t -> t.getName()).toList()
                : Collections.emptyList();
        List<String> developers = dto.getInvolvedCompanies() != null
                ? dto.getInvolvedCompanies().stream()
                        .filter(ic -> ic.isDeveloper() && ic.getCompany() != null)
                        .map(ic -> ic.getCompany().getName())
                        .filter(Objects::nonNull)
                        .toList()
                : Collections.emptyList();

        BigDecimal rating = dto.getAggregatedRating() != null
                ? BigDecimal.valueOf(dto.getAggregatedRating() / 20.0).setScale(2, RoundingMode.HALF_UP)
                : null;

        String released = dto.getFirstReleaseDate() != null
                ? Instant.ofEpochSecond(dto.getFirstReleaseDate())
                        .atZone(ZoneId.of("UTC"))
                        .toLocalDate()
                        .toString()
                : null;

        String coverImageUrl = dto.getCover() != null ? dto.getCover().toUrl() : null;

        return GameResponse.builder()
                .igdbId(dto.getId())
                .name(dto.getName())
                .description(dto.getSummary())
                .rating(rating)
                .backgroundImage(coverImageUrl)
                .coverImageUrl(coverImageUrl)
                .released(released)
                .genres(genres)
                .platforms(platforms)
                .tags(tags)
                .themes(themes)
                .developers(developers)
                .moods(MoodMapper.getMoods(tags, genres, themes))
                .build();
    }
}
