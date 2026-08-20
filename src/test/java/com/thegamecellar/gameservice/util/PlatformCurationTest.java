package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.entity.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCurationTest {

    private PlatformCuration curation;

    @BeforeEach
    void setUp() {
        curation = new PlatformCuration();
        curation.load();
    }

    @Test
    void load_reads_the_shipped_curation_file() {
        assertThat(curation.isEnabled()).isTrue();
        assertThat(curation.size()).isEqualTo(79);
        assertThat(curation.curatedNames()).contains("PlayStation 5", "Xbox Series X|S", "Sega Mega Drive/Genesis");
    }

    @Test
    void apply_flags_a_curated_platform_and_reports_the_change() {
        Platform platform = new Platform("PlayStation 5");

        assertThat(curation.apply(platform)).isTrue();
        assertThat(platform.getIsPreferenceEligible()).isTrue();
        assertThat(platform.getCategory()).isEqualTo("sony");
        assertThat(platform.getDisplayOrder()).isEqualTo(10);
    }

    @Test
    void apply_leaves_an_uncurated_platform_on_the_defaults() {
        Platform platform = new Platform("Fictional Console 9000");

        assertThat(curation.apply(platform)).isFalse();
        assertThat(platform.getIsPreferenceEligible()).isFalse();
        assertThat(platform.getCategory()).isNull();
        assertThat(platform.getDisplayOrder()).isEqualTo(999);
    }

    @Test
    void apply_is_idempotent_so_the_reconciler_can_skip_untouched_rows() {
        Platform platform = new Platform("Nintendo Switch");
        assertThat(curation.apply(platform)).isTrue();

        assertThat(curation.apply(platform)).isFalse();
    }

    // The file is authoritative in both directions: dropping an entry must un-flag the row.
    @Test
    void apply_resets_a_row_that_is_no_longer_in_the_file() {
        Platform platform = new Platform("Fictional Console 9000");
        platform.setIsPreferenceEligible(true);
        platform.setCategory("sony");
        platform.setDisplayOrder(10);

        assertThat(curation.apply(platform)).isTrue();
        assertThat(platform.getIsPreferenceEligible()).isFalse();
        assertThat(platform.getCategory()).isNull();
        assertThat(platform.getDisplayOrder()).isEqualTo(999);
    }

    @Test
    void apply_is_a_no_op_when_the_curation_file_is_missing() {
        PlatformCuration disabled = new PlatformCuration();
        Platform platform = new Platform("PlayStation 5");

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.apply(platform)).isFalse();
        assertThat(platform.getIsPreferenceEligible()).isFalse();
    }
}
