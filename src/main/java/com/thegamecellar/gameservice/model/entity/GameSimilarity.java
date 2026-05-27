package com.thegamecellar.gameservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_similarities")
@IdClass(GameSimilarityId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSimilarity {

    @Id
    @Column(name = "source_igdb_id", nullable = false)
    private Integer sourceIgdbId;

    @Id
    @Column(name = "similar_igdb_id", nullable = false)
    private Integer similarIgdbId;

    @Column(name = "score", nullable = false, precision = 5, scale = 4)
    private BigDecimal score;

    @Column(name = "rank", nullable = false)
    private Short rank;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
