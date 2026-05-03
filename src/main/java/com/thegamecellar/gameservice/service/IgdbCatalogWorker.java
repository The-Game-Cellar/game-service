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

    @Value("${igdb.worker.upcoming-pages:20}")
    private int upcomingPages;

    @Value("${igdb.worker.rate-limit-delay-ms:250}")
    private long rateLimitDelayMs;

    public void quickSync() {
        log.info("IGDB quick sync started");
        try {
            CatalogSyncResult result = gameService.syncIgdbCatalogOffset(0, 100);
            log.info("Quick sync discovery: {} new games (fetched {})", result.cached(), result.fetched());
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
        runMainDiscovery(startOffset);

        runNewReleasesDiscovery();

        runUpcomingReleasesDiscovery();

        runUpcomingRefresh();

        log.info("IGDB catalog sync complete");
    }

    private void runMainDiscovery(int startOffset) {
        int newGamesTotal = 0;
        int consecutiveEmpty = 0;
        int offset = startOffset;
        boolean earlyExit = false;

        for (int i = 0; i < discoveryPages; i++, offset += discoveryLimit) {
            try {
                CatalogSyncResult result = gameService.syncIgdbCatalogOffset(offset, discoveryLimit);
                newGamesTotal += result.cached();
                if (result.fetched() == 0) {
                    if (++consecutiveEmpty >= EARLY_EXIT_THRESHOLD) {
                        earlyExit = true;
                        log.warn("Discovery early exit after {} consecutive empty IGDB responses", EARLY_EXIT_THRESHOLD);
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

        int nextOffset = earlyExit ? 0 : offset;
        saveLastDiscoveryOffset(nextOffset);
        log.info("Main discovery complete — {} new games, next offset={}", newGamesTotal, nextOffset);
    }

    private void runNewReleasesDiscovery() {
        int newGames = 0;
        int offset = 0;
        for (int i = 0; i < newReleasesPages; i++, offset += discoveryLimit) {
            try {
                CatalogSyncResult result = gameService.syncIgdbNewReleasesOffset(offset, discoveryLimit);
                newGames += result.cached();
                rateLimitSleep();
            } catch (Exception e) {
                log.error("New releases discovery failed at offset {}: {}", offset, e.getMessage());
            }
        }
        log.info("New releases discovery complete — {} new games", newGames);
    }

    private void runUpcomingReleasesDiscovery() {
        int newGames = 0;
        int offset = 0;
        for (int i = 0; i < upcomingPages; i++, offset += discoveryLimit) {
            try {
                CatalogSyncResult result = gameService.syncIgdbUpcomingOffset(offset, discoveryLimit);
                newGames += result.cached();
                if (result.fetched() == 0) break;
                rateLimitSleep();
            } catch (Exception e) {
                log.error("Upcoming releases discovery failed at offset {}: {}", offset, e.getMessage());
            }
        }
        log.info("Upcoming releases discovery complete — {} new games", newGames);
    }

    /**
     * Daily refresh over every cached game whose canonical first_release_date is in the
     * future. Force-overwrites the volatile fields (date, hypes, per-platform releases,
     * totalRating) so date slips and hype movement propagate within 24h. Sized for the
     * upcoming subset only, which is typically ~1-3k games even on a 100k catalog — well
     * inside the daily IGDB rate budget.
     */
    private void runUpcomingRefresh() {
        java.util.List<Integer> upcomingIds = gameService.findUpcomingIgdbIds();
        int refreshed = 0;
        for (Integer igdbId : upcomingIds) {
            try {
                if (gameService.refreshUpcomingRow(igdbId)) refreshed++;
                rateLimitSleep();
            } catch (Exception e) {
                log.error("Upcoming refresh failed for igdbId={}: {}", igdbId, e.getMessage());
            }
        }
        log.info("Upcoming refresh complete — {}/{} rows updated", refreshed, upcomingIds.size());
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
