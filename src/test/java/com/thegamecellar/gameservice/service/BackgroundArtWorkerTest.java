package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundArtWorkerTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private BackgroundArtService backgroundArtService;

    @InjectMocks
    private BackgroundArtWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "enabled", true);
        ReflectionTestUtils.setField(worker, "batchSize", 5000);
        ReflectionTestUtils.setField(worker, "downloadDelayMs", 0L);
    }

    @Test
    void nightlyPass_skips_everything_when_disabled() {
        ReflectionTestUtils.setField(worker, "enabled", false);

        worker.nightlyPass();

        verifyNoInteractions(gameRepository, backgroundArtService);
    }

    @Test
    void runPass_decides_and_saves_every_row_on_the_work_list() {
        Game withArt = game(1, "[{\"id\":\"a\",\"w\":1920,\"h\":1080,\"s\":\"a\"}]");
        Game withoutArt = game(2, "[{\"id\":\"b\",\"w\":100,\"h\":100,\"s\":\"a\"}]");
        when(gameRepository.findBackgroundArtWork(any()))
                .thenReturn(List.of(withArt, withoutArt))
                .thenReturn(List.of());
        when(backgroundArtService.decide(withArt.getBackgroundArtPool()))
                .thenReturn(new BackgroundArtService.Pick("a", BackgroundArtService.SOURCE_ARTWORK));
        when(backgroundArtService.decide(withoutArt.getBackgroundArtPool()))
                .thenReturn(new BackgroundArtService.Pick(null, BackgroundArtService.SOURCE_NONE));

        worker.runPass();

        assertThat(withArt.getBackgroundImageId()).isEqualTo("a");
        assertThat(withArt.getBackgroundSource()).isEqualTo(BackgroundArtService.SOURCE_ARTWORK);
        assertThat(withoutArt.getBackgroundImageId()).isNull();
        assertThat(withoutArt.getBackgroundSource()).isEqualTo(BackgroundArtService.SOURCE_NONE);
        verify(gameRepository, times(2)).save(any(Game.class));
    }

    @Test
    void runPass_respects_the_batch_cap() {
        ReflectionTestUtils.setField(worker, "batchSize", 1);
        Game only = game(1, "[]");
        when(gameRepository.findBackgroundArtWork(PageRequest.of(0, 1)))
                .thenReturn(List.of(only));
        when(backgroundArtService.decide(any()))
                .thenReturn(new BackgroundArtService.Pick(null, BackgroundArtService.SOURCE_NONE));

        worker.runPass();

        verify(gameRepository, times(1)).findBackgroundArtWork(any());
        verify(gameRepository, times(1)).save(any(Game.class));
    }

    private Game game(int igdbId, String pool) {
        Game g = new Game();
        g.setIgdbId(igdbId);
        g.setBackgroundArtPool(pool);
        return g;
    }
}
