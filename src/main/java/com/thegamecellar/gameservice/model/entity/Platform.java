package com.thegamecellar.gameservice.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "platforms")
@Getter
@Setter
@NoArgsConstructor
public class Platform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "is_preference_eligible", nullable = false)
    private Boolean isPreferenceEligible = false;

    @Column(name = "category", length = 20)
    private String category;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 999;

    public Platform(String name) {
        this.name = name;
    }
}
