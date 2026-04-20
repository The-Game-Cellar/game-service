package com.thegamecellar.gameservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RawgKeyInterceptorTest {

    private final RawgKeyInterceptor interceptor = new RawgKeyInterceptor("test-api-key");

    @Test
    void appendsKeyWhenUriHasExistingQuery() throws Exception {
        URI original = URI.create("https://api.rawg.io/api/games?page=1&page_size=20");
        AtomicReference<URI> captured = new AtomicReference<>();

        interceptor.intercept(stubRequest(original), new byte[0], capturingExecution(captured));

        assertThat(captured.get().toString())
                .isEqualTo("https://api.rawg.io/api/games?page=1&page_size=20&key=test-api-key");
    }

    @Test
    void appendsKeyWhenUriHasNoQuery() throws Exception {
        URI original = URI.create("https://api.rawg.io/api/genres");
        AtomicReference<URI> captured = new AtomicReference<>();

        interceptor.intercept(stubRequest(original), new byte[0], capturingExecution(captured));

        assertThat(captured.get().toString())
                .isEqualTo("https://api.rawg.io/api/genres?key=test-api-key");
    }

    @Test
    void encodesSpecialCharactersInKey() throws Exception {
        RawgKeyInterceptor specialInterceptor = new RawgKeyInterceptor("key with spaces&and=special");
        URI original = URI.create("https://api.rawg.io/api/games");
        AtomicReference<URI> captured = new AtomicReference<>();

        specialInterceptor.intercept(stubRequest(original), new byte[0], capturingExecution(captured));

        assertThat(captured.get().toString())
                .doesNotContain(" ")
                .doesNotContain("&and=special")
                .contains("key=key%20with%20spaces%26and%3Dspecial");
    }

    private static HttpRequest stubRequest(URI uri) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        return request;
    }

    private static ClientHttpRequestExecution capturingExecution(AtomicReference<URI> captured) {
        return (req, body) -> {
            captured.set(req.getURI());
            return mock(ClientHttpResponse.class);
        };
    }
}
