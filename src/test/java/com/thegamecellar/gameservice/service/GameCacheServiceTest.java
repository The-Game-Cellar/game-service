package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.Genre;
import com.thegamecellar.gameservice.model.entity.Tag;
import com.thegamecellar.gameservice.model.entity.Theme;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.repository.GenreRepository;
import com.thegamecellar.gameservice.util.DerivedGenreEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameCacheServiceTest {

    private GenreRepository genreRepository;
    private DerivedGenreEngine derivedGenreEngine;
    private GameCacheService service;

    @BeforeEach
    void setUp() {
        genreRepository = mock(GenreRepository.class);
        derivedGenreEngine = mock(DerivedGenreEngine.class);
        service = new GameCacheService(
                mock(GameRepository.class),
                mock(com.thegamecellar.gameservice.repository.PlatformRepository.class),
                genreRepository,
                mock(com.thegamecellar.gameservice.repository.TagRepository.class),
                mock(com.thegamecellar.gameservice.repository.ThemeRepository.class),
                mock(com.thegamecellar.gameservice.repository.GameModeRepository.class),
                mock(com.thegamecellar.gameservice.repository.PlayerPerspectiveRepository.class),
                mock(com.thegamecellar.gameservice.repository.FranchiseRepository.class),
                mock(com.thegamecellar.gameservice.repository.GameCollectionRepository.class),
                mock(com.thegamecellar.gameservice.util.CuratedTagAllowlist.class),
                derivedGenreEngine
        );
    }

    @Test
    void applyDerivedGenres_adds_derived_genres_to_game_with_matching_tags() {
        Game game = newGame();
        game.getTags().add(tag("permadeath"));
        game.getTags().add(tag("procedural generation"));

        when(derivedGenreEngine.deriveGenres(any(), any())).thenReturn(Set.of("Roguelike"));
        when(genreRepository.findByName("Roguelike")).thenReturn(Optional.empty());
        when(genreRepository.save(any(Genre.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.applyDerivedGenres(game);

        assertThat(game.getGenres()).extracting(Genre::getName).contains("Roguelike");
        assertThat(game.getGenres()).filteredOn(g -> "Roguelike".equals(g.getName()))
                .extracting(Genre::getSource).containsOnly("DERIVED");
    }

    @Test
    void applyDerivedGenres_reuses_existing_genre_row_when_present() {
        Game game = newGame();
        game.getTags().add(tag("souls like"));
        Genre existing = derivedGenre("Soulslike");
        when(derivedGenreEngine.deriveGenres(any(), any())).thenReturn(Set.of("Soulslike"));
        when(genreRepository.findByName("Soulslike")).thenReturn(Optional.of(existing));

        service.applyDerivedGenres(game);

        assertThat(game.getGenres()).contains(existing);
        verify(genreRepository, never()).save(any(Genre.class));
    }

    @Test
    void applyDerivedGenres_does_not_touch_igdb_sourced_genres() {
        Game game = newGame();
        Genre igdbAdventure = igdbGenre("Adventure");
        Genre igdbRpg = igdbGenre("Role-playing (RPG)");
        game.getGenres().add(igdbAdventure);
        game.getGenres().add(igdbRpg);
        game.getTags().add(tag("metroidvania"));

        when(derivedGenreEngine.deriveGenres(any(), any())).thenReturn(Set.of("Metroidvania"));
        when(genreRepository.findByName("Metroidvania")).thenReturn(Optional.empty());
        when(genreRepository.save(any(Genre.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyDerivedGenres(game);

        assertThat(game.getGenres()).contains(igdbAdventure, igdbRpg);
        assertThat(game.getGenres()).extracting(Genre::getName).contains("Metroidvania");
    }

    @Test
    void applyDerivedGenres_replaces_prior_derived_when_rule_set_changed() {
        // Game previously had Roguelike applied. New rule set produces Roguelite instead.
        Game game = newGame();
        Genre staleRoguelike = derivedGenre("Roguelike");
        game.getGenres().add(staleRoguelike);
        game.getTags().add(tag("roguelite"));

        when(derivedGenreEngine.deriveGenres(any(), any())).thenReturn(Set.of("Roguelite"));
        when(genreRepository.findByName("Roguelite")).thenReturn(Optional.empty());
        when(genreRepository.save(any(Genre.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyDerivedGenres(game);

        assertThat(game.getGenres()).extracting(Genre::getName).doesNotContain("Roguelike");
        assertThat(game.getGenres()).extracting(Genre::getName).contains("Roguelite");
    }

    @Test
    void applyDerivedGenres_clears_all_derived_when_engine_returns_empty_set() {
        // Rule was removed from YAML — backfill / refresh should strip the old derived row.
        Game game = newGame();
        game.getGenres().add(derivedGenre("Soulslike"));
        game.getGenres().add(igdbGenre("Adventure"));

        when(derivedGenreEngine.deriveGenres(any(), any())).thenReturn(Set.of());

        service.applyDerivedGenres(game);

        assertThat(game.getGenres()).extracting(Genre::getName).doesNotContain("Soulslike");
        assertThat(game.getGenres()).extracting(Genre::getName).contains("Adventure");
        verify(genreRepository, never()).save(any(Genre.class));
    }

    @Test
    void applyDerivedGenres_handles_game_with_no_tags_or_themes() {
        Game game = newGame();
        when(derivedGenreEngine.deriveGenres(any(), any())).thenReturn(Set.of());

        service.applyDerivedGenres(game);

        assertThat(game.getGenres()).isEmpty();
        verify(genreRepository, never()).save(any(Genre.class));
    }

    @Test
    void applyDerivedGenres_creates_one_row_per_derived_name_when_none_exist() {
        Game game = newGame();
        game.getThemes().add(theme("Action"));
        game.getThemes().add(theme("Fantasy"));

        when(derivedGenreEngine.deriveGenres(any(), any())).thenReturn(Set.of("Action", "Fantasy"));
        when(genreRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(genreRepository.save(any(Genre.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyDerivedGenres(game);

        verify(genreRepository, times(2)).save(any(Genre.class));
        assertThat(game.getGenres()).extracting(Genre::getName).containsExactlyInAnyOrder("Action", "Fantasy");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Game newGame() {
        Game game = new Game();
        game.setIgdbId(123);
        game.setName("Test Game");
        game.setGenres(new HashSet<>());
        game.setTags(new HashSet<>());
        game.setThemes(new HashSet<>());
        return game;
    }

    private Tag tag(String name) {
        Tag t = new Tag();
        t.setName(name);
        return t;
    }

    private Theme theme(String name) {
        Theme th = new Theme();
        th.setName(name);
        return th;
    }

    private Genre igdbGenre(String name) {
        Genre g = new Genre(name);
        g.setSource("IGDB");
        return g;
    }

    private Genre derivedGenre(String name) {
        Genre g = new Genre(name, "DERIVED");
        return g;
    }
}
