package com.thegamecellar.gameservice.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One game's member-rating aggregate as library-service computed it. Aggregates only:
 * nothing here identifies who rated what.
 */
public record CellarRatingUpdate(

        @NotNull @Min(1)
        Integer igdbGameId,

        @NotNull @DecimalMin("1.0") @DecimalMax("10.0")
        BigDecimal average,

        @NotNull @Min(1)
        Integer count
) {
}
