package com.thegamecellar.gameservice.model.dto.igdb;

import lombok.Data;

@Data
public class IgdbInvolvedCompanyDto {
    private IgdbCompanyDto company;
    private boolean developer;
}
