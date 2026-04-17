package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.RawgApiException;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.rawg.RawgGameDto;
import com.thegamecellar.gameservice.model.dto.rawg.RawgSearchResponse;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final GameGenreRepository gameGenreRepository;
    private final GamePlatformRepository gamePlatformRepository;
    private final RawgApiClient rawgApiClient;

    @Transactional
    public GameResponse getGameById(Integer rawgId) {
        Optional<Game> cached = gameRepository.findByRawgId(rawgId);

        if (cached.isPresent()) {
            Game game = cached.get();
            if (game.getTags().isEmpty() || game.getGenres().isEmpty() || game.getDescription() == null || game.getDescription().isBlank()) {
                log.info("Cache hit but data incomplete for game id={} (tags={}, genres={}, description={}), refreshing from RAWG",
                        rawgId, game.getTags().size(), game.getGenres().size(), game.getDescription() != null ? "present" : "null");
                RawgGameDto dto = rawgApiClient.fetchGameById(rawgId);
                if (game.getTags().isEmpty()) {
                    game.getTags().addAll(GameMapper.toTagEntities(dto, game));
                }
                if (game.getGenres().isEmpty()) {
                    game.getGenres().addAll(GameMapper.toGenreEntities(dto, game));
                }
                if (game.getDescription() == null || game.getDescription().isBlank()) {
                    game.setDescription(dto.getDescriptionRaw());
                }
                return GameMapper.toResponse(gameRepository.save(game));
            }
            log.info("Cache hit for game id={}", rawgId);
            return GameMapper.toResponse(game);
        }

        log.info("Cache miss for game id={}, fetching from RAWG", rawgId);
        RawgGameDto dto = rawgApiClient.fetchGameById(rawgId);
        Game saved = cacheGame(dto);
        return GameMapper.toResponse(saved);
    }

    @Transactional
    public GameSearchResponse searchByMood(String mood, int page, int pageSize) {
        List<String> tags = MoodMapper.getTagsForMood(mood);
        if (tags.isEmpty()) {
            return GameSearchResponse.builder().games(List.of()).totalCount(0).page(page).pageSize(pageSize).build();
        }

        log.info("Searching cached games by mood={} (tags={})", mood, tags);
        List<GameResponse> games = gameRepository.findByTagNamesIn(tags).stream()
                .map(GameMapper::toResponse)
                .toList();

        int total = games.size();
        int fromIndex = Math.min(page * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<GameResponse> paged = games.subList(fromIndex, toIndex);

        return GameSearchResponse.builder()
                .games(paged)
                .totalCount(total)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre, int page, int pageSize) {
        // Cache-first for genre-only searches (used by recommendation algorithm).
        // If game_db has ≥5 cached games for this genre, serve them directly to avoid
        // hammering RAWG on every recommendation request.
        if ((query == null || query.isBlank()) && genre != null && !genre.isBlank()) {
            // Load pageSize*3 games max — avoids N+1 explosion loading thousands of EAGER collections.
            List<Game> cached = gameRepository.findByGenreName(genre, PageRequest.of(0, pageSize * 3));
            if (!cached.isEmpty()) {
                log.info("Genre cache hit: genre={}, loaded {} games (pool of {}x pageSize)", genre, cached.size(), 3);
                List<Game> shuffled = new ArrayList<>(cached);
                Collections.shuffle(shuffled);
                List<GameResponse> paged = shuffled.stream()
                        .limit(pageSize)
                        .map(GameMapper::toResponse)
                        .toList();
                return GameSearchResponse.builder()
                        .games(paged)
                        .totalCount(cached.size())
                        .page(page)
                        .pageSize(pageSize)
                        .build();
            }
            log.info("Genre cache empty for genre={}, trying broad pool fallback", genre);
            // Broad pool fallback: serve recent cached games so Recommendation Service
            // similarity scorer can rank them by genre relevance — Action/RPG games
            // score high, Racing/Sports score 0 against the user's profile.
            List<Game> broadPool = gameRepository.findRecentGames(PageRequest.of(0, pageSize * 3));
            if (!broadPool.isEmpty()) {
                log.info("Serving broad pool of {} recent games for genre='{}'", broadPool.size(), genre);
                List<Game> shuffled = new ArrayList<>(broadPool);
                Collections.shuffle(shuffled);
                return GameSearchResponse.builder()
                        .games(shuffled.stream().limit(pageSize).map(GameMapper::toResponse).toList())
                        .totalCount(broadPool.size())
                        .page(page)
                        .pageSize(pageSize)
                        .build();
            }
            log.info("DB empty, falling through to RAWG for genre={}", genre);
        }

        try {
            RawgSearchResponse rawgResponse = rawgApiClient.searchGames(query, platform, genre, page, pageSize);
            List<GameResponse> games = rawgResponse.getResults().stream()
                    .map(dto -> {
                        cacheIfAbsent(dto);
                        return GameMapper.toResponseFromRawg(dto);
                    })
                    .toList();

            return GameSearchResponse.builder()
                    .games(games)
                    .totalCount(rawgResponse.getCount())
                    .page(page)
                    .pageSize(pageSize)
                    .build();
        } catch (RawgApiException ex) {
            log.warn("RAWG search unavailable (query={}, genre={}): {}", query, genre, ex.getMessage());
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
        RawgSearchResponse rawgResponse = rawgApiClient.fetchPopularGames(platform, page);
        List<GameResponse> games = rawgResponse.getResults().stream()
                .map(dto -> {
                    cacheIfAbsent(dto);
                    return GameMapper.toResponseFromRawg(dto);
                })
                .toList();

        return GameSearchResponse.builder()
                .games(games)
                .totalCount(rawgResponse.getCount())
                .page(page)
                .pageSize(20)
                .build();
    }

    @Transactional
    public GameSearchResponse getUpcomingGames(String platform) {
        RawgSearchResponse rawgResponse = rawgApiClient.fetchUpcomingGames(platform);
        List<GameResponse> games = rawgResponse.getResults().stream()
                .map(GameMapper::toResponseFromRawg)
                .toList();

        return GameSearchResponse.builder()
                .games(games)
                .totalCount(rawgResponse.getCount())
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
        return rawgApiClient.fetchGenres().getResults().stream()
                .map(g -> g.getName())
                .toList();
    }

    public List<String> getPlatforms() {
        List<String> cached = gamePlatformRepository.findAllDistinctPlatformNames();
        if (!cached.isEmpty()) {
            return cached;
        }
        return rawgApiClient.fetchPlatforms().getResults().stream()
                .map(p -> p.getName())
                .toList();
    }

    private void cacheIfAbsent(RawgGameDto dto) {
        if (gameRepository.findByRawgId(dto.getId()).isEmpty()) {
            cacheGame(dto);
        }
    }

    private Game cacheGame(RawgGameDto dto) {
        Game game = GameMapper.toEntity(dto);
        game.setGenres(GameMapper.toGenreEntities(dto, game));
        game.setPlatforms(GameMapper.toPlatformEntities(dto, game));
        game.setTags(GameMapper.toTagEntities(dto, game));
        return gameRepository.save(game);
    }
}
