package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.support.GameServiceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The paths that reach IGDB, driven over HTTP with the real IgdbApiClient and IgdbTokenService; only the
// socket under their RestTemplate is replaced, so the URL, the headers, the Apicalypse body and the JSON
// mapping are all production code.
class IgdbPathTest extends GameServiceTestBase {

    private static final String FETCHED = """
            [{"id":777,"name":"Fetched Game","summary":"From IGDB.","total_rating":85.0,"total_rating_count":40,
              "first_release_date":1500000000,"game_type":0,"cover":{"id":1,"image_id":"co777"},
              "genres":[{"id":12,"name":"Role-playing (RPG)"}],"platforms":[{"id":6,"name":"PC (Microsoft Windows)"}],
              "keywords":[{"id":1,"name":"open world"}],
              "involved_companies":[{"company":{"name":"Dev Co"},"developer":true},{"company":{"name":"Pub Co"},"developer":false}]}]
            """;

    private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)));
    }

    @Test
    void aGameMissingFromTheCatalogIsFetchedFromIgdbCachedAndThenServedFromTheCatalog() throws Exception {
        igdb.expect(requestTo(IGDB_GAMES))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Client-ID", "test-client-id"))
                .andExpect(header("Authorization", "Bearer " + HELD_IGDB_TOKEN))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("where id = 777; limit 1;")))
                .andRespond(withSuccess(FETCHED, MediaType.APPLICATION_JSON));

        as(get("/api/v1/games/777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fetched Game"))
                .andExpect(jsonPath("$.description").value("From IGDB."))
                .andExpect(jsonPath("$.totalRating").value(8.5))
                .andExpect(jsonPath("$.genres", contains("Role-playing (RPG)")))
                .andExpect(jsonPath("$.platforms", contains("PC")))
                .andExpect(jsonPath("$.developers", contains("Dev Co")))
                .andExpect(jsonPath("$.coverImageUrl", containsString("co777")));
        igdb.verify();

        // The stub expected exactly one call, so a second trip to IGDB would fail here.
        as(get("/api/v1/games/777")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Fetched Game"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM game_genres gg JOIN games g ON g.id = gg.game_id WHERE g.igdb_id = 777
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void anIdIgdbDoesNotKnowIs404() throws Exception {
        igdb.expect(requestTo(IGDB_GAMES)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        as(get("/api/v1/games/424242"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertThat(games.findByIgdbId(424242)).isEmpty();
    }

    @Test
    void anIgdbFailureIs502AndTheBodyNamesNoUpstreamDetail() throws Exception {
        igdb.expect(requestTo(IGDB_GAMES)).andRespond(withServerError());

        as(get("/api/v1/games/424243"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Upstream game catalog unavailable"))
                .andExpect(MockMvcResultMatchers.content().string(not(containsString("igdb.com"))));
    }

    // A row missing its core data is refreshed from IGDB on read; the fields it already has stay.
    @Test
    void aStaleCachedGameIsRefreshedFromIgdbOnRead() throws Exception {
        games.save(Game.builder().igdbId(777).name("Fetched Game").category(0).build());
        igdb.expect(requestTo(IGDB_GAMES)).andRespond(withSuccess(FETCHED, MediaType.APPLICATION_JSON));

        as(get("/api/v1/games/777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("From IGDB."))
                .andExpect(jsonPath("$.genres", contains("Role-playing (RPG)")));

        Game refreshed = games.findByIgdbId(777).orElseThrow();
        assertThat(refreshed.getDescription()).isEqualTo("From IGDB.");
        assertThat(refreshed.getDevelopers()).isEqualTo("Dev Co");
    }

    @Test
    void aStaleCachedGameIsStillServedWhenIgdbFails() throws Exception {
        games.save(Game.builder().igdbId(778).name("Half Cached").category(0).build());
        igdb.expect(requestTo(IGDB_GAMES)).andRespond(withServerError());

        as(get("/api/v1/games/778"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Half Cached"));
    }

    // The query text is stripped to letters, digits and a few separators before it enters the Apicalypse
    // string, so a quote cannot close the search term and append clauses of its own.
    @Test
    void searchFallsBackToIgdbWithASanitisedTermAndCachesWhatComesBack() throws Exception {
        igdb.expect(requestTo(IGDB_GAMES))
                .andExpect(content().string(containsString("search \"zelda fields\";")))
                .andExpect(content().string(not(containsString("fields *"))))
                .andRespond(withSuccess(FETCHED, MediaType.APPLICATION_JSON));

        as(get("/api/v1/games/search").param("query", "zel\"da; fields *"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[*].igdbId", contains(777)));
        igdb.verify();
        assertThat(games.findByIgdbId(777)).isPresent();
    }

    @Test
    void aGenreOnlySearchOnAnEmptyCatalogAsksIgdbByGenre() throws Exception {
        igdb.expect(requestTo(IGDB_GAMES))
                .andExpect(content().string(containsString("where genres.name = \"Shooter\";")))
                .andRespond(withSuccess(FETCHED, MediaType.APPLICATION_JSON));

        as(get("/api/v1/games/search").param("genre", "Shooter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[*].igdbId", contains(777)));
        igdb.verify();
    }

    @Test
    void searchAnswersAnEmptyPageWhenIgdbFails() throws Exception {
        igdb.expect(requestTo(IGDB_GAMES)).andRespond(withServerError());

        as(get("/api/v1/games/search").param("query", "zelda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.games", empty()));
    }

    @Test
    void popularComesFromIgdbNarrowedToThePlatformAndIsCached() throws Exception {
        igdb.expect(requestTo(IGDB_GAMES))
                .andExpect(content().string(containsString("sort aggregated_rating_count desc;")))
                .andExpect(content().string(containsString("platforms = (6)")))
                .andExpect(content().string(containsString("limit 20; offset 20;")))
                .andRespond(withSuccess(FETCHED, MediaType.APPLICATION_JSON));

        as(get("/api/v1/games/popular").param("platform", "PC").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[*].igdbId", contains(777)));
        igdb.verify();
        assertThat(games.findByIgdbId(777)).isPresent();
    }

    @Test
    void popularIs502WhenIgdbFails() throws Exception {
        igdb.expect(requestTo(IGDB_GAMES)).andRespond(withServerError());

        as(get("/api/v1/games/popular")).andExpect(status().isBadGateway());
    }

    @Test
    void popularRejectsAnOutOfRangePageWith400() throws Exception {
        as(get("/api/v1/games/popular").param("page", "501")).andExpect(status().isBadRequest());
        as(get("/api/v1/games/popular").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void genresFallBackToIgdbWhileTheCatalogHasNone() throws Exception {
        igdb.expect(requestTo(IGDB_GENRES))
                .andExpect(content().string(containsString("fields id,name;")))
                .andRespond(withSuccess("[{\"id\":31,\"name\":\"Adventure\"},{\"id\":5,\"name\":\"Shooter\"}]",
                        MediaType.APPLICATION_JSON));

        as(get("/api/v1/games/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genres", contains("Adventure", "Shooter")));
        igdb.verify();
    }

    // The client secret travels in the form body, never in the URL where access logs would keep it.
    @Test
    void withNoTokenHeldOneIsFetchedFromTwitchAndCarriedToIgdb() throws Exception {
        dropHeldIgdbToken();
        igdb.expect(requestTo(TWITCH_TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "client_id", "test-client-id",
                        "client_secret", "test-client-secret",
                        "grant_type", "client_credentials")))
                .andRespond(withSuccess("{\"access_token\":\"fresh-token\",\"expires_in\":5000000,\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));
        igdb.expect(requestTo(IGDB_GAMES))
                .andExpect(header("Authorization", "Bearer fresh-token"))
                .andRespond(withSuccess(FETCHED, MediaType.APPLICATION_JSON));

        as(get("/api/v1/games/777")).andExpect(status().isOk());
        igdb.verify();
    }

    @Test
    void aTwitchTokenFailureIs502() throws Exception {
        dropHeldIgdbToken();
        igdb.expect(requestTo(TWITCH_TOKEN)).andRespond(withServerError());

        as(get("/api/v1/games/777")).andExpect(status().isBadGateway());
        assertThat(games.findByIgdbId(777)).isEmpty();
    }
}
