package com.thegamecellar.gameservice.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${igdb.connect-timeout:2000}")
    private int connectTimeout;

    @Value("${igdb.read-timeout:10000}")
    private int readTimeout;

    @Value("${igdb.worker.read-timeout:30000}")
    private int workerReadTimeout;

    @Bean
    @Qualifier("igdbRestTemplate")
    public RestTemplate igdbRestTemplate() {
        return withTimeouts(readTimeout);
    }

    // Deep-offset catalog pages take close to 10 s at IGDB; the request-path budget would drop them.
    @Bean
    @Qualifier("igdbWorkerRestTemplate")
    public RestTemplate igdbWorkerRestTemplate() {
        return withTimeouts(workerReadTimeout);
    }

    private RestTemplate withTimeouts(int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
