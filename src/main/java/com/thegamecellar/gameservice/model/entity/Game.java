package com.thegamecellar.gameservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "games", indexes = {
        @Index(name = "idx_games_first_release_date", columnList = "first_release_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "igdb_id", unique = true, nullable = false)
    private Integer igdbId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "storyline", columnDefinition = "TEXT")
    private String storyline;

    /** Critic score (IGDB {@code aggregated_rating}) normalized to 0–10. */
    @Column(name = "rating", precision = 4, scale = 2)
    private BigDecimal rating;

    @Column(name = "rating_count")
    private Integer ratingCount;

    /** User score (IGDB {@code total_rating}) normalized to 0–10. */
    @Column(name = "total_rating", precision = 4, scale = 2)
    private BigDecimal totalRating;

    @Column(name = "total_rating_count")
    private Integer totalRatingCount;

    @Column(name = "background_image", length = 500)
    private String backgroundImage;

    @Column(name = "cover_image_id", length = 255)
    private String coverImageId;

    @Column(name = "released", length = 20)
    private String released;

    /** IGDB {@code first_release_date} as Unix epoch seconds. Queryable column for upcoming-release range filters. Nullable when IGDB has no canonical worldwide date yet (TBA). */
    @Column(name = "first_release_date")
    private Long firstReleaseDate;

    /** IGDB {@code hypes} — count of users who marked the game as anticipated. Used as a weight signal for upcoming-release ordering. Null on rows IGDB has no hype data for. */
    @Column(name = "hypes")
    private Integer hypes;

    @Column(name = "esrb_rating", length = 50)
    private String esrbRating;

    @Column(name = "developers", columnDefinition = "TEXT")
    private String developers;

    /** IGDB enum: 0=main_game, 1=dlc_addon, 2=expansion, 3=bundle, 4=standalone_expansion, 5=mod, 6=episode, 7=season, 8=remake, 9=remaster, 10=expanded_game, 11=port, 12=fork, 13=pack, 14=update. */
    @Column(name = "category")
    private Integer category;

    @Column(name = "parent_game_id")
    private Integer parentGameId;

    @Column(name = "parent_game_name", length = 255)
    private String parentGameName;

    /** JSON array of IGDB image_id strings. */
    @Column(name = "screenshots", columnDefinition = "TEXT")
    private String screenshots;

    /** JSON array of YouTube video_id strings. */
    @Column(name = "videos", columnDefinition = "TEXT")
    private String videos;

    /** JSON array of IGDB game ids. */
    @Column(name = "dlc_ids", columnDefinition = "TEXT")
    private String dlcIds;

    /** JSON array of IGDB game ids. */
    @Column(name = "expansion_ids", columnDefinition = "TEXT")
    private String expansionIds;

    /** JSON array of IGDB game ids — IGDB's own similarity graph. */
    @Column(name = "similar_game_ids", columnDefinition = "TEXT")
    private String similarGameIds;

    /** JSON array of {@code {category, rating}} ints. */
    @Column(name = "age_ratings", columnDefinition = "TEXT")
    private String ageRatings;

    /** JSON array of {@code {platform, human, date}} per-platform release dates. */
    @Column(name = "release_dates", columnDefinition = "TEXT")
    private String releaseDates;

    /** JSON array of per-platform multiplayer mode descriptors. */
    @Column(name = "multiplayer_modes", columnDefinition = "TEXT")
    private String multiplayerModes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "game_genres",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    @Builder.Default
    private Set<Genre> genres = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "game_platforms",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "platform_id")
    )
    @Builder.Default
    private Set<Platform> platforms = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "game_tags",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "game_themes",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "theme_id")
    )
    @Builder.Default
    private Set<Theme> themes = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "game_game_modes",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "game_mode_id")
    )
    @Builder.Default
    private Set<GameMode> gameModes = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "game_player_perspectives",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "player_perspective_id")
    )
    @Builder.Default
    private Set<PlayerPerspective> playerPerspectives = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "game_franchises",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "franchise_id")
    )
    @Builder.Default
    private Set<Franchise> franchises = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "game_collections",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "collection_id")
    )
    @Builder.Default
    private Set<GameCollection> collections = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
