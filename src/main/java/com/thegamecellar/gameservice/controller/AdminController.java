package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.service.AdminSyncExecutor;
import com.thegamecellar.gameservice.service.DerivedGenreBackfillService;
import com.thegamecellar.gameservice.service.GameService;
import com.thegamecellar.gameservice.service.IgdbCatalogWorker;
import com.thegamecellar.gameservice.service.RatingScaleMigrationService;
import com.thegamecellar.gameservice.service.ReleaseDataBackfillService;
import com.thegamecellar.gameservice.service.TagPruneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final IgdbCatalogWorker igdbCatalogWorker;
    private final AdminSyncExecutor adminSyncExecutor;
    private final GameService gameService;
    private final TagPruneService tagPruneService;
    private final DerivedGenreBackfillService derivedGenreBackfillService;
    private final ReleaseDataBackfillService releaseDataBackfillService;
    private final RatingScaleMigrationService ratingScaleMigrationService;

    @PostMapping("/sync")
    public ResponseEntity<String> triggerFullSync() {
        if (!adminSyncExecutor.trySubmit("full-sync", igdbCatalogWorker::syncCatalog)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Sync already in progress");
        }
        return ResponseEntity.accepted().body("Full sync started");
    }

    @PostMapping("/sync/quick")
    public ResponseEntity<String> triggerQuickSync() {
        if (!adminSyncExecutor.trySubmit("quick-sync", igdbCatalogWorker::quickSync)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Sync already in progress");
        }
        return ResponseEntity.accepted().body("Quick sync started — fetching ~100 games");
    }

    @PostMapping("/backfill-developers")
    public ResponseEntity<String> triggerDeveloperBackfill() {
        if (!adminSyncExecutor.trySubmit("backfill-developers", gameService::backfillDevelopers)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Sync already in progress");
        }
        return ResponseEntity.accepted().body("Developer backfill started");
    }

    @GetMapping("/sync/status")
    public ResponseEntity<Map<String, Object>> syncStatus() {
        return ResponseEntity.ok(Map.of("running", adminSyncExecutor.isRunning()));
    }

    @PostMapping("/prune-tags")
    public ResponseEntity<Map<String, Object>> pruneTagsToAllowlist() {
        return ResponseEntity.ok(tagPruneService.pruneToAllowlist());
    }

    @PostMapping("/derive-genres")
    public ResponseEntity<Map<String, Object>> deriveGenres() {
        return ResponseEntity.ok(derivedGenreBackfillService.backfill());
    }

    @PostMapping("/backfill-release-data")
    public ResponseEntity<Map<String, Object>> backfillReleaseData() {
        return ResponseEntity.ok(releaseDataBackfillService.backfill());
    }

    @PostMapping("/migrate-rating-scale")
    public ResponseEntity<Map<String, Object>> migrateRatingScale() {
        return ResponseEntity.ok(ratingScaleMigrationService.migrate());
    }
}
