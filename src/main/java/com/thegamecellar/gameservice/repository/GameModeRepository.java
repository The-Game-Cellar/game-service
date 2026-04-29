package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.GameMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameModeRepository extends JpaRepository<GameMode, Long> {
    Optional<GameMode> findByName(String name);
}
