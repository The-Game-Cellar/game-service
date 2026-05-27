-- Catalog-side game similarity. Worker computes top-N similar games per source game by
-- genre + theme + tag overlap and writes here. Distinct from IGDB's similar_game_ids (which
-- carries IGDB's own recommendations); this table is rebuildable from local catalog state.
-- Read path for /similar moves here in Phase 8.

CREATE TABLE game_similarities (
    source_igdb_id  INT          NOT NULL,
    similar_igdb_id INT          NOT NULL,
    score           NUMERIC(5,4) NOT NULL,
    rank            SMALLINT     NOT NULL,
    computed_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (source_igdb_id, similar_igdb_id)
);

CREATE INDEX idx_game_similarities_source_rank ON game_similarities (source_igdb_id, rank);
CREATE INDEX idx_game_similarities_stale ON game_similarities (computed_at);
