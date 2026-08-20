package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbNamedEntityDto;
import com.thegamecellar.gameservice.model.entity.Game;
import com.thegamecellar.gameservice.model.entity.Platform;
import com.thegamecellar.gameservice.repository.*;
import com.thegamecellar.gameservice.util.CuratedTagAllowlist;
import com.thegamecellar.gameservice.util.DerivedGenreEngine;
import com.thegamecellar.gameservice.util.PlatformCuration;
import com.thegamecellar.gameservice.util.TestPlatformCuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Platform rows are born during a catalog sync, long after Flyway has run, so curation has to be applied here.
class GameCachePlatformCurationTest {

    private PlatformRepository platformRepository;
    private GameCacheService service;

    @BeforeEach
    void setUp() {
        platformRepository = mock(PlatformRepository.class);
        when(platformRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(platformRepository.save(any(Platform.class))).thenAnswer(inv -> inv.getArgument(0));

        GameRepository gameRepository = mock(GameRepository.class);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        PlatformCuration curation = TestPlatformCuration.loaded();

        service = new GameCacheService(
                gameRepository,
                platformRepository,
                mock(GenreRepository.class),
                mock(TagRepository.class),
                mock(ThemeRepository.class),
                mock(GameModeRepository.class),
                mock(PlayerPerspectiveRepository.class),
                mock(FranchiseRepository.class),
                mock(GameCollectionRepository.class),
                mock(CuratedTagAllowlist.class),
                mock(DerivedGenreEngine.class),
                curation
        );
    }

    @Test
    void a_platform_created_by_the_catalog_sync_is_curated_on_insert() {
        Game game = service.cacheGame(dtoWithPlatforms("PlayStation 5"));

        Platform saved = game.getPlatforms().iterator().next();
        assertThat(saved.getName()).isEqualTo("PlayStation 5");
        assertThat(saved.getIsPreferenceEligible()).isTrue();
        assertThat(saved.getCategory()).isEqualTo("sony");
        assertThat(saved.getDisplayOrder()).isEqualTo(10);
    }

    @Test
    void a_platform_outside_the_curation_file_keeps_the_defaults() {
        Game game = service.cacheGame(dtoWithPlatforms("Fictional Console 9000"));

        Platform saved = game.getPlatforms().iterator().next();
        assertThat(saved.getIsPreferenceEligible()).isFalse();
        assertThat(saved.getCategory()).isNull();
        assertThat(saved.getDisplayOrder()).isEqualTo(999);
    }

    private IgdbGameDto dtoWithPlatforms(String... names) {
        IgdbGameDto dto = new IgdbGameDto();
        dto.setId(1);
        dto.setName("Test Game");
        dto.setPlatforms(List.of(names).stream().map(name -> {
            IgdbNamedEntityDto platform = new IgdbNamedEntityDto();
            platform.setName(name);
            return platform;
        }).toList());
        return dto;
    }
}
