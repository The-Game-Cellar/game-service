package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.GameSimilarity;
import com.thegamecellar.gameservice.model.entity.GameSimilarityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameSimilarityRepository extends JpaRepository<GameSimilarity, GameSimilarityId> {

    @Query("SELECT s FROM GameSimilarity s WHERE s.sourceIgdbId = :sourceId ORDER BY s.rank ASC")
    List<GameSimilarity> findTopBySource(@Param("sourceId") Integer sourceId);

    @Modifying
    @Query("DELETE FROM GameSimilarity s WHERE s.sourceIgdbId = :sourceId")
    int deleteBySource(@Param("sourceId") Integer sourceId);

    boolean existsBySourceIgdbId(Integer sourceIgdbId);
}
