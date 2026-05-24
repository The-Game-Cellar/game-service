package com.thegamecellar.gameservice.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class GameResponse {
    private Integer igdbId;
    private String name;
    private String description;
    private String storyline;

    // Critic + user scores normalised to 0-10. See GameMapper.normalizeRating.
    private BigDecimal rating;
    private Integer ratingCount;
    private BigDecimal totalRating;
    private Integer totalRatingCount;

    private String backgroundImage;
    private String coverImageUrl;
    private String released;
    // IGDB first_release_date as Unix epoch seconds (canonical worldwide release).
    private Long firstReleaseDate;
    private Integer hypes;
    private String esrbRating;

    // IGDB category enum: 0=main, 1=dlc, 2=expansion, 3=bundle, 8=remake (see Game.java for full list).
    private Integer category;
    private Integer parentGameId;
    private String parentGameName;

    private List<String> genres;
    private List<String> platforms;
    private List<String> developers;
    private List<String> tags;
    private List<String> themes;
    private List<String> gameModes;
    private List<String> playerPerspectives;
    private List<String> franchises;
    private List<String> collections;

    // Pre-built IGDB CDN URLs, plug straight into <img src>.
    private List<String> screenshotUrls;

    // YouTube video ids; consume as https://www.youtube.com/embed/{id}.
    private List<String> videoIds;

    private List<Integer> dlcIds;
    private List<Integer> expansionIds;
    private List<Integer> similarGameIds;

    private List<AgeRatingDTO> ageRatings;
    private List<ReleaseDateDTO> releaseDates;
    private List<MultiplayerModeDTO> multiplayerModes;

    @Data
    @Builder
    public static class AgeRatingDTO {
        private Integer category;
        private Integer rating;
        private String body;
        private String label;
    }

    @Data
    @Builder
    public static class ReleaseDateDTO {
        private String platform;
        private String date;
        private String human;
    }

    @Data
    @Builder
    public static class MultiplayerModeDTO {
        private String platform;
        private Integer onlineMax;
        private Integer offlineMax;
        private Integer onlineCoopMax;
        private Integer offlineCoopMax;
        private Boolean lanCoop;
        private Boolean splitscreen;
        private Boolean campaignCoop;
        private Boolean dropIn;
    }
}
