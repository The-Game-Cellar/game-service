package com.thegamecellar.gameservice.model.dto.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IgdbVideoDto {
    private Integer id;
    private String name;

    /** YouTube video id ready for {@code https://www.youtube.com/embed/{video_id}}. */
    @JsonProperty("video_id")
    private String videoId;
}
