package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.service.GameService;
import com.thegamecellar.gameservice.util.MoodMapper;
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
    public ResponseEntity<GameResponse> getGameById(@PathVariable Integer igdbId) {
        return ResponseEntity.ok(gameService.getGameById(igdbId));
    }

    @GetMapping("/search")
    public ResponseEntity<GameSearchResponse> searchGames(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String mood,
            @RequestParam(defaultValue = "-rating") @Pattern(regexp = "-rating|-released|released|name|-name") String ordering,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        boolean moodOnly = mood != null && !mood.isBlank()
                && (query == null || query.isBlank())
                && (platform == null || platform.isBlank())
                && (genre == null || genre.isBlank());
        if (moodOnly) {
            return ResponseEntity.ok(gameService.searchByMood(mood, page, pageSize));
        }
        return ResponseEntity.ok(gameService.searchGames(query, platform, genre, ordering, page, pageSize));
    }

    @GetMapping("/moods")
    public ResponseEntity<Map<String, List<String>>> getMoods() {
        return ResponseEntity.ok(Map.of("moods", MoodMapper.getAllMoods()));
    }

    @GetMapping("/popular")
    public ResponseEntity<GameSearchResponse> getPopularGames(
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(gameService.getPopularGames(platform, page));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<GameSearchResponse> getUpcomingGames(
            @RequestParam(required = false) String platform) {
        return ResponseEntity.ok(gameService.getUpcomingGames(platform));
    }

    @GetMapping("/random")
    public ResponseEntity<GameSearchResponse> getRandomGames(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(gameService.getRandomGames(limit));
    }

    @GetMapping("/genres")
    public ResponseEntity<Map<String, List<String>>> getGenres() {
        return ResponseEntity.ok(Map.of("genres", gameService.getGenres()));
    }

    @GetMapping("/platforms")
    public ResponseEntity<Map<String, List<String>>> getPlatforms() {
        return ResponseEntity.ok(Map.of("platforms", gameService.getPlatforms()));
    }
}
