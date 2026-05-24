package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.dto.PlatformsResponse;
import com.thegamecellar.gameservice.model.dto.PlatformsResponse.PlatformGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Partitions IGDB platforms into Big-4 umbrellas + alphabetical "others" tail. Pin order PC -> PS -> Nintendo -> Xbox.
public final class PlatformGroups {

    private PlatformGroups() {}

    // Names MUST match IgdbPlatformMapper.NAME_NORMALIZATION; unmatched entries silently drop from the umbrella.
    private static final List<String> PLAYSTATION = List.of(
            "PlayStation", "PlayStation 2", "PlayStation 3", "PlayStation 4", "PlayStation 5",
            "PlayStation Portable", "PlayStation Vita");

    private static final List<String> PC = List.of("PC");

    private static final List<String> NINTENDO = List.of(
            "Nintendo Entertainment System", "Super Nintendo Entertainment System",
            "Nintendo 64", "Nintendo GameCube",
            "Wii", "Wii U", "Nintendo Switch",
            "Game Boy", "Game Boy Advance",
            "Nintendo DS", "Nintendo 3DS");

    private static final List<String> XBOX = List.of(
            "Xbox", "Xbox 360", "Xbox One", "Xbox Series X|S");

    public static PlatformsResponse group(List<String> available) {
        Set<String> taken = new HashSet<>();
        List<PlatformGroup> groups = new ArrayList<>();

        addGroup(groups, taken, "PC", PC, available, false);
        addGroup(groups, taken, "PlayStation", PLAYSTATION, available, true);
        addGroup(groups, taken, "Nintendo", NINTENDO, available, true);
        addGroup(groups, taken, "Xbox", XBOX, available, true);

        List<String> others = available.stream()
                .filter(p -> !taken.contains(p))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return new PlatformsResponse(groups, others);
    }

    private static void addGroup(List<PlatformGroup> groups, Set<String> taken, String label,
                                  List<String> canonical, List<String> available, boolean umbrella) {
        Set<String> avail = new HashSet<>(available);
        List<String> matched = canonical.stream().filter(avail::contains).toList();
        if (matched.isEmpty()) return;
        groups.add(new PlatformGroup(label, matched, umbrella));
        taken.addAll(matched);
    }
}
