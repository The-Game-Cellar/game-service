package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.GameNotFoundException;
import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbNamedEntityDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.GameGenre;
import com.thegamecellar.gameservice.model.entity.GameTag;
import com.thegamecellar.gameservice.repository.GameGenreRepository;
import com.thegamecellar.gameservice.repository.GamePlatformRepository;
import com.thegamecellar.gameservice.repository.GameRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameGenreRepository gameGenreRepository;

    @Mock
    private GamePlatformRepository gamePlatformRepository;

    @Mock
    private IgdbApiClient igdbApiClient;

    @InjectMocks
    private GameService gameService;

    private Game cachedGame;
    private IgdbGameDto igdbDto;

    @BeforeEach
    void setUp() {
        GameTag tag = new GameTag();
        tag.setTagName("Story Rich");

        GameGenre genre = new GameGenre();
        genre.setGenreName("RPG");

        cachedGame = Game.builder()
                .id(1L)
                .igdbId(1942)
                .name("The Witcher 3: Wild Hunt")
                .description("A story-driven open world RPG.")
                .rating(new BigDecimal("4.66"))
                .genres(new ArrayList<>(List.of(genre)))
                .platforms(new ArrayList<>())
                .tags(new ArrayList<>(List.of(tag)))
                .themes(new ArrayList<>())
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
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        GameResponse result = gameService.getGameById(1942);

        assertThat(result.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        verify(igdbApiClient).fetchGameById(1942);
    }

    @Test
    void shouldSaveGameAfterFetch() {
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.empty());
        when(igdbApiClient.fetchGameById(1942)).thenReturn(igdbDto);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        gameService.getGameById(1942);

        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(captor.capture());
        assertThat(captor.getValue().getIgdbId()).isEqualTo(1942);
    }

    @Test
    void shouldRefetchFromIgdbIfCachedGameHasNoTags() {
        Game gameWithoutTags = Game.builder()
                .id(1L)
                .igdbId(1942)
                .name("The Witcher 3: Wild Hunt")
                .genres(new ArrayList<>())
                .platforms(new ArrayList<>())
                .tags(new ArrayList<>())
                .themes(new ArrayList<>())
                .build();

        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(gameWithoutTags));
        when(igdbApiClient.fetchGameById(1942)).thenReturn(igdbDto);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        gameService.getGameById(1942);

        verify(igdbApiClient).fetchGameById(1942);
        verify(gameRepository).save(any(Game.class));
    }

    @Test
    void shouldRefetchFromIgdbIfCachedGameHasNoGenres() {
        GameTag tag = new GameTag();
        tag.setTagName("Story Rich");

        Game gameWithoutGenres = Game.builder()
                .id(1L)
                .igdbId(1942)
                .name("The Witcher 3: Wild Hunt")
                .genres(new ArrayList<>())
                .platforms(new ArrayList<>())
                .tags(new ArrayList<>(List.of(tag)))
                .themes(new ArrayList<>())
                .build();

        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(gameWithoutGenres));
        when(igdbApiClient.fetchGameById(1942)).thenReturn(igdbDto);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        gameService.getGameById(1942);

        verify(igdbApiClient).fetchGameById(1942);
        verify(gameRepository).save(any(Game.class));
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
                    .genres(new ArrayList<>())
                    .platforms(new ArrayList<>())
                    .tags(new ArrayList<>())
                    .themes(new ArrayList<>())
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
        when(gameGenreRepository.findAllDistinctGenreNames()).thenReturn(List.of("Action", "RPG"));

        List<String> genres = gameService.getGenres();

        assertThat(genres).containsExactly("Action", "RPG");
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldFetchGenresFromIgdbIfCacheEmpty() {
        IgdbNamedEntityDto action = new IgdbNamedEntityDto();
        action.setId(1);
        action.setName("Action");

        when(gameGenreRepository.findAllDistinctGenreNames()).thenReturn(List.of());
        when(igdbApiClient.fetchGenres()).thenReturn(List.of(action));

        List<String> genres = gameService.getGenres();

        assertThat(genres).containsExactly("Action");
    }

    @Test
    void shouldReturnCuratedPlatformList() {
        List<String> platforms = gameService.getPlatforms();

        assertThat(platforms).containsExactly(
                "PC", "PlayStation 5", "PlayStation 4", "Xbox Series S/X", "Xbox One", "Nintendo Switch");
        verifyNoInteractions(igdbApiClient);
        verifyNoInteractions(gamePlatformRepository);
    }

    @Test
    void shouldReturnSearchResultsFromIgdb() {
        when(igdbApiClient.searchGames("witcher", 20, 0)).thenReturn(List.of(igdbDto));
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(cachedGame));

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
                    .genres(new ArrayList<>()).platforms(new ArrayList<>())
                    .tags(new ArrayList<>()).themes(new ArrayList<>())
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
        when(gameRepository.findRecentGames(any())).thenReturn(List.of(cachedGame));

        GameSearchResponse result = gameService.searchGames(null, null, "RPG", "-rating", 0, 20);

        assertThat(result.getGames()).hasSize(1);
        verifyNoInteractions(igdbApiClient);
    }

    @Test
    void shouldFallThroughToIgdbWhenGenreCacheEmptyAndDbEmpty() {
        when(gameRepository.findByGenreName(eq("RPG"), any(Pageable.class))).thenReturn(List.of());
        when(gameRepository.findRecentGames(any())).thenReturn(List.of());
        when(igdbApiClient.searchByGenre("RPG", 20, 0)).thenReturn(List.of(igdbDto));
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(cachedGame));

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
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.empty());
        when(gameRepository.findByIgdbId(9999)).thenReturn(Optional.empty());
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        int cached = gameService.syncIgdbCatalogOffset(0, 500);

        assertThat(cached).isEqualTo(2);
        verify(gameRepository, times(2)).save(any(Game.class));
    }

    @Test
    void shouldSkipAlreadyCachedGamesDuringIgdbCatalogSync() {
        when(igdbApiClient.fetchCatalogPage(500, 0)).thenReturn(List.of(igdbDto));
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.of(cachedGame));

        int cached = gameService.syncIgdbCatalogOffset(0, 500);

        assertThat(cached).isEqualTo(0);
        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    void shouldContinueIgdbCatalogSyncWhenOneGameFails() {
        IgdbGameDto dto2 = new IgdbGameDto();
        dto2.setId(9999);
        dto2.setName("Failing Game");

        when(igdbApiClient.fetchCatalogPage(500, 0)).thenReturn(List.of(igdbDto, dto2));
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.empty());
        when(gameRepository.findByIgdbId(9999)).thenReturn(Optional.empty());
        when(gameRepository.save(any(Game.class)))
                .thenThrow(new RuntimeException("DB error"))
                .thenAnswer(inv -> inv.getArgument(0));

        int cached = gameService.syncIgdbCatalogOffset(0, 500);

        assertThat(cached).isEqualTo(1);
    }

    @Test
    void shouldCacheNewGamesDuringIgdbNewReleasesSync() {
        when(igdbApiClient.fetchNewReleases(500, 0)).thenReturn(List.of(igdbDto));
        when(gameRepository.findByIgdbId(1942)).thenReturn(Optional.empty());
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        int cached = gameService.syncIgdbNewReleasesOffset(0, 500);

        assertThat(cached).isEqualTo(1);
        verify(igdbApiClient).fetchNewReleases(500, 0);
    }

    @Test
    void shouldEnrichStubsFromIgdb() {
        Game stub = Game.builder()
                .id(1L).igdbId(1942).name("The Witcher 3")
                .genres(new ArrayList<>()).platforms(new ArrayList<>())
                .tags(new ArrayList<>()).themes(new ArrayList<>())
                .build();

        when(gameRepository.findGamesNeedingEnrichment(any())).thenReturn(List.of(stub));
        when(igdbApiClient.fetchGameByIdForEnrichment(1942)).thenReturn(igdbDto);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        int enriched = gameService.enrichNextBatchFromIgdb(10);

        assertThat(enriched).isEqualTo(1);
        verify(igdbApiClient).fetchGameByIdForEnrichment(1942);
        verify(gameRepository).save(any(Game.class));
    }

    @Test
    void shouldContinueIgdbEnrichmentWhenOneGameFails() {
        Game stub1 = Game.builder()
                .id(1L).igdbId(1942).name("Witcher 3")
                .genres(new ArrayList<>()).platforms(new ArrayList<>())
                .tags(new ArrayList<>()).themes(new ArrayList<>())
                .build();
        Game stub2 = Game.builder()
                .id(2L).igdbId(9999).name("Other Game")
                .genres(new ArrayList<>()).platforms(new ArrayList<>())
                .tags(new ArrayList<>()).themes(new ArrayList<>())
                .build();

        when(gameRepository.findGamesNeedingEnrichment(any())).thenReturn(List.of(stub1, stub2));
        when(igdbApiClient.fetchGameByIdForEnrichment(1942)).thenThrow(new RuntimeException("IGDB error"));
        when(igdbApiClient.fetchGameByIdForEnrichment(9999)).thenReturn(igdbDto);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        int enriched = gameService.enrichNextBatchFromIgdb(10);

        assertThat(enriched).isEqualTo(1);
        verify(gameRepository, times(1)).save(any(Game.class));
    }

}
