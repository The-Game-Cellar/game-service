package com.thegamecellar.gameservice.model.dto;

import java.util.List;

/**
 * Genre catalog returned by GET /api/v1/games/genres. Includes both IGDB-sourced and
 * derived (rule-based) genre names — the frontend treats them uniformly as filter values.
 */
public record GenresResponse(List<String> genres) {}
