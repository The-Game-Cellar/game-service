package com.thegamecellar.gameservice.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            if (game.getTags().isEmpty()) {
                log.info("Cache hit but tags missing for game id={}, refreshing from RAWG", rawgId);
                RawgGameDto dto = rawgApiClient.fetchGameById(rawgId);
                game.getTags().addAll(GameMapper.toTagEntities(dto, game));
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
