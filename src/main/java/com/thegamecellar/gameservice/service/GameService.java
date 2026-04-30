package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.Platform;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.repository.GenreRepository;
import com.thegamecellar.gameservice.repository.PlatformRepository;
import com.thegamecellar.gameservice.util.GameMapper;
import com.thegamecellar.gameservice.util.MoodMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            boolean stale = game.getTags().isEmpty()
                    || game.getGenres().isEmpty()
                    || game.getDescription() == null
                    || game.getDescription().isBlank()
                    || game.getDevelopers() == null;
            if (stale) {
                try {
                    IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
                    return GameMapper.toResponse(gameCacheService.refreshStaleGame(game, dto));
                } catch (Exception e) {
                    log.warn("Could not re-fetch stale game igdbId={}: {}", igdbId, e.getMessage());
                    return GameMapper.toResponse(game);
                }
            }
            return GameMapper.toResponse(game);
        }

        IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
        Game saved = gameCacheService.cacheGame(dto);
        return GameMapper.toResponse(saved);
    }

    @Transactional
    public GameSearchResponse searchByMood(String mood, int page, int pageSize) {
        List<String> tags = MoodMapper.getTagsForMood(mood);
        if (tags.isEmpty()) {
            return GameSearchResponse.builder().games(List.of()).totalCount(0).page(page).pageSize(pageSize).build();
        }

        List<GameResponse> games = gameRepository.findByTagNamesIn(tags).stream()
                .map(GameMapper::toResponse)
                .toList();

        int total = games.size();
        int fromIndex = Math.min(page * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);

        return GameSearchResponse.builder()
                .games(games.subList(fromIndex, toIndex))
                .totalCount(total)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre, String ordering, int page, int pageSize) {
        return searchGames(query, platform, genre, ordering, page, pageSize, false);
    }

    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre, String ordering, int page, int pageSize, boolean dbOnly) {
        boolean queryBlank = (query == null || query.isBlank());
        boolean genreSet = (genre != null && !genre.isBlank());
        boolean platformSet = (platform != null && !platform.isBlank());
        boolean noFilters = queryBlank && !genreSet && !platformSet;

        PageRequest pageable = PageRequest.of(page, pageSize, sortFromOrdering(ordering));

        if (noFilters) {
            long dbCount = gameRepository.count();
            if (dbCount > 0) {
                List<Game> games = gameRepository.findAll(pageable).getContent();
                if (!games.isEmpty()) {
                    return GameSearchResponse.builder()
                            .games(games.stream().map(GameMapper::toResponse).toList())
                            .totalCount((int) Math.min(dbCount, Integer.MAX_VALUE))
                            .page(page)
                            .pageSize(pageSize)
                            .build();
                }
            }
        }

        if (queryBlank && genreSet && platformSet) {
            List<Game> cached = gameRepository.findByGenreAndPlatformName(genre, platform, pageable);
            if (!cached.isEmpty()) {
                long total = gameRepository.countByGenreAndPlatformName(genre, platform);
                return GameSearchResponse.builder()
                        .games(cached.stream().map(GameMapper::toResponse).toList())
                        .totalCount((int) Math.min(total, Integer.MAX_VALUE))
                        .page(page)
                        .pageSize(pageSize)
                        .build();
            }
        }

        if (queryBlank && genreSet && !platformSet) {
            List<Game> cached = gameRepository.findByGenreName(genre, pageable);
            if (!cached.isEmpty()) {
                long genreTotal = gameRepository.countByGenreName(genre);
                return GameSearchResponse.builder()
                        .games(cached.stream().map(GameMapper::toResponse).toList())
                        .totalCount((int) Math.min(genreTotal, Integer.MAX_VALUE))
                        .page(page)
                        .pageSize(pageSize)
                        .build();
            }
            long dbCount = gameRepository.count();
            if (dbCount > 0) {
                List<Game> games = gameRepository.findAll(pageable).getContent();
                if (!games.isEmpty()) {
                    return GameSearchResponse.builder()
                            .games(games.stream().map(GameMapper::toResponse).toList())
                            .totalCount((int) Math.min(dbCount, Integer.MAX_VALUE))
                            .page(page)
                            .pageSize(pageSize)
                            .build();
                }
            }
        }

        if (dbOnly) {
            return GameSearchResponse.builder()
                    .games(List.of())
                    .totalCount(0)
                    .page(page)
                    .pageSize(pageSize)
                    .build();
        }

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

    @Transactional
    public GameSearchResponse getUpcomingGames(String platform) {
        List<IgdbGameDto> igdbResults = igdbApiClient.fetchUpcomingGames(platform, 20);
        List<GameResponse> games = igdbResults.stream()
                .map(dto -> {
                    gameCacheService.cacheIfAbsent(dto);
                    return GameMapper.toResponseFromIgdb(dto);
                })
                .toList();

        return GameSearchResponse.builder()
                .games(games)
                .totalCount(games.size())
                .page(0)
                .pageSize(20)
                .build();
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
     * One-shot backfill for games cached before GS26 (developers column added)
     * — re-fetches each {@code developers IS NULL} row from IGDB so the column
     * gets populated. Honours the same 250 ms IGDB rate-limit gap used by the
     * catalog worker. Designed to run via {@link AdminSyncExecutor} so only one
     * admin job runs at a time.
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

    // ── helpers ───────────────────────────────────────────────────────────────

    private Sort sortFromOrdering(String ordering) {
        if (ordering == null || ordering.isBlank() || ordering.equals("-rating")) {
            return Sort.by(Sort.Order.desc("rating").nullsLast());
        }
        return switch (ordering) {
            case "rating"    -> Sort.by(Sort.Order.asc("rating").nullsLast());
            case "-released" -> Sort.by(Sort.Order.desc("released").nullsLast());
            case "released"  -> Sort.by(Sort.Order.asc("released").nullsLast());
            case "name"      -> Sort.by(Sort.Order.asc("name").nullsLast());
            case "-name"     -> Sort.by(Sort.Order.desc("name").nullsLast());
            default          -> Sort.by(Sort.Order.desc("rating").nullsLast());
        };
    }
}
