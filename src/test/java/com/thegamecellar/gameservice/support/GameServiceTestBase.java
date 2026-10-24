package com.thegamecellar.gameservice.support;

import com.thegamecellar.gameservice.model.entity.Franchise;
import com.thegamecellar.gameservice.model.entity.GameCollection;
import com.thegamecellar.gameservice.model.entity.GameMode;
import com.thegamecellar.gameservice.model.entity.Genre;
import com.thegamecellar.gameservice.model.entity.Platform;
import com.thegamecellar.gameservice.model.entity.PlayerPerspective;
import com.thegamecellar.gameservice.model.entity.Tag;
import com.thegamecellar.gameservice.model.entity.Theme;
import com.thegamecellar.gameservice.repository.FranchiseRepository;
import com.thegamecellar.gameservice.repository.GameCollectionRepository;
import com.thegamecellar.gameservice.repository.GameModeRepository;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.repository.GenreRepository;
import com.thegamecellar.gameservice.repository.PlatformRepository;
import com.thegamecellar.gameservice.repository.PlayerPerspectiveRepository;
import com.thegamecellar.gameservice.repository.TagRepository;
import com.thegamecellar.gameservice.repository.ThemeRepository;
import com.thegamecellar.gameservice.service.AdminSyncExecutor;
import com.thegamecellar.gameservice.service.IgdbTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// One Spring context and one Postgres for the whole suite: the real migrations build the schema and
// Hibernate validates the entities against it. Only what lies outside the service is replaced: the
// JwtDecoder (a token is whatever a test issues), the socket under the two IGDB RestTemplates, so the
// client still builds and checks its URL, headers and body and maps the JSON, and the image CDN.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class GameServiceTestBase {

    // Started once and never stopped: every test class shares it, and Testcontainers removes it at JVM exit.
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("game_test_db")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    protected static final String USER = "11111111-0000-4000-8000-000000000001";
    protected static final String ADMIN = "aaaaaaaa-0000-4000-8000-00000000000a";
    protected static final String USER_TOKEN = "user-token";
    protected static final String ADMIN_TOKEN = "admin-token";
    protected static final String INTERNAL_HEADER = "X-Internal-Token";
    protected static final String INTERNAL_TOKEN = "test-internal-token";
    protected static final String IGDB_GAMES = "https://api.igdb.com/v4/games";
    protected static final String IGDB_GENRES = "https://api.igdb.com/v4/genres";
    protected static final String TWITCH_TOKEN = "https://id.twitch.tv/oauth2/token";
    protected static final String HELD_IGDB_TOKEN = "held-igdb-token";

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected GameRepository games;
    @Autowired protected AdminSyncExecutor adminSyncExecutor;
    @Autowired private GenreRepository genreRepository;
    @Autowired private PlatformRepository platformRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private ThemeRepository themeRepository;
    @Autowired private GameModeRepository gameModeRepository;
    @Autowired private PlayerPerspectiveRepository perspectiveRepository;
    @Autowired private FranchiseRepository franchiseRepository;
    @Autowired private GameCollectionRepository collectionRepository;
    @Autowired private IgdbTokenService igdbTokenService;
    @Autowired @Qualifier("igdbRestTemplate") private RestTemplate igdbRestTemplate;
    @Autowired @Qualifier("igdbWorkerRestTemplate") private RestTemplate igdbWorkerRestTemplate;

    @MockitoBean protected JwtDecoder jwtDecoder;

    // The request path and the token fetch share the first template; the worker's bulk pages use the second.
    protected MockRestServiceServer igdb;
    protected MockRestServiceServer igdbWorker;

    @BeforeEach
    void tokensAndIgdb() {
        lenient().when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("unknown token"));
        issue(USER_TOKEN, USER, "USER");
        issue(ADMIN_TOKEN, ADMIN, "USER", "ADMIN");

        igdb = MockRestServiceServer.bindTo(igdbRestTemplate).ignoreExpectOrder(true).build();
        igdbWorker = MockRestServiceServer.bindTo(igdbWorkerRestTemplate).ignoreExpectOrder(true).build();
        // A Twitch token already held, so a test meets the token endpoint only when it drops this itself.
        ReflectionTestUtils.setField(igdbTokenService, "accessToken", HELD_IGDB_TOKEN);
        ReflectionTestUtils.setField(igdbTokenService, "expiresAt", Instant.now().plus(Duration.ofDays(30)));
    }

    // An admin job still running would write into the next test's tables.
    @AfterEach
    void emptyEveryTable() {
        awaitAdminJobDone();
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
                String.class);
        jdbc.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }

    protected void awaitAdminJobDone() {
        await().atMost(Duration.ofSeconds(20)).until(() -> !adminSyncExecutor.isRunning());
    }

    // Keycloak-shaped, realm roles included, so the app's own role converter runs as in production.
    protected void issue(String token, String subject, String... realmRoles) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                .build();
        doReturn(jwt).when(jwtDecoder).decode(token);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // ── IGDB ──────────────────────────────────────────────────────────────────

    protected void dropHeldIgdbToken() {
        ReflectionTestUtils.setField(igdbTokenService, "accessToken", null);
        ReflectionTestUtils.setField(igdbTokenService, "expiresAt", Instant.EPOCH);
    }

    // Every /games query on the given server is answered by the function, which reads the Apicalypse body.
    protected static void answerGames(MockRestServiceServer server, Function<String, String> jsonForQuery) {
        server.expect(ExpectedCount.manyTimes(), requestTo(IGDB_GAMES))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(byBody(jsonForQuery));
    }

    protected static void failGames(MockRestServiceServer server) {
        server.expect(ExpectedCount.manyTimes(), requestTo(IGDB_GAMES))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    protected static ResponseCreator byBody(Function<String, String> jsonForQuery) {
        return request -> {
            String body = ((MockClientHttpRequest) request).getBodyAsString();
            return withSuccess(jsonForQuery.apply(body), MediaType.APPLICATION_JSON).createResponse(request);
        };
    }

    // ── catalog seeding ───────────────────────────────────────────────────────

    @SafeVarargs
    protected static <T> Set<T> set(T... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    protected Genre genre(String name) {
        return genreRepository.findByName(name).orElseGet(() -> genreRepository.save(new Genre(name)));
    }

    protected Platform platform(String name) {
        return platformRepository.findByName(name).orElseGet(() -> platformRepository.save(new Platform(name)));
    }

    protected Platform eligiblePlatform(String name, String category, int displayOrder) {
        Platform p = platform(name);
        p.setIsPreferenceEligible(true);
        p.setCategory(category);
        p.setDisplayOrder(displayOrder);
        return platformRepository.save(p);
    }

    protected Tag tag(String name) {
        return tagRepository.findByName(name).orElseGet(() -> tagRepository.save(new Tag(name)));
    }

    protected Theme theme(String name) {
        return themeRepository.findByName(name).orElseGet(() -> themeRepository.save(new Theme(name)));
    }

    protected GameMode mode(String name) {
        return gameModeRepository.findByName(name).orElseGet(() -> gameModeRepository.save(new GameMode(name)));
    }

    protected PlayerPerspective perspective(String name) {
        return perspectiveRepository.findByName(name).orElseGet(() -> perspectiveRepository.save(new PlayerPerspective(name)));
    }

    protected Franchise franchise(String name) {
        return franchiseRepository.findByName(name).orElseGet(() -> franchiseRepository.save(new Franchise(name)));
    }

    protected GameCollection collection(String name) {
        return collectionRepository.findByName(name).orElseGet(() -> collectionRepository.save(new GameCollection(name)));
    }

    protected static long daysFromNow(int days) {
        return Instant.now().plus(Duration.ofDays(days)).getEpochSecond();
    }
}
