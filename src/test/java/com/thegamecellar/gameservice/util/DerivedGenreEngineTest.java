package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.entity.Genre;
import com.thegamecellar.gameservice.repository.GenreRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DerivedGenreEngineTest {

    @Test
    void load_succeeds_when_no_igdb_collisions() {
        DerivedGenreEngine engine = newLoadedEngine();

        assertThat(engine.ruleCount()).isGreaterThan(0);
        assertThat(engine.getRuleNames())
                .contains("Roguelike", "Metroidvania", "Survival Horror", "Horror",
                        "Action", "Sci-fi", "Fantasy", "Sandbox", "Stealth", "Survival");
    }

    @Test
    void load_throws_when_rule_name_collides_with_existing_igdb_genre() {
        GenreRepository repo = mock(GenreRepository.class);
        when(repo.findByName(anyString())).thenReturn(Optional.empty());
        Genre igdbAction = new Genre("Action");
        igdbAction.setSource("IGDB");
        when(repo.findByName("Action")).thenReturn(Optional.of(igdbAction));

        DerivedGenreEngine engine = new DerivedGenreEngine(repo);

        assertThatThrownBy(engine::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Action");
    }

    @Test
    void load_does_not_throw_when_existing_genre_is_already_derived() {
        // Re-applying a YAML rule that produced rows in a previous run is fine.
        GenreRepository repo = mock(GenreRepository.class);
        when(repo.findByName(anyString())).thenReturn(Optional.empty());
        Genre derivedRoguelike = new Genre("Roguelike");
        derivedRoguelike.setSource("DERIVED");
        when(repo.findByName("Roguelike")).thenReturn(Optional.of(derivedRoguelike));

        DerivedGenreEngine engine = new DerivedGenreEngine(repo);
        engine.load();

        assertThat(engine.getRuleNames()).contains("Roguelike");
    }

    @Test
    void deriveGenres_returns_empty_set_when_no_rule_matches() {
        DerivedGenreEngine engine = newLoadedEngine();
        Set<String> result = engine.deriveGenres(Set.of("totally-unrelated"), Set.of("History"));
        assertThat(result).isEmpty();
    }

    @Test
    void deriveGenres_matches_tagsAny_for_roguelike() {
        DerivedGenreEngine engine = newLoadedEngine();
        // permadeath is one of Roguelike's tagsAny.
        Set<String> result = engine.deriveGenres(Set.of("permadeath"), Set.of());
        assertThat(result).contains("Roguelike");
    }

    @Test
    void deriveGenres_matches_themesAny_for_action() {
        DerivedGenreEngine engine = newLoadedEngine();
        Set<String> result = engine.deriveGenres(Set.of(), Set.of("Action"));
        assertThat(result).contains("Action");
    }

    @Test
    void deriveGenres_matches_themesAny_for_scifi_using_science_fiction_theme() {
        DerivedGenreEngine engine = newLoadedEngine();
        // Rule name is "Sci-fi" but matches against the IGDB theme "Science fiction".
        Set<String> result = engine.deriveGenres(Set.of(), Set.of("Science fiction"));
        assertThat(result).contains("Sci-fi");
    }

    @Test
    void deriveGenres_matches_themesAny_for_fantasy() {
        DerivedGenreEngine engine = newLoadedEngine();
        Set<String> result = engine.deriveGenres(Set.of(), Set.of("Fantasy"));
        assertThat(result).contains("Fantasy");
    }

    @Test
    void deriveGenres_normalises_surface_variants_for_survival_horror() {
        DerivedGenreEngine engine = newLoadedEngine();
        // Surface variants of "survival horror" must collapse to one normalized key.
        assertThat(engine.deriveGenres(Set.of("Survival-Horror"), Set.of())).contains("Survival Horror");
        assertThat(engine.deriveGenres(Set.of("survival horrors"), Set.of())).contains("Survival Horror");
        assertThat(engine.deriveGenres(Set.of("survival horror"), Set.of())).contains("Survival Horror");
    }

    @Test
    void deriveGenres_matches_survival_horror_via_psychological_horror_tag() {
        DerivedGenreEngine engine = newLoadedEngine();
        // IGDB tags many survival-horror titles (RE7, Silent Hill etc) with
        // "psychological horror" instead of "survival horror"; both should match.
        assertThat(engine.deriveGenres(Set.of("psychological horror"), Set.of())).contains("Survival Horror");
    }

    @Test
    void deriveGenres_matches_roguelike_via_action_roguelike_tag() {
        DerivedGenreEngine engine = newLoadedEngine();
        assertThat(engine.deriveGenres(Set.of("action roguelike"), Set.of())).contains("Roguelike");
    }

    @Test
    void deriveGenres_matches_roguelike_via_deckbuilder_variant() {
        DerivedGenreEngine engine = newLoadedEngine();
        assertThat(engine.deriveGenres(Set.of("roguelike deckbuilder"), Set.of())).contains("Roguelike");
    }

    @Test
    void deriveGenres_matches_roguelite_via_action_roguelite_tag() {
        DerivedGenreEngine engine = newLoadedEngine();
        assertThat(engine.deriveGenres(Set.of("action roguelite"), Set.of())).contains("Roguelite");
    }

    @Test
    void deriveGenres_matches_survival_horror_via_cosmic_horror_tag() {
        DerivedGenreEngine engine = newLoadedEngine();
        assertThat(engine.deriveGenres(Set.of("cosmic horror"), Set.of())).contains("Survival Horror");
    }

    @Test
    void deriveGenres_matches_horror_genre_via_horror_theme() {
        DerivedGenreEngine engine = newLoadedEngine();
        assertThat(engine.deriveGenres(Set.of(), Set.of("Horror"))).contains("Horror");
    }

    @Test
    void deriveGenres_horror_and_survival_horror_fire_independently() {
        // RE7-style game with Horror theme + psychological horror tag matches both rules.
        DerivedGenreEngine engine = newLoadedEngine();
        Set<String> result = engine.deriveGenres(Set.of("psychological horror"), Set.of("Horror"));
        assertThat(result).contains("Horror", "Survival Horror");
    }

    @Test
    void deriveGenres_matches_sandbox_stealth_survival_themes() {
        DerivedGenreEngine engine = newLoadedEngine();
        assertThat(engine.deriveGenres(Set.of(), Set.of("Sandbox"))).contains("Sandbox");
        assertThat(engine.deriveGenres(Set.of(), Set.of("Stealth"))).contains("Stealth");
        assertThat(engine.deriveGenres(Set.of(), Set.of("Survival"))).contains("Survival");
    }

    @Test
    void deriveGenres_can_match_multiple_rules_for_same_game() {
        DerivedGenreEngine engine = newLoadedEngine();
        Set<String> result = engine.deriveGenres(Set.of("roguelike"), Set.of("Fantasy"));
        assertThat(result).containsExactlyInAnyOrder("Roguelike", "Fantasy");
    }

    @Test
    void deriveGenres_handles_null_inputs_gracefully() {
        DerivedGenreEngine engine = newLoadedEngine();
        assertThat(engine.deriveGenres(null, null)).isEmpty();
    }

    @Test
    void deriveGenres_returns_empty_when_engine_not_loaded() {
        // No load() call → no rules registered → engine is a no-op.
        DerivedGenreEngine engine = new DerivedGenreEngine(mock(GenreRepository.class));
        assertThat(engine.deriveGenres(Set.of("permadeath"), Set.of("Action"))).isEmpty();
    }

    @Test
    void isDerivedName_returns_true_for_loaded_rule_names() {
        DerivedGenreEngine engine = newLoadedEngine();
        assertThat(engine.isDerivedName("Roguelike")).isTrue();
        assertThat(engine.isDerivedName("Adventure")).isFalse();
    }

    private DerivedGenreEngine newLoadedEngine() {
        GenreRepository repo = mock(GenreRepository.class);
        when(repo.findByName(anyString())).thenReturn(Optional.empty());
        DerivedGenreEngine engine = new DerivedGenreEngine(repo);
        engine.load();
        return engine;
    }
}
