package com.thegamecellar.gameservice.repository;

import com.thegamecellar.gameservice.model.entity.SyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncStateRepository extends JpaRepository<SyncState, String> {
}
