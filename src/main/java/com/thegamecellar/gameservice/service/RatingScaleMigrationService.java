package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.SyncState;
import com.thegamecellar.gameservice.repository.SyncStateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

// One-shot doubling of rating + total_rating to migrate legacy 0-5 scale to 0-10. Idempotent via sync_state flag.
// Operational: stop IGDB worker (IGDB_WORKER_ENABLED=false) before deploy, then run, then re-enable. The flag stops
// re-running, but does NOT protect a row that lands on the new scale between deploy and migration.
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingScaleMigrationService {

    static final String FLAG_KEY = "rating_scale_v2_migrated";

    private final SyncStateRepository syncStateRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Map<String, Object> migrate() {
        Map<String, Object> result = new LinkedHashMap<>();

        boolean alreadyMigrated = syncStateRepository.findById(FLAG_KEY)
                .map(s -> "true".equalsIgnoreCase(s.getStateValue()))
                .orElse(false);
        if (alreadyMigrated) {
            log.info("Rating-scale migration skipped: already marked complete in sync_state");
            result.put("skipped", true);
            result.put("reason", "Already migrated (sync_state flag '" + FLAG_KEY + "' = true)");
            return result;
        }

        // Widen precision explicitly; Hibernate ddl-auto:update doesn't re-issue ALTER COLUMN TYPE for precision-only changes.
        entityManager.createNativeQuery(
                "ALTER TABLE games ALTER COLUMN rating TYPE NUMERIC(4,2)").executeUpdate();
        entityManager.createNativeQuery(
                "ALTER TABLE games ALTER COLUMN total_rating TYPE NUMERIC(4,2)").executeUpdate();

        int ratingsUpdated = entityManager
                .createNativeQuery("UPDATE games SET rating = rating * 2 WHERE rating IS NOT NULL")
                .executeUpdate();
        int totalRatingsUpdated = entityManager
                .createNativeQuery("UPDATE games SET total_rating = total_rating * 2 WHERE total_rating IS NOT NULL")
                .executeUpdate();

        syncStateRepository.save(new SyncState(FLAG_KEY, "true"));

        log.info("Rating-scale migration complete: rating rows updated: {}, total_rating rows updated: {}",
                ratingsUpdated, totalRatingsUpdated);

        result.put("skipped", false);
        result.put("ratingRowsUpdated", ratingsUpdated);
        result.put("totalRatingRowsUpdated", totalRatingsUpdated);
        result.put("flagKey", FLAG_KEY);
        return result;
    }
}
