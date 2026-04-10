package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.GameGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GameGenreRepository extends JpaRepository<GameGenre, Long> {

    @Query("SELECT DISTINCT g.genreName FROM GameGenre g ORDER BY g.genreName")
    List<String> findAllDistinctGenreNames();
}