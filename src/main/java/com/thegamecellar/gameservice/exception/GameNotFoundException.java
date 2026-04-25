package com.thegamecellar.gameservice.exception;

public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(Integer igdbId) {
        super("Game with IGDB ID " + igdbId + " not found");
    }
}
