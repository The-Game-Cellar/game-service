package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nightly pass that turns harvested background art pools into decided backgrounds.
 * Runs an hour after the catalog worker so the night's pools are in hand. Talks only
 * to the database and the image CDN, never the IGDB API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackgroundArtWorker {

    private static final int PAGE_SIZE = 500;

    private final GameRepository gameRepository;
    private final BackgroundArtService backgroundArtService;

    @Value("${igdb.background-art.enabled:true}")
    private boolean enabled;

    @Value("${igdb.background-art.batch-size:5000}")
    private int batchSize;

    @Value("${igdb.background-art.download-delay-ms:200}")
    private long downloadDelayMs;

    @Scheduled(cron = "${igdb.background-art.cron:0 30 4 * * *}")
    public void nightlyPass() {
        if (!enabled) {
            log.info("Background art worker disabled, skipping pass");
            return;
        }
        runPass();
    }

    // Also the admin entry point; AdminSyncExecutor serialises it against the sync jobs.
    public void runPass() {
        log.info("Background art pass started");
        int processed = 0;
        int picked = 0;
        while (processed < batchSize) {
            int pageSize = Math.min(PAGE_SIZE, batchSize - processed);
            // Always page 0: every processed row gets a background_source and leaves the work list.
            List<Game> batch = gameRepository.findBackgroundArtWork(PageRequest.of(0, pageSize));
            if (batch.isEmpty()) break;
            for (Game game : batch) {
                BackgroundArtService.Pick pick = backgroundArtService.decide(game.getBackgroundArtPool());
                game.setBackgroundImageId(pick.imageId());
                game.setBackgroundSource(pick.source());
                gameRepository.save(game);
                processed++;
                if (!BackgroundArtService.SOURCE_NONE.equals(pick.source())) picked++;
                sleep(downloadDelayMs);
            }
        }
        log.info("Background art pass complete: processed={} picked={} none={}",
                processed, picked, processed - picked);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
