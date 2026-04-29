package com.thegamecellar.gameservice.model.dto.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IgdbScreenshotDto {
    private Integer id;

    @JsonProperty("image_id")
    private String imageId;
}
