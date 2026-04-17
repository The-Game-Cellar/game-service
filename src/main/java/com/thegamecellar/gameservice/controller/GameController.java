package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.dto.GameResponse;
import com.thegamecellar.gameservice.model.dto.GameSearchResponse;
import com.thegamecellar.gameservice.service.GameService;
import com.thegamecellar.gameservice.util.MoodMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @GetMapping("/{rawgId}")
    public ResponseEntity<GameResponse> getGameById(@PathVariable Integer rawgId) {
        return ResponseEntity.ok(gameService.getGameById(rawgId));
    }

    @GetMapping("/search")
    public ResponseEntity<GameSearchResponse> searchGames(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String mood,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (mood != null && !mood.isBlank()) {
            return ResponseEntity.ok(gameService.searchByMood(mood, page, pageSize));
        }
        return ResponseEntity.ok(gameService.searchGames(query, platform, genre, page, pageSize));
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
            @RequestParam(defaultValue = "20") int limit) {
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
