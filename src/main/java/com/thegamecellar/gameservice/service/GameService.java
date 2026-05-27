package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.PlatformsResponse;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.Platform;
import com.thegamecellar.gameservice.model.entity.GameSimilarity;
import com.thegamecellar.gameservice.repository.GameRepository;
import com.thegamecellar.gameservice.repository.GameSimilarityRepository;
import com.thegamecellar.gameservice.repository.GenreRepository;
import com.thegamecellar.gameservice.repository.PlatformRepository;
import com.thegamecellar.gameservice.repository.TagRepository;
import com.thegamecellar.gameservice.util.GameMapper;
import com.thegamecellar.gameservice.util.PlatformGroups;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final GenreRepository genreRepository;
    private final PlatformRepository platformRepository;
    private final TagRepository tagRepository;
    private final GameCacheService gameCacheService;
    private final IgdbApiClient igdbApiClient;
    private final GameSimilarityRepository similarityRepository;

    @PersistenceContext
    private EntityManager em;

    // Mirrors frontend Explore GAME_MODES / PERSPECTIVES (values, not labels) so count badges
    // reflect the same curated set the user actually sees in the dropdowns.
    private static final List<String> CURATED_GAME_MODES = List.of(
            "Single player", "Multiplayer", "Co-operative", "Split screen",
            "Massively Multiplayer Online (MMO)", "Battle Royale");
    private static final List<String> CURATED_PERSPECTIVES = List.of(
            "First person", "Third person", "Bird view / Isometric", "Side view",
            "Text", "Auditory", "Virtual Reality");

    private static final Set<String> JUNK_TAG_BLOCKLIST = Set.of(
            "2.5d", "3d", "8-bit", "a.i. companion", "abstract", "action-adventure",
            "adjustable difficulty", "anime", "artificial intelligence", "bloody",
            "bow and arrow", "breaking the fourth wall", "cartoony", "casual",
            "character creation", "character customization", "choices matter", "climbing",
            "collectibles", "colorful", "creepy", "customizable characters", "cute",
            "dark", "dark humor", "darkness", "dating simulation", "day/night cycle",
            "deliberately retro", "destructible environment", "dialogue trees", "difficult",
            "difficulty level", "dual wielding", "emotional", "eroge", "experimental",
            "extreme violence", "fairy", "fast paced", "flight", "forest", "funny",
            "good vs evil", "gore", "grapple", "grid-based movement", "immersive",
            "leaderboard", "low-poly", "magic", "magical girl", "mahjong", "mecha",
            "mercenary", "metroidvania", "minimalist", "modern military", "moral decisions",
            "multiple endings", "multiple protagonists", "murder", "nsfw", "nudity",
            "otome", "parody", "party system", "permadeath", "physics", "plot twist",
            "poisoning", "pve", "ragdoll physics", "real-time combat", "realism",
            "rivaling factions", "roguelike", "roguelite", "sexual content",
            "sexual themes", "shared screen", "shmup", "shopping", "short", "side quests",
            "skeletons", "speedrun", "sprinting mechanics", "stylized", "supernatural",
            "survival horror", "swimming", "tactical turn-based combat", "teleportation",
            "throwing weapons", "time limit", "turn-based combat", "turn-based rpg",
            "undead", "underwater", "underwater gameplay", "unlockables",
            "upgradeable weapons", "violent", "wholesome", "world map"
    );

    @Transactional
    public GameResponse getGameById(Integer igdbId) {
        Optional<Game> cached = gameRepository.findByIgdbId(igdbId);

        if (cached.isPresent()) {
            Game game = cached.get();
            // Refresh only on missing core data so read path stays cheap; worker handles the rest.
            // Tags excluded on purpose: empty is legit after allowlist filter, refetch would loop.
            boolean stale = game.getGenres().isEmpty()
                    || game.getDescription() == null
                    || game.getDescription().isBlank()
                    || game.getDevelopers() == null;
            if (stale) {
                try {
                    IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
                    Game refreshed = gameCacheService.refreshStaleGame(game, dto);
                    return augmentInferredParent(refreshed, GameMapper.toResponse(refreshed));
                } catch (Exception e) {
                    log.warn("Could not re-fetch stale game igdbId={}: {}", igdbId, e.getMessage());
                    return augmentInferredParent(game, GameMapper.toResponse(game));
                }
            }
            return augmentInferredParent(game, GameMapper.toResponse(game));
        }

        IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
        Game saved = gameCacheService.cacheGame(dto);
        return augmentInferredParent(saved, GameMapper.toResponse(saved));
    }

    // Reads from pre-computed game_similarities (catalog-side, no user context). Falls back to
    // empty when SimilarityWorker has not yet processed the source game; rec-service callers
    // treat empty as "no recommendations" rather than retrying.
    @Transactional(readOnly = true)
    public List<GameResponse> getSimilarGames(Integer igdbId, int limit) {
        List<GameSimilarity> rows = similarityRepository.findTopBySource(igdbId);
        if (rows.isEmpty()) return List.of();
        List<Integer> ids = rows.stream()
                .limit(limit)
                .map(GameSimilarity::getSimilarIgdbId)
                .toList();
        List<Game> games = gameRepository.findByIgdbIdIn(ids);
        java.util.Map<Integer, Game> byId = games.stream()
                .collect(java.util.stream.Collectors.toMap(Game::getIgdbId, g -> g, (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(g -> augmentInferredParent(g, GameMapper.toResponse(g)))
                .toList();
    }

    // Categories where IGDB routinely omits parent_game (Bundle/Mod/Update); the longest
    // separator-prefix match against cached names fills it in. Main + Remake/Remaster never run this.
    private static final java.util.Set<Integer> INFERABLE_PARENT_CATEGORIES = java.util.Set.of(3, 5, 14);

    private GameResponse augmentInferredParent(Game game, GameResponse response) {
        if (response.getParentGameId() != null) return response;
        if (game.getCategory() == null || !INFERABLE_PARENT_CATEGORIES.contains(game.getCategory())) return response;
        if (game.getName() == null) return response;
        String name = game.getName();
        if (!name.contains(" - ") && !name.contains(": ")) return response;

        List<Game> candidates = gameRepository.findPrefixParentCandidates(name, game.getId());
        for (Game parent : candidates) {
            // Reject junk-name parents (single-char placeholders, IGDB stub rows like "%").
            if (parent.getName() != null && parent.getName().trim().length() >= 3) {
                response.setParentGameId(parent.getIgdbId());
                response.setParentGameName(parent.getName());
                return response;
            }
        }
        return response;
    }

    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre, String ordering, int page, int pageSize) {
        return searchGames(query, platform, genre, ordering, page, pageSize, false, "main", null, null, null, null, null, null);
    }

    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre,
                                            String ordering, int page, int pageSize, boolean dbOnly,
                                            String gameType, String gameMode, String perspective) {
        return searchGames(query, platform, genre, ordering, page, pageSize, dbOnly,
                gameType, gameMode, perspective, null, null, null, null);
    }

    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre,
                                            String ordering, int page, int pageSize, boolean dbOnly,
                                            String gameType, String gameMode, String perspective,
                                            Long releasedFrom, Long releasedTo) {
        return searchGames(query, platform, genre, ordering, page, pageSize, dbOnly,
                gameType, gameMode, perspective, releasedFrom, releasedTo, null, null);
    }

    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre,
                                            String ordering, int page, int pageSize, boolean dbOnly,
                                            String gameType, String gameMode, String perspective,
                                            Long releasedFrom, Long releasedTo, String tags) {
        return searchGames(query, platform, genre, ordering, page, pageSize, dbOnly,
                gameType, gameMode, perspective, releasedFrom, releasedTo, tags, null);
    }

    // Single Specification AND-s every filter so combinations work; prior fan-out missed filters when 2+ were set.
    @Transactional
    public GameSearchResponse searchGames(String query, String platform, String genre,
                                            String ordering, int page, int pageSize, boolean dbOnly,
                                            String gameType, String gameMode, String perspective,
                                            Long releasedFrom, Long releasedTo, String tags,
                                            java.math.BigDecimal ratingFrom) {
        PageRequest pageable = PageRequest.of(page, pageSize, sortFromOrdering(ordering));

        Specification<Game> spec = buildSearchSpec(query, platform, genre, gameType, gameMode, perspective, releasedFrom, releasedTo, tags, ratingFrom);
        Page<Game> result = gameRepository.findAll(spec, pageable);

        // Facet counts via native SQL UNION ALL, men bara när user faktiskt filtrerar -- annars matchar spec hela
        // katalogen (~150k spel) och count-queries skannar tre join-tabeller i sin helhet. Frontend visar inga counts
        // i cold state, så fort user picks första filter aktiveras count-pathen mot mindre subset.
        boolean anyUserFilterActive = isSet(query) || isSet(platform) || isSet(genre)
                || isSet(gameMode) || isSet(perspective) || isSet(tags)
                || ratingFrom != null || releasedFrom != null || releasedTo != null;
        FacetCounts facets;
        if (anyUserFilterActive) {
            facets = computeAllFacetCounts(spec,
                    getPopularTags(50), genreRepository.findAllNames(),
                    CURATED_GAME_MODES, CURATED_PERSPECTIVES);
        } else {
            facets = FacetCounts.skipped();
        }

        if (result.getTotalElements() > 0) {
            return GameSearchResponse.builder()
                    .games(result.getContent().stream().map(GameMapper::toResponse).toList())
                    .totalCount((int) Math.min(result.getTotalElements(), Integer.MAX_VALUE))
                    .page(page)
                    .pageSize(pageSize)
                    .availableTagCounts(facets.tag())
                    .availableGenreCounts(facets.genre())
                    .availableGameModeCounts(facets.gameMode())
                    .availablePerspectiveCounts(facets.perspective())
                    .build();
        }

        // IGDB only filters by query/genre/platform; falling back with gameMode/perspective/year-range/tags/rating active
        // would return misleading broad results, so we return empty for an honest "no matches" UI.
        boolean igdbCanMatchFilters = !isSet(gameMode) && !isSet(perspective)
                && (gameType == null || "main".equalsIgnoreCase(gameType))
                && releasedFrom == null && releasedTo == null
                && !isSet(tags)
                && ratingFrom == null;

        if (dbOnly || !igdbCanMatchFilters) {
            return GameSearchResponse.builder()
                    .games(List.of())
                    .totalCount(0)
                    .page(page)
                    .pageSize(pageSize)
                    .availableTagCounts(facets.tag())
                    .availableGenreCounts(facets.genre())
                    .availableGameModeCounts(facets.gameMode())
                    .availablePerspectiveCounts(facets.perspective())
                    .build();
        }

        boolean queryBlank = (query == null || query.isBlank());
        boolean genreSet = (genre != null && !genre.isBlank());
        boolean platformSet = (platform != null && !platform.isBlank());

        try {
            List<IgdbGameDto> igdbResults;
            if (!queryBlank) {
                igdbResults = igdbApiClient.searchGames(query, pageSize, page * pageSize);
            } else if (genreSet && platformSet) {
                igdbResults = igdbApiClient.searchByGenreAndPlatform(genre, platform, pageSize, page * pageSize);
            } else if (genreSet) {
                igdbResults = igdbApiClient.searchByGenre(genre, pageSize, page * pageSize);
            } else if (platformSet) {
                igdbResults = igdbApiClient.fetchPopularGames(platform, pageSize, page * pageSize);
            } else {
                igdbResults = igdbApiClient.fetchCatalogPage(pageSize, page * pageSize);
            }

            List<GameResponse> games = igdbResults.stream()
                    .map(dto -> {
                        gameCacheService.cacheIfAbsent(dto);
                        return GameMapper.toResponseFromIgdb(dto);
                    })
                    .toList();

            return GameSearchResponse.builder()
                    .games(games)
                    .totalCount(games.size())
                    .page(page)
                    .pageSize(pageSize)
                    .build();
        } catch (IgdbApiException ex) {
            log.warn("IGDB search unavailable (query={}, genre={}): {}", query, genre, ex.getMessage());
            return GameSearchResponse.builder()
                    .games(List.of())
                    .totalCount(0)
                    .page(page)
                    .pageSize(pageSize)
                    .build();
        }
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static List<String> splitCsvLower(String csv) {
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    // Per-facet counts in one shot: hämta spec-game-IDs via Criteria (utnyttjar samma Specification som page-query),
    // sen en enda native SQL UNION ALL över 4 facet-tabeller scoped till de IDs. 5 round-trips → 3 (page-data + ids + union),
    // och varje facet-arm ärver inga spec-joins så query-plan blir platt.
    public record FacetCounts(Map<String, Long> tag, Map<String, Long> genre, Map<String, Long> gameMode, Map<String, Long> perspective) {
        // Zero-maps: spec gav 0 träffar -- alla candidates ska visas som "no match" (gray-out i UI).
        public static FacetCounts zeros(List<String> tagCandidates, List<String> genreCandidates,
                                        List<String> gameModeCandidates, List<String> perspectiveCandidates) {
            return new FacetCounts(zeroMap(tagCandidates), zeroMap(genreCandidates),
                    zeroMap(gameModeCandidates), zeroMap(perspectiveCandidates));
        }
        // Null-maps: counts beräknades inte (cold path / inget user-filter aktivt). UI hoppar gray-out helt.
        public static FacetCounts skipped() {
            return new FacetCounts(null, null, null, null);
        }
        static Map<String, Long> zeroMap(List<String> names) {
            Map<String, Long> m = new LinkedHashMap<>();
            for (String n : names) m.put(n, 0L);
            return m;
        }
    }

    private FacetCounts computeAllFacetCounts(
            Specification<Game> spec,
            List<String> tagCandidates, List<String> genreCandidates,
            List<String> gameModeCandidates, List<String> perspectiveCandidates) {
        if (em == null) {
            return FacetCounts.zeros(tagCandidates, genreCandidates, gameModeCandidates, perspectiveCandidates);
        }

        // Step 1: spec → matchande game-IDs. Criteria återanvänder Specification så DRY.
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<Game> idRoot = idQuery.from(Game.class);
        Predicate idPred = spec != null ? spec.toPredicate(idRoot, idQuery, cb) : null;
        idQuery.select(idRoot.get("id")).distinct(true);
        if (idPred != null) idQuery.where(idPred);
        List<Long> specIds = em.createQuery(idQuery).getResultList();
        if (specIds.isEmpty()) {
            return FacetCounts.zeros(tagCandidates, genreCandidates, gameModeCandidates, perspectiveCandidates);
        }

        // Step 2: native UNION ALL för 4 facets, scoped till specIds. IDs inlineas som literal BIGINT-lista i SQL
        // (säkert -- IDs är numeriska från egen DB, ingen injection-yta). Hibernate kan tolka samma named-list-param
        // över flera UNION-armar inkonsistent, så vi undviker det helt. Candidates (string-listor) stannar som named params.
        String idsLiteral = specIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        StringBuilder sql = new StringBuilder("SELECT facet, facet_value, cnt FROM (");
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> arms = new ArrayList<>();
        if (!tagCandidates.isEmpty()) {
            arms.add("SELECT 'tag' AS facet, t.name AS facet_value, COUNT(DISTINCT gt.game_id) AS cnt "
                    + "FROM game_tags gt JOIN tags t ON t.id = gt.tag_id "
                    + "WHERE gt.game_id IN (" + idsLiteral + ") AND LOWER(t.name) IN (:tagLower) GROUP BY t.name");
            params.put("tagLower", tagCandidates.stream().map(String::toLowerCase).toList());
        }
        if (!genreCandidates.isEmpty()) {
            arms.add("SELECT 'genre' AS facet, ge.name AS facet_value, COUNT(DISTINCT gg.game_id) AS cnt "
                    + "FROM game_genres gg JOIN genres ge ON ge.id = gg.genre_id "
                    + "WHERE gg.game_id IN (" + idsLiteral + ") AND LOWER(ge.name) IN (:genreLower) GROUP BY ge.name");
            params.put("genreLower", genreCandidates.stream().map(String::toLowerCase).toList());
        }
        if (!gameModeCandidates.isEmpty()) {
            arms.add("SELECT 'gameMode' AS facet, gm.name AS facet_value, COUNT(DISTINCT ggm.game_id) AS cnt "
                    + "FROM game_game_modes ggm JOIN game_modes gm ON gm.id = ggm.game_mode_id "
                    + "WHERE ggm.game_id IN (" + idsLiteral + ") AND LOWER(gm.name) IN (:gameModeLower) GROUP BY gm.name");
            params.put("gameModeLower", gameModeCandidates.stream().map(String::toLowerCase).toList());
        }
        if (!perspectiveCandidates.isEmpty()) {
            arms.add("SELECT 'perspective' AS facet, pp.name AS facet_value, COUNT(DISTINCT gpp.game_id) AS cnt "
                    + "FROM game_player_perspectives gpp JOIN player_perspectives pp ON pp.id = gpp.player_perspective_id "
                    + "WHERE gpp.game_id IN (" + idsLiteral + ") AND LOWER(pp.name) IN (:perspectiveLower) GROUP BY pp.name");
            params.put("perspectiveLower", perspectiveCandidates.stream().map(String::toLowerCase).toList());
        }
        if (arms.isEmpty()) {
            return FacetCounts.zeros(tagCandidates, genreCandidates, gameModeCandidates, perspectiveCandidates);
        }
        sql.append(String.join(" UNION ALL ", arms)).append(") all_facets");

        jakarta.persistence.Query nq = em.createNativeQuery(sql.toString());
        params.forEach(nq::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = nq.getResultList();

        Map<String, Long> tagMap = FacetCounts.zeroMap(tagCandidates);
        Map<String, Long> genreMap = FacetCounts.zeroMap(genreCandidates);
        Map<String, Long> gameModeMap = FacetCounts.zeroMap(gameModeCandidates);
        Map<String, Long> perspectiveMap = FacetCounts.zeroMap(perspectiveCandidates);

        for (Object[] r : rows) {
            String facet = (String) r[0];
            String value = (String) r[1];
            Long count = ((Number) r[2]).longValue();
            Map<String, Long> target;
            List<String> candidatesForFacet;
            switch (facet) {
                case "tag" -> { target = tagMap; candidatesForFacet = tagCandidates; }
                case "genre" -> { target = genreMap; candidatesForFacet = genreCandidates; }
                case "gameMode" -> { target = gameModeMap; candidatesForFacet = gameModeCandidates; }
                case "perspective" -> { target = perspectiveMap; candidatesForFacet = perspectiveCandidates; }
                default -> { continue; }
            }
            for (String orig : candidatesForFacet) {
                if (orig.equalsIgnoreCase(value)) {
                    target.put(orig, count);
                    break;
                }
            }
        }
        return new FacetCounts(tagMap, genreMap, gameModeMap, perspectiveMap);
    }

    private static Specification<Game> buildSearchSpec(String query, String platform, String genre,
                                                         String gameType, String gameMode, String perspective,
                                                         Long releasedFrom, Long releasedTo, String tags,
                                                         java.math.BigDecimal ratingFrom) {
        return (root, cq, cb) -> {
            cq.distinct(true);
            List<Predicate> preds = new ArrayList<>();

            if (releasedFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("firstReleaseDate"), releasedFrom));
            }
            if (releasedTo != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("firstReleaseDate"), releasedTo));
            }

            // main = cat 0 + 8 (remakes treated as full games) + null; variant = everything else except 8; all = no filter.
            if (gameType == null || "main".equalsIgnoreCase(gameType)) {
                preds.add(cb.or(
                        cb.equal(root.get("category"), 0),
                        cb.equal(root.get("category"), 8),
                        cb.isNull(root.get("category"))
                ));
            } else if ("variant".equalsIgnoreCase(gameType)) {
                preds.add(cb.and(
                        cb.greaterThan(root.get("category"), 2),
                        cb.notEqual(root.get("category"), 8)
                ));
            }

            if (isSet(query)) {
                preds.add(cb.like(cb.lower(root.get("name")), "%" + query.toLowerCase() + "%"));
            }

            if (isSet(genre)) {
                // CSV + AND: separat INNER JOIN per genre, raden måste matcha alla samtidigt.
                for (String wanted : splitCsvLower(genre)) {
                    var genreJoin = root.join("genres", JoinType.INNER);
                    preds.add(cb.equal(cb.lower(genreJoin.get("name")), wanted));
                }
            }

            if (isSet(platform)) {
                // CSV value carries umbrella expansion (frontend sends "PlayStation" pre-expanded to 7 child platforms).
                List<String> wanted = java.util.Arrays.stream(platform.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .toList();
                if (!wanted.isEmpty()) {
                    var platformJoin = root.join("platforms", JoinType.INNER);
                    preds.add(cb.lower(platformJoin.get("name")).in(wanted));
                }
            }

            if (isSet(gameMode)) {
                for (String wanted : splitCsvLower(gameMode)) {
                    var modeJoin = root.join("gameModes", JoinType.INNER);
                    preds.add(cb.equal(cb.lower(modeJoin.get("name")), wanted));
                }
            }

            if (isSet(perspective)) {
                for (String wanted : splitCsvLower(perspective)) {
                    var perspJoin = root.join("playerPerspectives", JoinType.INNER);
                    preds.add(cb.equal(cb.lower(perspJoin.get("name")), wanted));
                }
            }

            if (isSet(tags)) {
                // AND-semantik: en separat INNER JOIN per tag, raden måste matcha alla samtidigt.
                for (String wanted : splitCsvLower(tags)) {
                    var tagJoin = root.join("tags", JoinType.INNER);
                    preds.add(cb.equal(cb.lower(tagJoin.get("name")), wanted));
                }
            }

            if (ratingFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(root.<java.math.BigDecimal>get("totalRating"), ratingFrom));
            }

            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public GameSearchResponse getPopularGames(String platform, int page) {
        List<IgdbGameDto> igdbResults = igdbApiClient.fetchPopularGames(platform, 20, page * 20);
        List<GameResponse> games = igdbResults.stream()
                .map(dto -> {
                    gameCacheService.cacheIfAbsent(dto);
                    return GameMapper.toResponseFromIgdb(dto);
                })
                .toList();

        return GameSearchResponse.builder()
                .games(games)
                .totalCount(games.size())
                .page(page)
                .pageSize(20)
                .build();
    }

    // DB-only read path; worker keeps upcoming rows fresh so no IGDB call here.
    @Transactional(readOnly = true)
    public GameSearchResponse getUpcomingGames(List<String> platforms, int windowDays, int limit, java.util.Set<Integer> excludeIgdbIds) {
        List<GameResponse> games = getUpcoming(platforms, windowDays, limit, excludeIgdbIds);
        return GameSearchResponse.builder()
                .games(games)
                .totalCount(games.size())
                .page(0)
                .pageSize(limit)
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getUpcomingPlatformNames() {
        long now = java.time.Instant.now().getEpochSecond();
        return gameRepository.findDistinctUpcomingPlatformNames(now);
    }

    @Transactional(readOnly = true)
    public GameSearchResponse getRandomGames(int limit) {
        List<GameResponse> games = gameRepository.findRandom(limit).stream()
                .map(GameMapper::toResponse)
                .toList();
        return GameSearchResponse.builder()
                .games(games)
                .totalCount(games.size())
                .page(0)
                .pageSize(limit)
                .build();
    }

    @Transactional(readOnly = true)
    public GameSearchResponse getRandomQualityByGenre(String genre, java.math.BigDecimal minRating, int minVotes, int limit) {
        List<GameResponse> games = gameRepository.findRandomQualityByGenre(genre, minRating, minVotes, limit).stream()
                .map(GameMapper::toResponse)
                .toList();
        return GameSearchResponse.builder()
                .games(games)
                .totalCount(games.size())
                .page(0)
                .pageSize(limit)
                .build();
    }

    public List<String> getGenres() {
        List<String> cached = genreRepository.findAllNames();
        if (!cached.isEmpty()) {
            return cached;
        }
        return igdbApiClient.fetchGenres().stream()
                .map(g -> g.getName())
                .toList();
    }

    public List<String> getPopularTags(int limit) {
        return tagRepository.findPopularExcludingBlocklist(JUNK_TAG_BLOCKLIST, limit).stream()
                .map(row -> (String) row[0])
                .toList();
    }

    public List<String> getPlatforms() {
        return platformRepository.findAll().stream()
                .map(Platform::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    // Big-4 umbrellas first; children absent from DB are filtered so the dropdown can't offer dead options.
    public PlatformsResponse getPlatformGroups() {
        return PlatformGroups.group(getPlatforms());
    }

    @Transactional(readOnly = true)
    public List<GameResponse> getByFranchise(String franchiseName, int limit, Integer excludeIgdbId) {
        Sort sort = Sort.by(Sort.Order.asc("released").nullsLast());
        List<Game> raw = gameRepository.findByFranchiseName(franchiseName, PageRequest.of(0, Math.min(limit * 4, 100), sort));
        return dedupeVariants(raw, excludeIgdbId, limit).stream()
                .map(GameMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GameResponse> getByCollection(String collectionName, int limit, Integer excludeIgdbId) {
        Sort sort = Sort.by(Sort.Order.asc("released").nullsLast());
        List<Game> raw = gameRepository.findByCollectionName(collectionName, PageRequest.of(0, Math.min(limit * 4, 100), sort));
        return dedupeVariants(raw, excludeIgdbId, limit).stream()
                .map(GameMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GameResponse> getByDeveloper(String developerName, int limit, Integer excludeIgdbId) {
        Sort sort = Sort.by(Sort.Order.desc("released").nullsLast());
        List<Game> raw = gameRepository.findByDeveloperName(developerName, PageRequest.of(0, Math.min(limit * 4, 100), sort));
        return dedupeVariants(raw, excludeIgdbId, limit).stream()
                .map(GameMapper::toResponse)
                .toList();
    }

    // Matches on parentGameId OR separator-prefix name match because IGDB orphans many edition variants.
    // Excludes 1/2 (DLC + Expansion live in DLC stack) and 8 (Remake = standalone); Bundle 3 stays in.
    @Transactional(readOnly = true)
    public List<GameResponse> getEditionsOf(Integer parentIgdbId) {
        Game parent = gameRepository.findByIgdbId(parentIgdbId).orElse(null);
        if (parent == null || parent.getName() == null || parent.getName().isBlank()) {
            return List.of();
        }
        List<Integer> excludedCategories = List.of(1, 2, 8);
        List<Game> editions = gameRepository.findEditionsOf(
                parentIgdbId, parent.getName(), parent.getId(), excludedCategories);
        return editions.stream()
                .sorted(java.util.Comparator
                        .comparing(Game::getReleased,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparingInt(g -> g.getName() == null ? 0 : g.getName().length()))
                .map(GameMapper::toResponse)
                .toList();
    }

    // Drops IGDB-miscategorised editions: name starting with already-kept name + " - "/": " is a variant.
    // Sort (released asc, name length asc) ensures canonical base name lands first on ties.
    private static List<com.thegamecellar.gameservice.model.entity.Game> dedupeVariants(
            List<com.thegamecellar.gameservice.model.entity.Game> raw, Integer excludeIgdbId, int limit) {
        List<com.thegamecellar.gameservice.model.entity.Game> sorted = raw.stream()
                .sorted(java.util.Comparator
                        .comparing(com.thegamecellar.gameservice.model.entity.Game::getReleased,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparingInt(g -> g.getName() == null ? 0 : g.getName().length()))
                .toList();

        List<String> seenNames = new java.util.ArrayList<>();
        List<com.thegamecellar.gameservice.model.entity.Game> result = new java.util.ArrayList<>();
        for (com.thegamecellar.gameservice.model.entity.Game g : sorted) {
            if (result.size() >= limit) break;
            String n = g.getName();
            if (n == null) continue;
            boolean isVariant = seenNames.stream()
                    .anyMatch(kn -> n.startsWith(kn + " - ") || n.startsWith(kn + ": "));
            if (isVariant) continue;

            seenNames.add(n);
            if (excludeIgdbId == null || !excludeIgdbId.equals(g.getIgdbId())) {
                result.add(g);
            }
        }
        return result;
    }

    // ── Admin backfill ────────────────────────────────────────────────────────

    // Re-fetches developers IS NULL rows with the worker's 250ms IGDB gap; serialised via AdminSyncExecutor.
    public void backfillDevelopers() {
        long total = gameRepository.countByDevelopersIsNull();
        log.info("Developer backfill starting: {} candidate games", total);
        int processed = 0;
        int updated = 0;
        int batchSize = 100;

        while (true) {
            List<Game> batch = gameRepository.findByDevelopersIsNull(PageRequest.of(0, batchSize));
            if (batch.isEmpty()) break;
            for (Game game : batch) {
                try {
                    IgdbGameDto dto = igdbApiClient.fetchGameById(game.getIgdbId());
                    gameCacheService.refreshStaleGame(game, dto);
                    if (game.getDevelopers() != null) updated++;
                } catch (Exception e) {
                    log.warn("Developer backfill: failed for igdbId={}: {}", game.getIgdbId(), e.getMessage());
                }
                processed++;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Developer backfill interrupted at processed={}", processed);
                    return;
                }
            }
            log.info("Developer backfill progress: processed={}/{} updated={}", processed, total, updated);
        }
        log.info("Developer backfill complete: processed={} updated={}", processed, updated);
    }

    // ── IGDB catalog worker support ───────────────────────────────────────────

    public CatalogSyncResult syncIgdbCatalogOffset(int offset, int limit) {
        List<IgdbGameDto> results = igdbApiClient.fetchCatalogPage(limit, offset);
        int cached = 0;
        for (IgdbGameDto dto : results) {
            try {
                if (gameCacheService.cacheIfAbsent(dto)) cached++;
            } catch (Exception e) {
                log.warn("IGDB catalog sync: failed to cache igdbId={}: {}", dto.getId(), e.getMessage());
            }
        }
        return new CatalogSyncResult(results.size(), cached);
    }

    public CatalogSyncResult syncIgdbNewReleasesOffset(int offset, int limit) {
        List<IgdbGameDto> results = igdbApiClient.fetchNewReleases(limit, offset);
        int cached = 0;
        for (IgdbGameDto dto : results) {
            try {
                if (gameCacheService.cacheIfAbsent(dto)) cached++;
            } catch (Exception e) {
                log.warn("IGDB new releases sync: failed to cache igdbId={}: {}", dto.getId(), e.getMessage());
            }
        }
        return new CatalogSyncResult(results.size(), cached);
    }

    public CatalogSyncResult syncIgdbUpcomingOffset(int offset, int limit) {
        List<IgdbGameDto> results = igdbApiClient.fetchUpcomingReleases(limit, offset);
        int cached = 0;
        for (IgdbGameDto dto : results) {
            try {
                if (gameCacheService.cacheIfAbsent(dto)) cached++;
            } catch (Exception e) {
                log.warn("IGDB upcoming releases sync: failed to cache igdbId={}: {}", dto.getId(), e.getMessage());
            }
        }
        return new CatalogSyncResult(results.size(), cached);
    }

    // Force-overwrites volatile upcoming fields (release date, hypes, totalRating) so slips propagate within 24h.
    public boolean refreshUpcomingRow(Integer igdbId) {
        Optional<Game> existing = gameRepository.findByIgdbId(igdbId);
        if (existing.isEmpty()) return false;
        try {
            IgdbGameDto dto = igdbApiClient.fetchGameById(igdbId);
            gameCacheService.refreshUpcomingGame(existing.get(), dto);
            return true;
        } catch (Exception e) {
            log.warn("Upcoming refresh failed for igdbId={}: {}", igdbId, e.getMessage());
            return false;
        }
    }

    public List<Integer> findUpcomingIgdbIds() {
        long now = java.time.Instant.now().getEpochSecond();
        return gameRepository.findUpcomingIgdbIds(now);
    }

    // ── Coming Soon read path ─────────────────────────────────────────────────

    // Hype-weighted sample without replacement. Null hypes = 0 weight (only picked when all remaining are 0).
    @Transactional(readOnly = true)
    public List<GameResponse> getUpcoming(List<String> platformFilter, int windowDays, int limit, java.util.Set<Integer> excludeIgdbIds) {
        long now = java.time.Instant.now().getEpochSecond();
        // windowDays <= 0 = unbounded ("All" toggle); Long.MAX_VALUE degenerates BETWEEN to "any future date".
        long horizonEnd = (windowDays <= 0)
                ? Long.MAX_VALUE
                : now + (long) windowDays * 24L * 60L * 60L;

        List<Game> pool;
        if (platformFilter != null && !platformFilter.isEmpty()) {
            List<String> lowered = platformFilter.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(String::toLowerCase)
                    .toList();
            pool = lowered.isEmpty()
                    ? gameRepository.findUpcoming(now, horizonEnd)
                    : gameRepository.findUpcomingByPlatforms(now, horizonEnd, lowered);
        } else {
            pool = gameRepository.findUpcoming(now, horizonEnd);
        }
        if (pool.isEmpty() || limit <= 0) return List.of();

        java.util.Set<Integer> exclude = excludeIgdbIds == null ? java.util.Set.of() : excludeIgdbIds;

        // Dedupe (platform JOIN repeats rows) + skip owned games so Coming Soon stays "what's next".
        java.util.LinkedHashMap<Integer, Game> uniqueMap = new java.util.LinkedHashMap<>();
        for (Game g : pool) {
            if (exclude.contains(g.getIgdbId())) continue;
            uniqueMap.putIfAbsent(g.getIgdbId(), g);
        }
        List<Game> unique = new ArrayList<>(uniqueMap.values());
        if (unique.isEmpty()) return List.of();

        return weightedSampleByHype(unique, limit).stream()
                .map(GameMapper::toResponse)
                .toList();
    }

    private List<Game> weightedSampleByHype(List<Game> candidates, int limit) {
        java.util.List<Game> remaining = new java.util.ArrayList<>(candidates);
        java.util.List<Game> picked = new java.util.ArrayList<>(Math.min(limit, candidates.size()));
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        while (!remaining.isEmpty() && picked.size() < limit) {
            long totalWeight = 0L;
            for (Game g : remaining) {
                Integer h = g.getHypes();
                totalWeight += (h != null && h > 0) ? h : 0;
            }
            if (totalWeight == 0) {
                Game g = remaining.remove(rng.nextInt(remaining.size()));
                picked.add(g);
                continue;
            }
            long roll = rng.nextLong(totalWeight);
            long cum = 0;
            int idx = remaining.size() - 1;
            for (int i = 0; i < remaining.size(); i++) {
                Integer h = remaining.get(i).getHypes();
                long w = (h != null && h > 0) ? h : 0;
                cum += w;
                if (roll < cum) { idx = i; break; }
            }
            picked.add(remaining.remove(idx));
        }
        return picked;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Sort sortFromOrdering(String ordering) {
        // "-rating" = totalRating (user votes), not critic rating; critics skew to AAA review-bait coverage.
        if (ordering == null || ordering.isBlank() || ordering.equals("-rating")) {
            return Sort.by(Sort.Order.desc("totalRating").nullsLast());
        }
        return switch (ordering) {
            case "rating"    -> Sort.by(Sort.Order.asc("totalRating").nullsLast());
            case "-released" -> Sort.by(Sort.Order.desc("released").nullsLast());
            case "released"  -> Sort.by(Sort.Order.asc("released").nullsLast());
            case "name"      -> Sort.by(Sort.Order.asc("name").nullsLast());
            case "-name"     -> Sort.by(Sort.Order.desc("name").nullsLast());
            default          -> Sort.by(Sort.Order.desc("totalRating").nullsLast());
        };
    }
}
