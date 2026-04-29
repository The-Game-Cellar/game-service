package com.thegamecellar.gameservice.model.dto.igdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class IgdbGameDto {

    private Integer id;
    private String name;
    private String summary;
    private String storyline;

    @JsonProperty("aggregated_rating")
    private Double aggregatedRating;

    @JsonProperty("aggregated_rating_count")
    private Integer aggregatedRatingCount;

    @JsonProperty("total_rating")
    private Double totalRating;

    @JsonProperty("total_rating_count")
    private Integer totalRatingCount;

    @JsonProperty("first_release_date")
    private Long firstReleaseDate;

    /** IGDB renamed {@code category} to {@code game_type} in v4. Same enum (0=main_game, 1=dlc_addon, ...). */
    @JsonProperty("game_type")
    private Integer category;

    @JsonProperty("parent_game")
    private IgdbNamedEntityDto parentGame;

    private IgdbCoverDto cover;
    private List<IgdbNamedEntityDto> genres;
    private List<IgdbNamedEntityDto> platforms;
    private List<IgdbNamedEntityDto> themes;
    private List<IgdbNamedEntityDto> keywords;

    @JsonProperty("game_modes")
    private List<IgdbNamedEntityDto> gameModes;

    @JsonProperty("player_perspectives")
    private List<IgdbNamedEntityDto> playerPerspectives;

    private List<IgdbNamedEntityDto> franchises;
    private List<IgdbNamedEntityDto> collections;

    @JsonProperty("involved_companies")
    private List<IgdbInvolvedCompanyDto> involvedCompanies;

    private List<IgdbScreenshotDto> screenshots;
    private List<IgdbVideoDto> videos;

    private List<Integer> dlcs;
    private List<Integer> expansions;

    @JsonProperty("similar_games")
    private List<Integer> similarGames;

    @JsonProperty("age_ratings")
    private List<IgdbAgeRatingDto> ageRatings;

    @JsonProperty("release_dates")
    private List<IgdbReleaseDateDto> releaseDates;

    @JsonProperty("multiplayer_modes")
    private List<IgdbMultiplayerModeDto> multiplayerModes;
}
