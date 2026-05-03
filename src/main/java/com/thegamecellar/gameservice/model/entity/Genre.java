package com.thegamecellar.gameservice.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "genres")
@Getter
@Setter
@NoArgsConstructor
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    /**
     * Provenance of the genre row. {@code IGDB} = pulled from the IGDB API, {@code DERIVED} =
     * created by the rule-based derived-genre engine (see {@code DerivedGenreEngine}). Backfill
     * deletes only DERIVED rows before re-applying so IGDB-sourced genres are never touched.
     */
    @Column(name = "source", nullable = false, length = 10,
            columnDefinition = "VARCHAR(10) NOT NULL DEFAULT 'IGDB'")
    private String source = "IGDB";

    public Genre(String name) {
        this.name = name;
        this.source = "IGDB";
    }

    public Genre(String name, String source) {
        this.name = name;
        this.source = source;
    }
}
