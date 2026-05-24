package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.Game;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long>, JpaSpecificationExecutor<Game> {
    Optional<Game> findByIgdbId(Integer igdbId);
    boolean existsByIgdbId(Integer igdbId);

    // Default filter: category IN (0, 8) OR NULL. Remakes count as main games; variants live in the *Variants methods below.

    @Query("SELECT DISTINCT g FROM Game g JOIN g.tags t WHERE LOWER(t.name) IN (:tagNames) AND (g.category IN (0, 8) OR g.category IS NULL)")
    List<Game> findByTagNamesIn(@Param("tagNames") List<String> tagNames);

    @Query("SELECT g FROM Game g WHERE g.id IN (SELECT DISTINCT g2.id FROM Game g2 JOIN g2.genres gen WHERE LOWER(gen.name) = LOWER(:genreName)) AND (g.category IN (0, 8) OR g.category IS NULL)")
    List<Game> findByGenreName(@Param("genreName") String genreName, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT g.id) FROM Game g JOIN g.genres gen WHERE LOWER(gen.name) = LOWER(:genreName) AND (g.category IN (0, 8) OR g.category IS NULL)")
    long countByGenreName(@Param("genreName") String genreName);

    @Query("SELECT g FROM Game g WHERE g.id IN (SELECT DISTINCT g2.id FROM Game g2 JOIN g2.genres gen JOIN g2.platforms p WHERE LOWER(gen.name) = LOWER(:genreName) AND LOWER(p.name) = LOWER(:platformName)) AND (g.category IN (0, 8) OR g.category IS NULL)")
    List<Game> findByGenreAndPlatformName(@Param("genreName") String genreName, @Param("platformName") String platformName, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT g.id) FROM Game g JOIN g.genres gen JOIN g.platforms p WHERE LOWER(gen.name) = LOWER(:genreName) AND LOWER(p.name) = LOWER(:platformName) AND (g.category IN (0, 8) OR g.category IS NULL)")
    long countByGenreAndPlatformName(@Param("genreName") String genreName, @Param("platformName") String platformName);

    @Query(value = "SELECT * FROM games WHERE category IN (0, 8) OR category IS NULL ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Game> findRandom(@Param("limit") int limit);

    // Uniform random over quality subset (rating + vote-count gated). Used by Rec Service candidate-pool gen.
    @Query(value = """
            SELECT g.* FROM games g
            JOIN game_genres gg ON gg.game_id = g.id
            JOIN genres ge ON ge.id = gg.genre_id
            WHERE LOWER(ge.name) = LOWER(:genre)
              AND (g.category IN (0, 8) OR g.category IS NULL)
              AND g.total_rating IS NOT NULL
              AND g.total_rating >= :minRating
              AND g.total_rating_count IS NOT NULL
              AND g.total_rating_count >= :minVotes
            ORDER BY RANDOM()
            LIMIT :limit
            """, nativeQuery = true)
    List<Game> findRandomQualityByGenre(@Param("genre") String genre,
                                        @Param("minRating") java.math.BigDecimal minRating,
                                        @Param("minVotes") int minVotes,
                                        @Param("limit") int limit);

    @Query(value = "SELECT g FROM Game g WHERE g.category IN (0, 8) OR g.category IS NULL",
           countQuery = "SELECT COUNT(g) FROM Game g WHERE g.category IN (0, 8) OR g.category IS NULL")
    org.springframework.data.domain.Page<Game> findAllMainGames(Pageable pageable);

    // Variants-only: category > 2 AND <> 8. Excludes DLC/Expansion (in DLC stack) + Remake (in main).

    @Query(value = "SELECT g FROM Game g WHERE g.category > 2 AND g.category <> 8",
           countQuery = "SELECT COUNT(g) FROM Game g WHERE g.category > 2 AND g.category <> 8")
    org.springframework.data.domain.Page<Game> findAllVariants(Pageable pageable);

    @Query("SELECT g FROM Game g WHERE g.id IN (SELECT DISTINCT g2.id FROM Game g2 JOIN g2.genres gen WHERE LOWER(gen.name) = LOWER(:genreName)) AND g.category > 2 AND g.category <> 8")
    List<Game> findVariantsByGenreName(@Param("genreName") String genreName, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT g.id) FROM Game g JOIN g.genres gen WHERE LOWER(gen.name) = LOWER(:genreName) AND g.category > 2 AND g.category <> 8")
    long countVariantsByGenreName(@Param("genreName") String genreName);

    @Query("SELECT g FROM Game g WHERE g.id IN (SELECT DISTINCT g2.id FROM Game g2 JOIN g2.genres gen JOIN g2.platforms p WHERE LOWER(gen.name) = LOWER(:genreName) AND LOWER(p.name) = LOWER(:platformName)) AND g.category > 2 AND g.category <> 8")
    List<Game> findVariantsByGenreAndPlatformName(@Param("genreName") String genreName, @Param("platformName") String platformName, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT g.id) FROM Game g JOIN g.genres gen JOIN g.platforms p WHERE LOWER(gen.name) = LOWER(:genreName) AND LOWER(p.name) = LOWER(:platformName) AND g.category > 2 AND g.category <> 8")
    long countVariantsByGenreAndPlatformName(@Param("genreName") String genreName, @Param("platformName") String platformName);

    // Empty-string sentinels (not NULL) because PG JDBC binds NULL as bytea, breaking LOWER(?) wrap.
    @Query("SELECT DISTINCT g FROM Game g " +
            "LEFT JOIN g.gameModes gm " +
            "LEFT JOIN g.playerPerspectives pp " +
            "WHERE (g.category IN (0, 8) OR g.category IS NULL) " +
            "AND (:gameMode = '' OR LOWER(gm.name) = LOWER(:gameMode)) " +
            "AND (:perspective = '' OR LOWER(pp.name) = LOWER(:perspective))")
    List<Game> findByGameModeAndPerspective(@Param("gameMode") String gameMode,
                                              @Param("perspective") String perspective,
                                              Pageable pageable);

    @Query("SELECT COUNT(DISTINCT g.id) FROM Game g " +
            "LEFT JOIN g.gameModes gm " +
            "LEFT JOIN g.playerPerspectives pp " +
            "WHERE (g.category IN (0, 8) OR g.category IS NULL) " +
            "AND (:gameMode = '' OR LOWER(gm.name) = LOWER(:gameMode)) " +
            "AND (:perspective = '' OR LOWER(pp.name) = LOWER(:perspective))")
    long countByGameModeAndPerspective(@Param("gameMode") String gameMode,
                                         @Param("perspective") String perspective);

    List<Game> findByDevelopersIsNull(Pageable pageable);

    long countByDevelopersIsNull();

    @Query("SELECT g FROM Game g WHERE g.id IN (SELECT DISTINCT g2.id FROM Game g2 JOIN g2.franchises f WHERE LOWER(f.name) = LOWER(:franchiseName)) AND g.category IN (0, 8) AND g.parentGameId IS NULL")
    List<Game> findByFranchiseName(@Param("franchiseName") String franchiseName, Pageable pageable);

    @Query("SELECT g FROM Game g WHERE g.id IN (SELECT DISTINCT g2.id FROM Game g2 JOIN g2.collections c WHERE LOWER(c.name) = LOWER(:collectionName)) AND g.category IN (0, 8) AND g.parentGameId IS NULL")
    List<Game> findByCollectionName(@Param("collectionName") String collectionName, Pageable pageable);

    // Comma-wrap both sides so "Capcom" matches "Capcom" but not "Capcom Vancouver" in the CSV column.
    @Query("SELECT g FROM Game g WHERE LOWER(CONCAT(',', g.developers, ',')) LIKE LOWER(CONCAT('%,', :developerName, ',%')) AND g.category IN (0, 8) AND g.parentGameId IS NULL")
    List<Game> findByDeveloperName(@Param("developerName") String developerName, Pageable pageable);

    @Query("SELECT g FROM Game g WHERE g.id <> :parentDbId " +
            "AND (g.parentGameId = :parentIgdbId " +
            "     OR g.name LIKE CONCAT(:parentName, ' - %') " +
            "     OR g.name LIKE CONCAT(:parentName, ': %')) " +
            "AND (g.category IS NULL OR g.category NOT IN (:excludedCategories))")
    List<Game> findEditionsOf(@Param("parentIgdbId") Integer parentIgdbId,
                               @Param("parentName") String parentName,
                               @Param("parentDbId") Long parentDbId,
                               @Param("excludedCategories") List<Integer> excludedCategories);

    // Date-range over cached first_release_date. Worker keeps the column fresh; read path is DB-only.

    @Query("SELECT g FROM Game g WHERE g.firstReleaseDate IS NOT NULL " +
            "AND g.firstReleaseDate >= :fromEpochSeconds " +
            "AND g.firstReleaseDate <= :toEpochSeconds " +
            "AND (g.category IN (0, 8) OR g.category IS NULL)")
    List<Game> findUpcoming(@Param("fromEpochSeconds") long fromEpochSeconds,
                             @Param("toEpochSeconds") long toEpochSeconds);

    @Query("SELECT g FROM Game g JOIN g.platforms p " +
            "WHERE g.firstReleaseDate IS NOT NULL " +
            "AND g.firstReleaseDate >= :fromEpochSeconds " +
            "AND g.firstReleaseDate <= :toEpochSeconds " +
            "AND (g.category IN (0, 8) OR g.category IS NULL) " +
            "AND LOWER(p.name) IN (:platformNamesLower)")
    List<Game> findUpcomingByPlatforms(@Param("fromEpochSeconds") long fromEpochSeconds,
                                         @Param("toEpochSeconds") long toEpochSeconds,
                                         @Param("platformNamesLower") List<String> platformNamesLower);

    @Query("SELECT g.igdbId FROM Game g WHERE g.firstReleaseDate IS NOT NULL AND g.firstReleaseDate > :nowEpochSeconds")
    List<Integer> findUpcomingIgdbIds(@Param("nowEpochSeconds") long nowEpochSeconds);

    // Filters the Explore Coming-Soon dropdown to platforms that actually have upcoming rows, not the full IGDB catalog.
    @Query("SELECT DISTINCT p.name FROM Game g JOIN g.platforms p " +
            "WHERE g.firstReleaseDate IS NOT NULL " +
            "AND g.firstReleaseDate > :nowEpochSeconds " +
            "AND (g.category IN (0, 8) OR g.category IS NULL) " +
            "ORDER BY p.name")
    List<String> findDistinctUpcomingPlatformNames(@Param("nowEpochSeconds") long nowEpochSeconds);

    @Query(value = "SELECT g FROM Game g",
           countQuery = "SELECT COUNT(g) FROM Game g")
    org.springframework.data.domain.Page<Game> findAllForBackfill(Pageable pageable);

    // Strict-prefix + " - "/": " separator match. Caller picks longest = most specific parent.
    @Query("SELECT g FROM Game g WHERE g.id <> :childDbId " +
            "AND (g.category IS NULL OR g.category = 0) " +
            "AND (:childName LIKE CONCAT(g.name, ' - %') OR :childName LIKE CONCAT(g.name, ': %')) " +
            "ORDER BY LENGTH(g.name) DESC")
    List<Game> findPrefixParentCandidates(@Param("childName") String childName,
                                           @Param("childDbId") Long childDbId);
}
