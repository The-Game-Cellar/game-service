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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class IgdbApiClient {

    private static final String IGDB_HOST = "https://api.igdb.com";
    private static final String FIELDS_GAME =
            "fields id,name,summary,aggregated_rating,aggregated_rating_count,first_release_date," +
            "cover.image_id,genres.name,platforms.name,themes.name,keywords.name;";
    private static final String FIELDS_GAME_ENRICHED =
            "fields id,name,summary,aggregated_rating,aggregated_rating_count,first_release_date," +
            "cover.image_id,genres.name,platforms.name,themes.name,keywords.name," +
            "involved_companies.company.name,involved_companies.developer;";

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

    public IgdbGameDto fetchGameByIdForEnrichment(int igdbId) {
        String query = FIELDS_GAME_ENRICHED + " where id = " + igdbId + "; limit 1;";
        List<IgdbGameDto> results = queryGames(query);
        if (results.isEmpty()) throw new GameNotFoundException(igdbId);
        return results.get(0);
    }

    public List<IgdbGameDto> fetchNewReleases(int limit, int offset) {
        long now = Instant.now().getEpochSecond();
        String query = FIELDS_GAME +
                " where first_release_date != null & first_release_date < " + now + ";" +
                " sort first_release_date desc;" +
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

    public List<IgdbGameDto> fetchPopularGames(String platform, int limit, int offset) {
        StringBuilder query = new StringBuilder(FIELDS_GAME);
        query.append(" sort aggregated_rating_count desc;");
        query.append(" where aggregated_rating_count > 5");
        IgdbPlatformMapper.getIgdbId(platform)
                .ifPresent(id -> query.append(" & platforms = (").append(id).append(")"));
        query.append("; limit ").append(limit).append("; offset ").append(offset).append(";");
        return queryGames(query.toString());
    }

    public List<IgdbGameDto> fetchUpcomingGames(String platform, int limit) {
        long now = Instant.now().getEpochSecond();
        long oneYearLater = now + 365L * 24 * 3600;
        StringBuilder query = new StringBuilder(FIELDS_GAME);
        query.append(" where first_release_date > ").append(now)
             .append(" & first_release_date < ").append(oneYearLater);
        IgdbPlatformMapper.getIgdbId(platform)
                .ifPresent(id -> query.append(" & platforms = (").append(id).append(")"));
        query.append("; sort first_release_date asc; limit ").append(limit).append(";");
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
        if (!url.startsWith(IGDB_HOST)) {
            throw new IgdbApiException("Request blocked: host not in allowlist: " + url);
        }
        return url;
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\"", "").replace(";", "").replace("\\", "");
    }
}
