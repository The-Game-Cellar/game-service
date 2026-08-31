package com.thegamecellar.gameservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundArtServiceTest {

    private BackgroundArtService service;

    @BeforeEach
    void setUp() {
        service = new BackgroundArtService();
    }

    // ── ladder ────────────────────────────────────────────────────────────────

    @Test
    void ladder_prefers_wide_artworks_then_wide_screenshots_then_any_screenshot() {
        String pool = """
                [
                  {"id":"shot-narrow","w":800,"h":800,"s":"s"},
                  {"id":"shot-wide","w":1280,"h":720,"s":"s"},
                  {"id":"art-good","w":1920,"h":1080,"s":"a"},
                  {"id":"art-wider","w":2560,"h":1447,"s":"a"}
                ]
                """;

        List<BackgroundArtService.Candidate> ladder = service.ladder(pool);

        assertThat(ladder).extracting(BackgroundArtService.Candidate::imageId)
                .containsExactly("art-wider", "art-good", "shot-wide", "shot-narrow");
    }

    @Test
    void ladder_drops_banners_logos_and_portrait_artworks() {
        String pool = """
                [
                  {"id":"banner","w":5981,"h":920,"s":"a"},
                  {"id":"logo","w":256,"h":256,"s":"a"},
                  {"id":"portrait","w":1200,"h":2000,"s":"a"},
                  {"id":"just-under-min-width","w":1199,"h":700,"s":"a"}
                ]
                """;

        assertThat(service.ladder(pool)).isEmpty();
    }

    @Test
    void ladder_is_empty_for_null_empty_or_unreadable_pool() {
        assertThat(service.ladder(null)).isEmpty();
        assertThat(service.ladder("[]")).isEmpty();
        assertThat(service.ladder("not json")).isEmpty();
    }

    // ── decide ────────────────────────────────────────────────────────────────

    @Test
    void decide_returns_first_candidate_that_passes_the_pixel_check() throws Exception {
        Map<String, byte[]> images = new HashMap<>();
        images.put("art-flat", flatJpegPadded());
        images.put("art-real", noiseJpeg());
        service.setImageFetcher(url -> images.get(idFromUrl(url)));
        String pool = """
                [
                  {"id":"art-flat","w":2560,"h":1447,"s":"a"},
                  {"id":"art-real","w":1920,"h":1080,"s":"a"}
                ]
                """;

        BackgroundArtService.Pick pick = service.decide(pool);

        assertThat(pick.imageId()).isEqualTo("art-real");
        assertThat(pick.source()).isEqualTo(BackgroundArtService.SOURCE_ARTWORK);
    }

    @Test
    void decide_tries_at_most_three_candidates() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        byte[] flat = flatJpegPadded();
        service.setImageFetcher(url -> {
            fetches.incrementAndGet();
            return flat;
        });
        String pool = """
                [
                  {"id":"s1","w":1280,"h":720,"s":"s"},
                  {"id":"s2","w":1280,"h":720,"s":"s"},
                  {"id":"s3","w":1280,"h":720,"s":"s"},
                  {"id":"s4","w":1280,"h":720,"s":"s"},
                  {"id":"s5","w":1280,"h":720,"s":"s"}
                ]
                """;

        BackgroundArtService.Pick pick = service.decide(pool);

        assertThat(pick.source()).isEqualTo(BackgroundArtService.SOURCE_NONE);
        assertThat(pick.imageId()).isNull();
        assertThat(fetches.get()).isEqualTo(3);
    }

    @Test
    void decide_returns_none_without_downloading_when_nothing_qualifies() {
        service.setImageFetcher(url -> {
            throw new AssertionError("no download expected for a pool with no qualifying candidate");
        });

        BackgroundArtService.Pick pick =
                service.decide("[{\"id\":\"logo\",\"w\":256,\"h\":256,\"s\":\"a\"}]");

        assertThat(pick.source()).isEqualTo(BackgroundArtService.SOURCE_NONE);
        assertThat(pick.imageId()).isNull();
    }

    @Test
    void decide_survives_download_failures_and_falls_through_the_ladder() throws Exception {
        byte[] realShot = noiseJpeg();
        service.setImageFetcher(url -> {
            if (!"shot-ok".equals(idFromUrl(url))) throw new IOException("HTTP 404");
            return realShot;
        });
        String pool = """
                [
                  {"id":"art-missing","w":1920,"h":1080,"s":"a"},
                  {"id":"shot-ok","w":1280,"h":720,"s":"s"}
                ]
                """;

        BackgroundArtService.Pick pick = service.decide(pool);

        assertThat(pick.imageId()).isEqualTo("shot-ok");
        assertThat(pick.source()).isEqualTo(BackgroundArtService.SOURCE_SCREENSHOT);
    }

    // ── pixel check ───────────────────────────────────────────────────────────

    @Test
    void isRealArt_rejects_small_files_and_flat_images_and_accepts_detailed_ones() throws Exception {
        assertThat(service.isRealArt(new byte[10_000])).isFalse();
        assertThat(service.isRealArt(flatJpegPadded())).isFalse();
        assertThat(service.isRealArt(noiseJpeg())).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static byte[] noiseJpeg() throws Exception {
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < 720; y++) {
            for (int x = 0; x < 1280; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        return toJpeg(image);
    }

    // Flat colour, zero-padded past the file-size gate so the entropy and colour checks are exercised.
    private static byte[] flatJpegPadded() throws Exception {
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

    private static byte[] toJpeg(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static String idFromUrl(String url) {
        int slash = url.lastIndexOf('/');
        return url.substring(slash + 1, url.length() - ".jpg".length());
    }
}
