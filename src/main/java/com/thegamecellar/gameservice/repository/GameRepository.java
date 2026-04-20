package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.Game;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {
    Optional<Game> findByRawgId(Integer rawgId);

    @Query("SELECT DISTINCT g FROM Game g JOIN g.tags t WHERE LOWER(t.tagName) IN (:tagNames)")
    List<Game> findByTagNamesIn(@Param("tagNames") List<String> tagNames);

    // Subquery avoids the DISTINCT+JOIN+Pageable pagination-in-memory trap:
    // inner query selects matching IDs only; outer query loads Game entities with LIMIT applied at DB level.
    @Query("SELECT g FROM Game g WHERE g.id IN (SELECT DISTINCT g2.id FROM Game g2 JOIN g2.genres gen WHERE LOWER(gen.genreName) = LOWER(:genreName))")
    List<Game> findByGenreName(@Param("genreName") String genreName, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT g.id) FROM Game g JOIN g.genres gen WHERE LOWER(gen.genreName) = LOWER(:genreName)")
    long countByGenreName(@Param("genreName") String genreName);

    @Query("SELECT g FROM Game g ORDER BY g.id DESC")
    List<Game> findRecentGames(Pageable pageable);

    @Query(value = "SELECT * FROM games ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Game> findRandom(@Param("limit") int limit);
}