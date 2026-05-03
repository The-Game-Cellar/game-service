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

    // ── Main-game default filter ──────────────────────────────────────────────
    // Every browse / discovery / mood / random / genre query is filtered to
    // category IN (0, 8) (main game OR remake) OR category IS NULL by default.
    // Remakes are full standalone games and surface in default browse alongside
    // main games. Variants (DLC, Expansion, Bundle, Remaster, GOTY edition, etc.)
    // are excluded so recommendations and Explore default-view never surface
    // them. The /search endpoint accepts an explicit `gameType=variant` override
    // which routes to the *Variants counterparts below — used only by the
    // Explore variants toggle.

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

    /**
     * Random sample across all main-game rows in a genre that pass a quality bar
     * (combined critic+user rating + minimum vote count). Backs the Recommendation
     * Service candidate-pool generation — a single uniform random pick over the
     * full quality subset, no page math, no NULL-tail, no pagination bias.
     */
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

    // ── Variants-only counterparts (used by Explore variant-toggle ONLY) ──────
    // category > 2 AND <> 8 excludes plain DLC (1), Expansion (2) (already in
    // the parent's DLC stack rail) and Remake (8) (now lives in main browse).
    // Returns Bundles, Standalones, Remasters, Expanded Editions, Mods, Ports,
    // Updates, GOTY editions, deluxe editions, etc.

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

    // ── game_modes + player_perspectives filter ───────────────────────────────
    // Joined-table filters layered on top of the main-game default. Used by the
    // /search endpoint when `gameMode` and/or `perspective` query params are set.

    // gameMode and perspective use empty-string sentinels (instead of NULL) because Postgres
    // JDBC binds NULL parameters as bytea by default, which causes lower(bytea) errors when
    // wrapped in LOWER(). Empty string short-circuits the WHERE branch via plain equality.
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

    @Query("SELECT g FROM Game g WHERE g.id <> :parentDbId " +
            "AND (g.parentGameId = :parentIgdbId " +
            "     OR g.name LIKE CONCAT(:parentName, ' - %') " +
            "     OR g.name LIKE CONCAT(:parentName, ': %')) " +
            "AND (g.category IS NULL OR g.category NOT IN (:excludedCategories))")
    List<Game> findEditionsOf(@Param("parentIgdbId") Integer parentIgdbId,
                               @Param("parentName") String parentName,
                               @Param("parentDbId") Long parentDbId,
                               @Param("excludedCategories") List<Integer> excludedCategories);

    // ── Upcoming releases ─────────────────────────────────────────────────────
    // Date-range queries against the cached canonical first_release_date column.
    // Main-game default filter (category 0/8/null) applied so DLC + bundles do
    // not pollute the Coming Soon view. Worker keeps the column fresh; read path
    // never calls IGDB.

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

    /** Used by the daily worker refresh pass — pulls every game whose canonical date is in the future, regardless of platform or horizon. */
    @Query("SELECT g.igdbId FROM Game g WHERE g.firstReleaseDate IS NOT NULL AND g.firstReleaseDate > :nowEpochSeconds")
    List<Integer> findUpcomingIgdbIds(@Param("nowEpochSeconds") long nowEpochSeconds);

    /**
     * Distinct platform names across all upcoming main games. Used to populate the Explore
     * Coming Soon platform dropdown so it only offers platforms that actually have upcoming
     * releases (instead of the full ~150-platform IGDB catalog).
     */
    @Query("SELECT DISTINCT p.name FROM Game g JOIN g.platforms p " +
            "WHERE g.firstReleaseDate IS NOT NULL " +
            "AND g.firstReleaseDate > :nowEpochSeconds " +
            "AND (g.category IN (0, 8) OR g.category IS NULL) " +
            "ORDER BY p.name")
    List<String> findDistinctUpcomingPlatformNames(@Param("nowEpochSeconds") long nowEpochSeconds);

    /** Used by the one-shot backfill admin endpoint — paginates through every cached game so first_release_date + hypes can be repopulated from IGDB. */
    @Query(value = "SELECT g FROM Game g",
           countQuery = "SELECT COUNT(g) FROM Game g")
    org.springframework.data.domain.Page<Game> findAllForBackfill(Pageable pageable);

    /**
     * Finds the candidate parent of a variant whose IGDB {@code parent_game} link is missing.
     * Returns games whose name is a strict prefix of the variant's name followed by
     * {@code " - "} or {@code ": "}. Caller picks the longest match (most specific parent).
     */
    @Query("SELECT g FROM Game g WHERE g.id <> :childDbId " +
            "AND (g.category IS NULL OR g.category = 0) " +
            "AND (:childName LIKE CONCAT(g.name, ' - %') OR :childName LIKE CONCAT(g.name, ': %')) " +
            "ORDER BY LENGTH(g.name) DESC")
    List<Game> findPrefixParentCandidates(@Param("childName") String childName,
                                           @Param("childDbId") Long childDbId);
}
