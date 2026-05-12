package com.thegamecellar.gameservice.model.dto;

import java.util.List;

/**
 * Catalog tag list for the user-facing chip wall in the Preferences page. Names are returned
 * in descending order of occurrence count across the catalog, with curated junk filtered out
 * server-side (content warnings, vague descriptors, mechanic details, duplicates of existing
 * genre or visual-style chips). The list reflects live catalog state, so new gameplay-style
 * tags trending in IGDB surface automatically as the scheduled worker imports more games.
 */
public record PopularTagsResponse(List<String> tags) {}
