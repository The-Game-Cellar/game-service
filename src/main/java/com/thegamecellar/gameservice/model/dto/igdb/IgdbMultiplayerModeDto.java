package com.thegamecellar.gameservice.model.dto.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IgdbMultiplayerModeDto {
    private Integer id;

    private IgdbNamedEntityDto platform;

    @JsonProperty("onlinemax")
    private Integer onlineMax;

    @JsonProperty("offlinemax")
    private Integer offlineMax;

    @JsonProperty("onlinecoopmax")
    private Integer onlineCoopMax;

    @JsonProperty("offlinecoopmax")
    private Integer offlineCoopMax;

    @JsonProperty("lancoop")
    private Boolean lanCoop;

    @JsonProperty("splitscreen")
    private Boolean splitscreen;

    @JsonProperty("campaigncoop")
    private Boolean campaignCoop;

    @JsonProperty("dropin")
    private Boolean dropIn;
}
