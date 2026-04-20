package com.thegamecellar.gameservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
public class RawgKeyInterceptor implements ClientHttpRequestInterceptor {

    private final String apiKey;

    public RawgKeyInterceptor(@Value("${rawg.api.key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        URI original = request.getURI();
        String encodedKey = UriUtils.encodeQueryParam(apiKey, StandardCharsets.UTF_8);
        String separator = original.getRawQuery() == null ? "?" : "&";
        URI modified = URI.create(original + separator + "key=" + encodedKey);

        return execution.execute(new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return modified;
            }
        }, body);
    }
}
