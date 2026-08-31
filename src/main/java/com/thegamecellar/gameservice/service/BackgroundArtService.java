package com.thegamecellar.gameservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chooses one background image per game from the raw candidate pool the catalog sync stores.
 * Ladder: poster-shaped artworks first, then wide screenshots, then any screenshot. A candidate
 * must also survive a pixel check, because IGDB artworks mix real key art with logos and banner
 * strips that shape rules alone cannot catch, and t_1080p upscales small sources.
 */
@Slf4j
@Service
public class BackgroundArtService {

    public static final String SOURCE_ARTWORK = "artwork";
    public static final String SOURCE_SCREENSHOT = "screenshot";
    public static final String SOURCE_NONE = "none";

    static final int MAX_CANDIDATES = 3;
    // Shape rules run on the raw metadata width, never the served size: t_1080p upscales,
    // so a 256 px logo comes back as a plausible-looking 1080p file.
    private static final int MIN_ARTWORK_WIDTH = 1200;
    private static final double ARTWORK_ASPECT_MIN = 1.4;
    private static final double ARTWORK_ASPECT_MAX = 2.0;
    private static final double SCREENSHOT_ASPECT_MIN = 1.25;
    private static final int MIN_FILE_BYTES = 40_000;
    private static final double MIN_GREY_ENTROPY = 3.0;
    private static final int MIN_DISTINCT_COLOURS = 1_000;
    private static final int CHECK_THUMB_SIZE = 128;
    private static final String IMAGE_URL_TEMPLATE =
            "https://images.igdb.com/igdb/image/upload/t_1080p/%s.jpg";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @FunctionalInterface
    interface ImageFetcher {
        byte[] fetch(String url) throws IOException, InterruptedException;
    }

    private ImageFetcher imageFetcher = BackgroundArtService::defaultFetch;

    // Test seam: replaces the CDN download so unit tests run on synthetic images.
    void setImageFetcher(ImageFetcher imageFetcher) {
        this.imageFetcher = imageFetcher;
    }

    public record Pick(String imageId, String source) {}

    record Candidate(String imageId, String source, int width, int height) {
        double aspect() {
            return height <= 0 ? 0 : (double) width / height;
        }
    }

    /**
     * Downloads at most three ladder candidates and returns the first that passes the pixel
     * check. Always decides: a pool where nothing qualifies or nothing passes yields
     * {@code SOURCE_NONE}, which keeps the row off the work list on later passes.
     */
    public Pick decide(String poolJson) {
        List<Candidate> ladder = ladder(poolJson);
        int tried = 0;
        for (Candidate candidate : ladder) {
            if (tried >= MAX_CANDIDATES) break;
            tried++;
            try {
                byte[] bytes = imageFetcher.fetch(String.format(IMAGE_URL_TEMPLATE, candidate.imageId()));
                if (isRealArt(bytes)) {
                    return new Pick(candidate.imageId(), candidate.source());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Pick(null, SOURCE_NONE);
            } catch (Exception e) {
                log.debug("Background art candidate {} failed: {}", candidate.imageId(), e.getMessage());
            }
        }
        return new Pick(null, SOURCE_NONE);
    }

    List<Candidate> ladder(String poolJson) {
        List<Candidate> pool = readPool(poolJson);
        List<Candidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        pool.stream()
                .filter(c -> SOURCE_ARTWORK.equals(c.source()))
                .filter(c -> c.width() >= MIN_ARTWORK_WIDTH)
                .filter(c -> c.aspect() >= ARTWORK_ASPECT_MIN && c.aspect() <= ARTWORK_ASPECT_MAX)
                .sorted(Comparator.comparingInt(Candidate::width).reversed())
                .forEach(c -> addUnique(result, seen, c));
        pool.stream()
                .filter(c -> SOURCE_SCREENSHOT.equals(c.source()))
                .filter(c -> c.aspect() >= SCREENSHOT_ASPECT_MIN)
                .forEach(c -> addUnique(result, seen, c));
        pool.stream()
                .filter(c -> SOURCE_SCREENSHOT.equals(c.source()))
                .forEach(c -> addUnique(result, seen, c));
        return result;
    }

    private static void addUnique(List<Candidate> result, Set<String> seen, Candidate candidate) {
        if (seen.add(candidate.imageId())) {
            result.add(candidate);
        }
    }

    private List<Candidate> readPool(String poolJson) {
        if (poolJson == null || poolJson.isBlank()) return List.of();
        List<Map<String, Object>> rows;
        try {
            rows = JSON.readValue(poolJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("Unreadable background art pool, treating as empty: {}", e.getMessage());
            return List.of();
        }
        List<Candidate> pool = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            Object w = row.get("w");
            Object h = row.get("h");
            Object s = row.get("s");
            if (!(id instanceof String imageId) || imageId.isBlank()) continue;
            if (!(w instanceof Number width) || !(h instanceof Number height)) continue;
            String source = "a".equals(s) ? SOURCE_ARTWORK : "s".equals(s) ? SOURCE_SCREENSHOT : null;
            if (source == null) continue;
            pool.add(new Candidate(imageId, source, width.intValue(), height.intValue()));
        }
        return pool;
    }

    boolean isRealArt(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < MIN_FILE_BYTES) return false;
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) return false;
        BufferedImage thumb = scaleToCheckSize(image);
        int width = thumb.getWidth();
        int height = thumb.getHeight();
        int[] histogram = new int[256];
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = thumb.getRGB(x, y) & 0xFFFFFF;
                colours.add(rgb);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int grey = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                histogram[Math.min(grey, 255)]++;
            }
        }
        double total = (double) width * height;
        double entropy = 0;
        for (int count : histogram) {
            if (count == 0) continue;
            double p = count / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy >= MIN_GREY_ENTROPY && colours.size() >= MIN_DISTINCT_COLOURS;
    }

    private static BufferedImage scaleToCheckSize(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double scale = (double) CHECK_THUMB_SIZE / Math.max(width, height);
        if (scale >= 1.0 && image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        int targetWidth = Math.max(1, (int) Math.round(width * Math.min(scale, 1.0)));
        int targetHeight = Math.max(1, (int) Math.round(height * Math.min(scale, 1.0)));
        BufferedImage thumb = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return thumb;
    }

    private static byte[] defaultFetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }
}
