package com.thegamecellar.gameservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "tag_name", nullable = false, length = 100)
    private String tagName;
}
