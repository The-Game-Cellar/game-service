package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IgdbTokenServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private IgdbTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new IgdbTokenService(restTemplate, "test-client-id", "test-secret");
    }

    @Test
    void getToken_fetchesTokenOnFirstCall() {
        IgdbTokenResponse response = tokenResponse("token-abc", 5_000_000L);
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbTokenResponse.class))).thenReturn(response);

        String token = tokenService.getToken();

        assertThat(token).isEqualTo("token-abc");
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(IgdbTokenResponse.class));
    }

    @Test
    void getToken_cachesToken_doesNotRefetchWithinExpiry() {
        IgdbTokenResponse response = tokenResponse("token-abc", 5_000_000L);
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbTokenResponse.class))).thenReturn(response);

        tokenService.getToken();
        tokenService.getToken();

        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(IgdbTokenResponse.class));
    }

    @Test
    void getToken_throwsIgdbApiException_whenResponseIsNull() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbTokenResponse.class))).thenReturn(null);

        assertThatThrownBy(() -> tokenService.getToken())
                .isInstanceOf(IgdbApiException.class)
                .hasMessageContaining("null");
    }

    @Test
    void getToken_throwsIgdbApiException_whenAccessTokenMissing() {
        IgdbTokenResponse response = new IgdbTokenResponse();
        response.setExpiresIn(5_000_000L);
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbTokenResponse.class))).thenReturn(response);

        assertThatThrownBy(() -> tokenService.getToken())
                .isInstanceOf(IgdbApiException.class);
    }

    @Test
    void getToken_throwsIgdbApiException_onHttpError() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbTokenResponse.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> tokenService.getToken())
                .isInstanceOf(IgdbApiException.class)
                .hasMessageContaining("Failed to fetch IGDB access token");
    }

    @Test
    void getClientId_returnsConfiguredClientId() {
        assertThat(tokenService.getClientId()).isEqualTo("test-client-id");
    }

    private IgdbTokenResponse tokenResponse(String token, long expiresIn) {
        IgdbTokenResponse r = new IgdbTokenResponse();
        r.setAccessToken(token);
        r.setExpiresIn(expiresIn);
        r.setTokenType("bearer");
        return r;
    }
}
