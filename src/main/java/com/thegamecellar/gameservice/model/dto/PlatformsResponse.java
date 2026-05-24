package com.thegamecellar.gameservice.model.dto;

import java.util.List;

// Umbrella filter expands to ANY child (OR-match on platform predicate); single child filters to that child only.
public record PlatformsResponse(List<PlatformGroup> groups, List<String> others) {

    // umbrella=true expands to all children (PlayStation, Nintendo, Xbox); umbrella=false is a single pinned entry (PC).
    public record PlatformGroup(String label, List<String> platforms, boolean umbrella) {}
}
