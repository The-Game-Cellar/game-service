package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.GameNotFoundException;
import com.thegamecellar.gameservice.exception.RawgApiException;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.rawg.RawgGameDto;
import com.thegamecellar.gameservice.model.dto.rawg.RawgNamedEntity;
import com.thegamecellar.gameservice.model.dto.rawg.RawgSearchResponse;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private RawgApiClient rawgApiClient;

    @InjectMocks
    private GameService gameService;

    private Game cachedGame;
    private RawgGameDto rawgDto;

    @BeforeEach
    void setUp() {
        GameTag tag = new GameTag();
        tag.setTagName("Story Rich");

        GameGenre genre = new GameGenre();
        genre.setGenreName("RPG");

        cachedGame = Game.builder()
                .id(1L)
                .rawgId(3328)
                .name("The Witcher 3: Wild Hunt")
                .rating(new BigDecimal("4.66"))
                .genres(new ArrayList<>(List.of(genre)))
                .platforms(new ArrayList<>())
                .tags(new ArrayList<>(List.of(tag)))
                .build();

        RawgNamedEntity rawgTag = new RawgNamedEntity();
        rawgTag.setName("Story Rich");

        RawgNamedEntity rawgGenre = new RawgNamedEntity();
        rawgGenre.setName("RPG");

        rawgDto = new RawgGameDto();
        rawgDto.setId(3328);
        rawgDto.setName("The Witcher 3: Wild Hunt");
        rawgDto.setRating(new BigDecimal("4.66"));
        rawgDto.setTags(List.of(rawgTag));
        rawgDto.setGenres(List.of(rawgGenre));
        rawgDto.setPlatforms(List.of());
    }

    @Test
    void shouldReturnCachedGameIfExists() {
        when(gameRepository.findByRawgId(3328)).thenReturn(Optional.of(cachedGame));

        GameResponse result = gameService.getGameById(3328);

        assertThat(result.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        verify(rawgApiClient, never()).fetchGameById(any());
    }

    @Test
    void shouldFetchFromRawgIfNotCached() {
        when(gameRepository.findByRawgId(3328)).thenReturn(Optional.empty());
        when(rawgApiClient.fetchGameById(3328)).thenReturn(rawgDto);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        GameResponse result = gameService.getGameById(3328);

        assertThat(result.getName()).isEqualTo("The Witcher 3: Wild Hunt");
        verify(rawgApiClient).fetchGameById(3328);
    }

    @Test
    void shouldSaveGameAfterFetch() {
        when(gameRepository.findByRawgId(3328)).thenReturn(Optional.empty());
        when(rawgApiClient.fetchGameById(3328)).thenReturn(rawgDto);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        gameService.getGameById(3328);

        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(captor.capture());
        assertThat(captor.getValue().getRawgId()).isEqualTo(3328);
    }

    @Test
    void shouldRefetchFromRawgIfCachedGameHasNoTags() {
        Game gameWithoutTags = Game.builder()
                .id(1L)
                .rawgId(3328)
                .name("The Witcher 3: Wild Hunt")
                .genres(new ArrayList<>())
                .platforms(new ArrayList<>())
                .tags(new ArrayList<>())
                .build();

        when(gameRepository.findByRawgId(3328)).thenReturn(Optional.of(gameWithoutTags));
        when(rawgApiClient.fetchGameById(3328)).thenReturn(rawgDto);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        gameService.getGameById(3328);

        verify(rawgApiClient).fetchGameById(3328);
        verify(gameRepository).save(any(Game.class));
    }

    @Test
    void shouldNotRefetchFromRawgIfCachedGameHasTags() {
        when(gameRepository.findByRawgId(3328)).thenReturn(Optional.of(cachedGame));

        gameService.getGameById(3328);

        verify(rawgApiClient, never()).fetchGameById(any());
    }

    @Test
    void shouldPropagateGameNotFoundExceptionFromRawg() {
        when(gameRepository.findByRawgId(9999)).thenReturn(Optional.empty());
        when(rawgApiClient.fetchGameById(9999)).thenThrow(new GameNotFoundException(9999));

        assertThatThrownBy(() -> gameService.getGameById(9999))
                .isInstanceOf(GameNotFoundException.class)
                .hasMessageContaining("9999");
    }

    @Test
    void shouldPropagateRawgApiException() {
        when(gameRepository.findByRawgId(3328)).thenReturn(Optional.empty());
        when(rawgApiClient.fetchGameById(3328)).thenThrow(new RawgApiException("API error", new RuntimeException()));

        assertThatThrownBy(() -> gameService.getGameById(3328))
                .isInstanceOf(RawgApiException.class);
    }

    @Test
    void shouldReturnEmptyResultForUnknownMood() {
        GameSearchResponse result = gameService.searchByMood("nonexistentmood", 0, 20);

        assertThat(result.getGames()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
        verifyNoInteractions(rawgApiClient);
    }

    @Test
    void shouldSearchCachedGamesByMood() {
        when(gameRepository.findByTagNamesIn(anyList())).thenReturn(List.of(cachedGame));

        GameSearchResponse result = gameService.searchByMood("Story-driven", 0, 20);

        assertThat(result.getGames()).hasSize(1);
        assertThat(result.getGames().get(0).getName()).isEqualTo("The Witcher 3: Wild Hunt");
        verifyNoInteractions(rawgApiClient);
    }

    @Test
    void shouldPaginateSearchByMoodResults() {
        List<Game> games = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Game g = Game.builder()
                    .id((long) i)
                    .rawgId(i)
                    .name("Game " + i)
                    .genres(new ArrayList<>())
                    .platforms(new ArrayList<>())
                    .tags(new ArrayList<>())
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
        verifyNoInteractions(rawgApiClient);
    }

    @Test
    void shouldFetchGenresFromRawgIfCacheEmpty() {
        RawgNamedEntity action = new RawgNamedEntity();
        action.setName("Action");
        com.thegamecellar.gameservice.model.dto.rawg.RawgListResponse listResponse =
                new com.thegamecellar.gameservice.model.dto.rawg.RawgListResponse();
        listResponse.setResults(List.of(action));

        when(gameGenreRepository.findAllDistinctGenreNames()).thenReturn(List.of());
        when(rawgApiClient.fetchGenres()).thenReturn(listResponse);

        List<String> genres = gameService.getGenres();

        assertThat(genres).containsExactly("Action");
    }

    @Test
    void shouldReturnCachedPlatformsIfAvailable() {
        when(gamePlatformRepository.findAllDistinctPlatformNames()).thenReturn(List.of("PC", "PlayStation 5"));

        List<String> platforms = gameService.getPlatforms();

        assertThat(platforms).containsExactly("PC", "PlayStation 5");
        verifyNoInteractions(rawgApiClient);
    }

    @Test
    void shouldReturnSearchResultsFromRawg() {
        RawgSearchResponse rawgResponse = new RawgSearchResponse();
        rawgResponse.setCount(1);
        rawgResponse.setResults(List.of(rawgDto));

        when(rawgApiClient.searchGames("witcher", null, null, 0, 20)).thenReturn(rawgResponse);
        when(gameRepository.findByRawgId(3328)).thenReturn(Optional.of(cachedGame));

        GameSearchResponse result = gameService.searchGames("witcher", null, null, 0, 20);

        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getGames()).hasSize(1);
        assertThat(result.getGames().get(0).getName()).isEqualTo("The Witcher 3: Wild Hunt");
    }
}
