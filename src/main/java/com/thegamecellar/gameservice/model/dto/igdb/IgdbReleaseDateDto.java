package com.thegamecellar.gameservice.model.dto.igdb;

import lombok.Data;

@Data
public class IgdbReleaseDateDto {
    private Integer id;

    /** Epoch seconds. */
    private Long date;

    /** Pre-formatted string from IGDB e.g. {@code "Mar 15, 2024"}. */
    private String human;

    private IgdbNamedEntityDto platform;
}
