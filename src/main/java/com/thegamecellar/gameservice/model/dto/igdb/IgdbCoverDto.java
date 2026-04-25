package com.thegamecellar.gameservice.model.dto.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IgdbCoverDto {
    private Integer id;

    @JsonProperty("image_id")
    private String imageId;

    public String toUrl() {
        if (imageId == null) return null;
        return "https://images.igdb.com/igdb/image/upload/t_cover_big/" + imageId + ".jpg";
    }
}
