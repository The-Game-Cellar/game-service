package com.thegamecellar.gameservice.util;

// PlatformCuration.load() is package-private, matching the other resource loaders. Tests outside this
// package go through here rather than widening the production API.
public final class TestPlatformCuration {

    private TestPlatformCuration() {}

    public static PlatformCuration loaded() {
        PlatformCuration curation = new PlatformCuration();
        curation.load();
        return curation;
    }
}
