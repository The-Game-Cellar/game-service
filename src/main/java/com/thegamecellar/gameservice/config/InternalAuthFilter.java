package com.thegamecellar.gameservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

// Mirror of library-service's filter. Worker compute path (rec-service) reaches
// /internal/games/** without a user JWT, authenticated only by the shared secret.
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final String expectedToken;

    public InternalAuthFilter(@Value("${security.internal.token:}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }
        if (expectedToken == null || expectedToken.isBlank()) {
            log.warn("INTERNAL_SERVICE_TOKEN unset; rejecting /internal/** request");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "internal token not configured");
            return;
        }
        String supplied = request.getHeader(HEADER);
        if (supplied == null || !constantTimeEquals(supplied, expectedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid internal token");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ab, bb);
    }
}
