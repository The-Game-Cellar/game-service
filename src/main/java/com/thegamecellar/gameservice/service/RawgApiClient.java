package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.GameNotFoundException;
import com.thegamecellar.gameservice.exception.RawgApiException;
import com.thegamecellar.gameservice.model.dto.rawg.RawgGameDto;
import com.thegamecellar.gameservice.model.dto.rawg.RawgListResponse;
import com.thegamecellar.gameservice.model.dto.rawg.RawgSearchResponse;
import com.thegamecellar.gameservice.util.PlatformIdMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

@Slf4j
@Service
public class RawgApiClient {

    private final RestTemplate restTemplate;

    @Value("${rawg.api.key}")
    private String apiKey;

    @Value("${rawg.api.base-url}")
    private String baseUrl;

    public RawgApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public RawgGameDto fetchGameById(Integer rawgId) {
        String url = UriComponentsBuilder
                .fromUriString(baseUrl + "/games/{id}")
                .queryParam("key", apiKey)
                .buildAndExpand(rawgId)
                .toUriString();

        log.info("Fetching game from RAWG API: id={}", rawgId);
        try {
            return restTemplate.getForObject(url, RawgGameDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new GameNotFoundException(rawgId);
        } catch (Exception e) {
            throw new RawgApiException("Failed to fetch game from RAWG API", e);
        }
    }

    public RawgSearchResponse searchGames(String query, String platform, String genre, int page, int pageSize) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl + "/games")
                .queryParam("key", apiKey)
                .queryParam("page", page + 1) // RAWG pages are 1-indexed
                .queryParam("page_size", pageSize);

        if (query != null && !query.isBlank()) {
            builder.queryParam("search", query);
        }
        if (platform != null && !platform.isBlank()) {
            PlatformIdMapper.getRawgId(platform).ifPresent(id -> builder.queryParam("platforms", id));
        }
        if (genre != null && !genre.isBlank()) {
            builder.queryParam("genres", genre.toLowerCase());
        }

        String url = builder.build().toUriString();
        log.info("Searching RAWG API: query={}, platform={}, genre={}", query, platform, genre);
        try {
            return restTemplate.getForObject(url, RawgSearchResponse.class);
        } catch (Exception e) {
            throw new RawgApiException("Failed to search games from RAWG API", e);
        }
    }

    public RawgSearchResponse fetchPopularGames(String platform, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl + "/games")
                .queryParam("key", apiKey)
                .queryParam("ordering", "-rating")
                .queryParam("page", page + 1)
                .queryParam("page_size", 20);

        if (platform != null && !platform.isBlank()) {
            PlatformIdMapper.getRawgId(platform).ifPresent(id -> builder.queryParam("platforms", id));
        }

        String url = builder.build().toUriString();
        log.info("Fetching popular games from RAWG API: platform={}", platform);
        try {
            return restTemplate.getForObject(url, RawgSearchResponse.class);
        } catch (Exception e) {
            throw new RawgApiException("Failed to fetch popular games from RAWG API", e);
        }
    }

    public RawgSearchResponse fetchUpcomingGames(String platform) {
        String today = LocalDate.now().toString();
        String oneYearLater = LocalDate.now().plusYears(1).toString();

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl + "/games")
                .queryParam("key", apiKey)
                .queryParam("dates", today + "," + oneYearLater)
                .queryParam("ordering", "-added")
                .queryParam("page_size", 20);

        if (platform != null && !platform.isBlank()) {
            PlatformIdMapper.getRawgId(platform).ifPresent(id -> builder.queryParam("platforms", id));
        }

        String url = builder.build().toUriString();
        log.info("Fetching upcoming games from RAWG API: platform={}", platform);
        try {
            return restTemplate.getForObject(url, RawgSearchResponse.class);
        } catch (Exception e) {
            throw new RawgApiException("Failed to fetch upcoming games from RAWG API", e);
        }
    }

    public RawgListResponse fetchGenres() {
        String url = UriComponentsBuilder
                .fromUriString(baseUrl + "/genres")
                .queryParam("key", apiKey)
                .build().toUriString();
        try {
            return restTemplate.getForObject(url, RawgListResponse.class);
        } catch (Exception e) {
            throw new RawgApiException("Failed to fetch genres from RAWG API", e);
        }
    }

    public RawgListResponse fetchPlatforms() {
        String url = UriComponentsBuilder
                .fromUriString(baseUrl + "/platforms/lists/parents")
                .queryParam("key", apiKey)
                .build().toUriString();
        try {
            return restTemplate.getForObject(url, RawgListResponse.class);
        } catch (Exception e) {
            throw new RawgApiException("Failed to fetch platforms from RAWG API", e);
        }
    }
}
