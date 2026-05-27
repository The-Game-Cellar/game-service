package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.GameSimilarity;
import com.thegamecellar.gameservice.model.entity.Genre;
import com.thegamecellar.gameservice.model.entity.Tag;
import com.thegamecellar.gameservice.model.entity.Theme;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.repository.GameSimilarityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Catalog-side similarity computation. Background scheduled job picks a batch of main-category
// games each tick and computes top-K similar peers by Jaccard overlap over the union of
// genres + themes + tags. Output written to game_similarities. Phase 8 read path will serve
// /similar/{gameId} from this table.
@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarityWorker {

    public static final int TOP_K = 20;
    public static final int BATCH = 10;
    private static final BigDecimal SCORE_CAP = new BigDecimal("9.9999");

    private final GameRepository gameRepository;
    private final GameSimilarityRepository similarityRepository;

    // Initial delay long enough that the IGDB catalog worker grabs the early CPU at boot. Per-tick
    // load is bounded by BATCH so a 1m delay keeps DB pressure modest.
    @Scheduled(fixedDelayString = "${similarity.worker.fixed-delay-ms:60000}",
            initialDelayString = "${similarity.worker.initial-delay-ms:120000}")
    @Transactional
    public void runBatch() {
        // Pick BATCH games that don't already have similarity rows. Main category only so we
        // don't burn cycles on DLC/Bundle entries.
        List<Game> candidates = gameRepository.findUncomputedSimilaritySources(PageRequest.of(0, BATCH));
        if (candidates.isEmpty()) return;

        // Pre-fetch a broad pool to compare against. Limiting to recent + rated games keeps
        // the working set small; bulk catalog coverage builds up over many ticks.
        List<Game> peerPool = gameRepository.findRandomMainGamesForSimilarity(500);
        if (peerPool.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        int computed = 0;
        for (Game src : candidates) {
            Set<String> srcFeatures = features(src);
            if (srcFeatures.isEmpty()) continue;

            record Scored(Game peer, double score) {}
            List<Scored> scored = new ArrayList<>(peerPool.size());
            for (Game peer : peerPool) {
                if (peer.getIgdbId() == null || peer.getIgdbId().equals(src.getIgdbId())) continue;
                Set<String> peerFeatures = features(peer);
                if (peerFeatures.isEmpty()) continue;
                int intersection = 0;
                for (String f : srcFeatures) if (peerFeatures.contains(f)) intersection++;
                if (intersection == 0) continue;
                int union = srcFeatures.size() + peerFeatures.size() - intersection;
                double jaccard = (double) intersection / union;
                scored.add(new Scored(peer, jaccard));
            }
            scored.sort(Comparator.comparingDouble(Scored::score).reversed());

            similarityRepository.deleteBySource(src.getIgdbId());
            similarityRepository.flush();

            int rank = 1;
            List<GameSimilarity> rows = new ArrayList<>(Math.min(TOP_K, scored.size()));
            for (Scored s : scored) {
                if (rows.size() >= TOP_K) break;
                rows.add(GameSimilarity.builder()
                        .sourceIgdbId(src.getIgdbId())
                        .similarIgdbId(s.peer.getIgdbId())
                        .score(clamp(s.score))
                        .rank((short) rank)
                        .computedAt(now)
                        .build());
                rank++;
            }
            if (!rows.isEmpty()) {
                similarityRepository.saveAll(rows);
                computed++;
            }
        }
        if (computed > 0) {
            log.info("SimilarityWorker computed similarities for {} games this tick", computed);
        }
    }

    private static Set<String> features(Game g) {
        Set<String> all = new HashSet<>();
        if (g.getGenres() != null) {
            for (Genre x : g.getGenres()) if (x.getName() != null) all.add("g:" + x.getName());
        }
        if (g.getThemes() != null) {
            for (Theme x : g.getThemes()) if (x.getName() != null) all.add("th:" + x.getName());
        }
        if (g.getTags() != null) {
            for (Tag x : g.getTags()) if (x.getName() != null) all.add("t:" + x.getName());
        }
        return all;
    }

    private static BigDecimal clamp(double raw) {
        BigDecimal bd = BigDecimal.valueOf(Math.max(0.0, raw)).setScale(4, RoundingMode.HALF_UP);
        return bd.compareTo(SCORE_CAP) > 0 ? SCORE_CAP : bd;
    }
}
