package com.thegamecellar.gameservice.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * IGDB "collections": groups of related games. Named {@code GameCollection} (not
 * {@code Collection}) to avoid collision with {@link java.util.Collection}.
 */
@Entity
@Table(name = "collections")
@Getter
@Setter
@NoArgsConstructor
public class GameCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", unique = true, nullable = false, length = 200)
    private String name;

    public GameCollection(String name) {
        this.name = name;
    }
}
