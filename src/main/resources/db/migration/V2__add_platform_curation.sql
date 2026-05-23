-- Curation columns on platforms table.
-- is_preference_eligible: whether the platform appears in the user-facing Preferences picker.
-- category: manufacturer grouping (sony, microsoft, nintendo, pc, mobile, other) used to lay out the picker.
-- display_order: per-category ordering inside the picker (lower first; 999 sinks un-curated rows).

ALTER TABLE platforms
    ADD COLUMN is_preference_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN category                VARCHAR(20),
    ADD COLUMN display_order           INT NOT NULL DEFAULT 999;

CREATE INDEX idx_platforms_pref_category
    ON platforms (is_preference_eligible, category, display_order);
