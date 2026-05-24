package com.thegamecellar.gameservice.model.dto;

import java.util.List;

// Mixed IGDB + DERIVED genres; frontend treats them uniformly as filter values.
public record GenresResponse(List<String> genres) {}
