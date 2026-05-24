package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.GameNotFoundException;
import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbNamedEntityDto;
import com.thegamecellar.gameservice.util.IgdbPlatformMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class IgdbApiClient {

    private static final String IGDB_HOST = "api.igdb.com";
    private static final String FIELDS_GAME =
            "fields id,name,summary,storyline,aggregated_rating,aggregated_rating_count," +
            "total_rating,total_rating_count,first_release_date,game_type,hypes," +
            "parent_game.id,parent_game.name," +
            "cover.image_id,genres.name,platforms.name,themes.name,keywords.name," +
            "game_modes.name,player_perspectives.name,franchises.name,collections.name," +
            "involved_companies.company.name,involved_companies.developer," +
            "screenshots.image_id,videos.video_id,videos.name," +
            "dlcs,expansions,similar_games," +
            "age_ratings.category,age_ratings.rating," +
            "release_dates.date,release_dates.human,release_dates.platform.name," +
            "multiplayer_modes.platform.name,multiplayer_modes.onlinemax,multiplayer_modes.offlinemax," +
            "multiplayer_modes.onlinecoopmax,multiplayer_modes.offlinecoopmax," +
            "multiplayer_modes.lancoop,multiplayer_modes.splitscreen," +
            "multiplayer_modes.campaigncoop,multiplayer_modes.dropin;";

    private final RestTemplate restTemplate;
    private final IgdbTokenService tokenService;
    private final String baseUrl;

    public IgdbApiClient(@Qualifier("igdbRestTemplate") RestTemplate restTemplate,
                         IgdbTokenService tokenService,
                         @Value("${igdb.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.tokenService = tokenService;
        this.baseUrl = baseUrl;
    }

    public IgdbGameDto fetchGameById(int igdbId) {
        String query = FIELDS_GAME + " where id = " + igdbId + "; limit 1;";
        List<IgdbGameDto> results = queryGames(query);
        if (results.isEmpty()) throw new GameNotFoundException(igdbId);
        return results.get(0);
    }

    // Cap caller list at 500 (IGDB single-query max). Rate-limit is per-call so batching turns hours into minutes on backfills.
    public List<IgdbGameDto> fetchGamesByIds(List<Integer> igdbIds) {
        if (igdbIds == null || igdbIds.isEmpty()) return List.of();
        String idList = igdbIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String query = FIELDS_GAME + " where id = (" + idList + "); limit " + igdbIds.size() + ";";
        return queryGames(query);
    }

    public List<IgdbGameDto> fetchNewReleases(int limit, int offset) {
        long now = Instant.now().getEpochSecond();
        String query = FIELDS_GAME +
                " where first_release_date != null & first_release_date < " + now + ";" +
                " sort first_release_date desc;" +
                " limit " + limit + "; offset " + offset + ";";
        return queryGames(query);
    }

    public List<IgdbGameDto> fetchUpcomingReleases(int limit, int offset) {
        long now = Instant.now().getEpochSecond();
        String query = FIELDS_GAME +
                " where first_release_date != null & first_release_date > " + now + ";" +
                " sort first_release_date asc;" +
                " limit " + limit + "; offset " + offset + ";";
        return queryGames(query);
    }

    public List<IgdbGameDto> searchGames(String searchQuery, int limit, int offset) {
        String query = FIELDS_GAME +
                " search \"" + sanitize(searchQuery) + "\";" +
                " limit " + limit + "; offset " + offset + ";";
        return queryGames(query);
    }

    public List<IgdbGameDto> searchByGenre(String genre, int limit, int offset) {
        String query = FIELDS_GAME +
                " where genres.name = \"" + sanitize(genre) + "\";" +
                " sort aggregated_rating_count desc;" +
                " limit " + limit + "; offset " + offset + ";";
        return queryGames(query);
    }

    public List<IgdbGameDto> searchByGenreAndPlatform(String genre, String platform, int limit, int offset) {
        StringBuilder query = new StringBuilder(FIELDS_GAME);
        query.append(" where genres.name = \"").append(sanitize(genre)).append("\"");
        IgdbPlatformMapper.getIgdbId(platform)
                .ifPresent(id -> query.append(" & platforms = (").append(id).append(")"));
        query.append(";");
        query.append(" sort aggregated_rating_count desc;");
        query.append(" limit ").append(limit).append("; offset ").append(offset).append(";");
        return queryGames(query.toString());
    }

    public List<IgdbGameDto> fetchPopularGames(String platform, int limit, int offset) {
        StringBuilder query = new StringBuilder(FIELDS_GAME);
        query.append(" sort aggregated_rating_count desc;");
        query.append(" where aggregated_rating_count > 5");
        IgdbPlatformMapper.getIgdbId(platform)
                .ifPresent(id -> query.append(" & platforms = (").append(id).append(")"));
        query.append("; limit ").append(limit).append("; offset ").append(offset).append(";");
        return queryGames(query.toString());
    }

    public List<IgdbGameDto> fetchCatalogPage(int limit, int offset) {
        String query = FIELDS_GAME +
                " sort aggregated_rating_count desc;" +
                " limit " + limit + "; offset " + offset + ";";
        return queryGames(query);
    }

    public List<IgdbNamedEntityDto> fetchGenres() {
        return queryNamedEntities("fields id,name; limit 500;", "/genres");
    }

    public List<IgdbNamedEntityDto> fetchThemes() {
        return queryNamedEntities("fields id,name; limit 500;", "/themes");
    }

    private List<IgdbGameDto> queryGames(String apicalypse) {
        String url = endpoint("/games");
        try {
            IgdbGameDto[] response = restTemplate.postForObject(url, request(apicalypse), IgdbGameDto[].class);
            return response != null ? Arrays.asList(response) : List.of();
        } catch (Exception e) {
            throw new IgdbApiException("IGDB /games query failed: " + e.getMessage(), e);
        }
    }

    private List<IgdbNamedEntityDto> queryNamedEntities(String apicalypse, String path) {
        String url = endpoint(path);
        try {
            IgdbNamedEntityDto[] response = restTemplate.postForObject(url, request(apicalypse), IgdbNamedEntityDto[].class);
            return response != null ? Arrays.asList(response) : List.of();
        } catch (Exception e) {
            throw new IgdbApiException("IGDB " + path + " query failed: " + e.getMessage(), e);
        }
    }

    private HttpEntity<String> request(String apicalypse) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Client-ID", tokenService.getClientId());
        headers.set("Authorization", "Bearer " + tokenService.getToken());
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new HttpEntity<>(apicalypse, headers);
    }

    private String endpoint(String path) {
        String url = baseUrl + path;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String scheme = uri.getScheme();
            if (host == null || !"https".equalsIgnoreCase(scheme) || !IGDB_HOST.equalsIgnoreCase(host)) {
                throw new IgdbApiException("Request blocked: host not in allowlist: " + url);
            }
        } catch (URISyntaxException e) {
            throw new IgdbApiException("Request blocked: invalid URL: " + url, e);
        }
        return url;
    }

    private static final int SANITIZE_MAX_LENGTH = 100;
    private static final java.util.regex.Pattern SANITIZE_ALLOWED =
            java.util.regex.Pattern.compile("[^\\p{L}\\p{Nd} \\-':.&/]");

    private static String sanitize(String input) {
        if (input == null) return "";
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC);
        String stripped = SANITIZE_ALLOWED.matcher(normalized).replaceAll("");
        if (stripped.length() > SANITIZE_MAX_LENGTH) {
            stripped = stripped.substring(0, SANITIZE_MAX_LENGTH);
        }
        return stripped.trim();
    }
}
