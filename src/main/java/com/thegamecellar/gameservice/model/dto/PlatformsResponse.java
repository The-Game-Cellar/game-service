package com.thegamecellar.gameservice.model.dto;

import java.util.List;

/**
 * Hierarchical platform list returned by GET /api/v1/games/platforms.
 * <p>
 * Frontend dropdown pins the Big-4 ({@code PlayStation, PC, Nintendo, Xbox}) at the top
 * — those four cover the platforms ~95% of users actually own. The rest of IGDB's catalog
 * (NES, GameCube, Atari, arcade boards, handhelds, etc.) is collapsed under a single
 * umbrella per family or listed alphabetically under {@code others}.
 * <p>
 * Picking an umbrella label filters search to ANY of its children (OR-match across the
 * platform predicate); picking a child filters to that child only.
 */
public record PlatformsResponse(List<PlatformGroup> groups, List<String> others) {

    /**
     * @param label     dropdown label shown to user — e.g. {@code "PlayStation"}
     * @param platforms canonical IGDB platform names this group expands to
     * @param umbrella  {@code true} = expandable umbrella (PlayStation, Nintendo, Xbox);
     *                  {@code false} = single-leaf pinned entry (PC)
     */
    public record PlatformGroup(String label, List<String> platforms, boolean umbrella) {}
}
