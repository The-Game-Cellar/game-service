package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.SyncState;
import com.thegamecellar.gameservice.repository.SyncStateRepository;
import com.thegamecellar.gameservice.support.GameServiceTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// What each admin job does once the admin role lets it through; the refusals are in AdminControllerSecurityTest.
class AdminEndpointsTest extends GameServiceTestBase {

    private static final String ADMIN_PREFIX = "/api/v1/admin";

    @Autowired private SyncStateRepository syncState;

    // Three empty discovery pages in a row reset the saved offset to the start of the catalog.
    @Test
    void fullSyncIsAcceptedAndRunsTheCatalogWorker() throws Exception {
        syncState.save(new SyncState("igdb_discovery_offset", "500"));
        answerGames(igdbWorker, body -> "[]");

        mvc.perform(post(ADMIN_PREFIX + "/sync").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Full sync started"));
        awaitAdminJobDone();

        assertThat(syncState.findById("igdb_discovery_offset").orElseThrow().getStateValue()).isEqualTo("0");
    }

    @Test
    void quickSyncIsAcceptedAndCachesTheFirstCatalogPage() throws Exception {
        answerGames(igdbWorker, body -> body.contains("offset 0;")
                ? "[{\"id\":901,\"name\":\"Quick Game\",\"genres\":[{\"id\":31,\"name\":\"Adventure\"}]}]"
                : "[]");

        mvc.perform(post(ADMIN_PREFIX + "/sync/quick").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isAccepted());
        awaitAdminJobDone();

        assertThat(games.findByIgdbId(901)).get().extracting(Game::getName).isEqualTo("Quick Game");
    }

    // A pool with no candidate wide enough is decided as 'none' without downloading anything.
    @Test
    void backgroundArtPassIsAcceptedAndDecidesThePendingRows() throws Exception {
        games.save(Game.builder().igdbId(902).name("Tiny Art").category(0)
                .backgroundArtPool("[{\"id\":\"small\",\"w\":300,\"h\":300,\"s\":\"a\"}]").build());

        mvc.perform(post(ADMIN_PREFIX + "/background-art").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isAccepted());
        awaitAdminJobDone();

        assertThat(games.findByIgdbId(902).orElseThrow().getBackgroundSource()).isEqualTo("none");
    }

    @Test
    void developerBackfillIsAcceptedAndFillsMissingDevelopersFromIgdb() throws Exception {
        games.save(Game.builder().igdbId(903).name("No Devs").category(0).build());
        answerGames(igdb, body -> body.contains("where id = 903;")
                ? "[{\"id\":903,\"name\":\"No Devs\",\"involved_companies\":["
                        + "{\"company\":{\"name\":\"Studio One\"},\"developer\":true},"
                        + "{\"company\":{\"name\":\"Publisher Two\"},\"developer\":false}]}]"
                : "[]");

        mvc.perform(post(ADMIN_PREFIX + "/backfill-developers").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isAccepted());
        awaitAdminJobDone();

        assertThat(games.findByIgdbId(903).orElseThrow().getDevelopers()).isEqualTo("Studio One");
    }

    // A game IGDB lists no developer for, or one whose fetch fails, is tried once and left; the run ends
    // and frees the executor for the next job.
    @Test
    void developerBackfillVisitsEachGameOnceAndEndsEvenWhenSomeStayWithoutDevelopers() throws Exception {
        games.save(Game.builder().igdbId(903).name("Gets Devs").category(0).build());
        games.save(Game.builder().igdbId(910).name("No Devs Anywhere").category(0).build());
        games.save(Game.builder().igdbId(911).name("Fetch Fails").category(0).build());
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        answerGames(igdb, body -> {
            asked.add(body.replaceAll(".*where id = (\\d+);.*", "$1"));
            if (body.contains("where id = 911;")) throw new IllegalStateException("IGDB refused");
            if (body.contains("where id = 910;")) return "[{\"id\":910,\"name\":\"No Devs Anywhere\"}]";
            return "[{\"id\":903,\"name\":\"Gets Devs\",\"involved_companies\":"
                    + "[{\"company\":{\"name\":\"Studio One\"},\"developer\":true}]}]";
        });

        mvc.perform(post(ADMIN_PREFIX + "/backfill-developers").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isAccepted());
        awaitAdminJobDone();

        assertThat(asked).containsExactlyInAnyOrder("903", "910", "911");
        assertThat(games.findByIgdbId(903).orElseThrow().getDevelopers()).isEqualTo("Studio One");
        assertThat(games.findByIgdbId(910).orElseThrow().getDevelopers()).isNull();
        assertThat(games.findByIgdbId(911).orElseThrow().getDevelopers()).isNull();
    }

    // One admin job at a time: while one runs, every job endpoint answers 409 and starts nothing.
    @ParameterizedTest
    @ValueSource(strings = {"/sync", "/sync/quick", "/background-art", "/backfill-developers"})
    void aJobEndpointAnswers409WhileAnotherJobRuns(String path) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        assertThat(adminSyncExecutor.trySubmit("held", () -> awaitQuietly(release))).isTrue();
        try {
            mvc.perform(post(ADMIN_PREFIX + path).header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                    .andExpect(status().isConflict())
                    .andExpect(content().string("Sync already in progress"));
        } finally {
            release.countDown();
        }
        igdb.verify();
        igdbWorker.verify();
    }

    @Test
    void pruneTagsDropsTagsOutsideTheAllowlistWithTheirJoinRows() throws Exception {
        games.save(Game.builder().igdbId(904).name("Tagged").category(0)
                .tags(set(tag("roguelike"), tag("zz not a curated tag"))).build());

        mvc.perform(post(ADMIN_PREFIX + "/prune-tags").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examined").value(2))
                .andExpect(jsonPath("$.kept").value(1))
                .andExpect(jsonPath("$.dropped").value(1))
                .andExpect(jsonPath("$.joinRowsDeleted").value(1))
                .andExpect(jsonPath("$.sampleDropped", hasItem("zz not a curated tag")));

        assertThat(jdbc.queryForList("SELECT name FROM tags", String.class)).containsExactly("roguelike");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_tags", Integer.class)).isEqualTo(1);
    }

    @Test
    void deriveGenresAddsTheRuleGenreToAGameCarryingItsTheme() throws Exception {
        games.save(Game.builder().igdbId(905).name("Scary").category(0)
                .genres(set(genre("Adventure"))).themes(set(theme("Horror"))).build());

        mvc.perform(post(ADMIN_PREFIX + "/derive-genres").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examined").value(1))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.derivedRowsAdded").value(1));

        assertThat(jdbc.queryForList("""
                SELECT ge.name || ':' || ge.source FROM game_genres gg
                JOIN genres ge ON ge.id = gg.genre_id JOIN games g ON g.id = gg.game_id
                WHERE g.igdb_id = 905 ORDER BY ge.name
                """, String.class)).containsExactly("Adventure:IGDB", "Horror:DERIVED");
    }

    @Test
    void releaseDataBackfillFillsOnlyTheMissingFieldsFromOneBatchedIgdbQuery() throws Exception {
        games.save(Game.builder().igdbId(906).name("Undated").category(0).build());
        games.save(Game.builder().igdbId(907).name("Dated").category(0).firstReleaseDate(1_600_000_000L).hypes(5).build());
        answerGames(igdbWorker, body -> body.contains("where id = (906)")
                ? "[{\"id\":906,\"name\":\"Undated\",\"first_release_date\":1700000000,\"hypes\":42}]"
                : "[]");

        mvc.perform(post(ADMIN_PREFIX + "/backfill-release-data").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examined").value(2))
                .andExpect(jsonPath("$.skippedAlreadyFilled").value(1))
                .andExpect(jsonPath("$.updatedFirstReleaseDate").value(1))
                .andExpect(jsonPath("$.updatedHypes").value(1));

        Game filled = games.findByIgdbId(906).orElseThrow();
        assertThat(filled.getFirstReleaseDate()).isEqualTo(1_700_000_000L);
        assertThat(filled.getHypes()).isEqualTo(42);
        assertThat(games.findByIgdbId(907).orElseThrow().getHypes()).isEqualTo(5);
    }

    // The doubling runs once: the sync_state flag turns every later call into a no-op.
    @Test
    void ratingScaleMigrationDoublesBothScoresOnceAndThenSkips() throws Exception {
        games.save(Game.builder().igdbId(908).name("Old Scale").category(0)
                .rating(new BigDecimal("4.25")).totalRating(new BigDecimal("3.50")).build());
        games.save(Game.builder().igdbId(909).name("Unrated").category(0).build());

        mvc.perform(post(ADMIN_PREFIX + "/migrate-rating-scale").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(false))
                .andExpect(jsonPath("$.ratingRowsUpdated").value(1))
                .andExpect(jsonPath("$.totalRatingRowsUpdated").value(1));

        mvc.perform(post(ADMIN_PREFIX + "/migrate-rating-scale").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(true));

        Game migrated = games.findByIgdbId(908).orElseThrow();
        assertThat(migrated.getRating()).isEqualByComparingTo("8.50");
        assertThat(migrated.getTotalRating()).isEqualByComparingTo("7.00");
        assertThat(games.findByIgdbId(909).orElseThrow().getRating()).isNull();
        assertThat(syncState.findById("rating_scale_v2_migrated")).get()
                .extracting(SyncState::getStateValue).isEqualTo("true");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
