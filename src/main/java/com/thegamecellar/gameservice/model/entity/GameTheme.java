package com.thegamecellar.gameservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_themes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameTheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "theme_name", nullable = false, length = 100)
    private String themeName;
}
