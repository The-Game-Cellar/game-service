package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.GameCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameCollectionRepository extends JpaRepository<GameCollection, Long> {
    Optional<GameCollection> findByName(String name);
}
