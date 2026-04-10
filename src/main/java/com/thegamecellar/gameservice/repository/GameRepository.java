package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {
    Optional<Game> findByRawgId(Integer rawgId);

    @Query("SELECT DISTINCT g FROM Game g JOIN g.tags t WHERE LOWER(t.tagName) IN (:tagNames)")
    List<Game> findByTagNamesIn(@Param("tagNames") List<String> tagNames);
}