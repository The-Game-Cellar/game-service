package com.thegamecellar.gameservice.exception;

public class RawgApiException extends RuntimeException {
    public RawgApiException(String message) {
        super(message);
    }

    public RawgApiException(String message, Throwable cause) {
        super(message, cause);
    }
}