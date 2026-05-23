package com.thegamecellar.gameservice.controller;

import com.thegamecellar.gameservice.model.dto.PlatformCatalogDTO;
import com.thegamecellar.gameservice.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platforms")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformRepository platformRepository;

    @GetMapping("/catalog")
    public ResponseEntity<List<PlatformCatalogDTO>> getCatalog() {
        List<PlatformCatalogDTO> catalog = platformRepository
                .findByIsPreferenceEligibleTrueOrderByCategoryAscDisplayOrderAscNameAsc()
                .stream()
                .map(p -> new PlatformCatalogDTO(p.getId(), p.getName(), p.getCategory(), p.getDisplayOrder()))
                .toList();
        return ResponseEntity.ok(catalog);
    }
}
