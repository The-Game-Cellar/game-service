package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.GameTheme;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameThemeRepository extends JpaRepository<GameTheme, Long> {
}
