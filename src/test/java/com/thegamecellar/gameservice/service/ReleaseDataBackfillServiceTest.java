package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.repository.GameRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReleaseDataBackfillServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private IgdbApiClient igdbApiClient;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ReleaseDataBackfillService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    void shouldSkipRowsAlreadyFilled() {
        Game alreadyFilled = gameWith(101, 1234567890L, 50);
        when(gameRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(alreadyFilled)));

        Map<String, Object> result = service.backfill();

        assertThat(result.get("skippedAlreadyFilled")).isEqualTo(1L);
        assertThat(result.get("igdbFetched")).isEqualTo(0L);
        verify(igdbApiClient, never()).fetchGamesByIds(anyList());
    }

    @Test
    void shouldBatchFetchAndPopulateOnlyMissingFields() {
        Game needsBoth = gameWith(201, null, null);
        Game needsOnlyDate = gameWith(202, null, 99);
        Game needsOnlyHypes = gameWith(203, 5555L, null);
        when(gameRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(needsBoth, needsOnlyDate, needsOnlyHypes)));

        IgdbGameDto dtoBoth = igdbDto(201, 1700000000L, 42);
        IgdbGameDto dtoDate = igdbDto(202, 1800000000L, 7);
        IgdbGameDto dtoHypes = igdbDto(203, 9999L, 17);
        when(igdbApiClient.fetchGamesByIds(anyList()))
                .thenReturn(List.of(dtoBoth, dtoDate, dtoHypes));

        Map<String, Object> result = service.backfill();

        assertThat(result.get("igdbFetched")).isEqualTo(3L);
        assertThat(result.get("updatedFirstReleaseDate")).isEqualTo(2L);
        assertThat(result.get("updatedHypes")).isEqualTo(2L);

        assertThat(needsBoth.getFirstReleaseDate()).isEqualTo(1700000000L);
        assertThat(needsBoth.getHypes()).isEqualTo(42);
        assertThat(needsOnlyDate.getFirstReleaseDate()).isEqualTo(1800000000L);
        assertThat(needsOnlyDate.getHypes()).isEqualTo(99);
        assertThat(needsOnlyHypes.getFirstReleaseDate()).isEqualTo(5555L);
        assertThat(needsOnlyHypes.getHypes()).isEqualTo(17);
    }

    @Test
    void shouldNotOverwriteAlreadyPopulatedFields() {
        Game partial = gameWith(301, 1234567890L, null);
        when(gameRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(partial)));
        IgdbGameDto dto = igdbDto(301, 9999999999L, 123);
        when(igdbApiClient.fetchGamesByIds(anyList())).thenReturn(List.of(dto));

        service.backfill();

        assertThat(partial.getFirstReleaseDate()).isEqualTo(1234567890L);
        assertThat(partial.getHypes()).isEqualTo(123);
    }

    @Test
    void shouldFlushAndClearEntityManagerPerPage() {
        Game g = gameWith(401, null, null);
        when(gameRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(g)));
        when(igdbApiClient.fetchGamesByIds(anyList()))
                .thenReturn(List.of(igdbDto(401, 1L, 1)));

        service.backfill();

        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();
    }

    @Test
    void shouldEmptyPageStops() {
        when(gameRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Map<String, Object> result = service.backfill();

        assertThat(result.get("examined")).isEqualTo(0L);
        verify(igdbApiClient, never()).fetchGamesByIds(anyList());
    }

    private Game gameWith(int igdbId, Long firstReleaseDate, Integer hypes) {
        return Game.builder()
                .id((long) igdbId)
                .igdbId(igdbId)
                .name("Game " + igdbId)
                .firstReleaseDate(firstReleaseDate)
                .hypes(hypes)
                .genres(new HashSet<>())
                .platforms(new HashSet<>())
                .tags(new HashSet<>())
                .themes(new HashSet<>())
                .gameModes(new HashSet<>())
                .playerPerspectives(new HashSet<>())
                .franchises(new HashSet<>())
                .collections(new HashSet<>())
                .build();
    }

    private IgdbGameDto igdbDto(int id, Long firstReleaseDate, Integer hypes) {
        IgdbGameDto dto = new IgdbGameDto();
        dto.setId(id);
        dto.setName("Game " + id);
        dto.setFirstReleaseDate(firstReleaseDate);
        dto.setHypes(hypes);
        return dto;
    }
}
