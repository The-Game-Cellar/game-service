package com.thegamecellar.gameservice.model.dto.rawg;

import lombok.Data;

import java.util.List;

// Used for /genres and /platforms endpoints
@Data
public class RawgListResponse {
    private Integer count;
    private List<RawgNamedEntity> results;
}
