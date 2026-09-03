package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CellarRatingEndpointTest {

    private static final String PATH = "/internal/games/ratings";
    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private GameRepository gameRepository;

    @BeforeEach
    void seed() {
        gameRepository.deleteAll();
        gameRepository.save(Game.builder().igdbId(1942).name("The Witcher 3").build());
        gameRepository.save(Game.builder().igdbId(7346).name("Breath of the Wild").build());
    }

    private static String body(int igdbGameId, String average, int count) {
        return "[{\"igdbGameId\":%d,\"average\":%s,\"count\":%d}]".formatted(igdbGameId, average, count);
    }

    @Test
    void withoutTheInternalTokenTheBatchIsRejected() throws Exception {
        mvc.perform(post(PATH).param("runId", "run-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1942, "8.00", 3)))
                .andExpect(status().isUnauthorized());

        assertThat(gameRepository.findByIgdbId(1942).orElseThrow().getCellarRatingAvg()).isNull();
    }

    @Test
    void aBatchIsStoredAndTaggedWithItsRun() throws Exception {
        mvc.perform(post(PATH).param("runId", "run-1")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1942, "8.00", 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored").value(1));

        Game stored = gameRepository.findByIgdbId(1942).orElseThrow();
        assertThat(stored.getCellarRatingAvg()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(stored.getCellarRatingCount()).isEqualTo(3);
        assertThat(stored.getCellarRatingRunId()).isEqualTo("run-1");
    }

    // A library can hold a game this instance has never cached; that is not an error.
    @Test
    void anIdTheCatalogDoesNotHoldIsSkippedRatherThanRejected() throws Exception {
        mvc.perform(post(PATH).param("runId", "run-1")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999, "9.00", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored").value(0));
    }

    @Test
    void pruneClearsRowsTheLatestRunDidNotWrite() throws Exception {
        mvc.perform(post(PATH).param("runId", "run-1")
                .header(TOKEN_HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(1942, "8.00", 3)));
        mvc.perform(post(PATH).param("runId", "run-1")
                .header(TOKEN_HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(7346, "9.50", 2)));

        // The next night only Breath of the Wild is still rated by anyone.
        mvc.perform(post(PATH).param("runId", "run-2")
                .header(TOKEN_HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(7346, "9.00", 1)));

        mvc.perform(post(PATH + "/prune").param("runId", "run-2")
                        .header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(1));

        assertThat(gameRepository.findByIgdbId(1942).orElseThrow().getCellarRatingAvg()).isNull();
        assertThat(gameRepository.findByIgdbId(1942).orElseThrow().getCellarRatingCount()).isNull();
        assertThat(gameRepository.findByIgdbId(7346).orElseThrow().getCellarRatingAvg())
                .isEqualByComparingTo(new BigDecimal("9.00"));
    }

    @Test
    void anAverageOutsideTheRatingScaleIsRejected() throws Exception {
        mvc.perform(post(PATH).param("runId", "run-1")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1942, "11.00", 3)))
                .andExpect(status().isBadRequest());
    }
}
