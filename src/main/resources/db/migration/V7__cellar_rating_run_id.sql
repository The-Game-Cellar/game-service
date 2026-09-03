-- Tags the nightly pass that last wrote a row's cellar rating.
-- The pass is a full replacement sent in batches, so after the last batch the sender
-- prunes every row still carrying an older run id. Without this a game whose only
-- rating was deleted would keep its average forever: it stops appearing in the
-- aggregate rather than arriving with a zero, so nothing would ever clear it.
ALTER TABLE games ADD COLUMN cellar_rating_run_id VARCHAR(36);

CREATE INDEX idx_games_cellar_rating_run_id ON games (cellar_rating_run_id)
    WHERE cellar_rating_avg IS NOT NULL;
