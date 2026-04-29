package com.thegamecellar.gameservice.util;

import java.util.Map;
import java.util.Optional;

public class IgdbPlatformMapper {

    private static final Map<String, Integer> PLATFORM_IDS = Map.ofEntries(
            Map.entry("PC",                                  6),
            Map.entry("Mac",                                 14),
            Map.entry("Linux",                               3),
            Map.entry("PlayStation 5",                       167),
            Map.entry("PlayStation 4",                       48),
            Map.entry("PlayStation 3",                       9),
            Map.entry("PlayStation 2",                       8),
            Map.entry("PlayStation",                         7),
            Map.entry("PlayStation Portable",                38),
            Map.entry("PlayStation Vita",                    46),
            Map.entry("Xbox Series X|S",                     169),
            Map.entry("Xbox One",                            49),
            Map.entry("Xbox 360",                            12),
            Map.entry("Xbox",                                11),
            Map.entry("Nintendo Switch",                     130),
            Map.entry("Wii U",                               41),
            Map.entry("Wii",                                 5),
            Map.entry("Nintendo GameCube",                   21),
            Map.entry("Nintendo 64",                         4),
            Map.entry("Super Nintendo Entertainment System", 19),
            Map.entry("Nintendo Entertainment System",       18),
            Map.entry("Nintendo 3DS",                        37),
            Map.entry("Nintendo DS",                         20),
            Map.entry("Game Boy Advance",                    24),
            Map.entry("Game Boy",                            33),
            Map.entry("Android",                             34),
            Map.entry("iOS",                                 39)
    );

    private static final Map<String, String> NAME_NORMALIZATION = Map.ofEntries(
            Map.entry("PC (Microsoft Windows)",         "PC"),
            Map.entry("PlayStation 5 Pro",              "PlayStation 5"),
            Map.entry("PlayStation 4 Pro",              "PlayStation 4"),
            Map.entry("New Nintendo 3DS",               "Nintendo 3DS"),
            Map.entry("Nintendo Switch Lite",           "Nintendo Switch"),
            Map.entry("Nintendo Switch OLED Model",     "Nintendo Switch")
    );

    public static String normalize(String igdbName) {
        if (igdbName == null) return null;
        return NAME_NORMALIZATION.getOrDefault(igdbName, igdbName);
    }

    public static Optional<Integer> getIgdbId(String platformName) {
        if (platformName == null) return Optional.empty();
        return Optional.ofNullable(PLATFORM_IDS.get(platformName));
    }

    private IgdbPlatformMapper() {}
}
