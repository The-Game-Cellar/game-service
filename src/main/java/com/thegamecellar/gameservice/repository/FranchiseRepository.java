package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.Franchise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FranchiseRepository extends JpaRepository<Franchise, Long> {
    Optional<Franchise> findByName(String name);
}
