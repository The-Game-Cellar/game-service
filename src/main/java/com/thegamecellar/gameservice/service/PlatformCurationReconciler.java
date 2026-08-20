package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Platform;
import com.thegamecellar.gameservice.repository.PlatformRepository;
import com.thegamecellar.gameservice.util.PlatformCuration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// Platform rows are created lazily by GameCacheService as games are cached, so curation cannot live in a
// migration: on a fresh host Flyway runs against an empty platforms table. New rows are curated at creation;
// this pass covers rows that predate an edit to platform-curation.yaml.
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformCurationReconciler {

    private final PlatformRepository platformRepository;
    private final PlatformCuration curation;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileOnStartup() {
        if (!curation.isEnabled()) {
            log.warn("Platform curation reconcile skipped: curation not loaded. "
                    + "Add platform-curation.yaml to game-service resources.");
            return;
        }

        List<Platform> all = platformRepository.findAll();
        if (all.isEmpty()) {
            log.info("Platform curation reconcile: platforms table empty, nothing to reconcile. "
                    + "Rows created by the IGDB catalog sync are curated as they are inserted.");
            return;
        }

        List<Platform> changed = new ArrayList<>();
        int eligible = 0;
        for (Platform platform : all) {
            if (curation.apply(platform)) changed.add(platform);
            if (Boolean.TRUE.equals(platform.getIsPreferenceEligible())) eligible++;
        }
        if (!changed.isEmpty()) platformRepository.saveAll(changed);

        int unmatched = curation.size() - eligible;
        log.info("Platform curation reconcile: examined={} updated={} preferenceEligible={} curatedButAbsent={}",
                all.size(), changed.size(), eligible, unmatched);
    }
}
