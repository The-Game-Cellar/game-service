package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.repository.GameGenreRepository;
import com.thegamecellar.gameservice.repository.GamePlatformRepository;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.util.GameMapper;
import com.thegamecellar.gameservice.util.MoodMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final GameGenreRepository gameGenreRepository;
    private final GamePlatformRepository gamePlatformRepository;
    private final IgdbApiClient igdbApiClient;

    @Transactional
    public GameResponse getGameById(Integer igdbId) {
        Optional<Game> cached = gameRepository.findByIgdbId(igdbId);

        if (cached.isPresent()) {
            Game game = cached.get();
            boolean stale = game.getTags().isEmpty()
                    || game.getGenres().isEmpty()
                    || game.getDescription() == null
                    || game.getDescription().isBlank();
            if (stale) {
                try {
                    IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
                    if (game.getTags().isEmpty()) {
                        game.getTags().addAll(GameMapper.toTagEntities(dto, game));
                    }
                    if (game.getGenres().isEmpty()) {
                        game.getGenres().addAll(GameMapper.toGenreEntities(dto, game));
                    }
                    if (game.getDescription() == null || game.getDescription().isBlank()) {
                        game.setDescription(dto.getSummary());
                    }
                    if (game.getCoverImageId() == null && dto.getCover() != null) {
                        game.setCoverImageId(dto.getCover().getImageId());
                    }
                    return GameMapper.toResponse(gameRepository.save(game));
                } catch (Exception e) {
                    log.warn("Could not re-fetch stale game igdbId={}: {}", igdbId, e.getMessage());
                    return GameMapper.toResponse(game);
                }
            }
            return GameMapper.toResponse(game);
        }

        IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
        Game saved = cacheGame(dto);
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
        boolean noFilters = (query == null || query.isBlank())
                && (genre == null || genre.isBlank())
                && (platform == null || platform.isBlank());

        if (noFilters) {
            long dbCount = gameRepository.count();
            if (dbCount > 0) {
                int poolSize = Math.max(200, (page + 1) * pageSize);
                List<Game> pool = gameRepository.findRecentGames(PageRequest.of(0, poolSize));
                List<Game> sorted = new ArrayList<>(pool);
                applyOrdering(sorted, ordering);
                int from = page * pageSize;
                int to = Math.min(from + pageSize, sorted.size());
                if (from < sorted.size()) {
                    return GameSearchResponse.builder()
                            .games(sorted.subList(from, to).stream().map(GameMapper::toResponse).toList())
                            .totalCount((int) Math.min(dbCount, Integer.MAX_VALUE))
                            .page(page)
                            .pageSize(pageSize)
                            .build();
                }
            }
        }

        if ((query == null || query.isBlank()) && genre != null && !genre.isBlank()) {
            List<Game> cached = gameRepository.findByGenreName(genre, PageRequest.of(0, pageSize * 3));
            if (!cached.isEmpty()) {
                List<Game> sorted = new ArrayList<>(cached);
                applyOrdering(sorted, ordering);
                int from = page * pageSize;
                int to = Math.min(from + pageSize, sorted.size());
                if (from < sorted.size()) {
                    long genreTotal = gameRepository.countByGenreName(genre);
                    return GameSearchResponse.builder()
                            .games(sorted.subList(from, to).stream().map(GameMapper::toResponse).toList())
                            .totalCount((int) Math.min(genreTotal, Integer.MAX_VALUE))
                            .page(page)
                            .pageSize(pageSize)
                            .build();
                }
            }
            List<Game> broadPool = gameRepository.findRecentGames(PageRequest.of(0, pageSize * 3));
            if (!broadPool.isEmpty()) {
                List<Game> sorted = new ArrayList<>(broadPool);
                applyOrdering(sorted, ordering);
                int from = page * pageSize;
                int to = Math.min(from + pageSize, sorted.size());
                if (from < sorted.size()) {
                    return GameSearchResponse.builder()
                            .games(sorted.subList(from, to).stream().map(GameMapper::toResponse).toList())
                            .totalCount((int) Math.min(gameRepository.count(), Integer.MAX_VALUE))
                            .page(page)
                            .pageSize(pageSize)
                            .build();
                }
            }
        }

        try {
            List<IgdbGameDto> igdbResults;
            if (query != null && !query.isBlank()) {
                igdbResults = igdbApiClient.searchGames(query, pageSize, page * pageSize);
            } else if (genre != null && !genre.isBlank()) {
                igdbResults = igdbApiClient.searchByGenre(genre, pageSize, page * pageSize);
            } else if (platform != null && !platform.isBlank()) {
                igdbResults = igdbApiClient.fetchPopularGames(platform, pageSize, page * pageSize);
            } else {
                igdbResults = igdbApiClient.fetchCatalogPage(pageSize, page * pageSize);
            }

            List<GameResponse> games = igdbResults.stream()
                    .map(dto -> {
                        cacheIfAbsent(dto);
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
                    cacheIfAbsent(dto);
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
                .map(GameMapper::toResponseFromIgdb)
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
        List<String> cached = gameGenreRepository.findAllDistinctGenreNames();
        if (!cached.isEmpty()) {
            return cached;
        }
        return igdbApiClient.fetchGenres().stream()
                .map(g -> g.getName())
                .toList();
    }

    public List<String> getPlatforms() {
        return List.of("PC", "PlayStation 5", "PlayStation 4", "Xbox Series S/X", "Xbox One", "Nintendo Switch");
    }

    // ── IGDB catalog worker support ───────────────────────────────────────────

    @Transactional
    public int syncIgdbCatalogOffset(int offset, int limit) {
        List<IgdbGameDto> results = igdbApiClient.fetchCatalogPage(limit, offset);
        int cached = 0;
        for (IgdbGameDto dto : results) {
            try {
                if (gameRepository.findByIgdbId(dto.getId()).isEmpty()) {
                    cacheGame(dto);
                    cached++;
                }
            } catch (Exception e) {
                log.warn("IGDB catalog sync: failed to cache igdbId={}: {}", dto.getId(), e.getMessage());
            }
        }
        return cached;
    }

    @Transactional
    public int syncIgdbNewReleasesOffset(int offset, int limit) {
        List<IgdbGameDto> results = igdbApiClient.fetchNewReleases(limit, offset);
        int cached = 0;
        for (IgdbGameDto dto : results) {
            try {
                if (gameRepository.findByIgdbId(dto.getId()).isEmpty()) {
                    cacheGame(dto);
                    cached++;
                }
            } catch (Exception e) {
                log.warn("IGDB new releases sync: failed to cache igdbId={}: {}", dto.getId(), e.getMessage());
            }
        }
        return cached;
    }

    @Transactional
    public int enrichNextBatchFromIgdb(int limit) {
        List<Game> stubs = gameRepository.findGamesNeedingEnrichment(PageRequest.of(0, limit));
        int enriched = 0;
        for (Game game : stubs) {
            try {
                IgdbGameDto dto = igdbApiClient.fetchGameByIdForEnrichment(game.getIgdbId());
                if (game.getDescription() == null || game.getDescription().isBlank()) {
                    game.setDescription(dto.getSummary());
                }
                if (dto.getCover() != null && game.getCoverImageId() == null) {
                    game.setCoverImageId(dto.getCover().getImageId());
                }
                if (game.getGenres().isEmpty()) {
                    game.getGenres().addAll(GameMapper.toGenreEntities(dto, game));
                }
                if (game.getThemes().isEmpty()) {
                    game.getThemes().addAll(GameMapper.toThemeEntities(dto, game));
                }
                if (game.getTags().isEmpty()) {
                    game.getTags().addAll(GameMapper.toTagEntities(dto, game));
                }
                if (game.getDevelopers() == null && dto.getInvolvedCompanies() != null) {
                    String devNames = dto.getInvolvedCompanies().stream()
                            .filter(ic -> ic.isDeveloper() && ic.getCompany() != null)
                            .map(ic -> ic.getCompany().getName())
                            .filter(java.util.Objects::nonNull)
                            .reduce((a, b) -> a + "," + b)
                            .orElse(null);
                    game.setDevelopers(devNames);
                }
                game.setEnrichedAt(java.time.LocalDateTime.now());
                gameRepository.save(game);
                enriched++;
            } catch (Exception e) {
                log.warn("IGDB enrichment failed igdbId={}: {}", game.getIgdbId(), e.getMessage());
            }
        }
        return enriched;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void applyOrdering(List<Game> games, String ordering) {
        if (ordering == null || ordering.isBlank() || ordering.equals("-rating")) {
            games.sort(Comparator.comparing((Game g) -> g.getRating() == null ? BigDecimal.ZERO : g.getRating(), Comparator.reverseOrder()));
        } else if (ordering.equals("-released")) {
            games.sort(Comparator.comparing(g -> g.getReleased() == null ? "" : g.getReleased(), Comparator.reverseOrder()));
        } else if (ordering.equals("released")) {
            games.sort(Comparator.comparing(g -> g.getReleased() == null ? "" : g.getReleased()));
        } else if (ordering.equals("name")) {
            games.sort(Comparator.comparing(g -> g.getName() == null ? "" : g.getName()));
        } else if (ordering.equals("-name")) {
            games.sort(Comparator.comparing(g -> g.getName() == null ? "" : g.getName(), Comparator.reverseOrder()));
        }
    }

    private void cacheIfAbsent(IgdbGameDto dto) {
        if (gameRepository.findByIgdbId(dto.getId()).isEmpty()) {
            cacheGame(dto);
        }
    }

    private Game cacheGame(IgdbGameDto dto) {
        Game game = GameMapper.toEntity(dto);
        game.setGenres(GameMapper.toGenreEntities(dto, game));
        game.setPlatforms(GameMapper.toPlatformEntities(dto, game));
        game.setTags(GameMapper.toTagEntities(dto, game));
        game.setThemes(GameMapper.toThemeEntities(dto, game));
        game.setEnrichedAt(java.time.LocalDateTime.now());
        return gameRepository.save(game);
    }

}
