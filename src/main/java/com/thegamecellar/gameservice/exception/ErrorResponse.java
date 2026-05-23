package com.thegamecellar.gameservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.time.Instant;

/**
 * Standardized error response shape used across all services.
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code error}: short human-readable message</li>
 *   <li>{@code status}: HTTP status code</li>
 *   <li>{@code timestamp}: ISO-8601 instant the error was emitted</li>
 *   <li>{@code path}: original request URI (or {@code null} if unknown)</li>
 *   <li>{@code requestId}: value of MDC {@code requestId} for log correlation</li>
 * </ul>
 */
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
