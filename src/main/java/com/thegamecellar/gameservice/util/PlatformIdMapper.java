package com.thegamecellar.gameservice.util;

import java.util.Map;
import java.util.Optional;

public class PlatformIdMapper {

    // Maps display names to RAWG platform IDs
    private static final Map<String, Integer> PLATFORM_IDS = Map.of(
            "pc", 4,
            "playstation 5", 187,
            "ps5", 187,
            "playstation 4", 18,
            "ps4", 18,
            "xbox series s/x", 186,
            "xbox series", 186,
            "xbox one", 1,
            "nintendo switch", 7,
            "switch", 7
    );

    public static Optional<Integer> getRawgId(String platformName) {
        if (platformName == null) return Optional.empty();
        return Optional.ofNullable(PLATFORM_IDS.get(platformName.toLowerCase()));
    }
}
