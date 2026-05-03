package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.SyncState;
import com.thegamecellar.gameservice.repository.SyncStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.thegamecellar.gameservice.service.IgdbCatalogWorker.IGDB_DISCOVERY_OFFSET_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IgdbCatalogWorkerTest {

    @Mock
    private GameService gameService;

    @Mock
    private SyncStateRepository syncStateRepository;

    @InjectMocks
    private IgdbCatalogWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "enabled", true);
        ReflectionTestUtils.setField(worker, "discoveryLimit", 500);
        ReflectionTestUtils.setField(worker, "discoveryPages", 3);
        ReflectionTestUtils.setField(worker, "newReleasesPages", 2);
        ReflectionTestUtils.setField(worker, "upcomingPages", 2);
        ReflectionTestUtils.setField(worker, "rateLimitDelayMs", 0L);
        lenient().when(gameService.syncIgdbUpcomingOffset(anyInt(), anyInt()))
                .thenReturn(CatalogSyncResult.empty());
        lenient().when(gameService.findUpcomingIgdbIds()).thenReturn(java.util.List.of());
    }

    @Test
    void shouldRunUpcomingDiscoveryAndRefreshAfterMainPhases() {
        when(syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY)).thenReturn(Optional.empty());
        when(gameService.syncIgdbCatalogOffset(anyInt(), anyInt())).thenReturn(new CatalogSyncResult(500, 5));
        when(gameService.syncIgdbNewReleasesOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());
        when(gameService.syncIgdbUpcomingOffset(anyInt(), anyInt()))
                .thenReturn(new CatalogSyncResult(500, 3))
                .thenReturn(new CatalogSyncResult(500, 1));
        when(gameService.findUpcomingIgdbIds()).thenReturn(java.util.List.of(101, 202, 303));
        when(gameService.refreshUpcomingRow(anyInt())).thenReturn(true);

        worker.syncCatalog();

        verify(gameService, times(2)).syncIgdbUpcomingOffset(anyInt(), eq(500));
        verify(gameService).findUpcomingIgdbIds();
        verify(gameService).refreshUpcomingRow(101);
        verify(gameService).refreshUpcomingRow(202);
        verify(gameService).refreshUpcomingRow(303);
    }

    @Test
    void shouldStopUpcomingDiscoveryEarlyWhenIgdbReturnsEmpty() {
        when(syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY)).thenReturn(Optional.empty());
        when(gameService.syncIgdbCatalogOffset(anyInt(), anyInt())).thenReturn(new CatalogSyncResult(500, 5));
        when(gameService.syncIgdbNewReleasesOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());
        when(gameService.syncIgdbUpcomingOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());

        worker.syncCatalog();

        verify(gameService, times(1)).syncIgdbUpcomingOffset(anyInt(), eq(500));
    }

    @Test
    void shouldSkipAllPhasesWhenDisabled() {
        ReflectionTestUtils.setField(worker, "enabled", false);

        worker.syncCatalog();

        verifyNoInteractions(gameService, syncStateRepository);
    }

    @Test
    void shouldRunBothPhasesInOrder() {
        when(syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY)).thenReturn(Optional.empty());
        when(gameService.syncIgdbCatalogOffset(anyInt(), anyInt())).thenReturn(new CatalogSyncResult(500, 5));
        when(gameService.syncIgdbNewReleasesOffset(anyInt(), anyInt())).thenReturn(new CatalogSyncResult(500, 2));

        worker.syncCatalog();

        verify(gameService, times(3)).syncIgdbCatalogOffset(anyInt(), eq(500));
        verify(gameService, times(2)).syncIgdbNewReleasesOffset(anyInt(), eq(500));
    }

    @Test
    void shouldContinueFromLastDiscoveryOffset() {
        when(syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY))
                .thenReturn(Optional.of(new SyncState(IGDB_DISCOVERY_OFFSET_KEY, "1000")));
        when(gameService.syncIgdbCatalogOffset(anyInt(), anyInt())).thenReturn(new CatalogSyncResult(500, 5));
        when(gameService.syncIgdbNewReleasesOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());

        worker.syncCatalog();

        verify(gameService).syncIgdbCatalogOffset(1000, 500);
        verify(gameService).syncIgdbCatalogOffset(1500, 500);
        verify(gameService).syncIgdbCatalogOffset(2000, 500);
    }

    @Test
    void shouldExitEarlyOnlyWhenIgdbReturnsEmpty_notWhenAllAlreadyCached() {
        when(syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY)).thenReturn(Optional.empty());
        when(gameService.syncIgdbCatalogOffset(anyInt(), anyInt())).thenReturn(new CatalogSyncResult(500, 0));
        when(gameService.syncIgdbNewReleasesOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());

        worker.syncCatalog();

        // 3 pages run all the way through — fetched > 0 means catalog isn't exhausted
        verify(gameService, times(3)).syncIgdbCatalogOffset(anyInt(), anyInt());
        ArgumentCaptor<SyncState> captor = ArgumentCaptor.forClass(SyncState.class);
        verify(syncStateRepository).save(captor.capture());
        assertThat(captor.getValue().getStateValue()).isEqualTo("1500");
    }

    @Test
    void shouldExitDiscoveryEarlyAfterConsecutiveEmptyIgdbResponses() {
        when(syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY)).thenReturn(Optional.empty());
        when(gameService.syncIgdbCatalogOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());
        when(gameService.syncIgdbNewReleasesOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());

        worker.syncCatalog();

        verify(gameService, times(3)).syncIgdbCatalogOffset(anyInt(), anyInt());
    }

    @Test
    void shouldResetOffsetToZeroAfterEarlyExit() {
        when(syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY))
                .thenReturn(Optional.of(new SyncState(IGDB_DISCOVERY_OFFSET_KEY, "50000")));
        when(gameService.syncIgdbCatalogOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());
        when(gameService.syncIgdbNewReleasesOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());

        worker.syncCatalog();

        ArgumentCaptor<SyncState> captor = ArgumentCaptor.forClass(SyncState.class);
        verify(syncStateRepository).save(captor.capture());
        assertThat(captor.getValue().getStateValue()).isEqualTo("0");
    }

    @Test
    void shouldSaveNextOffsetAfterNormalCompletion() {
        when(syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY))
                .thenReturn(Optional.of(new SyncState(IGDB_DISCOVERY_OFFSET_KEY, "1000")));
        when(gameService.syncIgdbCatalogOffset(anyInt(), anyInt())).thenReturn(new CatalogSyncResult(500, 5));
        when(gameService.syncIgdbNewReleasesOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());

        worker.syncCatalog();

        // 3 pages × 500 = 1500 offset advanced, starting at 1000 → saves 2500
        ArgumentCaptor<SyncState> captor = ArgumentCaptor.forClass(SyncState.class);
        verify(syncStateRepository).save(captor.capture());
        assertThat(captor.getValue().getStateValue()).isEqualTo("2500");
    }

    @Test
    void shouldContinueRemainingPhasesWhenDiscoveryFails() {
        when(syncStateRepository.findById(IGDB_DISCOVERY_OFFSET_KEY)).thenReturn(Optional.empty());
        when(gameService.syncIgdbCatalogOffset(anyInt(), anyInt())).thenThrow(new RuntimeException("IGDB down"));
        when(gameService.syncIgdbNewReleasesOffset(anyInt(), anyInt())).thenReturn(CatalogSyncResult.empty());

        worker.syncCatalog();

        verify(gameService, atLeastOnce()).syncIgdbNewReleasesOffset(anyInt(), anyInt());
    }
}
