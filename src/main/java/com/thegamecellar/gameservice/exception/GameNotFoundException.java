package com.thegamecellar.gameservice.exception;

public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(Integer rawgId) {
        super("Game with RAWG ID " + rawgId + " not found");
    }
}