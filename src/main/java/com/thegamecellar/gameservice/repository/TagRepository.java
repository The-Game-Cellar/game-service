package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    @Modifying
    @Query(value = "DELETE FROM game_tags WHERE tag_id = :tagId", nativeQuery = true)
    int deleteJoinRowsForTag(@Param("tagId") Long tagId);

    /** Top tag names by game_tags occurrence count with blocklist excluded at SQL level. Returns [name, freq] rows. */
    @Query(value = """
            SELECT t.name AS name, COUNT(*) AS freq
            FROM tags t
            JOIN game_tags gt ON gt.tag_id = t.id
            WHERE t.name NOT IN (:blocklist)
            GROUP BY t.name
            ORDER BY freq DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findPopularExcludingBlocklist(
            @Param("blocklist") Collection<String> blocklist,
            @Param("limit") int limit);
}
