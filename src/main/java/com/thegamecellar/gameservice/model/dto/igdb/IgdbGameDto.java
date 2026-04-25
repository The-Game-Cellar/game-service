package com.thegamecellar.gameservice.model.dto.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class IgdbGameDto {

    private Integer id;
    private String name;
    private String summary;

    @JsonProperty("aggregated_rating")
    private Double aggregatedRating;

    @JsonProperty("aggregated_rating_count")
    private Integer aggregatedRatingCount;

    @JsonProperty("first_release_date")
    private Long firstReleaseDate;

    private IgdbCoverDto cover;
    private List<IgdbNamedEntityDto> genres;
    private List<IgdbNamedEntityDto> platforms;
    private List<IgdbNamedEntityDto> themes;
    private List<IgdbNamedEntityDto> keywords;

    @JsonProperty("involved_companies")
    private List<IgdbInvolvedCompanyDto> involvedCompanies;
}
