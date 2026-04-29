package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.Game;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {
    Optional<Game> findByIgdbId(Integer igdbId);
    boolean existsByIgdbId(Integer igdbId);

    @Query("SELECT DISTINCT g FROM Game g JOIN g.tags t WHERE LOWER(t.name) IN (:tagNames)")
    List<Game> findByTagNamesIn(@Param("tagNames") List<String> tagNames);

    @Query("SELECT g FROM Game g WHERE g.id IN (SELECT DISTINCT g2.id FROM Game g2 JOIN g2.genres gen WHERE LOWER(gen.name) = LOWER(:genreName))")
    List<Game> findByGenreName(@Param("genreName") String genreName, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT g.id) FROM Game g JOIN g.genres gen WHERE LOWER(gen.name) = LOWER(:genreName)")
    long countByGenreName(@Param("genreName") String genreName);

    @Query("SELECT g FROM Game g WHERE g.id IN (SELECT DISTINCT g2.id FROM Game g2 JOIN g2.genres gen JOIN g2.platforms p WHERE LOWER(gen.name) = LOWER(:genreName) AND LOWER(p.name) = LOWER(:platformName))")
    List<Game> findByGenreAndPlatformName(@Param("genreName") String genreName, @Param("platformName") String platformName, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT g.id) FROM Game g JOIN g.genres gen JOIN g.platforms p WHERE LOWER(gen.name) = LOWER(:genreName) AND LOWER(p.name) = LOWER(:platformName)")
    long countByGenreAndPlatformName(@Param("genreName") String genreName, @Param("platformName") String platformName);

    @Query(value = "SELECT * FROM games ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Game> findRandom(@Param("limit") int limit);

    List<Game> findByDevelopersIsNull(Pageable pageable);

    long countByDevelopersIsNull();
}
