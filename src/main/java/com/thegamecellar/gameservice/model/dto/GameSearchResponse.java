package com.thegamecellar.gameservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GameSearchResponse {
    private List<GameResponse> games;
    private Integer totalCount;
    private Integer page;
    private Integer pageSize;
    // Matching games per facet value, null when not computed (no user filter, IGDB fallback). Under match=any a
    // facet's own picks are left out of its counts, so a value shows what a click on it would add.
    private Map<String, Long> availableTagCounts;
    private Map<String, Long> availableGenreCounts;
    private Map<String, Long> availableGameModeCounts;
    private Map<String, Long> availablePerspectiveCounts;
}