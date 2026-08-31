-- Background art for the full-bleed dashboard stage.
-- background_art_pool: raw candidate metadata harvested by the catalog sync,
--   a JSON array of {id, w, h, s} where s is 'a' (artwork) or 's' (screenshot).
--   '[]' means harvested with nothing to offer; NULL means the sync has not visited the row yet.
-- background_image_id + background_source: the background art worker's decision.
--   source is 'artwork' or 'screenshot' on a winner, 'none' when every candidate failed
--   the quality check, NULL while undecided.
ALTER TABLE games ADD COLUMN background_art_pool TEXT;
ALTER TABLE games ADD COLUMN background_image_id VARCHAR(255);
ALTER TABLE games ADD COLUMN background_source VARCHAR(16);
