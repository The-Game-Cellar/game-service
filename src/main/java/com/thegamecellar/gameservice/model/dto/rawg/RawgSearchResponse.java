package com.thegamecellar.gameservice.model.dto.rawg;

import lombok.Data;

import java.util.List;

@Data
public class RawgSearchResponse {
    private Integer count;
    private List<RawgGameDto> results;
}