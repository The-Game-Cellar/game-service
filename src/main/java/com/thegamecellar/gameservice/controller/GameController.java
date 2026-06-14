package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.GenresResponse;
import com.thegamecellar.gameservice.model.dto.PlatformsResponse;
import com.thegamecellar.gameservice.model.dto.PopularTagsResponse;
import com.thegamecellar.gameservice.model.dto.UpcomingGamesRequest;
import com.thegamecellar.gameservice.service.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @GetMapping("/{igdbId}")
    public ResponseEntity<GameResponse> getGameById(@PathVariable @Min(1) Integer igdbId) {
        return ResponseEntity.ok(gameService.getGameById(igdbId));
    }

    @GetMapping("/search")
    public ResponseEntity<GameSearchResponse> searchGames(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String gameMode,
            @RequestParam(required = false) String perspective,
            @RequestParam(defaultValue = "main") @Pattern(regexp = "main|variant|all") String gameType,
            @RequestParam(defaultValue = "-rating") @Pattern(regexp = "-rating|-released|released|name|-name") String ordering,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "false") boolean dbOnly,
            @RequestParam(required = false) Long releasedFrom,
            @RequestParam(required = false) Long releasedTo,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) java.math.BigDecimal ratingFrom) {
        return ResponseEntity.ok(gameService.searchGames(
                query, platform, genre, ordering, page, pageSize, dbOnly,
                gameType, gameMode, perspective, releasedFrom, releasedTo, tags, ratingFrom));
    }

    @GetMapping("/popular")
    public ResponseEntity<GameSearchResponse> getPopularGames(
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page) {
        return ResponseEntity.ok(gameService.getPopularGames(platform, page));
    }

    @PostMapping("/upcoming")
    public ResponseEntity<GameSearchResponse> getUpcomingGames(@Valid @RequestBody(required = false) UpcomingGamesRequest request) {
        UpcomingGamesRequest req = (request == null) ? new UpcomingGamesRequest() : request;
        List<String> platforms = (req.getPlatform() == null || req.getPlatform().isBlank())
                ? List.of()
                : List.of(req.getPlatform().split(","));
        java.util.Set<Integer> exclude = (req.getExcludeIds() == null) ? java.util.Set.of() : req.getExcludeIds();
        List<Integer> recentlyShown = (req.getRecentlyShownIds() == null) ? List.of() : req.getRecentlyShownIds();
        int windowDays = (req.getWindowDays() == null) ? 90 : req.getWindowDays();
        int limit = (req.getLimit() == null) ? 20 : req.getLimit();
        return ResponseEntity.ok(gameService.getUpcomingGames(
                platforms, windowDays, limit, exclude, recentlyShown, req.getPage(), req.getPageSize()));
    }

    @GetMapping("/upcoming/platforms")
    public ResponseEntity<Map<String, List<String>>> getUpcomingPlatforms() {
        return ResponseEntity.ok(Map.of("platforms", gameService.getUpcomingPlatformNames()));
    }

    @GetMapping("/random")
    public ResponseEntity<GameSearchResponse> getRandomGames(
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int limit) {
        return ResponseEntity.ok(gameService.getRandomGames(limit));
    }

    @GetMapping("/random-quality")
    public ResponseEntity<GameSearchResponse> getRandomQualityByGenre(
            @RequestParam String genre,
            @RequestParam(defaultValue = "7.0") java.math.BigDecimal minRating,
            @RequestParam(defaultValue = "10") @Min(0) int minVotes,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return ResponseEntity.ok(gameService.getRandomQualityByGenre(genre, minRating, minVotes, limit));
    }

    @GetMapping("/genres")
    public ResponseEntity<GenresResponse> getGenres() {
        return ResponseEntity.ok(new GenresResponse(gameService.getGenres()));
    }

    @GetMapping("/tags/popular")
    public ResponseEntity<PopularTagsResponse> getPopularTags(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(new PopularTagsResponse(gameService.getPopularTags(limit)));
    }

    @GetMapping("/platforms")
    public ResponseEntity<PlatformsResponse> getPlatforms() {
        return ResponseEntity.ok(gameService.getPlatformGroups());
    }

    @GetMapping("/by-franchise/{name}")
    public ResponseEntity<List<GameResponse>> getByFranchise(
            @PathVariable String name,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Min(1) Integer excludeIgdbId) {
        return ResponseEntity.ok(gameService.getByFranchise(name, limit, excludeIgdbId));
    }

    @GetMapping("/by-collection/{name}")
    public ResponseEntity<List<GameResponse>> getByCollection(
            @PathVariable String name,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Min(1) Integer excludeIgdbId) {
        return ResponseEntity.ok(gameService.getByCollection(name, limit, excludeIgdbId));
    }

    @GetMapping("/by-developer/{name}")
    public ResponseEntity<List<GameResponse>> getByDeveloper(
            @PathVariable String name,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @Min(1) Integer excludeIgdbId) {
        return ResponseEntity.ok(gameService.getByDeveloper(name, limit, excludeIgdbId));
    }

    @GetMapping("/{igdbId}/editions")
    public ResponseEntity<List<GameResponse>> getEditionsOf(@PathVariable @Min(1) Integer igdbId) {
        return ResponseEntity.ok(gameService.getEditionsOf(igdbId));
    }

    // Pre-computed similar games (catalog-side). Reads top-K from game_similarities then
    // hydrates via the games table in one round-trip. Replaces rec-service's old genre-overlap
    // search loop for /similar; that path stays in rec-service only for /because-you-liked
    // which composes similar + library exclusion.
    @GetMapping("/{igdbId}/similar")
    public ResponseEntity<List<GameResponse>> getSimilar(@PathVariable @Min(1) Integer igdbId,
                                                         @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(gameService.getSimilarGames(igdbId, limit));
    }
}
