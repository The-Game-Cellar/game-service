package com.thegamecellar.gameservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.time.Instant;

// Shared error shape across services. requestId mirrors MDC requestId for cross-service log correlation.
public record ErrorResponse(
        String error,
        int status,
        String timestamp,
        String path,
        String requestId
) {
    public static ErrorResponse of(int status, String message, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : null;
        return new ErrorResponse(message, status, Instant.now().toString(), path, MDC.get("requestId"));
    }
}
