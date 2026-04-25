package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.SyncState;
import com.thegamecellar.gameservice.repository.SyncStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IgdbCatalogWorker {

    static final String IGDB_DISCOVERY_OFFSET_KEY = "igdb_discovery_offset";
    private static final int EARLY_EXIT_THRESHOLD = 3;

    private final GameService gameService;
    private final SyncStateRepository syncStateRepository;

    @Value("${igdb.worker.enabled:true}")
    private boolean enabled;

    @Value("${igdb.worker.discovery-limit:500}")
    private int discoveryLimit;

    @Value("${igdb.worker.discovery-pages:100}")
    private int discoveryPages;

    @Value("${igdb.worker.new-releases-pages:10}")
    private int newReleasesPages;

    @Value("${igdb.worker.enrichment-limit:400}")
    private int enrichmentLimit;

    @Value("${igdb.worker.rate-limit-delay-ms:250}")
    private long rateLimitDelayMs;

    public void quickSync() {
        log.info("IGDB quick sync started");
        try {
            int newGames = gameService.syncIgdbCatalogOffset(0, 100);
            log.info("Quick sync discovery: {} new games", newGames);
            int enriched = gameService.enrichNextBatchFromIgdb(50);
            log.info("Quick sync enrichment: {} games enriched", enriched);
        } catch (Exception e) {
            log.error("Quick sync failed: {}", e.getMessage());
        }
        log.info("IGDB quick sync complete");
    }

    @Scheduled(cron = "${igdb.worker.cron:0 30 3 * * *}")
    public void syncCatalog() {
        if (!enabled) {
            log.info("IGDB catalog worker disabled, skipping sync");
            return;
        }
        log.info("IGDB catalog sync started");

        int startOffset = getLastDiscoveryOffset();
        int extraEnrichmentBudget = runMainDiscovery(startOffset);

        runNewReleasesDiscovery();

        int totalEnrichmentLimit = enrichmentLimit + extraEnrichmentBudget;
        int enriched = gameService.enrichNextBatchFromIgdb(totalEnrichmentLimit);

        log.info("IGDB catalog sync complete — enriched {} games", enriched);
    }

    private int runMainDiscovery(int startOffset) {
        int newGamesTotal = 0;
        int consecutiveEmpty = 0;
        int extraBudget = 0;
        int offset = startOffset;
        boolean earlyExit = false;

        for (int i = 0; i < discoveryPages; i++, offset += discoveryLimit) {
            try {
                int newGames = gameService.syncIgdbCatalogOffset(offset, discoveryLimit);
                newGamesTotal += newGames;
                if (newGames == 0) {
                    if (++consecutiveEmpty >= EARLY_EXIT_THRESHOLD) {
                        int remaining = discoveryPages - i - 1;
                        extraBudget += remaining;
                        earlyExit = true;
                        log.warn("Discovery early exit after {} consecutive empty responses — {} budget redirected to enrichment", EARLY_EXIT_THRESHOLD, remaining);
                        break;
                    }
                } else {
                    consecutiveEmpty = 0;
                }
                rateLimitSleep();
            } catch (Exception e) {
                log.error("Main discovery failed at offset {}: {}", offset, e.getMessage());
            }
        }

        // Catalog exhausted → reset to 0 for next run; otherwise continue from current position
        int nextOffset = earlyExit ? 0 : offset;
        saveLastDiscoveryOffset(nextOffset);
        log.info("Main discovery complete — {} new games, next offset={}", newGamesTotal, nextOffset);
        return extraBudget;
    }

    private void runNewReleasesDiscovery() {
        int newGames = 0;
        int offset = 0;
        for (int i = 0; i < newReleasesPages; i++, offset += discoveryLimit) {
            try {
                newGames += gameService.syncIgdbNewReleasesOffset(offset, discoveryLimit);
                rateLimitSleep();
            } catch (Exception e) {
                log.error("New releases discovery failed at offset {}: {}", offset, e.getMessage());
            }
        }
        log.info("New releases discovery complete — {} new games", newGames);
    }

    private void rateLimitSleep() {
        try {
            Thread.sleep(rateLimitDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int getLastDiscoveryOffset() {
        return syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getStateValue());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .orElse(0);
    }

    private void saveLastDiscoveryOffset(int offset) {
        syncStateRepository.save(new SyncState(IGDB_DISCOVERY_OFFSET_KEY, String.valueOf(offset)));
    }
}
