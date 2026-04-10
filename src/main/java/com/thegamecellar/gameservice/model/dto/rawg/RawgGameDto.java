package com.thegamecellar.gameservice.model.dto.rawg;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RawgGameDto {

    private Integer id;
    private String name;
    private String released;
    private BigDecimal rating;

    @JsonProperty("background_image")
    private String backgroundImage;

    // Only present in detail endpoint (/games/{id})
    @JsonProperty("description_raw")
    private String descriptionRaw;

    @JsonProperty("esrb_rating")
    private RawgNamedEntity esrbRating;

    private List<RawgNamedEntity> genres;
    private List<RawgPlatformEntry> platforms;
    private List<RawgNamedEntity> developers;
    private List<RawgNamedEntity> tags;
}