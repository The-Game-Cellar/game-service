package com.thegamecellar.gameservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class GameResponse {
    private Integer igdbId;
    private String name;
    private String description;
    private BigDecimal rating;
    private String backgroundImage;
    private String coverImageUrl;
    private String released;
    private String esrbRating;
    private List<String> genres;
    private List<String> platforms;
    private List<String> developers;
    private List<String> tags;
    private List<String> themes;
    private List<String> moods;
}
