package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.Genre;
import com.thegamecellar.gameservice.model.entity.Tag;
import com.thegamecellar.gameservice.repository.SyncStateRepository;
import com.thegamecellar.gameservice.support.GameServiceTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// The three scheduled jobs, each run once by hand against Postgres. IGDB answers through the stubbed
// socket and the image CDN through the art service's fetcher seam; everything else is production code.
class JobsPostgresTest extends GameServiceTestBase {

    @Autowired private SimilarityWorker similarityWorker;
    @Autowired private IgdbCatalogWorker catalogWorker;
    @Autowired private BackgroundArtWorker backgroundArtWorker;
    @Autowired private BackgroundArtService backgroundArtService;
    @Autowired private SyncStateRepository syncState;

    private Object originalFetcher;

    @AfterEach
    void restoreTheCdnFetcher() {
        if (originalFetcher != null) {
            ReflectionTestUtils.setField(backgroundArtService, "imageFetcher", originalFetcher);
        }
    }

    // ── SimilarityWorker ──────────────────────────────────────────────────────

    // Jaccard over genres + themes + tags: A shares two of three features with B and with E, so both
    // score 2/3; C shares nothing and gets no rows; the DLC is never a source.
    @Test
    void similarityWorkerWritesRankedPeersByFeatureOverlapAndLeavesComputedSourcesAlone() {
        rated(1, 0, set(genre("RPG")), set(tag("open world"), tag("story rich")));
        rated(2, 0, set(genre("RPG")), set(tag("open world")));
        rated(3, 0, set(genre("Puzzle")), set());
        rated(4, 1, set(genre("RPG")), set(tag("open world")));
        rated(5, 0, set(genre("RPG")), set(tag("story rich")));

        similarityWorker.runBatch();

        assertThat(rowsFor(1)).containsExactlyInAnyOrder("2:0.6667", "5:0.6667");
        assertThat(rowsFor(2)).containsExactly("1:0.6667", "5:0.3333");
        assertThat(rowsFor(3)).isEmpty();
        assertThat(rowsFor(4)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_similarities WHERE source_igdb_id = similar_igdb_id",
                Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT rank FROM game_similarities WHERE source_igdb_id = 1 ORDER BY rank",
                Integer.class)).containsExactly(1, 2);

        var before = jdbc.queryForList("SELECT computed_at FROM game_similarities WHERE source_igdb_id = 1", Object.class);
        similarityWorker.runBatch();
        assertThat(jdbc.queryForList("SELECT computed_at FROM game_similarities WHERE source_igdb_id = 1", Object.class))
                .isEqualTo(before);
    }

    private void rated(int igdbId, int category, Set<Genre> genres, Set<Tag> tags) {
        games.save(Game.builder().igdbId(igdbId).name("Game " + igdbId).category(category)
                .totalRating(new BigDecimal("7.00")).totalRatingCount(10).genres(genres).tags(tags).build());
    }

    private List<String> rowsFor(int source) {
        return jdbc.queryForList("SELECT similar_igdb_id || ':' || score FROM game_similarities "
                + "WHERE source_igdb_id = ? ORDER BY rank", String.class, source);
    }

    // ── IgdbCatalogWorker ─────────────────────────────────────────────────────

    // One night: the catalog walk, new releases and upcoming releases each cache what IGDB hands over,
    // the walk's next offset is saved, and every upcoming row is refreshed with IGDB's current date.
    @Test
    void catalogSyncCachesEachDiscoveryPhaseSavesTheOffsetAndRefreshesUpcomingRows() {
        long future = daysFromNow(30);
        long slipped = daysFromNow(60);
        games.save(Game.builder().igdbId(804).name("Already Upcoming").category(0)
                .firstReleaseDate(future).hypes(1).build());

        answerGames(igdbWorker, body -> {
            boolean firstPage = body.contains("offset 0;");
            if (body.contains("sort first_release_date desc")) return firstPage ? igdbGame(802, "New Release", 1_600_000_000L) : "[]";
            if (body.contains("sort first_release_date asc")) return firstPage ? igdbGame(803, "Coming Up", future) : "[]";
            return firstPage ? igdbGame(801, "Catalog Pick", 1_400_000_000L) : "[]";
        });
        answerGames(igdb, body -> {
            if (body.contains("where id = 804;")) return "[{\"id\":804,\"name\":\"Already Upcoming\",\"first_release_date\":" + slipped + ",\"hypes\":99}]";
            if (body.contains("where id = 803;")) return igdbGame(803, "Coming Up", future);
            return "[]";
        });

        catalogWorker.syncCatalog();

        assertThat(games.findByIgdbId(801)).isPresent();
        assertThat(games.findByIgdbId(802)).isPresent();
        assertThat(games.findByIgdbId(803)).isPresent();
        assertThat(jdbc.queryForList("""
                SELECT ge.name FROM game_genres gg JOIN genres ge ON ge.id = gg.genre_id
                JOIN games g ON g.id = gg.game_id WHERE g.igdb_id = 801
                """, String.class)).containsExactly("Adventure");
        assertThat(syncState.findById("igdb_discovery_offset").orElseThrow().getStateValue()).isEqualTo("1500");

        Game refreshed = games.findByIgdbId(804).orElseThrow();
        assertThat(refreshed.getHypes()).isEqualTo(99);
        assertThat(refreshed.getFirstReleaseDate()).isEqualTo(slipped);
    }

    // A failed page is retried once; a second failure costs only that page, not the night.
    @Test
    void aCatalogPageThatFailsTwiceIsSkippedAndTheRestOfTheNightStillRuns() {
        answerGames(igdbWorker, body -> {
            if (body.contains("sort first_release_date desc") && body.contains("offset 0;")) {
                return igdbGame(802, "New Release", 1_600_000_000L);
            }
            if (body.contains("sort aggregated_rating_count desc") && body.contains("offset 0;")) {
                throw new IllegalStateException("IGDB down for the catalog walk");
            }
            return "[]";
        });

        catalogWorker.syncCatalog();

        assertThat(games.findByIgdbId(802)).isPresent();
        assertThat(games.findByIgdbId(801)).isEmpty();
        assertThat(syncState.findById("igdb_discovery_offset").orElseThrow().getStateValue()).isEqualTo("1500");
    }

    private static String igdbGame(int id, String name, long firstReleaseDate) {
        return "[{\"id\":" + id + ",\"name\":\"" + name + "\",\"summary\":\"About " + name + ".\",\"game_type\":0,"
                + "\"first_release_date\":" + firstReleaseDate + ",\"genres\":[{\"id\":31,\"name\":\"Adventure\"}],"
                + "\"platforms\":[{\"id\":6,\"name\":\"PC (Microsoft Windows)\"}]}]";
    }

    // ── BackgroundArtWorker ───────────────────────────────────────────────────

    // Each undecided row gets a decision: the first candidate that passes the pixel check, or 'none'.
    // A decided row and a harvested-but-empty pool are never revisited.
    @Test
    void backgroundArtPassDecidesEveryPendingRowAndDownloadsNothingForTheRest() throws Exception {
        Map<String, byte[]> cdn = Map.of(
                "art-good", noiseJpeg(), "art-flat", flatJpeg(), "shot-good", noiseJpeg(), "art-flat-2", flatJpeg());
        List<String> requested = new ArrayList<>();
        originalFetcher = ReflectionTestUtils.getField(backgroundArtService, "imageFetcher");
        backgroundArtService.setImageFetcher(url -> {
            requested.add(url);
            String id = url.substring(url.lastIndexOf('/') + 1, url.length() - ".jpg".length());
            byte[] bytes = cdn.get(id);
            if (bytes == null) throw new IOException("not on the CDN: " + id);
            return bytes;
        });

        games.save(Game.builder().igdbId(1).name("Artwork wins").ratingCount(100)
                .backgroundArtPool("[{\"id\":\"art-good\",\"w\":1920,\"h\":1080,\"s\":\"a\"}]").build());
        games.save(Game.builder().igdbId(2).name("Screenshot wins").ratingCount(50)
                .backgroundArtPool("[{\"id\":\"art-flat\",\"w\":1920,\"h\":1080,\"s\":\"a\"},"
                        + "{\"id\":\"shot-good\",\"w\":1280,\"h\":720,\"s\":\"s\"}]").build());
        games.save(Game.builder().igdbId(3).name("Nothing passes").ratingCount(10)
                .backgroundArtPool("[{\"id\":\"art-flat-2\",\"w\":1920,\"h\":1080,\"s\":\"a\"}]").build());
        games.save(Game.builder().igdbId(4).name("Already decided").ratingCount(999)
                .backgroundArtPool("[{\"id\":\"never-fetched\",\"w\":1920,\"h\":1080,\"s\":\"a\"}]")
                .backgroundImageId("kept").backgroundSource("artwork").build());
        games.save(Game.builder().igdbId(5).name("Empty pool").ratingCount(999).backgroundArtPool("[]").build());

        backgroundArtWorker.nightlyPass();

        assertThat(decision(1)).isEqualTo("art-good:artwork");
        assertThat(decision(2)).isEqualTo("shot-good:screenshot");
        assertThat(decision(3)).isEqualTo("null:none");
        assertThat(decision(4)).isEqualTo("kept:artwork");
        assertThat(decision(5)).isEqualTo("null:null");
        assertThat(requested).allMatch(u -> u.startsWith("https://images.igdb.com/igdb/image/upload/t_1080p/"));
        assertThat(requested).noneMatch(u -> u.contains("never-fetched"));
    }

    private String decision(int igdbId) {
        Game g = games.findByIgdbId(igdbId).orElseThrow();
        return g.getBackgroundImageId() + ":" + g.getBackgroundSource();
    }

    private static byte[] noiseJpeg() throws IOException {
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < 720; y++) {
            for (int x = 0; x < 1280; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        return toJpeg(image);
    }

    // Flat colour, zero-padded past the file-size gate so the entropy and colour checks decide.
    private static byte[] flatJpeg() throws IOException {
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 30, 30));
        g.fillRect(0, 0, 1280, 720);
        g.dispose();
        byte[] jpeg = toJpeg(image);
        byte[] padded = new byte[Math.max(45_000, jpeg.length)];
        System.arraycopy(jpeg, 0, padded, 0, jpeg.length);
        return padded;
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
