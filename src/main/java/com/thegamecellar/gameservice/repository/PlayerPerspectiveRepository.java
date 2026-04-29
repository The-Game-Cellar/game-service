package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.PlayerPerspective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerPerspectiveRepository extends JpaRepository<PlayerPerspective, Long> {
    Optional<PlayerPerspective> findByName(String name);
}
