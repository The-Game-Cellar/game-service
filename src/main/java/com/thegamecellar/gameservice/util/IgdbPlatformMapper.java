package com.thegamecellar.gameservice.util;

import java.util.Map;
import java.util.Optional;

public class IgdbPlatformMapper {

    private static final Map<String, Integer> PLATFORM_IDS = Map.of(
            "PC",                 6,
            "PlayStation 5",     167,
            "PlayStation 4",     48,
            "Xbox Series S/X",   169,
            "Xbox One",          49,
            "Nintendo Switch",   130
    );

    public static Optional<Integer> getIgdbId(String platformName) {
        if (platformName == null) return Optional.empty();
        return Optional.ofNullable(PLATFORM_IDS.get(platformName));
    }

    private IgdbPlatformMapper() {}
}
