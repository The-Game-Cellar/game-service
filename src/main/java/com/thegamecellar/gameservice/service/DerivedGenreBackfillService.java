package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.Genre;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.util.DerivedGenreEngine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Idempotent re-apply of derived-genre rules across the cache. Replace-pattern via GameCacheService.applyDerivedGenres.
@Slf4j
@Service
@RequiredArgsConstructor
public class DerivedGenreBackfillService {

    private static final int PAGE_SIZE = 500;
    private static final int SAMPLE_SIZE = 30;

    private final GameRepository gameRepository;
    private final GameCacheService gameCacheService;
    private final DerivedGenreEngine derivedGenreEngine;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Map<String, Object> backfill() {
        if (derivedGenreEngine.ruleCount() == 0) {
            log.warn("Derived-genre backfill skipped: engine has no rules loaded.");
            return Map.of(
                    "skipped", true,
                    "reason", "DerivedGenreEngine has no rules loaded (see startup logs)"
            );
        }

        long examined = 0, updated = 0, derivedAdded = 0, derivedRemoved = 0;
        List<String> sampleAdded = new ArrayList<>();

        int page = 0;
        while (true) {
            Page<Game> chunk = gameRepository.findAll(PageRequest.of(page, PAGE_SIZE));
            if (chunk.isEmpty()) break;

            for (Game game : chunk.getContent()) {
                examined++;

                Set<String> oldDerived = derivedGenreNames(game);
                gameCacheService.applyDerivedGenres(game);
                Set<String> newDerived = derivedGenreNames(game);

                if (oldDerived.equals(newDerived)) continue;

                Set<String> addedNames = new HashSet<>(newDerived);
                addedNames.removeAll(oldDerived);
                Set<String> removedNames = new HashSet<>(oldDerived);
                removedNames.removeAll(newDerived);

                derivedAdded += addedNames.size();
                derivedRemoved += removedNames.size();
                updated++;

                gameRepository.save(game);

                if (sampleAdded.size() < SAMPLE_SIZE && !addedNames.isEmpty()) {
                    sampleAdded.add(game.getName() + " ← " + String.join(", ", addedNames));
                }
            }

            entityManager.flush();
            entityManager.clear();

            if (!chunk.hasNext()) break;
            page++;
        }

        log.info("Derived-genre backfill complete: examined={} updated={} derivedAdded={} derivedRemoved={}",
                examined, updated, derivedAdded, derivedRemoved);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examined", examined);
        result.put("updated", updated);
        result.put("derivedRowsAdded", derivedAdded);
        result.put("derivedRowsRemoved", derivedRemoved);
        result.put("ruleCount", derivedGenreEngine.ruleCount());
        result.put("rules", derivedGenreEngine.getRuleNames());
        result.put("sampleAdded", sampleAdded);
        return result;
    }

    private static Set<String> derivedGenreNames(Game game) {
        return game.getGenres().stream()
                .filter(g -> "DERIVED".equals(g.getSource()))
                .map(Genre::getName)
                .collect(Collectors.toSet());
    }
}
