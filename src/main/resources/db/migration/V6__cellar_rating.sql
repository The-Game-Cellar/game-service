-- Cellar score: what this site's own members rated a game.
-- cellar_rating_avg + cellar_rating_count are raw aggregates posted nightly by
--   library-service, which owns the per-user ratings. Only the averages cross the
--   boundary, never a user id.
-- Both NULL means nobody here has rated the game, which is deliberately different
--   from an average of zero: zero is not a rating the UI can produce.
-- The blend against the IGDB score is computed on read, so the prior weight can
--   change without recomputing anything stored here.
ALTER TABLE games ADD COLUMN cellar_rating_avg NUMERIC(4,2);
ALTER TABLE games ADD COLUMN cellar_rating_count INTEGER;
