package com.thegamecellar.gameservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbAgeRatingDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbMultiplayerModeDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbReleaseDateDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbScreenshotDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbVideoDto;
import com.thegamecellar.gameservice.model.entity.*;
import com.thegamecellar.gameservice.repository.*;
import com.thegamecellar.gameservice.util.CuratedTagAllowlist;
import com.thegamecellar.gameservice.util.DerivedGenreEngine;
import com.thegamecellar.gameservice.util.GameMapper;
import com.thegamecellar.gameservice.util.IgdbPlatformMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameCacheService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** IGDB tag noise filter prefixes. Tags starting with these are dropped at ingest. */
    private static final List<String> NOISE_TAG_PREFIXES = List.of(
            "released-on-", "released on ", "available-on-", "available on ",
            "exclusive-to-", "exclusive to "
    );

    /** Minimum tag length. Below this is almost always noise. */
    private static final int TAG_MIN_LENGTH = 3;

    private final GameRepository gameRepository;
    private final PlatformRepository platformRepository;
    private final GenreRepository genreRepository;
    private final TagRepository tagRepository;
    private final ThemeRepository themeRepository;
    private final GameModeRepository gameModeRepository;
    private final PlayerPerspectiveRepository playerPerspectiveRepository;
    private final FranchiseRepository franchiseRepository;
    private final GameCollectionRepository gameCollectionRepository;
    private final CuratedTagAllowlist curatedTagAllowlist;
    private final DerivedGenreEngine derivedGenreEngine;

    /**
     * Caches the game if not already present. Each call runs in its own
     * transaction so a concurrent insert collision only rolls back this one
     * game, not the caller's broader transaction.
     *
     * @return true if the game was newly inserted, false if it already existed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cacheIfAbsent(IgdbGameDto dto) {
        Optional<Game> existing = gameRepository.findByIgdbId(dto.getId());
        if (existing.isPresent()) {
            // When search/popular returns a game we cached earlier with partial
            // data, use the fresh DTO to backfill, no extra IGDB roundtrip needed.
            Game game = existing.get();
            if (isStale(game)) {
                try {
                    refreshStaleGame(game, dto);
                } catch (Exception e) {
                    log.debug("Stale-refresh on cacheIfAbsent failed for igdbId={}: {}", dto.getId(), e.getMessage());
                }
            }
            return false;
        }
        try {
            cacheGame(dto);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Game igdbId={} already cached by concurrent request", dto.getId());
            return false;
        }
    }

    @Transactional
    public Game cacheGame(IgdbGameDto dto) {
        Game game = GameMapper.toEntity(dto);
        applyAssociations(game, dto);
        applyDerivedGenres(game);
        applyOwnedJsonFields(game, dto);
        return gameRepository.save(game);
    }

    @Transactional
    public Game refreshStaleGame(Game game, IgdbGameDto dto) {
        if (game.getTags().isEmpty()) {
            game.getTags().addAll(resolveTags(dto));
        }
        if (game.getGenres().isEmpty()) {
            game.getGenres().addAll(resolveGenres(dto));
        }
        if (game.getThemes().isEmpty()) {
            game.getThemes().addAll(resolveThemes(dto));
        }
        if (game.getGameModes().isEmpty()) {
            game.getGameModes().addAll(resolveGameModes(dto));
        }
        if (game.getPlayerPerspectives().isEmpty()) {
            game.getPlayerPerspectives().addAll(resolvePlayerPerspectives(dto));
        }
        if (game.getFranchises().isEmpty()) {
            game.getFranchises().addAll(resolveFranchises(dto));
        }
        if (game.getCollections().isEmpty()) {
            game.getCollections().addAll(resolveCollections(dto));
        }
        if (game.getDescription() == null || game.getDescription().isBlank()) {
            game.setDescription(dto.getSummary());
        }
        if (game.getStoryline() == null || game.getStoryline().isBlank()) {
            game.setStoryline(dto.getStoryline());
        }
        if (game.getCoverImageId() == null && dto.getCover() != null) {
            game.setCoverImageId(dto.getCover().getImageId());
        }
        if (game.getDevelopers() == null) {
            game.setDevelopers(GameMapper.extractDevelopers(dto));
        }
        if (game.getCategory() == null) {
            game.setCategory(dto.getCategory());
        }
        if (game.getParentGameId() == null && dto.getParentGame() != null) {
            game.setParentGameId(dto.getParentGame().getId());
            game.setParentGameName(dto.getParentGame().getName());
        }
        if (game.getTotalRating() == null && dto.getTotalRating() != null) {
            game.setTotalRating(GameMapper.normalizeRating(dto.getTotalRating()));
            game.setTotalRatingCount(dto.getTotalRatingCount());
        }
        if (game.getRatingCount() == null) {
            game.setRatingCount(dto.getAggregatedRatingCount());
        }
        if (game.getFirstReleaseDate() == null && dto.getFirstReleaseDate() != null) {
            game.setFirstReleaseDate(dto.getFirstReleaseDate());
        }
        if (game.getHypes() == null && dto.getHypes() != null) {
            game.setHypes(dto.getHypes());
        }
        // JSON-as-TEXT fields, fill if currently null
        if (game.getScreenshots() == null) game.setScreenshots(serializeScreenshots(dto));
        if (game.getVideos() == null) game.setVideos(serializeVideos(dto));
        if (game.getDlcIds() == null) game.setDlcIds(serializeIntList(dto.getDlcs()));
        if (game.getExpansionIds() == null) game.setExpansionIds(serializeIntList(dto.getExpansions()));
        if (game.getSimilarGameIds() == null) game.setSimilarGameIds(serializeIntList(dto.getSimilarGames()));
        if (game.getAgeRatings() == null) game.setAgeRatings(serializeAgeRatings(dto));
        if (game.getReleaseDates() == null) game.setReleaseDates(serializeReleaseDates(dto));
        if (game.getMultiplayerModes() == null) game.setMultiplayerModes(serializeMultiplayerModes(dto));
        applyDerivedGenres(game);
        return gameRepository.save(game);
    }

    /**
     * Force-overwrite refresh for volatile upcoming-release fields. Used by the daily
     * worker pass over rows whose canonical {@code first_release_date} is in the future.
     * dates slip, hype counts move daily, per-platform release plans change, so these
     * fields are re-set unconditionally rather than the fill-if-null behaviour of
     * {@link #refreshStaleGame}.
     */
    @Transactional
    public Game refreshUpcomingGame(Game game, IgdbGameDto dto) {
        game.setFirstReleaseDate(dto.getFirstReleaseDate());
        game.setHypes(dto.getHypes());
        if (dto.getFirstReleaseDate() != null) {
            game.setReleased(java.time.Instant.ofEpochSecond(dto.getFirstReleaseDate())
                    .atZone(java.time.ZoneId.of("UTC"))
                    .toLocalDate()
                    .toString());
        }
        game.setReleaseDates(serializeReleaseDates(dto));
        if (dto.getTotalRating() != null) {
            game.setTotalRating(GameMapper.normalizeRating(dto.getTotalRating()));
            game.setTotalRatingCount(dto.getTotalRatingCount());
        }
        return gameRepository.save(game);
    }

    private boolean isStale(Game game) {
        return game.getTags().isEmpty()
                || game.getGenres().isEmpty()
                || game.getDescription() == null
                || game.getDescription().isBlank()
                || game.getDevelopers() == null
                || game.getCategory() == null
                || game.getRatingCount() == null
                || game.getScreenshots() == null
                || game.getVideos() == null
                || game.getDlcIds() == null
                || game.getExpansionIds() == null
                || game.getSimilarGameIds() == null
                || game.getAgeRatings() == null
                || game.getReleaseDates() == null
                || game.getMultiplayerModes() == null;
    }

    // ── associations ──────────────────────────────────────────────────────────

    /**
     * Re-applies the derived-genre rule set to the game. Replace-pattern: removes any prior
     * {@code source=DERIVED} entries from the join, then re-derives from the game's current
     * tags + themes and adds the new set. Idempotent; re-running with the same rule set is
     * a no-op. Called from both {@link #cacheGame} (new rows) and {@link #refreshStaleGame}
     * (existing rows hitting the stale path) so derived genres stay in sync with the YAML
     * regardless of how the game enters the cache.
     */
    void applyDerivedGenres(Game game) {
        Set<String> tagNames = game.getTags().stream().map(Tag::getName).collect(java.util.stream.Collectors.toSet());
        Set<String> themeNames = game.getThemes().stream().map(Theme::getName).collect(java.util.stream.Collectors.toSet());
        Set<String> derivedNames = derivedGenreEngine.deriveGenres(tagNames, themeNames);

        game.getGenres().removeIf(g -> "DERIVED".equals(g.getSource()));

        for (String name : derivedNames) {
            Genre genre = genreRepository.findByName(name)
                    .orElseGet(() -> genreRepository.save(new Genre(name, "DERIVED")));
            game.getGenres().add(genre);
        }
    }

    private void applyAssociations(Game game, IgdbGameDto dto) {
        game.setGenres(resolveGenres(dto));
        game.setPlatforms(resolvePlatforms(dto));
        game.setTags(resolveTags(dto));
        game.setThemes(resolveThemes(dto));
        game.setGameModes(resolveGameModes(dto));
        game.setPlayerPerspectives(resolvePlayerPerspectives(dto));
        game.setFranchises(resolveFranchises(dto));
        game.setCollections(resolveCollections(dto));
    }

    private void applyOwnedJsonFields(Game game, IgdbGameDto dto) {
        game.setScreenshots(serializeScreenshots(dto));
        game.setVideos(serializeVideos(dto));
        game.setDlcIds(serializeIntList(dto.getDlcs()));
        game.setExpansionIds(serializeIntList(dto.getExpansions()));
        game.setSimilarGameIds(serializeIntList(dto.getSimilarGames()));
        game.setAgeRatings(serializeAgeRatings(dto));
        game.setReleaseDates(serializeReleaseDates(dto));
        game.setMultiplayerModes(serializeMultiplayerModes(dto));
    }

    private Set<Platform> resolvePlatforms(IgdbGameDto dto) {
        if (dto.getPlatforms() == null) return new HashSet<>();
        Set<Platform> result = new HashSet<>();
        for (var p : dto.getPlatforms()) {
            String canonical = IgdbPlatformMapper.normalize(p.getName());
            if (canonical == null || canonical.isBlank()) continue;
            result.add(platformRepository.findByName(canonical)
                    .orElseGet(() -> platformRepository.save(new Platform(canonical))));
        }
        return result;
    }

    private Set<Genre> resolveGenres(IgdbGameDto dto) {
        if (dto.getGenres() == null) return new HashSet<>();
        Set<Genre> result = new HashSet<>();
        for (var g : dto.getGenres()) {
            result.add(genreRepository.findByName(g.getName())
                    .orElseGet(() -> genreRepository.save(new Genre(g.getName()))));
        }
        return result;
    }

    private Set<Tag> resolveTags(IgdbGameDto dto) {
        if (dto.getKeywords() == null) return new HashSet<>();
        Set<Tag> result = new HashSet<>();
        for (var k : dto.getKeywords()) {
            String name = k.getName();
            if (isNoiseTag(name)) continue;
            if (!curatedTagAllowlist.isAllowed(name)) continue;
            result.add(tagRepository.findByName(name)
                    .orElseGet(() -> tagRepository.save(new Tag(name))));
        }
        return result;
    }

    /**
     * Filters IGDB keyword noise at ingest. Drops:
     * <ul>
     *   <li>null / blank</li>
     *   <li>shorter than {@link #TAG_MIN_LENGTH}</li>
     *   <li>platform-prefix tags ({@code released-on-steam}, {@code exclusive-to-pc} etc.)</li>
     * </ul>
     * Allowlist enforcement runs as a second gate after this method. See
     * {@link CuratedTagAllowlist} for the curated keep-set.
     */
    private boolean isNoiseTag(String name) {
        if (name == null) return true;
        String trimmed = name.trim().toLowerCase();
        if (trimmed.length() < TAG_MIN_LENGTH) return true;
        for (String prefix : NOISE_TAG_PREFIXES) {
            if (trimmed.startsWith(prefix)) return true;
        }
        return false;
    }

    private Set<Theme> resolveThemes(IgdbGameDto dto) {
        if (dto.getThemes() == null) return new HashSet<>();
        Set<Theme> result = new HashSet<>();
        for (var t : dto.getThemes()) {
            result.add(themeRepository.findByName(t.getName())
                    .orElseGet(() -> themeRepository.save(new Theme(t.getName()))));
        }
        return result;
    }

    private Set<GameMode> resolveGameModes(IgdbGameDto dto) {
        if (dto.getGameModes() == null) return new HashSet<>();
        Set<GameMode> result = new HashSet<>();
        for (var m : dto.getGameModes()) {
            result.add(gameModeRepository.findByName(m.getName())
                    .orElseGet(() -> gameModeRepository.save(new GameMode(m.getName()))));
        }
        return result;
    }

    private Set<PlayerPerspective> resolvePlayerPerspectives(IgdbGameDto dto) {
        if (dto.getPlayerPerspectives() == null) return new HashSet<>();
        Set<PlayerPerspective> result = new HashSet<>();
        for (var p : dto.getPlayerPerspectives()) {
            result.add(playerPerspectiveRepository.findByName(p.getName())
                    .orElseGet(() -> playerPerspectiveRepository.save(new PlayerPerspective(p.getName()))));
        }
        return result;
    }

    private Set<Franchise> resolveFranchises(IgdbGameDto dto) {
        if (dto.getFranchises() == null) return new HashSet<>();
        Set<Franchise> result = new HashSet<>();
        for (var f : dto.getFranchises()) {
            result.add(franchiseRepository.findByName(f.getName())
                    .orElseGet(() -> franchiseRepository.save(new Franchise(f.getName()))));
        }
        return result;
    }

    private Set<GameCollection> resolveCollections(IgdbGameDto dto) {
        if (dto.getCollections() == null) return new HashSet<>();
        Set<GameCollection> result = new HashSet<>();
        for (var c : dto.getCollections()) {
            result.add(gameCollectionRepository.findByName(c.getName())
                    .orElseGet(() -> gameCollectionRepository.save(new GameCollection(c.getName()))));
        }
        return result;
    }

    // ── JSON serialization for owned-data fields ──────────────────────────────

    private String serializeScreenshots(IgdbGameDto dto) {
        if (dto.getScreenshots() == null) return "[]";
        List<String> ids = dto.getScreenshots().stream()
                .map(IgdbScreenshotDto::getImageId)
                .filter(s -> s != null && !s.isBlank())
                .toList();
        return writeJson(ids);
    }

    private String serializeVideos(IgdbGameDto dto) {
        if (dto.getVideos() == null) return "[]";
        List<String> ids = dto.getVideos().stream()
                .map(IgdbVideoDto::getVideoId)
                .filter(s -> s != null && !s.isBlank())
                .toList();
        return writeJson(ids);
    }

    private String serializeIntList(List<Integer> ints) {
        if (ints == null) return "[]";
        return writeJson(new ArrayList<>(ints));
    }

    private String serializeAgeRatings(IgdbGameDto dto) {
        if (dto.getAgeRatings() == null) return "[]";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IgdbAgeRatingDto a : dto.getAgeRatings()) {
            if (a.getCategory() == null || a.getRating() == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", a.getCategory());
            row.put("rating", a.getRating());
            rows.add(row);
        }
        return writeJson(rows);
    }

    private String serializeReleaseDates(IgdbGameDto dto) {
        if (dto.getReleaseDates() == null) return "[]";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IgdbReleaseDateDto r : dto.getReleaseDates()) {
            Map<String, Object> row = new LinkedHashMap<>();
            String platform = (r.getPlatform() != null)
                    ? IgdbPlatformMapper.normalize(r.getPlatform().getName())
                    : null;
            row.put("platform", platform);
            row.put("human", r.getHuman());
            if (r.getDate() != null) {
                row.put("date", java.time.Instant.ofEpochSecond(r.getDate())
                        .atZone(java.time.ZoneId.of("UTC"))
                        .toLocalDate()
                        .toString());
            } else {
                row.put("date", null);
            }
            rows.add(row);
        }
        return writeJson(rows);
    }

    private String serializeMultiplayerModes(IgdbGameDto dto) {
        if (dto.getMultiplayerModes() == null) return "[]";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IgdbMultiplayerModeDto m : dto.getMultiplayerModes()) {
            Map<String, Object> row = new LinkedHashMap<>();
            String platform = (m.getPlatform() != null)
                    ? IgdbPlatformMapper.normalize(m.getPlatform().getName())
                    : null;
            row.put("platform", platform);
            row.put("onlineMax", m.getOnlineMax());
            row.put("offlineMax", m.getOfflineMax());
            row.put("onlineCoopMax", m.getOnlineCoopMax());
            row.put("offlineCoopMax", m.getOfflineCoopMax());
            row.put("lanCoop", m.getLanCoop());
            row.put("splitscreen", m.getSplitscreen());
            row.put("campaignCoop", m.getCampaignCoop());
            row.put("dropIn", m.getDropIn());
            rows.add(row);
        }
        return writeJson(rows);
    }

    private String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return "[]";
        }
    }
}
