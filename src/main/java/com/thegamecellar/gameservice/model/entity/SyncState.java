package com.thegamecellar.gameservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sync_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncState {

    @Id
    @Column(name = "state_key")
    private String stateKey;

    @Column(name = "state_value")
    private String stateValue;
}
