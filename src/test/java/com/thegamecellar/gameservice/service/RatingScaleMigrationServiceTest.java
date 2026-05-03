package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.SyncState;
import com.thegamecellar.gameservice.repository.SyncStateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingScaleMigrationServiceTest {

    @Mock
    private SyncStateRepository syncStateRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query alterRatingQuery;

    @Mock
    private Query alterTotalRatingQuery;

    @Mock
    private Query ratingQuery;

    @Mock
    private Query totalRatingQuery;

    @InjectMocks
    private RatingScaleMigrationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    void shouldDoubleRatingsAndPersistFlagOnFirstRun() {
        when(syncStateRepository.findById(RatingScaleMigrationService.FLAG_KEY))
                .thenReturn(Optional.empty());
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(alterRatingQuery)
                .thenReturn(alterTotalRatingQuery)
                .thenReturn(ratingQuery)
                .thenReturn(totalRatingQuery);
        when(ratingQuery.executeUpdate()).thenReturn(50000);
        when(totalRatingQuery.executeUpdate()).thenReturn(20000);

        Map<String, Object> result = service.migrate();

        assertThat(result.get("skipped")).isEqualTo(false);
        assertThat(result.get("ratingRowsUpdated")).isEqualTo(50000);
        assertThat(result.get("totalRatingRowsUpdated")).isEqualTo(20000);

        ArgumentCaptor<SyncState> captor = ArgumentCaptor.forClass(SyncState.class);
        verify(syncStateRepository).save(captor.capture());
        assertThat(captor.getValue().getStateKey()).isEqualTo(RatingScaleMigrationService.FLAG_KEY);
        assertThat(captor.getValue().getStateValue()).isEqualTo("true");
    }

    @Test
    void shouldSkipWhenFlagAlreadySet() {
        when(syncStateRepository.findById(RatingScaleMigrationService.FLAG_KEY))
                .thenReturn(Optional.of(new SyncState(RatingScaleMigrationService.FLAG_KEY, "true")));

        Map<String, Object> result = service.migrate();

        assertThat(result.get("skipped")).isEqualTo(true);
        assertThat(result.get("reason")).asString().contains("Already migrated");
        verify(entityManager, never()).createNativeQuery(anyString());
        verify(syncStateRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRunWhenFlagPresentButNotTrue() {
        when(syncStateRepository.findById(RatingScaleMigrationService.FLAG_KEY))
                .thenReturn(Optional.of(new SyncState(RatingScaleMigrationService.FLAG_KEY, "false")));
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(alterRatingQuery)
                .thenReturn(alterTotalRatingQuery)
                .thenReturn(ratingQuery)
                .thenReturn(totalRatingQuery);
        when(ratingQuery.executeUpdate()).thenReturn(0);
        when(totalRatingQuery.executeUpdate()).thenReturn(0);

        Map<String, Object> result = service.migrate();

        assertThat(result.get("skipped")).isEqualTo(false);
        verify(syncStateRepository).save(org.mockito.ArgumentMatchers.any());
    }
}
