package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {
    Optional<Genre> findByName(String name);

    @Query("SELECT g.name FROM Genre g ORDER BY g.name")
    List<String> findAllNames();
}
