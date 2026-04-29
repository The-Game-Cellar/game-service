package com.thegamecellar.gameservice.service;

public record CatalogSyncResult(int fetched, int cached) {
    public static CatalogSyncResult empty() {
        return new CatalogSyncResult(0, 0);
    }
}
