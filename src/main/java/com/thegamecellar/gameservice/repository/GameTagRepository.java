package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.GameTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GameTagRepository extends JpaRepository<GameTag, Long> {

    @Query("SELECT DISTINCT t.tagName FROM GameTag t ORDER BY t.tagName")
    List<String> findAllDistinctTagNames();
}
