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

    /** Critic score (0–5). */
    private BigDecimal rating;
    private Integer ratingCount;

    /** User score (0–5). */
    private BigDecimal totalRating;
    private Integer totalRatingCount;

    private String backgroundImage;
    private String coverImageUrl;
    private String released;
    /** IGDB {@code first_release_date} as Unix epoch seconds. Queryable canonical worldwide release. */
    private Long firstReleaseDate;
    /** IGDB {@code hypes}. Anticipation count for upcoming titles. Null when IGDB has no hype signal. */
    private Integer hypes;
    private String esrbRating;

    /** IGDB category enum (0=main_game, 1=dlc_addon, 2=expansion, etc.). */
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

    /** Full IGDB CDN URLs for screenshots, ready to be used as {@code <img src>}. */
    private List<String> screenshotUrls;

    /** YouTube video ids (use as {@code https://www.youtube.com/embed/{id}}). */
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
        /** Human-readable rating body (e.g. "PEGI", "ESRB"). Null for unsupported bodies. */
        private String body;
        /** Human-readable rating label (e.g. "16", "M"). Null for unsupported bodies. */
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
