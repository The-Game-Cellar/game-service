package com.thegamecellar.gameservice.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbAgeRatingDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbMultiplayerModeDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbReleaseDateDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbScreenshotDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbVideoDto;
import com.thegamecellar.gameservice.model.entity.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GameMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String IGDB_COVER_URL_TEMPLATE =
            "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/%s.jpg";
    private static final String IGDB_SCREENSHOT_URL_TEMPLATE =
            "https://images.igdb.com/igdb/image/upload/t_screenshot_big/%s.jpg";

    // IGDB DTO -> Game entity, scalar fields only. Collections resolved in GameCacheService.cacheGame.
    public static Game toEntity(IgdbGameDto dto) {
        Game game = new Game();
        game.setIgdbId(dto.getId());
        game.setName(dto.getName());
        game.setDescription(dto.getSummary());
        game.setStoryline(dto.getStoryline());

        if (dto.getAggregatedRating() != null) {
            game.setRating(normalizeRating(dto.getAggregatedRating()));
        }
        game.setRatingCount(dto.getAggregatedRatingCount());

        if (dto.getTotalRating() != null) {
            game.setTotalRating(normalizeRating(dto.getTotalRating()));
        }
        game.setTotalRatingCount(dto.getTotalRatingCount());

        game.setCategory(dto.getCategory());
        if (dto.getParentGame() != null) {
            game.setParentGameId(dto.getParentGame().getId());
            game.setParentGameName(dto.getParentGame().getName());
        }

        if (dto.getCover() != null && dto.getCover().getImageId() != null) {
            game.setCoverImageId(dto.getCover().getImageId());
        }

        if (dto.getFirstReleaseDate() != null) {
            game.setFirstReleaseDate(dto.getFirstReleaseDate());
            game.setReleased(Instant.ofEpochSecond(dto.getFirstReleaseDate())
                    .atZone(ZoneId.of("UTC"))
                    .toLocalDate()
                    .toString());
        }

        game.setHypes(dto.getHypes());

        game.setDevelopers(extractDevelopers(dto));

        return game;
    }

    // IGDB scale is 0-100; normalise to 0-10 so it matches igdb.com display + user_games.rating axis.
    public static BigDecimal normalizeRating(double igdbRating) {
        return BigDecimal.valueOf(igdbRating / 10.0).setScale(2, RoundingMode.HALF_UP);
    }

    public static String extractDevelopers(IgdbGameDto dto) {
        if (dto.getInvolvedCompanies() == null) return null;
        return dto.getInvolvedCompanies().stream()
                .filter(ic -> ic.isDeveloper() && ic.getCompany() != null)
                .map(ic -> ic.getCompany().getName())
                .filter(Objects::nonNull)
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }

    public static GameResponse toResponse(Game game) {
        List<String> genres = game.getGenres().stream().map(Genre::getName).toList();
        List<String> platforms = game.getPlatforms().stream().map(Platform::getName).toList();
        List<String> tags = game.getTags().stream().map(Tag::getName).toList();
        List<String> themes = game.getThemes().stream().map(Theme::getName).toList();
        List<String> gameModes = game.getGameModes().stream().map(GameMode::getName).toList();
        List<String> playerPerspectives = game.getPlayerPerspectives().stream().map(PlayerPerspective::getName).toList();
        List<String> franchises = game.getFranchises().stream().map(Franchise::getName).toList();
        List<String> collections = game.getCollections().stream().map(GameCollection::getName).toList();
        List<String> developers = game.getDevelopers() != null
                ? List.of(game.getDevelopers().split(","))
                : Collections.emptyList();

        String coverImageUrl = game.getCoverImageId() != null
                ? String.format(IGDB_COVER_URL_TEMPLATE, game.getCoverImageId())
                : null;

        List<String> screenshotUrls = readJson(game.getScreenshots(), new TypeReference<List<String>>() {})
                .stream()
                .map(id -> String.format(IGDB_SCREENSHOT_URL_TEMPLATE, id))
                .toList();
        List<String> videoIds = readJson(game.getVideos(), new TypeReference<List<String>>() {});
        List<Integer> dlcIds = readJson(game.getDlcIds(), new TypeReference<List<Integer>>() {});
        List<Integer> expansionIds = readJson(game.getExpansionIds(), new TypeReference<List<Integer>>() {});
        List<Integer> similarGameIds = readJson(game.getSimilarGameIds(), new TypeReference<List<Integer>>() {});

        List<GameResponse.AgeRatingDTO> ageRatings = readJson(game.getAgeRatings(),
                new TypeReference<List<Map<String, Object>>>() {})
                .stream()
                .map(row -> {
                    Integer cat = asInteger(row.get("category"));
                    Integer rat = asInteger(row.get("rating"));
                    return GameResponse.AgeRatingDTO.builder()
                            .category(cat)
                            .rating(rat)
                            .body(AgeRatingMapper.body(cat))
                            .label(AgeRatingMapper.label(cat, rat))
                            .build();
                })
                .toList();

        List<GameResponse.ReleaseDateDTO> releaseDates = readJson(game.getReleaseDates(),
                new TypeReference<List<Map<String, Object>>>() {})
                .stream()
                .map(row -> GameResponse.ReleaseDateDTO.builder()
                        .platform(asString(row.get("platform")))
                        .date(asString(row.get("date")))
                        .human(asString(row.get("human")))
                        .build())
                .toList();

        List<GameResponse.MultiplayerModeDTO> multiplayerModes = readJson(game.getMultiplayerModes(),
                new TypeReference<List<Map<String, Object>>>() {})
                .stream()
                .map(row -> GameResponse.MultiplayerModeDTO.builder()
                        .platform(asString(row.get("platform")))
                        .onlineMax(asInteger(row.get("onlineMax")))
                        .offlineMax(asInteger(row.get("offlineMax")))
                        .onlineCoopMax(asInteger(row.get("onlineCoopMax")))
                        .offlineCoopMax(asInteger(row.get("offlineCoopMax")))
                        .lanCoop(asBoolean(row.get("lanCoop")))
                        .splitscreen(asBoolean(row.get("splitscreen")))
                        .campaignCoop(asBoolean(row.get("campaignCoop")))
                        .dropIn(asBoolean(row.get("dropIn")))
                        .build())
                .toList();

        return GameResponse.builder()
                .igdbId(game.getIgdbId())
                .name(game.getName())
                .description(game.getDescription())
                .storyline(game.getStoryline())
                .rating(game.getRating())
                .ratingCount(game.getRatingCount())
                .totalRating(game.getTotalRating())
                .totalRatingCount(game.getTotalRatingCount())
                .backgroundImage(game.getBackgroundImage() != null ? game.getBackgroundImage() : coverImageUrl)
                .coverImageUrl(coverImageUrl)
                .released(game.getReleased())
                .firstReleaseDate(game.getFirstReleaseDate())
                .hypes(game.getHypes())
                .esrbRating(game.getEsrbRating())
                .category(game.getCategory())
                .parentGameId(game.getParentGameId())
                .parentGameName(game.getParentGameName())
                .genres(genres)
                .platforms(platforms)
                .developers(developers)
                .tags(tags)
                .themes(themes)
                .gameModes(gameModes)
                .playerPerspectives(playerPerspectives)
                .franchises(franchises)
                .collections(collections)
                .screenshotUrls(screenshotUrls)
                .videoIds(videoIds)
                .dlcIds(dlcIds)
                .expansionIds(expansionIds)
                .similarGameIds(similarGameIds)
                .ageRatings(ageRatings)
                .releaseDates(releaseDates)
                .multiplayerModes(multiplayerModes)
                .build();
    }

    // Bypasses the cache; used when surfacing live IGDB rows that aren't persisted yet.
    public static GameResponse toResponseFromIgdb(IgdbGameDto dto) {
        List<String> genres = nameList(dto.getGenres());
        List<String> platforms = nameList(dto.getPlatforms());
        List<String> tags = nameList(dto.getKeywords());
        List<String> themes = nameList(dto.getThemes());
        List<String> gameModes = nameList(dto.getGameModes());
        List<String> playerPerspectives = nameList(dto.getPlayerPerspectives());
        List<String> franchises = nameList(dto.getFranchises());
        List<String> collections = nameList(dto.getCollections());
        String devString = extractDevelopers(dto);
        List<String> developers = devString != null ? List.of(devString.split(",")) : Collections.emptyList();

        BigDecimal rating = dto.getAggregatedRating() != null ? normalizeRating(dto.getAggregatedRating()) : null;
        BigDecimal totalRating = dto.getTotalRating() != null ? normalizeRating(dto.getTotalRating()) : null;

        String released = dto.getFirstReleaseDate() != null
                ? Instant.ofEpochSecond(dto.getFirstReleaseDate())
                        .atZone(ZoneId.of("UTC"))
                        .toLocalDate()
                        .toString()
                : null;

        String coverImageUrl = dto.getCover() != null ? dto.getCover().toUrl() : null;

        List<String> screenshotUrls = dto.getScreenshots() == null ? Collections.emptyList()
                : dto.getScreenshots().stream()
                        .map(IgdbScreenshotDto::getImageId)
                        .filter(Objects::nonNull)
                        .map(id -> String.format(IGDB_SCREENSHOT_URL_TEMPLATE, id))
                        .toList();

        List<String> videoIds = dto.getVideos() == null ? Collections.emptyList()
                : dto.getVideos().stream()
                        .map(IgdbVideoDto::getVideoId)
                        .filter(Objects::nonNull)
                        .toList();

        List<Integer> dlcIds = dto.getDlcs() != null ? new ArrayList<>(dto.getDlcs()) : Collections.emptyList();
        List<Integer> expansionIds = dto.getExpansions() != null ? new ArrayList<>(dto.getExpansions()) : Collections.emptyList();
        List<Integer> similarGameIds = dto.getSimilarGames() != null ? new ArrayList<>(dto.getSimilarGames()) : Collections.emptyList();

        List<GameResponse.AgeRatingDTO> ageRatings = dto.getAgeRatings() == null ? Collections.emptyList()
                : dto.getAgeRatings().stream()
                        .map(a -> GameResponse.AgeRatingDTO.builder()
                                .category(a.getCategory())
                                .rating(a.getRating())
                                .body(AgeRatingMapper.body(a.getCategory()))
                                .label(AgeRatingMapper.label(a.getCategory(), a.getRating()))
                                .build())
                        .toList();

        List<GameResponse.ReleaseDateDTO> releaseDates = dto.getReleaseDates() == null ? Collections.emptyList()
                : dto.getReleaseDates().stream()
                        .map(r -> GameResponse.ReleaseDateDTO.builder()
                                .platform(r.getPlatform() != null ? IgdbPlatformMapper.normalize(r.getPlatform().getName()) : null)
                                .human(r.getHuman())
                                .date(r.getDate() != null
                                        ? Instant.ofEpochSecond(r.getDate()).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                                        : null)
                                .build())
                        .toList();

        List<GameResponse.MultiplayerModeDTO> multiplayerModes = dto.getMultiplayerModes() == null ? Collections.emptyList()
                : dto.getMultiplayerModes().stream()
                        .map(m -> GameResponse.MultiplayerModeDTO.builder()
                                .platform(m.getPlatform() != null ? IgdbPlatformMapper.normalize(m.getPlatform().getName()) : null)
                                .onlineMax(m.getOnlineMax())
                                .offlineMax(m.getOfflineMax())
                                .onlineCoopMax(m.getOnlineCoopMax())
                                .offlineCoopMax(m.getOfflineCoopMax())
                                .lanCoop(m.getLanCoop())
                                .splitscreen(m.getSplitscreen())
                                .campaignCoop(m.getCampaignCoop())
                                .dropIn(m.getDropIn())
                                .build())
                        .toList();

        Integer parentGameId = dto.getParentGame() != null ? dto.getParentGame().getId() : null;
        String parentGameName = dto.getParentGame() != null ? dto.getParentGame().getName() : null;

        return GameResponse.builder()
                .igdbId(dto.getId())
                .name(dto.getName())
                .description(dto.getSummary())
                .storyline(dto.getStoryline())
                .rating(rating)
                .ratingCount(dto.getAggregatedRatingCount())
                .totalRating(totalRating)
                .totalRatingCount(dto.getTotalRatingCount())
                .backgroundImage(coverImageUrl)
                .coverImageUrl(coverImageUrl)
                .released(released)
                .firstReleaseDate(dto.getFirstReleaseDate())
                .hypes(dto.getHypes())
                .category(dto.getCategory())
                .parentGameId(parentGameId)
                .parentGameName(parentGameName)
                .genres(genres)
                .platforms(platforms)
                .tags(tags)
                .themes(themes)
                .gameModes(gameModes)
                .playerPerspectives(playerPerspectives)
                .franchises(franchises)
                .collections(collections)
                .developers(developers)
                .screenshotUrls(screenshotUrls)
                .videoIds(videoIds)
                .dlcIds(dlcIds)
                .expansionIds(expansionIds)
                .similarGameIds(similarGameIds)
                .ageRatings(ageRatings)
                .releaseDates(releaseDates)
                .multiplayerModes(multiplayerModes)
                .build();
    }

    private static List<String> nameList(List<com.thegamecellar.gameservice.model.dto.igdb.IgdbNamedEntityDto> source) {
        return source != null
                ? source.stream().map(com.thegamecellar.gameservice.model.dto.igdb.IgdbNamedEntityDto::getName).toList()
                : Collections.emptyList();
    }

    private static <T> T readJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            try {
                return JSON.readValue("[]", typeRef);
            } catch (Exception e) {
                throw new IllegalStateException("readValue([]) failed", e);
            }
        }
        try {
            return JSON.readValue(json, typeRef);
        } catch (Exception e) {
            try {
                return JSON.readValue("[]", typeRef);
            } catch (Exception inner) {
                throw new IllegalStateException("readValue([]) failed", inner);
            }
        }
    }

    private static Integer asInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Boolean asBoolean(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(o.toString());
    }
}
