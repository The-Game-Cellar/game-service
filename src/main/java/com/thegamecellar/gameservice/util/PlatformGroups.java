package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.dto.PlatformsResponse;
import com.thegamecellar.gameservice.model.dto.PlatformsResponse.PlatformGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Partitions IGDB's flat platform list into the Big-4 umbrellas (PlayStation, PC, Nintendo,
 * Xbox) plus an alphabetically-sorted "others" tail. Names are matched against canonical
 * IGDB platform names. Entries the DB doesn't have are silently dropped from each
 * umbrella so the dropdown only shows generations the user could actually filter by.
 * <p>
 * Pin order: PC → PlayStation → Nintendo → Xbox. PC sits above the console
 * umbrellas so the leaf row appears before any expandable row, giving a smoother
 * scan from "All Platforms" down through the pinned tier.
 */
public final class PlatformGroups {

    private PlatformGroups() {}

    // Names below MUST match the post-normalization values stored in the DB
    // (see IgdbPlatformMapper.NAME_NORMALIZATION). Entries that never land in
    // the DB just get dropped from the umbrella, so listing the canonical
    // form is the load-bearing detail here.
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
