package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Platform;
import com.thegamecellar.gameservice.repository.PlatformRepository;
import com.thegamecellar.gameservice.util.PlatformCuration;
import com.thegamecellar.gameservice.util.TestPlatformCuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformCurationReconcilerTest {

    private PlatformRepository platformRepository;
    private PlatformCurationReconciler reconciler;

    @BeforeEach
    void setUp() {
        platformRepository = mock(PlatformRepository.class);
        PlatformCuration curation = TestPlatformCuration.loaded();
        reconciler = new PlatformCurationReconciler(platformRepository, curation);
    }

    // The production symptom: rows exist, none of them flagged, so the catalog endpoint returns an empty list.
    @Test
    void reconcile_flags_rows_that_the_catalog_sync_inserted_uncurated() {
        List<Platform> existing = List.of(
                new Platform("PlayStation 5"),
                new Platform("Nintendo Switch"),
                new Platform("Fictional Console 9000")
        );
        when(platformRepository.findAll()).thenReturn(existing);

        reconciler.reconcileOnStartup();

        ArgumentCaptor<List<Platform>> saved = ArgumentCaptor.forClass(List.class);
        verify(platformRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Platform::getName)
                .containsExactly("PlayStation 5", "Nintendo Switch");
        assertThat(saved.getValue()).allMatch(Platform::getIsPreferenceEligible);
    }

    @Test
    void reconcile_writes_nothing_when_every_row_already_matches_the_file() {
        Platform curated = new Platform("PlayStation 5");
        curated.setIsPreferenceEligible(true);
        curated.setCategory("sony");
        curated.setDisplayOrder(10);
        when(platformRepository.findAll()).thenReturn(List.of(curated));

        reconciler.reconcileOnStartup();

        verify(platformRepository, never()).saveAll(any());
    }

    // A fresh host boots against an empty table; GameCacheService curates rows as the sync inserts them.
    @Test
    void reconcile_is_a_no_op_on_an_empty_platforms_table() {
        when(platformRepository.findAll()).thenReturn(List.of());

        reconciler.reconcileOnStartup();

        verify(platformRepository, never()).saveAll(any());
    }

    @Test
    void reconcile_skips_everything_when_the_curation_file_is_missing() {
        PlatformCurationReconciler disabled =
                new PlatformCurationReconciler(platformRepository, new PlatformCuration());

        disabled.reconcileOnStartup();

        verify(platformRepository, never()).findAll();
        verify(platformRepository, never()).saveAll(any());
    }
}
