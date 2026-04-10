package com.thegamecellar.gameservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_genres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameGenre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "genre_name", nullable = false, length = 100)
    private String genreName;
}