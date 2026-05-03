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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
import static org.mockito.ArgumentMatchers.anyLong;
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
        when(gameRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        GameSearchResponse result = gameService.searchByMood("nonexistentmood", 0, 20);

        assertThat(result.getGames()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldSearchCachedGamesByMood() {
        when(gameRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cachedGame)));

        GameSearchResponse result = gameService.searchByMood("Story-driven", 0, 20);

        assertThat(result.getGames()).hasSize(1);
        assertThat(result.getGames().get(0).getName()).isEqualTo("The Witcher 3: Wild Hunt");
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldPaginateSearchByMoodResults() {
        List<Game> games = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
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
        when(gameRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(games, Pageable.ofSize(3), 5));

        GameSearchResponse page0 = gameService.searchByMood("Story-driven", 0, 3);

        assertThat(page0.getGames()).hasSize(3);
        assertThat(page0.getTotalCount()).isEqualTo(5);
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
    void shouldGroupPlatformsIntoBig4UmbrellasAndAlphabeticalOthers() {
        Platform ps5 = new Platform("PlayStation 5");
        Platform ps4 = new Platform("PlayStation 4");
        Platform xboxOne = new Platform("Xbox One");
        Platform switch_ = new Platform("Nintendo Switch");
        Platform pc = new Platform("PC");
        Platform atari = new Platform("Atari 2600");
        Platform sega = new Platform("Sega Genesis");
        when(platformRepository.findAll()).thenReturn(List.of(ps5, ps4, xboxOne, switch_, pc, atari, sega));

        var resp = gameService.getPlatformGroups();

        // Big-4 pin order: PC → PlayStation → Nintendo → Xbox.
        assertThat(resp.groups()).extracting("label")
                .containsExactly("PC", "PlayStation", "Nintendo", "Xbox");

        var pcGroup = resp.groups().get(0);
        assertThat(pcGroup.umbrella()).isFalse();
        assertThat(pcGroup.platforms()).containsExactly("PC");

        var ps = resp.groups().get(1);
        assertThat(ps.umbrella()).isTrue();
        assertThat(ps.platforms()).containsExactly("PlayStation 4", "PlayStation 5");

        // Unrecognised platforms sort alphabetically into "others", umbrella members do not appear.
        assertThat(resp.others()).containsExactly("Atari 2600", "Sega Genesis");
    }

    @Test
    void shouldOrPlatformChildrenWhenSpecGetsCommaSeparatedUmbrella() {
        Game ps4Game = Game.builder().id(1L).igdbId(101).name("Bloodborne")
                .genres(new HashSet<>()).platforms(new HashSet<>())
                .tags(new HashSet<>()).themes(new HashSet<>()).build();
        when(gameRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ps4Game), Pageable.ofSize(20), 1));

        // Comma list is the umbrella expansion sent by the new PlatformDropdown.
        GameSearchResponse result = gameService.searchGames(
                null, "PlayStation 4,PlayStation 5", null, null,
                "-rating", 0, 20, false, "main", null, null);

        // Spec accepts the multi-value filter without throwing; DB result short-circuits
        // before IGDB fallback fires, so no platform-name normalisation is needed there.
        assertThat(result.getGames()).hasSize(1);
        verify(gameRepository).findAll(any(Specification.class), any(Pageable.class));
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldReturnSearchResultsFromIgdb() {
        when(gameRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
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
        when(gameRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(cachedGames, Pageable.ofSize(20), 6));

        GameSearchResponse result = gameService.searchGames(null, null, "RPG", "-rating", 0, 20);

        assertThat(result.getGames()).hasSize(6);
        assertThat(result.getTotalCount()).isEqualTo(6);
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldFallThroughToIgdbWhenSpecQueryEmpty() {
        when(gameRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
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

    @Test
    void shouldCacheNewGamesDuringIgdbUpcomingSync() {
        when(igdbApiClient.fetchUpcomingReleases(500, 0)).thenReturn(List.of(igdbDto));
        when(gameCacheService.cacheIfAbsent(igdbDto)).thenReturn(true);

        CatalogSyncResult result = gameService.syncIgdbUpcomingOffset(0, 500);

        assertThat(result.cached()).isEqualTo(1);
        verify(igdbApiClient).fetchUpcomingReleases(500, 0);
    }

    @Test
    void shouldRefreshUpcomingRowFromIgdb() {
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(cachedGame));
        when(igdbApiClient.fetchGameById(1942)).thenReturn(igdbDto);

        boolean refreshed = gameService.refreshUpcomingRow(1942);

        assertThat(refreshed).isTrue();
        verify(gameCacheService).refreshUpcomingGame(cachedGame, igdbDto);
    }

    @Test
    void shouldReturnFalseWhenRefreshUpcomingRowMissesDb() {
        when(gameRepository.findByIgdbId(9999)).thenReturn(Optional.empty());

        boolean refreshed = gameService.refreshUpcomingRow(9999);

        assertThat(refreshed).isFalse();
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void getUpcomingReturnsEmptyWhenPoolEmpty() {
        when(gameRepository.findUpcoming(anyLong(), anyLong())).thenReturn(List.of());

        List<GameResponse> result = gameService.getUpcoming(List.of(), 90, 10, java.util.Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void getUpcomingPlatformFilterRoutesToPlatformQuery() {
        Game upcoming = upcomingGame(7777, "Far Future Game", 100);
        when(gameRepository.findUpcomingByPlatforms(anyLong(), anyLong(), anyList()))
                .thenReturn(List.of(upcoming));

        List<GameResponse> result = gameService.getUpcoming(List.of("PC"), 90, 5, java.util.Set.of());

        assertThat(result).hasSize(1);
        verify(gameRepository).findUpcomingByPlatforms(anyLong(), anyLong(), eq(List.of("pc")));
        verify(gameRepository, never()).findUpcoming(anyLong(), anyLong());
    }

    @Test
    void getUpcomingDeduplicatesAcrossPlatformJoins() {
        Game g = upcomingGame(7777, "Multi-Platform Title", 100);
        when(gameRepository.findUpcomingByPlatforms(anyLong(), anyLong(), anyList()))
                .thenReturn(List.of(g, g, g));

        List<GameResponse> result = gameService.getUpcoming(List.of("PC", "PlayStation 5"), 90, 5, java.util.Set.of());

        assertThat(result).hasSize(1);
    }

    @Test
    void getUpcomingHonoursLimitWhenPoolLarger() {
        List<Game> pool = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pool.add(upcomingGame(2000 + i, "Game " + i, 50));
        }
        when(gameRepository.findUpcoming(anyLong(), anyLong())).thenReturn(pool);

        List<GameResponse> result = gameService.getUpcoming(List.of(), 90, 5, java.util.Set.of());

        assertThat(result).hasSize(5);
    }

    @Test
    void getUpcomingFallsBackToUniformWhenAllHypesAreZeroOrNull() {
        List<Game> pool = List.of(
                upcomingGame(3001, "A", null),
                upcomingGame(3002, "B", 0),
                upcomingGame(3003, "C", null)
        );
        when(gameRepository.findUpcoming(anyLong(), anyLong())).thenReturn(pool);

        List<GameResponse> result = gameService.getUpcoming(List.of(), 90, 3, java.util.Set.of());

        assertThat(result).hasSize(3);
    }

    @Test
    void getUpcomingZeroLimitReturnsEmpty() {
        when(gameRepository.findUpcoming(anyLong(), anyLong())).thenReturn(List.of(upcomingGame(4001, "X", 100)));

        List<GameResponse> result = gameService.getUpcoming(List.of(), 90, 0, java.util.Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void getUpcomingExcludesOwnedIdsBeforeSampling() {
        Game owned = upcomingGame(5001, "Already Owned", 100);
        Game fresh = upcomingGame(5002, "Not Yet Owned", 50);
        when(gameRepository.findUpcoming(anyLong(), anyLong())).thenReturn(List.of(owned, fresh));

        List<GameResponse> result = gameService.getUpcoming(List.of(), 90, 5, java.util.Set.of(5001));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIgdbId()).isEqualTo(5002);
    }

    @Test
    void getUpcomingPlatformNamesDelegatesToRepository() {
        when(gameRepository.findDistinctUpcomingPlatformNames(anyLong()))
                .thenReturn(List.of("Nintendo Switch", "PC", "PlayStation 5"));

        List<String> names = gameService.getUpcomingPlatformNames();

        assertThat(names).containsExactly("Nintendo Switch", "PC", "PlayStation 5");
    }

    @Test
    void findUpcomingIgdbIdsDelegatesToRepository() {
        when(gameRepository.findUpcomingIgdbIds(anyLong())).thenReturn(List.of(11, 22, 33));

        List<Integer> ids = gameService.findUpcomingIgdbIds();

        assertThat(ids).containsExactly(11, 22, 33);
    }

    private Game upcomingGame(int igdbId, String name, Integer hypes) {
        long futureEpoch = java.time.Instant.now().getEpochSecond() + 30L * 24 * 3600;
        return Game.builder()
                .id((long) igdbId)
                .igdbId(igdbId)
                .name(name)
                .firstReleaseDate(futureEpoch)
                .hypes(hypes)
                .genres(new HashSet<>())
                .platforms(new HashSet<>())
                .tags(new HashSet<>())
                .themes(new HashSet<>())
                .gameModes(new HashSet<>())
                .playerPerspectives(new HashSet<>())
                .franchises(new HashSet<>())
                .collections(new HashSet<>())
                .build();
    }
}
