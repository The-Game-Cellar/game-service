package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.GameNotFoundException;
import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbNamedEntityDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.Genre;
import com.thegamecellar.gameservice.model.entity.Platform;
import com.thegamecellar.gameservice.model.entity.Tag;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.repository.GenreRepository;
import com.thegamecellar.gameservice.repository.PlatformRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private PlatformRepository platformRepository;

    @Mock
    private GameCacheService gameCacheService;

    @Mock
    private IgdbApiClient igdbApiClient;

    @InjectMocks
    private GameService gameService;

    private Game cachedGame;
    private IgdbGameDto igdbDto;

    @BeforeEach
    void setUp() {
        Tag tag = new Tag("Story Rich");
        Genre genre = new Genre("RPG");

        cachedGame = Game.builder()
                .id(1L)
                .igdbId(1942)
                .name("The Witcher 3: Wild Hunt")
                .description("A story-driven open world RPG.")
                .rating(new BigDecimal("4.66"))
                .genres(new HashSet<>(Set.of(genre)))
                .platforms(new HashSet<>())
                .tags(new HashSet<>(Set.of(tag)))
                .themes(new HashSet<>())
                .developers("CD Projekt Red")
                .build();

        IgdbNamedEntityDto igdbTag = new IgdbNamedEntityDto();
        igdbTag.setId(10);
        igdbTag.setName("Story Rich");

        IgdbNamedEntityDto igdbGenre = new IgdbNamedEntityDto();
        igdbGenre.setId(12);
        igdbGenre.setName("RPG");

        igdbDto = new IgdbGameDto();
        igdbDto.setId(1942);
        igdbDto.setName("The Witcher 3: Wild Hunt");
        igdbDto.setAggregatedRating(93.2);
        igdbDto.setKeywords(List.of(igdbTag));
        igdbDto.setGenres(List.of(igdbGenre));
        igdbDto.setPlatforms(List.of());
        igdbDto.setThemes(List.of());
    }

    @Test
    void shouldReturnCachedGameIfExists() {
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(cachedGame));

        GameResponse result = gameService.getGameById(1942);

        assertThat(result.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        verify(igdbApiClient, never()).fetchGameById(anyInt());
    }

    @Test
    void shouldFetchFromIgdbIfNotCached() {
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.empty());
        when(igdbApiClient.fetchGameById(1942)).thenReturn(igdbDto);
        when(gameCacheService.cacheGame(igdbDto)).thenReturn(cachedGame);

        GameResponse result = gameService.getGameById(1942);

        assertThat(result.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        verify(igdbApiClient).fetchGameById(1942);
    }

    @Test
    void shouldDelegateSaveToGameCacheServiceAfterFetch() {
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.empty());
        when(igdbApiClient.fetchGameById(1942)).thenReturn(igdbDto);
        when(gameCacheService.cacheGame(igdbDto)).thenReturn(cachedGame);

        gameService.getGameById(1942);

        verify(gameCacheService).cacheGame(igdbDto);
    }

    @Test
    void shouldRefetchFromIgdbIfCachedGameHasNoTags() {
        Game gameWithoutTags = Game.builder()
                .id(1L)
                .igdbId(1942)
                .name("The Witcher 3: Wild Hunt")
                .genres(new HashSet<>())
                .platforms(new HashSet<>())
                .tags(new HashSet<>())
                .themes(new HashSet<>())
                .build();

        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(gameWithoutTags));
        when(igdbApiClient.fetchGameById(1942)).thenReturn(igdbDto);
        when(gameCacheService.refreshStaleGame(any(Game.class), any(IgdbGameDto.class))).thenReturn(cachedGame);

        gameService.getGameById(1942);

        verify(igdbApiClient).fetchGameById(1942);
        verify(gameCacheService).refreshStaleGame(any(Game.class), any(IgdbGameDto.class));
    }

    @Test
    void shouldRefetchFromIgdbIfCachedGameHasNoGenres() {
        Tag tag = new Tag("Story Rich");

        Game gameWithoutGenres = Game.builder()
                .id(1L)
                .igdbId(1942)
                .name("The Witcher 3: Wild Hunt")
                .genres(new HashSet<>())
                .platforms(new HashSet<>())
                .tags(new HashSet<>(Set.of(tag)))
                .themes(new HashSet<>())
                .build();

        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(gameWithoutGenres));
        when(igdbApiClient.fetchGameById(1942)).thenReturn(igdbDto);
        when(gameCacheService.refreshStaleGame(any(Game.class), any(IgdbGameDto.class))).thenReturn(cachedGame);

        gameService.getGameById(1942);

        verify(igdbApiClient).fetchGameById(1942);
        verify(gameCacheService).refreshStaleGame(any(Game.class), any(IgdbGameDto.class));
    }

    @Test
    void shouldNotRefetchFromIgdbIfCachedGameHasTags() {
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(cachedGame));

        gameService.getGameById(1942);

        verify(igdbApiClient, never()).fetchGameById(anyInt());
    }

    @Test
    void shouldPropagateGameNotFoundExceptionFromIgdb() {
        when(gameRepository.findByIgdbId(9999)).thenReturn(Optional.empty());
        when(igdbApiClient.fetchGameById(9999)).thenThrow(new GameNotFoundException(9999));

        assertThatThrownBy(() -> gameService.getGameById(9999))
                .isInstanceOf(GameNotFoundException.class)
                .hasMessageContaining("9999");
    }

    @Test
    void shouldPropagateIgdbApiException() {
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.empty());
        when(igdbApiClient.fetchGameById(1942)).thenThrow(new IgdbApiException("API error"));

        assertThatThrownBy(() -> gameService.getGameById(1942))
                .isInstanceOf(IgdbApiException.class);
    }

    @Test
    void shouldReturnEmptyResultForUnknownMood() {
        GameSearchResponse result = gameService.searchByMood("nonexistentmood", 0, 20);

        assertThat(result.getGames()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldSearchCachedGamesByMood() {
        when(gameRepository.findByTagNamesIn(anyList())).thenReturn(List.of(cachedGame));

        GameSearchResponse result = gameService.searchByMood("Story-driven", 0, 20);

        assertThat(result.getGames()).hasSize(1);
        assertThat(result.getGames().get(0).getName()).isEqualTo("The Witcher 3: Wild Hunt");
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldPaginateSearchByMoodResults() {
        List<Game> games = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Game g = Game.builder()
                    .id((long) i)
                    .igdbId(i)
                    .name("Game " + i)
                    .genres(new HashSet<>())
                    .platforms(new HashSet<>())
                    .tags(new HashSet<>())
                    .themes(new HashSet<>())
                    .build();
            games.add(g);
        }
        when(gameRepository.findByTagNamesIn(anyList())).thenReturn(games);

        GameSearchResponse page0 = gameService.searchByMood("Story-driven", 0, 3);
        GameSearchResponse page1 = gameService.searchByMood("Story-driven", 1, 3);

        assertThat(page0.getGames()).hasSize(3);
        assertThat(page1.getGames()).hasSize(2);
    }

    @Test
    void shouldReturnCachedGenresIfAvailable() {
        when(genreRepository.findAllNames()).thenReturn(List.of("Action", "RPG"));

        List<String> genres = gameService.getGenres();

        assertThat(genres).containsExactly("Action", "RPG");
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldFetchGenresFromIgdbIfCacheEmpty() {
        IgdbNamedEntityDto action = new IgdbNamedEntityDto();
        action.setId(1);
        action.setName("Action");

        when(genreRepository.findAllNames()).thenReturn(List.of());
        when(igdbApiClient.fetchGenres()).thenReturn(List.of(action));

        List<String> genres = gameService.getGenres();

        assertThat(genres).containsExactly("Action");
    }

    @Test
    void shouldReturnPlatformsFromDb_sortedAlphabetically() {
        Platform pc = new Platform("PC");
        Platform ps5 = new Platform("PlayStation 5");
        Platform xbox = new Platform("Xbox One");
        when(platformRepository.findAll()).thenReturn(List.of(ps5, xbox, pc));

        List<String> platforms = gameService.getPlatforms();

        assertThat(platforms).containsExactly("PC", "PlayStation 5", "Xbox One");
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldReturnSearchResultsFromIgdb() {
        when(igdbApiClient.searchGames("witcher", 20, 0)).thenReturn(List.of(igdbDto));

        GameSearchResponse result = gameService.searchGames("witcher", null, null, "-rating", 0, 20);

        assertThat(result.getGames()).hasSize(1);
        assertThat(result.getGames().get(0).getName()).isEqualTo("The Witcher 3: Wild Hunt");
    }

    @Test
    void shouldServeGenreSearchFromCacheWhenEnoughGamesPresent() {
        List<Game> cachedGames = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            cachedGames.add(Game.builder()
                    .id((long) i).igdbId(i).name("RPG Game " + i)
                    .genres(new HashSet<>()).platforms(new HashSet<>())
                    .tags(new HashSet<>()).themes(new HashSet<>())
                    .build());
        }
        when(gameRepository.findByGenreName(eq("RPG"), any(Pageable.class))).thenReturn(cachedGames);
        when(gameRepository.countByGenreName("RPG")).thenReturn(6L);

        GameSearchResponse result = gameService.searchGames(null, null, "RPG", "-rating", 0, 20);

        assertThat(result.getGames()).hasSize(6);
        assertThat(result.getTotalCount()).isEqualTo(6);
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldServeBroadPoolWhenGenreCacheEmptyButDbHasGames() {
        when(gameRepository.findByGenreName(eq("RPG"), any(Pageable.class))).thenReturn(List.of());
        when(gameRepository.count()).thenReturn(1L);
        when(gameRepository.findAll(any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(cachedGame)));

        GameSearchResponse result = gameService.searchGames(null, null, "RPG", "-rating", 0, 20);

        assertThat(result.getGames()).hasSize(1);
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldFallThroughToIgdbWhenGenreCacheEmptyAndDbEmpty() {
        when(gameRepository.findByGenreName(eq("RPG"), any(Pageable.class))).thenReturn(List.of());
        when(gameRepository.count()).thenReturn(0L);
        when(igdbApiClient.searchByGenre("RPG", 20, 0)).thenReturn(List.of(igdbDto));

        GameSearchResponse result = gameService.searchGames(null, null, "RPG", "-rating", 0, 20);

        assertThat(result.getGames()).hasSize(1);
        verify(igdbApiClient).searchByGenre("RPG", 20, 0);
    }

    // ── IGDB catalog worker path ──────────────────────────────────────────────

    @Test
    void shouldCacheNewGamesDuringIgdbCatalogSync() {
        IgdbGameDto dto2 = new IgdbGameDto();
        dto2.setId(9999);
        dto2.setName("Another Game");

        when(igdbApiClient.fetchCatalogPage(500, 0)).thenReturn(List.of(igdbDto, dto2));
        when(gameCacheService.cacheIfAbsent(igdbDto)).thenReturn(true);
        when(gameCacheService.cacheIfAbsent(dto2)).thenReturn(true);

        CatalogSyncResult result = gameService.syncIgdbCatalogOffset(0, 500);

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.cached()).isEqualTo(2);
        verify(gameCacheService, times(2)).cacheIfAbsent(any());
    }

    @Test
    void shouldReportFetchedButZeroCachedWhenAllAlreadyInDb() {
        when(igdbApiClient.fetchCatalogPage(500, 0)).thenReturn(List.of(igdbDto));
        when(gameCacheService.cacheIfAbsent(igdbDto)).thenReturn(false);

        CatalogSyncResult result = gameService.syncIgdbCatalogOffset(0, 500);

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.cached()).isEqualTo(0);
        verify(gameCacheService).cacheIfAbsent(igdbDto);
    }

    @Test
    void shouldContinueIgdbCatalogSyncWhenOneGameFails() {
        IgdbGameDto dto2 = new IgdbGameDto();
        dto2.setId(9999);
        dto2.setName("Failing Game");

        when(igdbApiClient.fetchCatalogPage(500, 0)).thenReturn(List.of(igdbDto, dto2));
        when(gameCacheService.cacheIfAbsent(igdbDto)).thenThrow(new RuntimeException("DB error"));
        when(gameCacheService.cacheIfAbsent(dto2)).thenReturn(true);

        CatalogSyncResult result = gameService.syncIgdbCatalogOffset(0, 500);

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.cached()).isEqualTo(1);
    }

    @Test
    void shouldCacheNewGamesDuringIgdbNewReleasesSync() {
        when(igdbApiClient.fetchNewReleases(500, 0)).thenReturn(List.of(igdbDto));
        when(gameCacheService.cacheIfAbsent(igdbDto)).thenReturn(true);

        CatalogSyncResult result = gameService.syncIgdbNewReleasesOffset(0, 500);

        assertThat(result.cached()).isEqualTo(1);
        verify(igdbApiClient).fetchNewReleases(500, 0);
    }
}
