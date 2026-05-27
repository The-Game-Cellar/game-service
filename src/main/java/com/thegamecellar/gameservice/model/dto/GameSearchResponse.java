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
    // Per-tag match-count over the same filter spec; lets the Tags dropdown gray out tags whose
    // selection would yield zero results. Null when not computed (non-DB paths, IGDB fallback).
    private Map<String, Long> availableTagCounts;
    // Same idea for single-pick dropdowns. Each count uses spec WITHOUT that dropdown's predicate
    // so the value = "om jag valde X istället för current". Stable under switching, not additive.
    private Map<String, Long> availableGenreCounts;
    private Map<String, Long> availableGameModeCounts;
    private Map<String, Long> availablePerspectiveCounts;
}