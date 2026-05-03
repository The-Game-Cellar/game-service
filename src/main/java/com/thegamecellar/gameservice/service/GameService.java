package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.PlatformsResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.Platform;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.repository.GenreRepository;
import com.thegamecellar.gameservice.repository.PlatformRepository;
import com.thegamecellar.gameservice.util.GameMapper;
import com.thegamecellar.gameservice.util.MoodMapper;
import com.thegamecellar.gameservice.util.PlatformGroups;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final GenreRepository genreRepository;
    private final PlatformRepository platformRepository;
    private final GameCacheService gameCacheService;
    private final IgdbApiClient igdbApiClient;

    @Transactional
    public GameResponse getGameById(Integer igdbId) {
        Optional<Game> cached = gameRepository.findByIgdbId(igdbId);

        if (cached.isPresent()) {
            Game game = cached.get();
            // Critical-data stale check on the user-triggered path. Each refresh
            // here costs an extra IGDB roundtrip, so we only fire on truly
            // missing core data — the looser cacheIfAbsent stale check picks up
            // the rest naturally as the worker re-walks the catalog.
            //
            // Tags deliberately excluded: an empty tag set is now the legitimate
            // post-allowlist-filter state for many games. Re-fetching on read
            // would just re-pull the same IGDB keywords, drop them through the
            // allowlist, and leave tags empty again — every view would hit IGDB
            // for nothing. Tag refresh stays on the worker's nightly catalog walk.
            boolean stale = game.getGenres().isEmpty()
                    || game.getDescription() == null
                    || game.getDescription().isBlank()
                    || game.getDevelopers() == null;
            if (stale) {
                try {
                    IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
                    Game refreshed = gameCacheService.refreshStaleGame(game, dto);
                    return augmentInferredParent(refreshed, GameMapper.toResponse(refreshed));
                } catch (Exception e) {
                    log.warn("Could not re-fetch stale game igdbId={}: {}", igdbId, e.getMessage());
                    return augmentInferredParent(game, GameMapper.toResponse(game));
                }
            }
            return augmentInferredParent(game, GameMapper.toResponse(game));
        }

        IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
        Game saved = gameCacheService.cacheGame(dto);
        return augmentInferredParent(saved, GameMapper.toResponse(saved));
    }

    /**
     * Fills {@code parentGameId} + {@code parentGameName} on the response when IGDB left
     * them null but the game name has a separator-prefix matching another cached game (e.g.
     * "The Witcher 3: Wild Hunt - Game of the Year Edition" → parent = "The Witcher 3: Wild
     * Hunt"). Picks the longest prefix-match so deeply-nested editions still resolve to the
     * most specific parent. Gated to categories where IGDB routinely omits parent_game while
     * still tagging the variant correctly (3 Bundle / 5 Mod / 14 Update) — main titles
     * (category 0) and properly-linked Remakes/Remasters/Standalone Expansions (4/8/9/10)
     * never go through this path.
     */
    private static final java.util.Set<Integer> INFERABLE_PARENT_CATEGORIES = java.util.Set.of(3, 5, 14);

    private GameResponse augmentInferredParent(Game game, GameResponse response) {
        if (response.getParentGameId() != null) return response;
        if (game.getCategory() == null || !INFERABLE_PARENT_CATEGORIES.contains(game.getCategory())) return response;
        if (game.getName() == null) return response;
        String name = game.getName();
        if (!name.contains(" - ") && !name.contains(": ")) return response;

        List<Game> candidates = gameRepository.findPrefixParentCandidates(name, game.getId());
        for (Game parent : candidates) {
            // Reject junk-name parents (single-char placeholders, IGDB stub rows like "%").
            if (parent.getName() != null && parent.getName().trim().length() >= 3) {
                response.setParentGameId(parent.getIgdbId());
                response.setParentGameName(parent.getName());
                return response;
            }
        }
        return response;
    }

    @Transactional
    public GameSearchResponse searchByMood(String mood, int page, int pageSize) {
        return searchGames(null, null, null, mood, "-rating", page, pageSize, true, "main", null, null);
    }

    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre, String ordering, int page, int pageSize) {
        return searchGames(query, platform, genre, null, ordering, page, pageSize, false, "main", null, null);
    }

    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre, String ordering, int page, int pageSize, boolean dbOnly) {
        return searchGames(query, platform, genre, null, ordering, page, pageSize, dbOnly, "main", null, null);
    }

    /**
     * Unified DB-first search. Builds a Spring Data {@link Specification} from every filter
     * the caller provides, AND-ing them together so any combination of (query, platform, genre,
     * mood, gameMode, perspective) works correctly. The previous implementation fanned out
     * across multiple branched repository methods and ended up applying only a subset of the
     * filters whenever two or more were active. Falls back to IGDB only when the DB returns
     * zero matches AND {@code dbOnly} is false.
     */
    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre, String mood,
                                            String ordering, int page, int pageSize, boolean dbOnly,
                                            String gameType, String gameMode, String perspective) {
        PageRequest pageable = PageRequest.of(page, pageSize, sortFromOrdering(ordering));

        Specification<Game> spec = buildSearchSpec(query, platform, genre, mood, gameType, gameMode, perspective);
        Page<Game> result = gameRepository.findAll(spec, pageable);

        if (result.getTotalElements() > 0) {
            return GameSearchResponse.builder()
                    .games(result.getContent().stream().map(GameMapper::toResponse).toList())
                    .totalCount((int) Math.min(result.getTotalElements(), Integer.MAX_VALUE))
                    .page(page)
                    .pageSize(pageSize)
                    .build();
        }

        // IGDB's search endpoint can only filter by query / genre / platform. If the user
        // narrowed by mood, gameMode, perspective, or asked for variants-only, IGDB would
        // return generic results that ignore those filters — i.e. misleading fallback. In
        // that case we return empty to let the UI render an honest "no matches" state rather
        // than a fake-broad list.
        boolean igdbCanMatchFilters = !isSet(mood) && !isSet(gameMode) && !isSet(perspective)
                && (gameType == null || "main".equalsIgnoreCase(gameType));

        if (dbOnly || !igdbCanMatchFilters) {
            return GameSearchResponse.builder()
                    .games(List.of())
                    .totalCount(0)
                    .page(page)
                    .pageSize(pageSize)
                    .build();
        }

        boolean queryBlank = (query == null || query.isBlank());
        boolean genreSet = (genre != null && !genre.isBlank());
        boolean platformSet = (platform != null && !platform.isBlank());

        try {
            List<IgdbGameDto> igdbResults;
            if (!queryBlank) {
                igdbResults = igdbApiClient.searchGames(query, pageSize, page * pageSize);
            } else if (genreSet && platformSet) {
                igdbResults = igdbApiClient.searchByGenreAndPlatform(genre, platform, pageSize, page * pageSize);
            } else if (genreSet) {
                igdbResults = igdbApiClient.searchByGenre(genre, pageSize, page * pageSize);
            } else if (platformSet) {
                igdbResults = igdbApiClient.fetchPopularGames(platform, pageSize, page * pageSize);
            } else {
                igdbResults = igdbApiClient.fetchCatalogPage(pageSize, page * pageSize);
            }

            List<GameResponse> games = igdbResults.stream()
                    .map(dto -> {
                        gameCacheService.cacheIfAbsent(dto);
                        return GameMapper.toResponseFromIgdb(dto);
                    })
                    .toList();

            return GameSearchResponse.builder()
                    .games(games)
                    .totalCount(games.size())
                    .page(page)
                    .pageSize(pageSize)
                    .build();
        } catch (IgdbApiException ex) {
            log.warn("IGDB search unavailable (query={}, genre={}): {}", query, genre, ex.getMessage());
            return GameSearchResponse.builder()
                    .games(List.of())
                    .totalCount(0)
                    .page(page)
                    .pageSize(pageSize)
                    .build();
        }
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static Specification<Game> buildSearchSpec(String query, String platform, String genre, String mood,
                                                         String gameType, String gameMode, String perspective) {
        return (root, cq, cb) -> {
            cq.distinct(true);
            List<Predicate> preds = new ArrayList<>();

            // gameType: main (default) treats remakes (cat 8) as full games alongside main_game (0)
            // and uncategorised rows. variant covers GOTY/deluxe/bundle/expansion/port/etc — but
            // never remake, since those now live in main. all skips the category filter entirely.
            if (gameType == null || "main".equalsIgnoreCase(gameType)) {
                preds.add(cb.or(
                        cb.equal(root.get("category"), 0),
                        cb.equal(root.get("category"), 8),
                        cb.isNull(root.get("category"))
                ));
            } else if ("variant".equalsIgnoreCase(gameType)) {
                preds.add(cb.and(
                        cb.greaterThan(root.get("category"), 2),
                        cb.notEqual(root.get("category"), 8)
                ));
            }

            if (isSet(query)) {
                preds.add(cb.like(cb.lower(root.get("name")), "%" + query.toLowerCase() + "%"));
            }

            if (isSet(genre)) {
                var genreJoin = root.join("genres", JoinType.INNER);
                preds.add(cb.equal(cb.lower(genreJoin.get("name")), genre.toLowerCase()));
            }

            if (isSet(platform)) {
                // Multi-platform umbrella support. Frontend sends a comma-separated list
                // when the user picks an umbrella label like "PlayStation" — the children expand
                // to all 7 PS platforms server-side. A single child platform comes through as
                // one value and falls into the same IN-clause naturally.
                List<String> wanted = java.util.Arrays.stream(platform.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .toList();
                if (!wanted.isEmpty()) {
                    var platformJoin = root.join("platforms", JoinType.INNER);
                    preds.add(cb.lower(platformJoin.get("name")).in(wanted));
                }
            }

            if (isSet(gameMode)) {
                var modeJoin = root.join("gameModes", JoinType.INNER);
                preds.add(cb.equal(cb.lower(modeJoin.get("name")), gameMode.toLowerCase()));
            }

            if (isSet(perspective)) {
                var perspJoin = root.join("playerPerspectives", JoinType.INNER);
                preds.add(cb.equal(cb.lower(perspJoin.get("name")), perspective.toLowerCase()));
            }

            if (isSet(mood)) {
                List<String> tags = MoodMapper.getTagsForMood(mood);
                if (!tags.isEmpty()) {
                    var tagJoin = root.join("tags", JoinType.INNER);
                    List<String> lowered = tags.stream().map(String::toLowerCase).toList();
                    preds.add(cb.lower(tagJoin.get("name")).in(lowered));
                } else {
                    // Mood produced no tags — return empty rather than ignoring the filter.
                    preds.add(cb.disjunction());
                }
            }

            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public GameSearchResponse getPopularGames(String platform, int page) {
        List<IgdbGameDto> igdbResults = igdbApiClient.fetchPopularGames(platform, 20, page * 20);
        List<GameResponse> games = igdbResults.stream()
                .map(dto -> {
                    gameCacheService.cacheIfAbsent(dto);
                    return GameMapper.toResponseFromIgdb(dto);
                })
                .toList();

        return GameSearchResponse.builder()
                .games(games)
                .totalCount(games.size())
                .page(page)
                .pageSize(20)
                .build();
    }

    /**
     * DB-backed Coming Soon read path. Returns hype-weighted random sample over upcoming
     * games whose canonical first_release_date falls within the window. Worker keeps the
     * underlying rows fresh — no IGDB call on the read path.
     */
    @Transactional(readOnly = true)
    public GameSearchResponse getUpcomingGames(List<String> platforms, int windowDays, int limit, java.util.Set<Integer> excludeIgdbIds) {
        List<GameResponse> games = getUpcoming(platforms, windowDays, limit, excludeIgdbIds);
        return GameSearchResponse.builder()
                .games(games)
                .totalCount(games.size())
                .page(0)
                .pageSize(limit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getUpcomingPlatformNames() {
        long now = java.time.Instant.now().getEpochSecond();
        return gameRepository.findDistinctUpcomingPlatformNames(now);
    }

    @Transactional(readOnly = true)
    public GameSearchResponse getRandomGames(int limit) {
        List<GameResponse> games = gameRepository.findRandom(limit).stream()
                .map(GameMapper::toResponse)
                .toList();
        return GameSearchResponse.builder()
                .games(games)
                .totalCount(games.size())
                .page(0)
                .pageSize(limit)
                .build();
    }

    @Transactional(readOnly = true)
    public GameSearchResponse getRandomQualityByGenre(String genre, java.math.BigDecimal minRating, int minVotes, int limit) {
        List<GameResponse> games = gameRepository.findRandomQualityByGenre(genre, minRating, minVotes, limit).stream()
                .map(GameMapper::toResponse)
                .toList();
        return GameSearchResponse.builder()
                .games(games)
                .totalCount(games.size())
                .page(0)
                .pageSize(limit)
                .build();
    }

    public List<String> getGenres() {
        List<String> cached = genreRepository.findAllNames();
        if (!cached.isEmpty()) {
            return cached;
        }
        return igdbApiClient.fetchGenres().stream()
                .map(g -> g.getName())
                .toList();
    }

    public List<String> getPlatforms() {
        return platformRepository.findAll().stream()
                .map(Platform::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Hierarchical view of {@link #getPlatforms()} for the Explore platform dropdown.
     * Big-4 umbrellas (PlayStation / PC / Nintendo / Xbox) come first; everything else
     * IGDB has cached lands alphabetically under {@code others}. Children of an
     * umbrella that aren't actually present in the DB get filtered out so the dropdown
     * never offers a generation the user can't filter by.
     */
    public PlatformsResponse getPlatformGroups() {
        return PlatformGroups.group(getPlatforms());
    }

    @Transactional(readOnly = true)
    public List<GameResponse> getByFranchise(String franchiseName, int limit, Integer excludeIgdbId) {
        Sort sort = Sort.by(Sort.Order.asc("released").nullsLast());
        List<Game> raw = gameRepository.findByFranchiseName(franchiseName, PageRequest.of(0, Math.min(limit * 4, 100), sort));
        return dedupeVariants(raw, excludeIgdbId, limit).stream()
                .map(GameMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GameResponse> getByCollection(String collectionName, int limit, Integer excludeIgdbId) {
        Sort sort = Sort.by(Sort.Order.asc("released").nullsLast());
        List<Game> raw = gameRepository.findByCollectionName(collectionName, PageRequest.of(0, Math.min(limit * 4, 100), sort));
        return dedupeVariants(raw, excludeIgdbId, limit).stream()
                .map(GameMapper::toResponse)
                .toList();
    }

    /**
     * Returns derivative releases of a main game: editions, remakes, remasters, standalone
     * expansions, ports, updates, etc. Matches on TWO axes because IGDB data is unreliable:
     * (a) {@code parentGameId} pointing at this game (catches official IGDB-linked derivatives
     * like "Free Next-Gen Update" and "Enhanced Edition - Redux"), and (b) name-pattern
     * starting with the parent name plus {@code " - "} or {@code ": "} (catches IGDB-orphaned
     * variants like "Complete Edition", "GOTY Edition", "Collector's Edition", "10th Anniversary
     * Edition" which routinely come back as {@code parentGameId=NULL} regardless of category).
     * Excludes plain DLC + Expansion (1, 2) since those live in the DLC stack, and
     * Remake (8) since remakes are full standalone games surfaced in default browse.
     * Bundle (3) is INCLUDED — that's where Complete / GOTY editions sit.
     */
    @Transactional(readOnly = true)
    public List<GameResponse> getEditionsOf(Integer parentIgdbId) {
        Game parent = gameRepository.findByIgdbId(parentIgdbId).orElse(null);
        if (parent == null || parent.getName() == null || parent.getName().isBlank()) {
            return List.of();
        }
        List<Integer> excludedCategories = List.of(1, 2, 8);
        List<Game> editions = gameRepository.findEditionsOf(
                parentIgdbId, parent.getName(), parent.getId(), excludedCategories);
        return editions.stream()
                .sorted(java.util.Comparator
                        .comparing(Game::getReleased,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparingInt(g -> g.getName() == null ? 0 : g.getName().length()))
                .map(GameMapper::toResponse)
                .toList();
    }

    /**
     * Drops edition / anniversary / "complete" variants that IGDB miscategorises
     * as {@code category=0} main games. A game is treated as a variant when its
     * name starts with an already-kept name plus a separator ({@code " - "} or
     * {@code ": "}). Sorting by release date asc + name length asc ensures the
     * canonical base name lands first when releases tie.
     */
    private static List<com.thegamecellar.gameservice.model.entity.Game> dedupeVariants(
            List<com.thegamecellar.gameservice.model.entity.Game> raw, Integer excludeIgdbId, int limit) {
        List<com.thegamecellar.gameservice.model.entity.Game> sorted = raw.stream()
                .sorted(java.util.Comparator
                        .comparing(com.thegamecellar.gameservice.model.entity.Game::getReleased,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparingInt(g -> g.getName() == null ? 0 : g.getName().length()))
                .toList();

        List<String> seenNames = new java.util.ArrayList<>();
        List<com.thegamecellar.gameservice.model.entity.Game> result = new java.util.ArrayList<>();
        for (com.thegamecellar.gameservice.model.entity.Game g : sorted) {
            if (result.size() >= limit) break;
            String n = g.getName();
            if (n == null) continue;
            boolean isVariant = seenNames.stream()
                    .anyMatch(kn -> n.startsWith(kn + " - ") || n.startsWith(kn + ": "));
            if (isVariant) continue;

            seenNames.add(n);
            if (excludeIgdbId == null || !excludeIgdbId.equals(g.getIgdbId())) {
                result.add(g);
            }
        }
        return result;
    }

    // ── Admin backfill ────────────────────────────────────────────────────────

    /**
     * One-shot backfill for games cached before the developers column was added —
     * re-fetches each {@code developers IS NULL} row from IGDB so the column gets
     * populated. Honours the same 250 ms IGDB rate-limit gap used by the catalog
     * worker. Designed to run via {@link AdminSyncExecutor} so only one admin job
     * runs at a time.
     */
    public void backfillDevelopers() {
        long total = gameRepository.countByDevelopersIsNull();
        log.info("Developer backfill starting — {} candidate games", total);
        int processed = 0;
        int updated = 0;
        int batchSize = 100;

        while (true) {
            List<Game> batch = gameRepository.findByDevelopersIsNull(PageRequest.of(0, batchSize));
            if (batch.isEmpty()) break;
            for (Game game : batch) {
                try {
                    IgdbGameDto dto = igdbApiClient.fetchGameById(game.getIgdbId());
                    gameCacheService.refreshStaleGame(game, dto);
                    if (game.getDevelopers() != null) updated++;
                } catch (Exception e) {
                    log.warn("Developer backfill: failed for igdbId={}: {}", game.getIgdbId(), e.getMessage());
                }
                processed++;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Developer backfill interrupted at processed={}", processed);
                    return;
                }
            }
            log.info("Developer backfill progress: processed={}/{} updated={}", processed, total, updated);
        }
        log.info("Developer backfill complete — processed={} updated={}", processed, updated);
    }

    // ── IGDB catalog worker support ───────────────────────────────────────────

    public CatalogSyncResult syncIgdbCatalogOffset(int offset, int limit) {
        List<IgdbGameDto> results = igdbApiClient.fetchCatalogPage(limit, offset);
        int cached = 0;
        for (IgdbGameDto dto : results) {
            try {
                if (gameCacheService.cacheIfAbsent(dto)) cached++;
            } catch (Exception e) {
                log.warn("IGDB catalog sync: failed to cache igdbId={}: {}", dto.getId(), e.getMessage());
            }
        }
        return new CatalogSyncResult(results.size(), cached);
    }

    public CatalogSyncResult syncIgdbNewReleasesOffset(int offset, int limit) {
        List<IgdbGameDto> results = igdbApiClient.fetchNewReleases(limit, offset);
        int cached = 0;
        for (IgdbGameDto dto : results) {
            try {
                if (gameCacheService.cacheIfAbsent(dto)) cached++;
            } catch (Exception e) {
                log.warn("IGDB new releases sync: failed to cache igdbId={}: {}", dto.getId(), e.getMessage());
            }
        }
        return new CatalogSyncResult(results.size(), cached);
    }

    /**
     * Pulls IGDB games whose canonical worldwide release is in the future and caches
     * any that are not already in our DB. The page-by-page contract mirrors
     * {@link #syncIgdbNewReleasesOffset} so the worker drives both with the same shape.
     * Existing rows that this page touches go through the standard cacheIfAbsent stale-check,
     * which now also fills first_release_date + hypes when those columns are null.
     */
    public CatalogSyncResult syncIgdbUpcomingOffset(int offset, int limit) {
        List<IgdbGameDto> results = igdbApiClient.fetchUpcomingReleases(limit, offset);
        int cached = 0;
        for (IgdbGameDto dto : results) {
            try {
                if (gameCacheService.cacheIfAbsent(dto)) cached++;
            } catch (Exception e) {
                log.warn("IGDB upcoming releases sync: failed to cache igdbId={}: {}", dto.getId(), e.getMessage());
            }
        }
        return new CatalogSyncResult(results.size(), cached);
    }

    /**
     * Re-fetches a single game from IGDB and force-overwrites its volatile upcoming fields
     * (first_release_date, hypes, per-platform release_dates JSON, totalRating). Worker calls
     * this per row in the upcoming-refresh pass so date slips and hype movements propagate
     * within 24h.
     */
    public boolean refreshUpcomingRow(Integer igdbId) {
        Optional<Game> existing = gameRepository.findByIgdbId(igdbId);
        if (existing.isEmpty()) return false;
        try {
            IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
            gameCacheService.refreshUpcomingGame(existing.get(), dto);
            return true;
        } catch (Exception e) {
            log.warn("Upcoming refresh failed for igdbId={}: {}", igdbId, e.getMessage());
            return false;
        }
    }

    public List<Integer> findUpcomingIgdbIds() {
        long now = java.time.Instant.now().getEpochSecond();
        return gameRepository.findUpcomingIgdbIds(now);
    }

    // ── Coming Soon read path ─────────────────────────────────────────────────

    /**
     * Hype-weighted random pick over upcoming games whose canonical release falls within
     * {@code [now, now + windowDays]}. Each candidate's pick weight = max(hypes, 1). Null
     * hypes are treated as 0 (per user decision — fair if all candidates have null, otherwise
     * never picked). Sample without replacement until {@code limit} reached or pool exhausted.
     * Read path is DB-only — worker keeps the underlying rows fresh.
     */
    @Transactional(readOnly = true)
    public List<GameResponse> getUpcoming(List<String> platformFilter, int windowDays, int limit, java.util.Set<Integer> excludeIgdbIds) {
        long now = java.time.Instant.now().getEpochSecond();
        // windowDays <= 0 = unbounded horizon (the "All" toggle on the frontend). Long.MAX_VALUE
        // is the natural upper bound for the BIGINT first_release_date column — IGDB never sets
        // dates near that magnitude so the BETWEEN filter degenerates to "any future date".
        long horizonEnd = (windowDays <= 0)
                ? Long.MAX_VALUE
                : now + (long) windowDays * 24L * 60L * 60L;

        List<Game> pool;
        if (platformFilter != null && !platformFilter.isEmpty()) {
            List<String> lowered = platformFilter.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(String::toLowerCase)
                    .toList();
            pool = lowered.isEmpty()
                    ? gameRepository.findUpcoming(now, horizonEnd)
                    : gameRepository.findUpcomingByPlatforms(now, horizonEnd, lowered);
        } else {
            pool = gameRepository.findUpcoming(now, horizonEnd);
        }
        if (pool.isEmpty() || limit <= 0) return List.of();

        java.util.Set<Integer> exclude = excludeIgdbIds == null ? java.util.Set.of() : excludeIgdbIds;

        // De-duplicate (platform JOIN can repeat a game across matching platforms) and exclude
        // games already in the user's library so Coming Soon stays a "what's next to consider"
        // surface, not a recap of what they already own.
        java.util.LinkedHashMap<Integer, Game> uniqueMap = new java.util.LinkedHashMap<>();
        for (Game g : pool) {
            if (exclude.contains(g.getIgdbId())) continue;
            uniqueMap.putIfAbsent(g.getIgdbId(), g);
        }
        List<Game> unique = new ArrayList<>(uniqueMap.values());
        if (unique.isEmpty()) return List.of();

        return weightedSampleByHype(unique, limit).stream()
                .map(GameMapper::toResponse)
                .toList();
    }

    /**
     * Weighted random sample without replacement. Weight of each candidate is
     * {@code max(hypes, 0)}; null hypes count as 0 — they only get picked when every
     * remaining candidate has zero weight (uniform random fallback over zero-weight tail).
     */
    private List<Game> weightedSampleByHype(List<Game> candidates, int limit) {
        java.util.List<Game> remaining = new java.util.ArrayList<>(candidates);
        java.util.List<Game> picked = new java.util.ArrayList<>(Math.min(limit, candidates.size()));
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        while (!remaining.isEmpty() && picked.size() < limit) {
            long totalWeight = 0L;
            for (Game g : remaining) {
                Integer h = g.getHypes();
                totalWeight += (h != null && h > 0) ? h : 0;
            }
            if (totalWeight == 0) {
                Game g = remaining.remove(rng.nextInt(remaining.size()));
                picked.add(g);
                continue;
            }
            long roll = rng.nextLong(totalWeight);
            long cum = 0;
            int idx = remaining.size() - 1;
            for (int i = 0; i < remaining.size(); i++) {
                Integer h = remaining.get(i).getHypes();
                long w = (h != null && h > 0) ? h : 0;
                cum += w;
                if (roll < cum) { idx = i; break; }
            }
            picked.add(remaining.remove(idx));
        }
        return picked;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Sort sortFromOrdering(String ordering) {
        // "Popular" ordering tracks IGDB's user-vote score (totalRating), not the critic
        // score (rating). User scores reflect actual play sentiment; critic scores skew
        // to AAA review-bait coverage. Frontend keeps the "-rating" identifier for
        // backwards compat with the existing dropdown values.
        if (ordering == null || ordering.isBlank() || ordering.equals("-rating")) {
            return Sort.by(Sort.Order.desc("totalRating").nullsLast());
        }
        return switch (ordering) {
            case "rating"    -> Sort.by(Sort.Order.asc("totalRating").nullsLast());
            case "-released" -> Sort.by(Sort.Order.desc("released").nullsLast());
            case "released"  -> Sort.by(Sort.Order.asc("released").nullsLast());
            case "name"      -> Sort.by(Sort.Order.asc("name").nullsLast());
            case "-name"     -> Sort.by(Sort.Order.desc("name").nullsLast());
            default          -> Sort.by(Sort.Order.desc("totalRating").nullsLast());
        };
    }
}
