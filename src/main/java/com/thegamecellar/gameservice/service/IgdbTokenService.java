package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Slf4j
@Service
public class IgdbTokenService {

    private static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    private static final long REFRESH_BUFFER_SECONDS = 3600;

    private final RestTemplate restTemplate;
    private final String clientId;
    private final String clientSecret;

    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public IgdbTokenService(@Qualifier("igdbRestTemplate") RestTemplate restTemplate,
                            @Value("${igdb.client-id}") String clientId,
                            @Value("${igdb.client-secret}") String clientSecret) {
        this.restTemplate = restTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getToken() {
        if (needsRefresh()) {
            refresh();
        }
        return accessToken;
    }

    public String getClientId() {
        return clientId;
    }

    private boolean needsRefresh() {
        return accessToken == null || Instant.now().isAfter(expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS));
    }

    private synchronized void refresh() {
        if (!needsRefresh()) return;
        // Credentials in form body (not URL): query-string secrets leak into access logs / RFC 6749 §2.3.1.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "client_credentials");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        try {
            IgdbTokenResponse response = restTemplate.postForObject(TOKEN_URL, request, IgdbTokenResponse.class);
            if (response == null || response.getAccessToken() == null) {
                throw new IgdbApiException("Twitch token response was null or missing access_token");
            }
            accessToken = response.getAccessToken();
            expiresAt = Instant.now().plusSeconds(response.getExpiresIn());
            log.info("IGDB access token refreshed, expires in {}s", response.getExpiresIn());
        } catch (IgdbApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IgdbApiException("Failed to fetch IGDB access token from Twitch", e);
        }
    }
}
