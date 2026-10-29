package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.GameSimilarity;
import com.thegamecellar.gameservice.model.entity.Tag;
import com.thegamecellar.gameservice.support.GameServiceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Every @Query in the catalog repositories, run against Postgres with the schema the migrations build.
class GameRepositoryPostgresTest extends GameServiceTestBase {

    @Autowired private GameSimilarityRepository similarities;
    @Autowired private GenreRepository genreNames;
    @Autowired private TagRepository tags;
    @Autowired private TransactionTemplate tx;

    private Game save(Game.GameBuilder builder) {
        return games.save(builder.build());
    }

    private static List<Integer> ids(List<Game> list) {
        return list.stream().map(Game::getIgdbId).toList();
    }

    // ── GameRepository ────────────────────────────────────────────────────────

    @Test
    void clearCellarRatingsNotFromRun_clearsEveryRatedRowTheRunDidNotWrite() {
        save(Game.builder().igdbId(1).name("Current").cellarRatingAvg(new BigDecimal("8.00")).cellarRatingCount(2).cellarRatingRunId("r2"));
        save(Game.builder().igdbId(2).name("Older run").cellarRatingAvg(new BigDecimal("7.00")).cellarRatingCount(1).cellarRatingRunId("r1"));
        save(Game.builder().igdbId(3).name("No run").cellarRatingAvg(new BigDecimal("6.00")).cellarRatingCount(1));
        save(Game.builder().igdbId(4).name("Unrated"));

        Integer cleared = tx.execute(s -> games.clearCellarRatingsNotFromRun("r2"));

        assertThat(cleared).isEqualTo(2);
        assertThat(games.findByIgdbId(1).orElseThrow().getCellarRatingAvg()).isEqualByComparingTo("8.00");
        for (int id : List.of(2, 3)) {
            Game g = games.findByIgdbId(id).orElseThrow();
            assertThat(g.getCellarRatingAvg()).isNull();
            assertThat(g.getCellarRatingCount()).isNull();
            assertThat(g.getCellarRatingRunId()).isNull();
        }
    }

    @Test
    void findRandom_dealsMainCategoryGamesOnlyUpToTheLimit() {
        save(Game.builder().igdbId(1).name("Main").category(0));
        save(Game.builder().igdbId(2).name("Remake").category(8));
        save(Game.builder().igdbId(3).name("Uncategorised"));
        save(Game.builder().igdbId(4).name("DLC").category(1));
        save(Game.builder().igdbId(5).name("Bundle").category(3));

        assertThat(ids(games.findRandom(10))).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(games.findRandom(2)).hasSize(2);
    }

    @Test
    void findRandomOnPlatforms_dealsMainGamesOnTheNamedPlatformsOnceEach() {
        save(Game.builder().igdbId(1).name("PS5 main").category(0).platforms(set(platform("PlayStation 5"))));
        save(Game.builder().igdbId(2).name("PS5 DLC").category(1).platforms(set(platform("PlayStation 5"))));
        save(Game.builder().igdbId(3).name("PC main").category(0).platforms(set(platform("PC"))));
        save(Game.builder().igdbId(4).name("On both").category(0).platforms(set(platform("PC"), platform("PlayStation 5"))));
        save(Game.builder().igdbId(5).name("Switch main").category(0).platforms(set(platform("Nintendo Switch"))));
        save(Game.builder().igdbId(6).name("PC remake").category(8).platforms(set(platform("PC"))));
        save(Game.builder().igdbId(7).name("PC uncategorised").platforms(set(platform("PC"))));

        assertThat(ids(games.findRandomOnPlatforms(List.of("PlayStation 5", "PC"), 10, 40)))
                .containsExactlyInAnyOrder(1, 3, 4, 6, 7);
        assertThat(games.findRandomOnPlatforms(List.of("Dreamcast"), 10, 40)).isEmpty();
    }

    // perPlatform caps each platform's share; sample caps the rows drawn before the category filter.
    @Test
    void findRandomOnPlatforms_capsEachPlatformAtItsShareAndItsSample() {
        for (int i = 1; i <= 6; i++) {
            save(Game.builder().igdbId(i).name("PC " + i).category(0).platforms(set(platform("PC"))));
            save(Game.builder().igdbId(100 + i).name("Switch " + i).category(0).platforms(set(platform("Nintendo Switch"))));
        }

        List<Game> drawn = games.findRandomOnPlatforms(List.of("PC", "Nintendo Switch"), 2, 40);
        assertThat(drawn).hasSize(4);
        assertThat(drawn).filteredOn(g -> g.getIgdbId() < 100).hasSize(2);

        assertThat(games.findRandomOnPlatforms(List.of("PC", "Nintendo Switch"), 5, 1)).hasSize(2);
    }

    @Test
    void findRandomQualityByGenre_gatesOnGenreRatingVotesAndCategory() {
        save(Game.builder().igdbId(1).name("Good").category(0).totalRating(new BigDecimal("8.00")).totalRatingCount(50)
                .genres(set(genre("Shooter"))));
        save(Game.builder().igdbId(2).name("Low score").category(0).totalRating(new BigDecimal("6.00")).totalRatingCount(50)
                .genres(set(genre("Shooter"))));
        save(Game.builder().igdbId(3).name("Few votes").category(0).totalRating(new BigDecimal("9.00")).totalRatingCount(3)
                .genres(set(genre("Shooter"))));
        save(Game.builder().igdbId(4).name("Unscored").category(0).genres(set(genre("Shooter"))));
        save(Game.builder().igdbId(5).name("Bundle").category(3).totalRating(new BigDecimal("9.00")).totalRatingCount(50)
                .genres(set(genre("Shooter"))));
        save(Game.builder().igdbId(6).name("Other genre").category(0).totalRating(new BigDecimal("9.00")).totalRatingCount(50)
                .genres(set(genre("Puzzle"))));

        assertThat(ids(games.findRandomQualityByGenre("SHOOTER", new BigDecimal("7.0"), 10, 100))).containsExactly(1);
        assertThat(games.findRandomQualityByGenre("shooter", new BigDecimal("0"), 0, 1)).hasSize(1);
    }

    // Franchise, collection and developer lists hold main games and remakes with no parent; a
    // category-less row is left out here, unlike the search filter.
    @Test
    void findByDevelopersIsNullAndIdGreaterThanOrderByIdAsc_walksRowsWithNoDeveloperColumnPastTheCursor() {
        Game first = save(Game.builder().igdbId(1).name("Missing"));
        save(Game.builder().igdbId(2).name("Known").developers("Studio"));
        save(Game.builder().igdbId(3).name("Also missing"));
        save(Game.builder().igdbId(4).name("Empty string").developers(""));
        save(Game.builder().igdbId(5).name("Missing too"));

        assertThat(ids(games.findByDevelopersIsNullAndIdGreaterThanOrderByIdAsc(0L, PageRequest.of(0, 10))))
                .containsExactly(1, 3, 5);
        assertThat(ids(games.findByDevelopersIsNullAndIdGreaterThanOrderByIdAsc(first.getId(), PageRequest.of(0, 1))))
                .containsExactly(3);
        assertThat(games.countByDevelopersIsNull()).isEqualTo(3);
    }

    @Test
    void findByFranchiseName_matchesIgnoringCaseAndKeepsStandaloneMainGamesOnly() {
        save(Game.builder().igdbId(1).name("Main").category(0).released("2010").franchises(set(franchise("Halo"))));
        save(Game.builder().igdbId(2).name("Remake").category(8).released("2020").franchises(set(franchise("Halo"))));
        save(Game.builder().igdbId(3).name("Child").category(0).parentGameId(1).franchises(set(franchise("Halo"))));
        save(Game.builder().igdbId(4).name("DLC").category(1).franchises(set(franchise("Halo"))));
        save(Game.builder().igdbId(5).name("No category").franchises(set(franchise("Halo"))));

        assertThat(ids(games.findByFranchiseName("halo", PageRequest.of(0, 10, Sort.by("released")))))
                .containsExactly(1, 2);
    }

    @Test
    void findByCollectionName_matchesIgnoringCaseAndKeepsStandaloneMainGamesOnly() {
        save(Game.builder().igdbId(1).name("Main").category(0).released("2010").collections(set(collection("Saga"))));
        save(Game.builder().igdbId(2).name("Remake").category(8).released("2020").collections(set(collection("Saga"))));
        save(Game.builder().igdbId(3).name("Child").category(0).parentGameId(1).collections(set(collection("Saga"))));
        save(Game.builder().igdbId(4).name("Bundle").category(3).collections(set(collection("Saga"))));

        assertThat(ids(games.findByCollectionName("SAGA", PageRequest.of(0, 10, Sort.by("released")))))
                .containsExactly(1, 2);
    }

    @Test
    void findByDeveloperName_matchesWholeCsvEntriesIgnoringCase() {
        save(Game.builder().igdbId(1).name("Solo").category(0).developers("Capcom"));
        save(Game.builder().igdbId(2).name("Joint").category(0).developers("Sega,Capcom"));
        save(Game.builder().igdbId(3).name("Branch").category(0).developers("Capcom Vancouver"));
        save(Game.builder().igdbId(4).name("Port").category(11).developers("Capcom"));

        assertThat(ids(games.findByDeveloperName("capcom", PageRequest.of(0, 10, Sort.by("igdbId")))))
                .containsExactly(1, 2);
    }

    @Test
    void findEditionsOf_matchesByParentIdOrNamePrefixAndLeavesOutTheExcludedCategoriesAndItself() {
        Game parent = save(Game.builder().igdbId(1).name("Hollow").category(0));
        save(Game.builder().igdbId(2).name("Hollow - Deluxe").category(3));
        save(Game.builder().igdbId(3).name("Hollow: Director's Cut").category(10));
        save(Game.builder().igdbId(4).name("Orphan Edition").category(9).parentGameId(1));
        save(Game.builder().igdbId(5).name("Hollow - Extra Levels").category(2));
        save(Game.builder().igdbId(6).name("Hollow - Remade").category(8));
        save(Game.builder().igdbId(7).name("Hollow - Uncategorised"));
        save(Game.builder().igdbId(8).name("Hollowed Out").category(0));

        List<Game> editions = games.findEditionsOf(1, "Hollow", parent.getId(), List.of(1, 2, 8));

        assertThat(ids(editions)).containsExactlyInAnyOrder(2, 3, 4, 7);
    }

    @Test
    void findUpcoming_keepsMainGamesInsideTheInclusiveRange() {
        save(Game.builder().igdbId(1).name("At start").category(0).firstReleaseDate(100L));
        save(Game.builder().igdbId(2).name("At end").category(0).firstReleaseDate(200L));
        save(Game.builder().igdbId(3).name("After").category(0).firstReleaseDate(201L));
        save(Game.builder().igdbId(4).name("Undated").category(0));
        save(Game.builder().igdbId(5).name("DLC").category(1).firstReleaseDate(150L));

        assertThat(ids(games.findUpcoming(100L, 200L))).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void findUpcomingByPlatforms_matchesLowerCasedPlatformNames() {
        save(Game.builder().igdbId(1).name("PS5 game").category(0).firstReleaseDate(150L).platforms(set(platform("PlayStation 5"))));
        save(Game.builder().igdbId(2).name("PC game").category(0).firstReleaseDate(150L).platforms(set(platform("PC"))));
        save(Game.builder().igdbId(3).name("Late PS5").category(0).firstReleaseDate(300L).platforms(set(platform("PlayStation 5"))));

        assertThat(ids(games.findUpcomingByPlatforms(100L, 200L, List.of("playstation 5")))).containsExactly(1);
    }

    @Test
    void findUpcomingIgdbIds_listsEveryFutureRowWhateverItsCategory() {
        save(Game.builder().igdbId(1).name("Future main").category(0).firstReleaseDate(500L));
        save(Game.builder().igdbId(2).name("Future DLC").category(1).firstReleaseDate(500L));
        save(Game.builder().igdbId(3).name("Released").category(0).firstReleaseDate(100L));
        save(Game.builder().igdbId(4).name("Exactly now").category(0).firstReleaseDate(300L));

        assertThat(games.findUpcomingIgdbIds(300L)).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void findDistinctUpcomingPlatformNames_listsEachPlatformOnceInOrder() {
        save(Game.builder().igdbId(1).name("A").category(0).firstReleaseDate(500L).platforms(set(platform("Xbox One"), platform("PC"))));
        save(Game.builder().igdbId(2).name("B").category(0).firstReleaseDate(600L).platforms(set(platform("PC"))));
        save(Game.builder().igdbId(3).name("Released").category(0).firstReleaseDate(100L).platforms(set(platform("Wii"))));
        save(Game.builder().igdbId(4).name("DLC").category(1).firstReleaseDate(500L).platforms(set(platform("Nintendo Switch"))));

        assertThat(games.findDistinctUpcomingPlatformNames(300L)).containsExactly("PC", "Xbox One");
    }

    @Test
    void findPrefixParentCandidates_offersMainGamesWhoseNamePrefixesTheChildLongestFirst() {
        save(Game.builder().igdbId(1).name("Dark").category(0));
        save(Game.builder().igdbId(2).name("Dark Souls").category(0));
        save(Game.builder().igdbId(3).name("Dark Souls: Artorias"));
        Game child = save(Game.builder().igdbId(4).name("Dark Souls: Artorias - Deluxe").category(3));
        save(Game.builder().igdbId(5).name("Dark Souls").category(9));

        List<Game> candidates = games.findPrefixParentCandidates(child.getName(), child.getId());

        assertThat(ids(candidates)).containsExactly(3, 2);
    }

    @Test
    void findBackgroundArtWork_takesUndecidedRowsWithCandidatesMostRatedFirst() {
        save(Game.builder().igdbId(1).name("Popular").ratingCount(900).backgroundArtPool("[{\"id\":\"a\"}]"));
        save(Game.builder().igdbId(2).name("Niche").ratingCount(10).backgroundArtPool("[{\"id\":\"b\"}]"));
        save(Game.builder().igdbId(3).name("Unrated").backgroundArtPool("[{\"id\":\"c\"}]"));
        save(Game.builder().igdbId(4).name("Empty pool").ratingCount(999).backgroundArtPool("[]"));
        save(Game.builder().igdbId(5).name("Not harvested").ratingCount(999));
        save(Game.builder().igdbId(6).name("Decided").ratingCount(999).backgroundArtPool("[{\"id\":\"d\"}]").backgroundSource("none"));

        assertThat(ids(games.findBackgroundArtWork(PageRequest.of(0, 10)))).containsExactly(1, 2, 3);
        assertThat(ids(games.findBackgroundArtWork(PageRequest.of(0, 1)))).containsExactly(1);
    }

    @Test
    void findUncomputedSimilaritySources_skipsGamesThatAlreadyHaveRowsAndNonMainCategories() {
        save(Game.builder().igdbId(1).name("Computed").category(0));
        save(Game.builder().igdbId(2).name("Pending").category(0));
        save(Game.builder().igdbId(3).name("Pending remake").category(8));
        save(Game.builder().igdbId(4).name("DLC").category(1));
        similarities.save(GameSimilarity.builder().sourceIgdbId(1).similarIgdbId(2)
                .score(new BigDecimal("0.5")).rank((short) 1).computedAt(LocalDateTime.now()).build());

        assertThat(ids(games.findUncomputedSimilaritySources(PageRequest.of(0, 10)))).containsExactly(2, 3);
        assertThat(ids(games.findUncomputedSimilaritySources(PageRequest.of(0, 1)))).containsExactly(2);
    }

    @Test
    void findRandomMainGamesForSimilarity_needsARatingWithVotes() {
        save(Game.builder().igdbId(1).name("Rated").category(0).totalRating(new BigDecimal("7.00")).totalRatingCount(4));
        save(Game.builder().igdbId(2).name("Zero votes").category(0).totalRating(new BigDecimal("7.00")).totalRatingCount(0));
        save(Game.builder().igdbId(3).name("Unrated").category(0));
        save(Game.builder().igdbId(4).name("Rated DLC").category(1).totalRating(new BigDecimal("7.00")).totalRatingCount(4));

        assertThat(ids(games.findRandomMainGamesForSimilarity(10))).containsExactly(1);
    }

    // ── GameSimilarityRepository ──────────────────────────────────────────────

    @Test
    void findTopBySource_returnsTheSourcesRowsInRankOrder() {
        similarities.saveAll(List.of(row(1, 30, 3), row(1, 10, 1), row(1, 20, 2), row(2, 40, 1)));

        assertThat(similarities.findTopBySource(1)).extracting(GameSimilarity::getSimilarIgdbId).containsExactly(10, 20, 30);
    }

    @Test
    void deleteBySource_removesOnlyThatSourcesRows() {
        similarities.saveAll(List.of(row(1, 10, 1), row(1, 20, 2), row(2, 40, 1)));

        Integer deleted = tx.execute(s -> similarities.deleteBySource(1));

        assertThat(deleted).isEqualTo(2);
        assertThat(similarities.findTopBySource(1)).isEmpty();
        assertThat(similarities.findTopBySource(2)).hasSize(1);
    }

    private static GameSimilarity row(int source, int similar, int rank) {
        return GameSimilarity.builder().sourceIgdbId(source).similarIgdbId(similar)
                .score(new BigDecimal("0.5000")).rank((short) rank).computedAt(LocalDateTime.now()).build();
    }

    // ── GenreRepository, TagRepository ────────────────────────────────────────

    @Test
    void findAllNames_listsGenreNamesAlphabetically() {
        genre("Shooter");
        genre("Adventure");
        genre("Puzzle");

        assertThat(genreNames.findAllNames()).containsExactly("Adventure", "Puzzle", "Shooter");
    }

    @Test
    void deleteJoinRowsForTag_removesThatTagFromEveryGameAndNothingElse() {
        Tag drop = tag("drop me");
        save(Game.builder().igdbId(1).name("A").tags(set(drop, tag("keep"))));
        save(Game.builder().igdbId(2).name("B").tags(set(drop)));

        Integer deleted = tx.execute(s -> tags.deleteJoinRowsForTag(drop.getId()));

        assertThat(deleted).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT t.name FROM game_tags gt JOIN tags t ON t.id = gt.tag_id", String.class))
                .containsExactly("keep");
    }

    @Test
    void findPopularExcludingBlocklist_ranksTagsByUseLeavingOutTheBlocklist() {
        save(Game.builder().igdbId(1).name("A").tags(set(tag("common"), tag("junk"), tag("rare"))));
        save(Game.builder().igdbId(2).name("B").tags(set(tag("common"), tag("junk"))));
        save(Game.builder().igdbId(3).name("C").tags(set(tag("common"), tag("junk"))));

        List<Object[]> rows = tags.findPopularExcludingBlocklist(Set.of("junk"), 10);

        assertThat(rows).extracting(r -> (String) r[0]).containsExactly("common", "rare");
        assertThat(((Number) rows.get(0)[1]).longValue()).isEqualTo(3);
        assertThat(tags.findPopularExcludingBlocklist(Set.of("junk"), 1)).hasSize(1);
    }
}
