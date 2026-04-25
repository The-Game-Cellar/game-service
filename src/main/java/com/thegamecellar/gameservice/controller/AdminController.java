package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.service.IgdbCatalogWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final IgdbCatalogWorker igdbCatalogWorker;

    @PostMapping("/sync")
    public ResponseEntity<String> triggerFullSync() {
        new Thread(igdbCatalogWorker::syncCatalog).start();
        return ResponseEntity.accepted().body("Full sync started");
    }

    @PostMapping("/sync/quick")
    public ResponseEntity<String> triggerQuickSync() {
        new Thread(igdbCatalogWorker::quickSync).start();
        return ResponseEntity.accepted().body("Quick sync started — fetching ~100 games");
    }
}
