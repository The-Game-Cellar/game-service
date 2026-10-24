package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.support.GameServiceTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The security chain permits /internal/**, so InternalAuthFilter and its shared token are the only guard.
class InternalEndpointsTest extends GameServiceTestBase {

    @BeforeEach
    void catalog() {
        games.save(Game.builder().igdbId(1942).name("The Witcher 3: Wild Hunt").category(0)
                .description("Geralt hunts.").developers("CD Projekt RED")
                .totalRating(new BigDecimal("9.30")).totalRatingCount(500)
                .genres(set(genre("Role-playing (RPG)"))).build());
        games.save(Game.builder().igdbId(1941).name("The Witcher 2").category(0)
                .description("Geralt again.").developers("CD Projekt RED")
                .totalRating(new BigDecimal("6.00")).totalRatingCount(500)
                .genres(set(genre("Role-playing (RPG)"))).build());
    }

    private ResultActions internal(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(INTERNAL_HEADER, INTERNAL_TOKEN));
    }

    private static MockHttpServletRequestBuilder request(String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        return "GET".equals(parts[0]) ? get(parts[1])
                : post(parts[1]).contentType(MediaType.APPLICATION_JSON).content("[]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /internal/games/1942", "GET /internal/games/popular",
            "GET /internal/games/random-quality?genre=Role-playing (RPG)",
            "POST /internal/games/ratings?runId=run-1", "POST /internal/games/ratings/prune?runId=run-1"})
    void withoutTheSharedTokenOrWithAWrongOneEveryInternalEndpointAnswers401(String endpoint) throws Exception {
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(request(endpoint).header(INTERNAL_HEADER, "wrong-internal-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(request(endpoint).header(INTERNAL_HEADER, INTERNAL_TOKEN + "x"))
                .andExpect(status().isUnauthorized());
        // A user's token opens the public API, never the internal one.
        mvc.perform(request(endpoint).header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRejectedPruneClearsNothing() throws Exception {
        jdbc.update("UPDATE games SET cellar_rating_avg = 8.0, cellar_rating_count = 2, cellar_rating_run_id = 'old'");

        mvc.perform(post("/internal/games/ratings/prune").param("runId", "new").header(INTERNAL_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM games WHERE cellar_rating_avg IS NOT NULL", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void theWorkerReadsACachedGameById() throws Exception {
        internal(get("/internal/games/1942"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("The Witcher 3: Wild Hunt"));
        internal(get("/internal/games/0")).andExpect(status().isBadRequest());
    }

    @Test
    void theWorkerReadsPopularGamesFromIgdb() throws Exception {
        igdb.expect(requestTo(IGDB_GAMES))
                .andExpect(content().string(containsString("sort aggregated_rating_count desc;")))
                .andRespond(withSuccess("[{\"id\":555,\"name\":\"Popular One\"}]", MediaType.APPLICATION_JSON));

        internal(get("/internal/games/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[*].igdbId", contains(555)));
        igdb.verify();
    }

    @Test
    void theWorkerReadsQualityCandidatesByGenre() throws Exception {
        internal(get("/internal/games/random-quality").param("genre", "Role-playing (RPG)").param("minRating", "7.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[*].igdbId", contains(1942)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "genre=X&limit=0", "genre=X&limit=201", "genre=X&minVotes=-1"})
    void qualityCandidatesRejectAMissingGenreOrAnOutOfRangeParameterWith400(String query) throws Exception {
        internal(get("/internal/games/random-quality?" + query)).andExpect(status().isBadRequest());
    }

    @Test
    void aRatingsBatchWithABadRunIdOrTooManyRowsOrABrokenBodyIs400() throws Exception {
        String one = "[{\"igdbGameId\":1942,\"average\":8.0,\"count\":1}]";
        internal(post("/internal/games/ratings").param("runId", " ").contentType(MediaType.APPLICATION_JSON).content(one))
                .andExpect(status().isBadRequest());
        internal(post("/internal/games/ratings").param("runId", "r".repeat(37)).contentType(MediaType.APPLICATION_JSON).content(one))
                .andExpect(status().isBadRequest());
        internal(post("/internal/games/ratings").contentType(MediaType.APPLICATION_JSON).content(one))
                .andExpect(status().isBadRequest());

        String row = "{\"igdbGameId\":1942,\"average\":8.0,\"count\":1}";
        String tooMany = "[" + String.join(",", Collections.nCopies(1001, row)) + "]";
        internal(post("/internal/games/ratings").param("runId", "run-1").contentType(MediaType.APPLICATION_JSON).content(tooMany))
                .andExpect(status().isBadRequest());
        internal(post("/internal/games/ratings").param("runId", "run-1").contentType(MediaType.APPLICATION_JSON).content("[{"))
                .andExpect(status().isBadRequest());
        internal(post("/internal/games/ratings").param("runId", "run-1").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"igdbGameId\":0,\"average\":8.0,\"count\":1}]"))
                .andExpect(status().isBadRequest());

        assertThat(games.findByIgdbId(1942).orElseThrow().getCellarRatingAvg()).isNull();
    }

    @Test
    void pruneWithoutARunIdIs400() throws Exception {
        internal(post("/internal/games/ratings/prune")).andExpect(status().isBadRequest());
        internal(post("/internal/games/ratings/prune").param("runId", "")).andExpect(status().isBadRequest());
    }
}
