package com.thegamecellar.gameservice.model.dto.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IgdbArtworkDto {
    private Integer id;

    @JsonProperty("image_id")
    private String imageId;

    private Integer width;
    private Integer height;
}
