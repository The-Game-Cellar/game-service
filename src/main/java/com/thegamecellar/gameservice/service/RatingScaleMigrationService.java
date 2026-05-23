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

/**
 * One-shot migration that doubles every cached {@code rating} and {@code total_rating} value
 * to convert the legacy 0–5 scale to the current 0–10 scale. Idempotent via a flag stored in
 * the {@code sync_state} table. Re-running after a successful pass is a no-op.
 *
 * <p><b>Operational note:</b> deployments that change the divisor in
 * {@link com.thegamecellar.gameservice.util.GameMapper#normalizeRating(double)} must run this
 * exactly once. Stop the IGDB worker before deploy ({@code IGDB_WORKER_ENABLED=false}) so no
 * new rows are written during the swap window, deploy, hit this endpoint, then re-enable the
 * worker. Without disabling the worker, a row freshly cached on the new divisor between deploy
 * and migration would get doubled incorrectly; the flag prevents a SECOND migration but does
 * not protect a row that lands on the new scale before the migration touches it.
 */
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

        // Widen the column precision before doubling values. A row currently storing 5.00 on
        // the legacy 0–5 scale must be able to hold 10.00 after migration. Hibernate's
        // ddl-auto: update does not always re-issue ALTER COLUMN TYPE for precision-only
        // changes, so we run it explicitly here. Postgres treats ALTER TYPE NUMERIC(4,2) on
        // an already-NUMERIC(4,2) column as a no-op, so re-running this against a migrated
        // schema is safe.
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
