package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.model.dto.GenresResponse;
import com.thegamecellar.gameservice.model.dto.PlatformsResponse;
import com.thegamecellar.gameservice.model.dto.PopularTagsResponse;
import com.thegamecellar.gameservice.service.GameService;
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
            @RequestParam(defaultValue = "false") boolean dbOnly) {
        return ResponseEntity.ok(gameService.searchGames(query, platform, genre, ordering, page, pageSize, dbOnly, gameType, gameMode, perspective));
    }

    @GetMapping("/popular")
    public ResponseEntity<GameSearchResponse> getPopularGames(
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page) {
        return ResponseEntity.ok(gameService.getPopularGames(platform, page));
    }

    private static final int EXCLUDE_IDS_MAX = 100;

    @GetMapping("/upcoming")
    public ResponseEntity<GameSearchResponse> getUpcomingGames(
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "90") @Min(0) @Max(3650) int windowDays,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String excludeIds) {
        List<String> platforms = (platform == null || platform.isBlank())
                ? List.of()
                : List.of(platform.split(","));
        java.util.Set<Integer> exclude = java.util.Set.of();
        if (excludeIds != null && !excludeIds.isBlank()) {
            String[] parts = excludeIds.split(",");
            if (parts.length > EXCLUDE_IDS_MAX) {
                throw new IllegalArgumentException(
                        "excludeIds exceeds maximum of " + EXCLUDE_IDS_MAX + " entries");
            }
            exclude = java.util.Arrays.stream(parts)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return ResponseEntity.ok(gameService.getUpcomingGames(platforms, windowDays, limit, exclude));
    }

    @GetMapping("/upcoming/platforms")
    public ResponseEntity<Map<String, List<String>>> getUpcomingPlatforms() {
        return ResponseEntity.ok(Map.of("platforms", gameService.getUpcomingPlatformNames()));
    }

    @GetMapping("/random")
    public ResponseEntity<GameSearchResponse> getRandomGames(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
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

    @GetMapping("/{igdbId}/editions")
    public ResponseEntity<List<GameResponse>> getEditionsOf(@PathVariable @Min(1) Integer igdbId) {
        return ResponseEntity.ok(gameService.getEditionsOf(igdbId));
    }
}
