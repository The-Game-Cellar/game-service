package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.dto.CellarRatingUpdate;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stores the member-rating aggregates library-service posts each night. The sender owns
 * the arithmetic; this only records it and clears what the pass no longer covers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CellarRatingService {

    private final GameRepository gameRepository;

    /**
     * Stores one batch, tagged with the run that sent it, and returns how many rows were
     * written. Ids the catalog does not hold are skipped rather than rejected: a library
     * can reference a game this instance has never cached, which is nobody's error.
     */
    @Transactional
    public int apply(String runId, List<CellarRatingUpdate> updates) {
        if (updates.isEmpty()) {
            return 0;
        }

        Map<Integer, CellarRatingUpdate> byIgdbId = updates.stream()
                .collect(Collectors.toMap(CellarRatingUpdate::igdbGameId, Function.identity(), (a, b) -> b));

        List<Game> games = gameRepository.findByIgdbIdIn(byIgdbId.keySet());
        for (Game game : games) {
            CellarRatingUpdate update = byIgdbId.get(game.getIgdbId());
            game.setCellarRatingAvg(update.average().setScale(2, RoundingMode.HALF_UP));
            game.setCellarRatingCount(update.count());
            game.setCellarRatingRunId(runId);
        }
        gameRepository.saveAll(games);

        int skipped = byIgdbId.size() - games.size();
        if (skipped > 0) {
            log.debug("Cellar ratings run {}: stored {}, skipped {} not in catalog",
                    runId, games.size(), skipped);
        }
        return games.size();
    }

    /**
     * Clears every aggregate the given run did not write, which is how a game loses its
     * average when its last rating is deleted: it stops appearing in the nightly pass
     * rather than arriving with a zero, so only its absence marks it.
     */
    @Transactional
    public int prune(String runId) {
        int cleared = gameRepository.clearCellarRatingsNotFromRun(runId);
        if (cleared > 0) {
            log.info("Cellar ratings run {}: cleared {} rows no longer rated", runId, cleared);
        }
        return cleared;
    }
}
