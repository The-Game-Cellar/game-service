package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.dto.CellarRatingUpdate;
import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.service.CellarRatingService;
import com.thegamecellar.gameservice.service.GameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Worker compute path (rec-service) calls these without a user JWT. Two protection layers:
// api-gateway has no route for /internal/**, and InternalAuthFilter enforces the X-Internal-Token
// shared secret. Subset of /api/v1/games/* the worker needs: getById (similar-graph),
// popular (Tier-3), random-quality (Tier-1/2 candidates).
@Validated
@RestController
@RequestMapping("/internal/games")
@RequiredArgsConstructor
public class InternalGameController {

    private final GameService gameService;
    private final CellarRatingService cellarRatingService;

    @GetMapping("/{igdbId}")
    public ResponseEntity<GameResponse> getGameById(@PathVariable @Min(1) Integer igdbId) {
        return ResponseEntity.ok(gameService.getGameById(igdbId));
    }

    @GetMapping("/popular")
    public ResponseEntity<GameSearchResponse> getPopularGames(
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page) {
        return ResponseEntity.ok(gameService.getPopularGames(platform, page));
    }

    @GetMapping("/random-quality")
    public ResponseEntity<GameSearchResponse> getRandomQualityByGenre(
            @RequestParam String genre,
            @RequestParam(defaultValue = "7.0") java.math.BigDecimal minRating,
            @RequestParam(defaultValue = "10") @Min(0) int minVotes,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return ResponseEntity.ok(gameService.getRandomQualityByGenre(genre, minRating, minVotes, limit));
    }

    // Aggregates only: library-service owns the per-user ratings and never sends them.
    // One nightly pass arrives as several batches under one runId, closed by the prune below.
    @PostMapping("/ratings")
    public ResponseEntity<Map<String, Integer>> putCellarRatings(
            @RequestParam @NotBlank @Size(max = 36) String runId,
            @RequestBody @Valid @Size(max = 1000) List<@Valid CellarRatingUpdate> updates) {
        return ResponseEntity.ok(Map.of("stored", cellarRatingService.apply(runId, updates)));
    }

    @PostMapping("/ratings/prune")
    public ResponseEntity<Map<String, Integer>> pruneCellarRatings(
            @RequestParam @NotBlank @Size(max = 36) String runId) {
        return ResponseEntity.ok(Map.of("cleared", cellarRatingService.prune(runId)));
    }

}
