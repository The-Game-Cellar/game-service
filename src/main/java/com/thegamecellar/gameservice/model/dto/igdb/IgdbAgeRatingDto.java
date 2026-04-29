package com.thegamecellar.gameservice.model.dto.igdb;

import lombok.Data;

@Data
public class IgdbAgeRatingDto {
    private Integer id;

    /** IGDB enum: 1=ESRB, 2=PEGI, 3=CERO, 4=USK, 5=GRAC, 6=ClassInd, 7=ACB. */
    private Integer category;

    /** IGDB enum within category — meaning depends on {@link #category}. */
    private Integer rating;
}
