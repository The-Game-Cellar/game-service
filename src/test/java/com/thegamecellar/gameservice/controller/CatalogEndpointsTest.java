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

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The catalog endpoints read what the database holds; IGDB must not be reached on any of these
// paths, so the unbound IGDB stub fails the test if one is.
class CatalogEndpointsTest extends GameServiceTestBase {

    private static final String RPG = "Role-playing (RPG)";

    @BeforeEach
    void catalog() {
        eligiblePlatform("PC", "pc", 10);
        eligiblePlatform("PlayStation 5", "sony", 10);
        platform("Nintendo Switch");

        games.save(Game.builder().igdbId(1942).name("The Witcher 3: Wild Hunt").category(0)
                .description("Geralt hunts.").developers("CD Projekt RED")
                .totalRating(new BigDecimal("9.30")).totalRatingCount(500)
                .released("2015-05-19").firstReleaseDate(1_431_993_600L)
                .genres(set(genre(RPG), genre("Adventure"))).platforms(set(platform("PC"), platform("PlayStation 5")))
                .tags(set(tag("open world"), tag("story rich"), tag("3d"))).themes(set(theme("Fantasy")))
                .gameModes(set(mode("Single player"))).playerPerspectives(set(perspective("Third person")))
                .franchises(set(franchise("The Witcher"))).collections(set(collection("The Witcher"))).build());
        games.save(Game.builder().igdbId(1941).name("The Witcher 2: Assassins of Kings").category(0)
                .description("Geralt again.").developers("CD Projekt RED")
                .totalRating(new BigDecimal("8.50")).totalRatingCount(200)
                .released("2011-05-17").firstReleaseDate(1_305_590_400L)
                .genres(set(genre(RPG))).platforms(set(platform("PC"))).tags(set(tag("story rich")))
                .gameModes(set(mode("Single player"))).playerPerspectives(set(perspective("Third person")))
                .franchises(set(franchise("The Witcher"))).collections(set(collection("The Witcher"))).build());
        games.save(Game.builder().igdbId(3001).name("The Witcher 3: Wild Hunt - Game of the Year Edition").category(3)
                .description("Everything.").developers("CD Projekt RED")
                .totalRating(new BigDecimal("9.40")).totalRatingCount(50).released("2016-08-30")
                .genres(set(genre(RPG))).platforms(set(platform("PC")))
                .franchises(set(franchise("The Witcher"))).build());
        games.save(Game.builder().igdbId(3002).name("The Witcher 3: Wild Hunt - Blood and Wine").category(2)
                .parentGameId(1942).description("Toussaint.").developers("CD Projekt RED")
                .genres(set(genre(RPG))).platforms(set(platform("PC"))).build());
        games.save(Game.builder().igdbId(17000).name("Stardew Valley").category(0)
                .description("Farming.").developers("ConcernedApe")
                .totalRating(new BigDecimal("8.90")).totalRatingCount(300).released("2016-02-26")
                .genres(set(genre("Simulator"), genre(RPG)))
                .platforms(set(platform("PC"), platform("PlayStation 5"), platform("Nintendo Switch")))
                .tags(set(tag("pixel art"))).gameModes(set(mode("Single player"), mode("Multiplayer")))
                .playerPerspectives(set(perspective("Bird view / Isometric"))).build());
        games.save(Game.builder().igdbId(4000).name("Remade Classic").category(8)
                .description("Again.").developers("Remake House")
                .totalRating(new BigDecimal("7.00")).totalRatingCount(20)
                .genres(set(genre("Adventure"))).platforms(set(platform("PlayStation 5"))).build());
        games.save(Game.builder().igdbId(5001).name("Soon on PS5").category(0).firstReleaseDate(daysFromNow(10))
                .genres(set(genre("Adventure"))).platforms(set(platform("PlayStation 5"))).build());
        games.save(Game.builder().igdbId(5002).name("Later on PC").category(0).firstReleaseDate(daysFromNow(40))
                .platforms(set(platform("PC"))).build());
        games.save(Game.builder().igdbId(5003).name("Far Future").category(0).firstReleaseDate(daysFromNow(400))
                .platforms(set(platform("PC"))).build());
    }

    private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)));
    }

    private static MockHttpServletRequestBuilder request(String endpoint) {
        String[] parts = endpoint.split(" ", 2);
        return "GET".equals(parts[0]) ? get(parts[1])
                : post(parts[1]).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /api/v1/games/1942", "GET /api/v1/games/search", "GET /api/v1/games/popular",
            "POST /api/v1/games/upcoming", "GET /api/v1/games/upcoming/platforms", "GET /api/v1/games/random",
            "GET /api/v1/games/random-quality?genre=Adventure", "GET /api/v1/games/genres",
            "GET /api/v1/games/tags/popular", "GET /api/v1/games/platforms",
            "GET /api/v1/games/by-franchise/The Witcher", "GET /api/v1/games/by-collection/The Witcher",
            "GET /api/v1/games/by-developer/ConcernedApe", "GET /api/v1/games/1942/editions",
            "GET /api/v1/games/1942/similar", "GET /api/v1/platforms/catalog"})
    void withoutATokenOrWithARejectedOneEveryCatalogEndpointAnswers401(String endpoint) throws Exception {
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(request(endpoint).header(HttpHeaders.AUTHORIZATION, bearer("forged")))
                .andExpect(status().isUnauthorized());
    }

    // ── /games/{igdbId} ───────────────────────────────────────────────────────

    @Test
    void aCachedGameIsServedFromTheDatabaseWithItsAssociations() throws Exception {
        as(get("/api/v1/games/1942"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.igdbId").value(1942))
                .andExpect(jsonPath("$.name").value("The Witcher 3: Wild Hunt"))
                .andExpect(jsonPath("$.genres", containsInAnyOrder(RPG, "Adventure")))
                .andExpect(jsonPath("$.platforms", containsInAnyOrder("PC", "PlayStation 5")))
                .andExpect(jsonPath("$.developers", contains("CD Projekt RED")))
                .andExpect(jsonPath("$.franchises", contains("The Witcher")))
                .andExpect(jsonPath("$.totalRating").value(9.3));
    }

    // Bundles routinely arrive without parent_game; the longest name-prefix match in the catalog fills it in.
    @Test
    void aBundleWithoutAParentGetsItInferredFromTheCatalog() throws Exception {
        as(get("/api/v1/games/3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentGameId").value(1942))
                .andExpect(jsonPath("$.parentGameName").value("The Witcher 3: Wild Hunt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "abc"})
    void anIgdbIdThatIsNotAPositiveNumberIs400(String id) throws Exception {
        as(get("/api/v1/games/" + id)).andExpect(status().isBadRequest());
    }

    // ── /games/search ─────────────────────────────────────────────────────────

    @Test
    void aTextQueryMatchesMainGamesOnlyAndCountsTheFacetsOverTheMatch() throws Exception {
        as(get("/api/v1/games/search").param("query", "WITCHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1942, 1941)))
                .andExpect(jsonPath("$.availableGenreCounts['" + RPG + "']").value(2))
                .andExpect(jsonPath("$.availableGenreCounts.Adventure").value(1))
                .andExpect(jsonPath("$.availableGenreCounts.Simulator").value(0))
                .andExpect(jsonPath("$.availableTagCounts['story rich']").value(2))
                .andExpect(jsonPath("$.availableTagCounts", not(hasKey("3d"))))
                .andExpect(jsonPath("$.availableGameModeCounts['Single player']").value(2))
                .andExpect(jsonPath("$.availablePerspectiveCounts['Third person']").value(2));
    }

    @Test
    void withNoFilterTheWholeMainCatalogIsServedAndNoFacetIsCounted() throws Exception {
        as(get("/api/v1/games/search").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(7))
                .andExpect(jsonPath("$.games[0].igdbId").value(1942))
                .andExpect(jsonPath("$.availableGenreCounts").value(nullValue()))
                .andExpect(jsonPath("$.availableTagCounts").value(nullValue()));
    }

    @Test
    void withMatchAllSeveralGenresMustAllBePresentAndMatchIgnoresCase() throws Exception {
        as(get("/api/v1/games/search").param("genre", "role-playing (rpg),SIMULATOR").param("match", "all"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(17000)));
    }

    // any is the default: a game carrying one of the chosen values is enough.
    @Test
    void withMatchAnyOrNoMatchSeveralGenresMatchAnyOfThem() throws Exception {
        as(get("/api/v1/games/search").param("genre", "role-playing (rpg),SIMULATOR").param("match", "any"))
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 1941, 17000)));
        as(get("/api/v1/games/search").param("genre", "role-playing (rpg),SIMULATOR"))
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 1941, 17000)));
    }

    @Test
    void matchGovernsTagsGameModesAndPerspectivesToo() throws Exception {
        as(get("/api/v1/games/search").param("tags", "open world,pixel art"))
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 17000)));
        as(get("/api/v1/games/search").param("tags", "open world,pixel art").param("match", "all"))
                .andExpect(jsonPath("$.games").isEmpty());
        as(get("/api/v1/games/search").param("gameMode", "Single player,Multiplayer"))
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 1941, 17000)));
        as(get("/api/v1/games/search").param("gameMode", "Single player,Multiplayer").param("match", "all"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(17000)));
        as(get("/api/v1/games/search").param("perspective", "Third person,Bird view / Isometric"))
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 1941, 17000)));
        as(get("/api/v1/games/search").param("perspective", "Third person,Bird view / Isometric").param("match", "all"))
                .andExpect(jsonPath("$.games").isEmpty());
    }

    // Platform always takes any, whatever match says.
    @Test
    void platformTakesAnyEvenUnderMatchAll() throws Exception {
        as(get("/api/v1/games/search").param("platform", "playstation 5,Nintendo Switch").param("match", "all").param("pageSize", "50"))
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 17000, 4000, 5001)));
    }

    // Under any a facet's own picks stay out of its counts, so each genre shows what picking it would add, while
    // the other facets are counted over the match. Under all every count is over the match itself.
    @Test
    void facetCountsLeaveAFacetsOwnPicksOutUnderAnyAndKeepThemUnderAll() throws Exception {
        as(get("/api/v1/games/search").param("genre", "Simulator"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(17000)))
                .andExpect(jsonPath("$.availableGenreCounts['" + RPG + "']").value(3))
                .andExpect(jsonPath("$.availableGenreCounts.Adventure").value(3))
                .andExpect(jsonPath("$.availableGenreCounts.Simulator").value(1))
                .andExpect(jsonPath("$.availableTagCounts['pixel art']").value(1))
                .andExpect(jsonPath("$.availableTagCounts['open world']").value(0));
        as(get("/api/v1/games/search").param("genre", "Simulator").param("match", "all"))
                .andExpect(jsonPath("$.availableGenreCounts['" + RPG + "']").value(1))
                .andExpect(jsonPath("$.availableGenreCounts.Adventure").value(0))
                .andExpect(jsonPath("$.availableGenreCounts.Simulator").value(1));
    }

    @Test
    void severalPlatformsMatchAnyOfThem() throws Exception {
        as(get("/api/v1/games/search").param("platform", "playstation 5,Nintendo Switch").param("pageSize", "50"))
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 17000, 4000, 5001)));
    }

    @Test
    void withMatchAllSeveralTagsMustAllBePresent() throws Exception {
        as(get("/api/v1/games/search").param("tags", "open world,story rich").param("match", "all"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1942)));
        as(get("/api/v1/games/search").param("tags", "story rich,open world").param("match", "all"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1942)));
        as(get("/api/v1/games/search").param("tags", "story rich"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1942, 1941)));
    }

    @Test
    void gameModeAndPerspectiveFilterOnTheirOwnJoins() throws Exception {
        as(get("/api/v1/games/search").param("gameMode", "multiplayer"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(17000)));
        as(get("/api/v1/games/search").param("perspective", "Third person"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1942, 1941)));
    }

    @Test
    void ratingAndReleaseRangeBoundTheMatch() throws Exception {
        as(get("/api/v1/games/search").param("ratingFrom", "8.9"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1942, 17000)));
        as(get("/api/v1/games/search").param("releasedFrom", "1300000000").param("releasedTo", "1400000000"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1941)));
    }

    // main = categories 0 and 8 plus none; variant = above 2 except 8; all = no category filter.
    @Test
    void gameTypeSelectsMainVariantOrEverything() throws Exception {
        as(get("/api/v1/games/search").param("query", "witcher").param("gameType", "variant"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(3001)));
        as(get("/api/v1/games/search").param("query", "witcher").param("gameType", "all"))
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 1941, 3001, 3002)));
        as(get("/api/v1/games/search").param("query", "remade"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(4000)));
    }

    @Test
    void orderingAndPagingShapeTheResult() throws Exception {
        as(get("/api/v1/games/search").param("query", "the witcher").param("ordering", "name"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1941, 1942)));
        as(get("/api/v1/games/search").param("query", "the witcher").param("ordering", "-released"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1942, 1941)));
        as(get("/api/v1/games/search").param("query", "the witcher").param("pageSize", "1").param("page", "1"))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1941)));
    }

    @Test
    void noMatchWithDbOnlyOrWithAFilterIgdbCannotApplyStaysEmptyWithoutAskingIgdb() throws Exception {
        as(get("/api/v1/games/search").param("query", "zelda").param("dbOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.games", empty()));
        as(get("/api/v1/games/search").param("query", "zelda").param("gameMode", "Multiplayer"))
                .andExpect(jsonPath("$.games", empty()))
                .andExpect(jsonPath("$.availableGameModeCounts.Multiplayer").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ordering=rating", "gameType=dlc", "page=501", "page=-1", "pageSize=0",
            "pageSize=101", "ratingFrom=high", "releasedFrom=soon", "match=some", "match=ANY"})
    void searchRejectsInvalidParametersWith400(String param) throws Exception {
        String[] kv = param.split("=");
        as(get("/api/v1/games/search").param(kv[0], kv[1])).andExpect(status().isBadRequest());
    }

    // ── /games/upcoming ───────────────────────────────────────────────────────

    @Test
    void upcomingBrowseIsSortedByReleaseDateWithinTheWindow() throws Exception {
        as(post("/api/v1/games/upcoming").contentType(MediaType.APPLICATION_JSON).content("{\"page\":0,\"pageSize\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.games[*].igdbId", contains(5001, 5002)));
        as(post("/api/v1/games/upcoming").contentType(MediaType.APPLICATION_JSON)
                .content("{\"page\":0,\"pageSize\":10,\"windowDays\":0}"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(5001, 5002, 5003)));
    }

    @Test
    void upcomingNarrowsToPlatformsAndLeavesOutExcludedIds() throws Exception {
        as(post("/api/v1/games/upcoming").contentType(MediaType.APPLICATION_JSON)
                .content("{\"page\":0,\"platform\":\"PlayStation 5\"}"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(5001)));
        as(post("/api/v1/games/upcoming").contentType(MediaType.APPLICATION_JSON)
                .content("{\"page\":0,\"excludeIds\":[5001]}"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(5002)));
    }

    @Test
    void upcomingSampleModeDealsAtMostTheLimitFromTheWindow() throws Exception {
        as(post("/api/v1/games/upcoming").contentType(MediaType.APPLICATION_JSON).content("{\"limit\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games", hasSize(1)))
                .andExpect(jsonPath("$.games[0].igdbId", anyOf(is(5001), is(5002))));
        as(post("/api/v1/games/upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games", hasSize(2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"windowDays\":3651}", "{\"windowDays\":-1}", "{\"limit\":0}", "{\"limit\":101}",
            "{\"page\":501}", "{\"pageSize\":0}"})
    void upcomingRejectsAnOutOfRangeBodyWith400(String body) throws Exception {
        as(post("/api/v1/games/upcoming").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // A malformed request is the caller's error: 400, 405 or 415, never a 500 that pages as a server fault.
    @Test
    void aMalformedBodyAWrongMethodOrAWrongMediaTypeIsRefusedAsTheCallersError() throws Exception {
        as(post("/api/v1/games/upcoming").contentType(MediaType.APPLICATION_JSON).content("{\"limit\":"))
                .andExpect(status().isBadRequest());
        as(post("/api/v1/games/upcoming").contentType(MediaType.APPLICATION_JSON).content("{\"limit\":\"many\"}"))
                .andExpect(status().isBadRequest());
        as(post("/api/v1/games/upcoming").contentType(MediaType.TEXT_PLAIN).content("limit=1"))
                .andExpect(status().isUnsupportedMediaType());
        as(delete("/api/v1/games/search"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"));
    }

    @Test
    void upcomingPlatformsListsOnlyPlatformsWithAFutureRelease() throws Exception {
        as(get("/api/v1/games/upcoming/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platforms", contains("PC", "PlayStation 5")));
    }

    // ── random ────────────────────────────────────────────────────────────────

    @Test
    void randomDealsMainGamesOnlyUpToTheLimit() throws Exception {
        as(get("/api/v1/games/random").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 1941, 17000, 4000, 5001, 5002, 5003)));
        as(get("/api/v1/games/random").param("limit", "2"))
                .andExpect(jsonPath("$.games", hasSize(2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "501", "many"})
    void randomRejectsAnOutOfRangeLimitWith400(String limit) throws Exception {
        as(get("/api/v1/games/random").param("limit", limit)).andExpect(status().isBadRequest());
    }

    // The PlayStation-only games stay out; unreleased games are as eligible as released ones, and the
    // PC bundle and DLC are not main games.
    @Test
    void randomOnPlatformsDealsMainGamesOnThosePlatformsOnly() throws Exception {
        as(get("/api/v1/games/random").param("limit", "50").param("platform", "PC", "Nintendo Switch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 1941, 17000, 5002, 5003)));
        as(get("/api/v1/games/random").param("limit", "50").param("platform", "Dreamcast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games", empty()));
    }

    @Test
    void aBlankPlatformDrawsFromTheWholeCatalog() throws Exception {
        as(get("/api/v1/games/random").param("limit", "50").param("platform", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 1941, 17000, 4000, 5001, 5002, 5003)));
    }

    // Saturn's one game against Dreamcast's six: split evenly, a limit of 2 is one each, so the Saturn
    // game is in every hand; a draw over both platforms together would leave it out a third of the time.
    @Test
    void randomSplitsTheLimitEvenlyAcrossThePlatforms() throws Exception {
        for (int i = 1; i <= 6; i++) {
            games.save(Game.builder().igdbId(7000 + i).name("Dreamcast " + i).category(0).platforms(set(platform("Dreamcast"))).build());
        }
        games.save(Game.builder().igdbId(7100).name("Saturn only").category(0).platforms(set(platform("Saturn"))).build());

        for (int round = 0; round < 10; round++) {
            as(get("/api/v1/games/random").param("limit", "2").param("platform", "Dreamcast", "Saturn"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.games", hasSize(2)))
                    .andExpect(jsonPath("$.games[*].igdbId", hasItem(7100)));
        }
        as(get("/api/v1/games/random").param("limit", "1").param("platform", "Dreamcast", "Saturn"))
                .andExpect(jsonPath("$.games", hasSize(1)));
    }

    @Test
    void randomTakesFiftyPlatformsAndRejectsMoreWith400() throws Exception {
        String[] fifty = java.util.stream.IntStream.range(0, 50).mapToObj(i -> "Platform " + i).toArray(String[]::new);
        String[] fiftyOne = java.util.Arrays.copyOf(fifty, 51);
        fiftyOne[50] = "Platform 50";

        as(get("/api/v1/games/random").param("platform", fifty)).andExpect(status().isOk());
        as(get("/api/v1/games/random").param("platform", fiftyOne)).andExpect(status().isBadRequest());
    }

    @Test
    void randomQualityGatesOnGenreRatingAndVotes() throws Exception {
        as(get("/api/v1/games/random-quality").param("genre", "role-playing (rpg)")
                .param("minRating", "8.6").param("minVotes", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games[*].igdbId", containsInAnyOrder(1942, 17000)));
        as(get("/api/v1/games/random-quality").param("genre", RPG).param("minRating", "8.6").param("minVotes", "400"))
                .andExpect(jsonPath("$.games[*].igdbId", contains(1942)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "genre=Adventure&limit=0", "genre=Adventure&limit=201", "genre=Adventure&minVotes=-1",
            "genre=Adventure&minRating=good"})
    void randomQualityRejectsAMissingGenreOrAnOutOfRangeParameterWith400(String query) throws Exception {
        as(get("/api/v1/games/random-quality?" + query)).andExpect(status().isBadRequest());
    }

    // ── genres, tags, platforms ───────────────────────────────────────────────

    @Test
    void genresAreTheCachedNamesInOrder() throws Exception {
        as(get("/api/v1/games/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genres", contains("Adventure", RPG, "Simulator")));
    }

    @Test
    void popularTagsAreOrderedByUseWithTheJunkListLeftOut() throws Exception {
        as(get("/api/v1/games/tags/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("story rich"))
                .andExpect(jsonPath("$.tags", containsInAnyOrder("story rich", "open world", "pixel art")));
        as(get("/api/v1/games/tags/popular").param("limit", "1"))
                .andExpect(jsonPath("$.tags", contains("story rich")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    void popularTagsRejectsAnOutOfRangeLimitWith400(String limit) throws Exception {
        as(get("/api/v1/games/tags/popular").param("limit", limit)).andExpect(status().isBadRequest());
    }

    @Test
    void platformsAreGroupedUnderTheirUmbrellas() throws Exception {
        as(get("/api/v1/games/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].label").value("PC"))
                .andExpect(jsonPath("$.groups[0].umbrella").value(false))
                .andExpect(jsonPath("$.groups[1].label").value("PlayStation"))
                .andExpect(jsonPath("$.groups[1].platforms", contains("PlayStation 5")))
                .andExpect(jsonPath("$.groups[2].label").value("Nintendo"))
                .andExpect(jsonPath("$.others", empty()));
    }

    @Test
    void platformCatalogHoldsOnlyPreferenceEligiblePlatformsInPickerOrder() throws Exception {
        as(get("/api/v1/platforms/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("PC", "PlayStation 5")))
                .andExpect(jsonPath("$[0].category").value("pc"))
                .andExpect(jsonPath("$[1].displayOrder").value(10));
    }

    // ── franchise, collection, developer, editions, similar ───────────────────

    @Test
    void byFranchiseServesMainGamesOldestFirstAndHonoursTheExclusion() throws Exception {
        as(get("/api/v1/games/by-franchise/the witcher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].igdbId", contains(1941, 1942)));
        as(get("/api/v1/games/by-franchise/The Witcher").param("excludeIgdbId", "1942"))
                .andExpect(jsonPath("$[*].igdbId", contains(1941)));
        as(get("/api/v1/games/by-franchise/The Witcher").param("limit", "1"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void byCollectionServesMainGamesOldestFirst() throws Exception {
        as(get("/api/v1/games/by-collection/The Witcher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].igdbId", contains(1941, 1942)));
    }

    // The developer column is a CSV, so a name matches whole entries only. The newest games are fetched,
    // then listed oldest first, since the variant dedupe sorts ascending.
    @Test
    void byDeveloperMatchesAWholeDeveloperName() throws Exception {
        as(get("/api/v1/games/by-developer/CD Projekt RED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].igdbId", contains(1941, 1942)));
        as(get("/api/v1/games/by-developer/CD Projekt"))
                .andExpect(jsonPath("$", empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"by-franchise/The Witcher?limit=0", "by-collection/The Witcher?limit=101",
            "by-developer/ConcernedApe?excludeIgdbId=0"})
    void listEndpointsRejectAnOutOfRangeParameterWith400(String path) throws Exception {
        as(get("/api/v1/games/" + path)).andExpect(status().isBadRequest());
    }

    // DLC and expansions live in their own stack, so an edition list leaves them out.
    @Test
    void editionsListTheParentsVariantsWithoutItsExpansions() throws Exception {
        as(get("/api/v1/games/1942/editions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].igdbId", contains(3001)));
        as(get("/api/v1/games/99999/editions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        as(get("/api/v1/games/0/editions")).andExpect(status().isBadRequest());
    }

    @Test
    void similarServesThePrecomputedPeersInRankOrder() throws Exception {
        jdbc.update("INSERT INTO game_similarities VALUES (1942, 17000, 0.4, 2, now()), (1942, 1941, 0.8, 1, now())");

        as(get("/api/v1/games/1942/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].igdbId", contains(1941, 17000)));
        as(get("/api/v1/games/1942/similar").param("limit", "1"))
                .andExpect(jsonPath("$[*].igdbId", contains(1941)));
        as(get("/api/v1/games/1941/similar"))
                .andExpect(jsonPath("$", empty()));
        as(get("/api/v1/games/1942/similar").param("limit", "51")).andExpect(status().isBadRequest());
    }
}
