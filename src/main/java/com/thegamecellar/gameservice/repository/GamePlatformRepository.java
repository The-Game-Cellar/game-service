package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.GamePlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GamePlatformRepository extends JpaRepository<GamePlatform, Long> {

    @Query("SELECT DISTINCT p.platformName FROM GamePlatform p ORDER BY p.platformName")
    List<String> findAllDistinctPlatformNames();
}