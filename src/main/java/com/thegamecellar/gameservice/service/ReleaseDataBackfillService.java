package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.repository.GameRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot backfill that populates the {@code first_release_date} and {@code hypes}
 * columns on every cached game. Pre-existing rows (cached before these columns existed)
 * have both fields NULL. Walks the whole table page-by-page, batches IGDB lookups
 * 500 ids at a time so a 101k-row catalog completes in minutes rather than hours,
 * and skips rows that already have a populated first_release_date. Second runs are
 * cheap enough to use as a self-heal sweep after a future schema bump.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseDataBackfillService {

    private static final int DB_PAGE_SIZE = 500;
    private static final int IGDB_BATCH_SIZE = 500;
    private static final long IGDB_RATE_LIMIT_DELAY_MS = 250;

    private final GameRepository gameRepository;
    private final IgdbApiClient igdbApiClient;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Map<String, Object> backfill() {
        long examined = 0;
        long fetched = 0;
        long updatedDates = 0;
        long updatedHypes = 0;
        long skipped = 0;
        List<String> sample = new ArrayList<>();

        int page = 0;
        while (true) {
            Page<Game> chunk = gameRepository.findAll(PageRequest.of(page, DB_PAGE_SIZE));
            if (chunk.isEmpty()) break;

            List<Game> needsFetch = new ArrayList<>();
            for (Game g : chunk.getContent()) {
                examined++;
                if (g.getFirstReleaseDate() != null && g.getHypes() != null) {
                    skipped++;
                    continue;
                }
                needsFetch.add(g);
            }

            for (int i = 0; i < needsFetch.size(); i += IGDB_BATCH_SIZE) {
                List<Game> batch = needsFetch.subList(i, Math.min(i + IGDB_BATCH_SIZE, needsFetch.size()));
                List<Integer> ids = batch.stream().map(Game::getIgdbId).toList();

                List<IgdbGameDto> dtos;
                try {
                    dtos = igdbApiClient.fetchGamesByIds(ids);
                    fetched += dtos.size();
                } catch (Exception e) {
                    log.error("IGDB batch fetch failed for {} ids: {}", ids.size(), e.getMessage());
                    rateLimitSleep();
                    continue;
                }

                Map<Integer, IgdbGameDto> byId = new HashMap<>();
                for (IgdbGameDto dto : dtos) byId.put(dto.getId(), dto);

                for (Game g : batch) {
                    IgdbGameDto dto = byId.get(g.getIgdbId());
                    if (dto == null) continue;

                    boolean changed = false;
                    if (g.getFirstReleaseDate() == null && dto.getFirstReleaseDate() != null) {
                        g.setFirstReleaseDate(dto.getFirstReleaseDate());
                        updatedDates++;
                        changed = true;
                    }
                    if (g.getHypes() == null && dto.getHypes() != null) {
                        g.setHypes(dto.getHypes());
                        updatedHypes++;
                        changed = true;
                    }
                    if (changed) {
                        gameRepository.save(g);
                        if (sample.size() < 30) {
                            sample.add(g.getName() + " ← date=" + g.getFirstReleaseDate() + " hypes=" + g.getHypes());
                        }
                    }
                }
                rateLimitSleep();
            }

            entityManager.flush();
            entityManager.clear();

            if (!chunk.hasNext()) break;
            page++;
        }

        log.info("Release-data backfill complete: examined={} skipped={} fetched={} updatedDates={} updatedHypes={}",
                examined, skipped, fetched, updatedDates, updatedHypes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examined", examined);
        result.put("skippedAlreadyFilled", skipped);
        result.put("igdbFetched", fetched);
        result.put("updatedFirstReleaseDate", updatedDates);
        result.put("updatedHypes", updatedHypes);
        result.put("sample", sample);
        return result;
    }

    private void rateLimitSleep() {
        try {
            Thread.sleep(IGDB_RATE_LIMIT_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
