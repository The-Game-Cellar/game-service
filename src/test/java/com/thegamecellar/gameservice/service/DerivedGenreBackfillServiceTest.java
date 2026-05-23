package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.Genre;
import com.thegamecellar.gameservice.model.entity.Tag;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.util.DerivedGenreEngine;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DerivedGenreBackfillServiceTest {

    private GameRepository gameRepository;
    private GameCacheService gameCacheService;
    private DerivedGenreEngine engine;
    private EntityManager entityManager;
    private DerivedGenreBackfillService service;

    @BeforeEach
    void setUp() throws Exception {
        gameRepository = mock(GameRepository.class);
        gameCacheService = mock(GameCacheService.class);
        engine = mock(DerivedGenreEngine.class);
        entityManager = mock(EntityManager.class);

        service = new DerivedGenreBackfillService(gameRepository, gameCacheService, engine);

        Field emField = DerivedGenreBackfillService.class.getDeclaredField("entityManager");
        emField.setAccessible(true);
        emField.set(service, entityManager);
    }

    @Test
    void backfill_returns_skipped_when_engine_has_no_rules() {
        when(engine.ruleCount()).thenReturn(0);

        Map<String, Object> result = service.backfill();

        assertThat(result).containsEntry("skipped", true);
        verify(gameRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void backfill_walks_all_pages_and_applies_derived_per_game() {
        when(engine.ruleCount()).thenReturn(8);
        when(engine.getRuleNames()).thenReturn(Set.of("Roguelike", "Action"));

        Game g1 = newGame(1, "Hades");
        Game g2 = newGame(2, "Witcher 3");
        Page<Game> page = new PageImpl<>(List.of(g1, g2));
        when(gameRepository.findAll(any(Pageable.class))).thenReturn(page);

        // applyDerivedGenres mutates the game in-place to add Roguelike to g1.
        org.mockito.stubbing.Answer<Void> apply = invocation -> {
            Game game = invocation.getArgument(0);
            if (game.getIgdbId() == 1) {
                game.getGenres().add(derivedGenre("Roguelike"));
            }
            return null;
        };
        org.mockito.Mockito.doAnswer(apply).when(gameCacheService).applyDerivedGenres(any(Game.class));

        Map<String, Object> result = service.backfill();

        verify(gameCacheService).applyDerivedGenres(g1);
        verify(gameCacheService).applyDerivedGenres(g2);
        assertThat(result).containsEntry("examined", 2L);
        assertThat(result).containsEntry("updated", 1L);
        assertThat(result).containsEntry("derivedRowsAdded", 1L);
        assertThat(result).containsEntry("derivedRowsRemoved", 0L);
        assertThat(result.get("sampleAdded")).asList().anyMatch(s -> s.toString().contains("Hades"));
    }

    @Test
    void backfill_only_saves_games_where_derived_set_changed() {
        when(engine.ruleCount()).thenReturn(8);
        when(engine.getRuleNames()).thenReturn(Set.of());

        Game stable = newGame(1, "Stable Game");
        stable.getGenres().add(derivedGenre("Action"));
        Page<Game> page = new PageImpl<>(List.of(stable));
        when(gameRepository.findAll(any(Pageable.class))).thenReturn(page);

        // applyDerivedGenres simulates idempotent re-application; replaces Action with Action.
        org.mockito.stubbing.Answer<Void> apply = invocation -> {
            Game game = invocation.getArgument(0);
            game.getGenres().removeIf(g -> "DERIVED".equals(g.getSource()));
            game.getGenres().add(derivedGenre("Action"));
            return null;
        };
        org.mockito.Mockito.doAnswer(apply).when(gameCacheService).applyDerivedGenres(any(Game.class));

        service.backfill();

        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    void backfill_counts_added_and_removed_when_rule_set_diverged() {
        when(engine.ruleCount()).thenReturn(8);
        when(engine.getRuleNames()).thenReturn(Set.of());

        Game game = newGame(1, "Stale Game");
        game.getGenres().add(derivedGenre("Roguelike"));
        Page<Game> page = new PageImpl<>(List.of(game));
        when(gameRepository.findAll(any(Pageable.class))).thenReturn(page);

        // applyDerivedGenres replaces Roguelike with Roguelite.
        org.mockito.stubbing.Answer<Void> apply = invocation -> {
            Game g = invocation.getArgument(0);
            g.getGenres().removeIf(genre -> "DERIVED".equals(genre.getSource()));
            g.getGenres().add(derivedGenre("Roguelite"));
            return null;
        };
        org.mockito.Mockito.doAnswer(apply).when(gameCacheService).applyDerivedGenres(any(Game.class));

        Map<String, Object> result = service.backfill();

        assertThat(result).containsEntry("derivedRowsAdded", 1L);
        assertThat(result).containsEntry("derivedRowsRemoved", 1L);
        verify(gameRepository, times(1)).save(game);
    }

    @Test
    void backfill_flushes_and_clears_per_page() {
        when(engine.ruleCount()).thenReturn(8);
        when(engine.getRuleNames()).thenReturn(Set.of());
        Page<Game> page = new PageImpl<>(List.of(newGame(1, "Test")));
        when(gameRepository.findAll(any(Pageable.class))).thenReturn(page);

        service.backfill();

        verify(entityManager, atLeastOnce()).flush();
        verify(entityManager, atLeastOnce()).clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Game newGame(int igdbId, String name) {
        Game g = new Game();
        g.setIgdbId(igdbId);
        g.setName(name);
        g.setGenres(new HashSet<>());
        g.setTags(new HashSet<>());
        g.setThemes(new HashSet<>());
        return g;
    }

    private Genre derivedGenre(String name) {
        return new Genre(name, "DERIVED");
    }

    @SuppressWarnings("unused")
    private Tag tag(String name) {
        Tag t = new Tag();
        t.setName(name);
        return t;
    }
}
