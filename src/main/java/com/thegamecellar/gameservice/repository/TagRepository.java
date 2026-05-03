package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    @Modifying
    @Query(value = "DELETE FROM game_tags WHERE tag_id = :tagId", nativeQuery = true)
    int deleteJoinRowsForTag(@Param("tagId") Long tagId);
}
