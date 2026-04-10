package com.thegamecellar.gameservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GameSearchResponse {
    private List<GameResponse> games;
    private Integer totalCount;
    private Integer page;
    private Integer pageSize;
}