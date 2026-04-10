package com.thegamecellar.gameservice.model.dto.rawg;

import lombok.Data;

// RAWG wraps each platform in an object: { "platform": { "id": 4, "name": "PC" } }
@Data
public class RawgPlatformEntry {
    private RawgNamedEntity platform;
}