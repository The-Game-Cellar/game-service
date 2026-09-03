# Game Service

> IGDB API client and local game catalog cache. Serves search, browse, and game-detail data to the rest of the system and runs a nightly background worker that walks IGDB to keep the cache warm.

[![CI](https://github.com/The-Game-Cellar/game-service/actions/workflows/ci.yml/badge.svg)](https://github.com/The-Game-Cellar/game-service/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791)
![License](https://img.shields.io/badge/License-MIT-blue)

**Port:** `8081` &nbsp;|&nbsp; **Database:** `game_db` (port `5432`)

## Responsibilities

- Proxy IGDB v4 with a local Postgres cache, so repeat reads never hit the IGDB rate limit (4 req/sec).
- Serve a normalized catalog: games, genres, tags, themes, platforms, modes, perspectives, franchises, collections (all `@ManyToMany`).
- Backfill missing fields on demand: a cached game with missing tags or developers triggers a re-fetch on the next read.
- Run a nightly `IgdbWorker` that walks IGDB pages, enriches stubs, and tracks progress in a `sync_state` table.
- Expose admin endpoints for manual sync triggers and developer-field backfill.

## Position in the System

```
Library Service ----+
Recommendation Svc -+--> Game Service (8081) ---> IGDB v4 (api.igdb.com)
Frontend (via GW) --+         |
                              v
                          game_db (5432)
```

Game Service is read-mostly: the frontend and the other backend services pull catalog data, and the worker is the only writer at steady state. There is no direct frontend-to-Game Service path; everything routes through the API Gateway.

## Tech Stack

- Java 17, Spring Boot 4.0
- Spring Data JPA with Hibernate
- PostgreSQL 17 with Flyway-managed migrations
- Spring Security OAuth 2 Resource Server for JWT validation
- IGDB v4 via Twitch OAuth (`Client-ID` + bearer)
- `@Scheduled` cron worker for nightly catalog walks
- Sentry for error tracking (`sentry-spring-boot-4` + `sentry-logback`)

## Caching Strategy

Two ingestion paths share the local cache:

- **User-triggered (`getGameById`)**: narrow stale check. Returns immediately if the cached row has core data; refetches from IGDB only when `tags`, `genres`, `description`, or `developers` are missing. Each refresh costs an IGDB round trip, so the bar is high.
- **Worker / search-side (`cacheIfAbsent`)**: wider stale check. Refreshes when any of `tags`, `genres`, `description`, `developers`, `category`, `rating_count`, `screenshots`, `videos`, `dlc_ids`, `expansion_ids`, `similar_game_ids`, `age_ratings`, `release_dates`, `multiplayer_modes`, `background_art_pool` is missing. The DTO is already in hand, so the marginal cost is zero.

Genre-only searches use a three-step fallback: genre cache hit, broad pool fallback (recent cached games re-ranked by similarity), then IGDB.

## Database Schema

Schema is managed by **Flyway** (`src/main/resources/db/migration/V*__*.sql`). Hibernate is set to `ddl-auto: validate` by default; production schema changes go through new migrations only.

Core tables (normalized to `@ManyToMany` reference tables):

```
games              genres       tags         themes
platforms          game_modes   franchises   collections
player_perspectives             sync_state
```

Plus the join tables: `game_genres`, `game_tags`, `game_themes`, `game_platforms`, `game_modes_link`, `game_franchises`, `game_collections`, `game_player_perspectives`.

A small set of columns are stored as JSON-as-`TEXT` (`screenshots`, `videos`, `dlc_ids`, `expansion_ids`, `similar_game_ids`, `age_ratings`, `release_dates`, `multiplayer_modes`, `background_art_pool`) since nothing queries into them; they round-trip through the mapper.

`games` also carries a decided background image for full-bleed surfaces. The catalog sync stores raw artwork/screenshot candidates (id + dimensions) in `background_art_pool`; a nightly background art worker then downloads up to three candidates per game, quality-checks them (file size, grey entropy, colour count) and stores the winner as `background_image_id` + `background_source` (`artwork`/`screenshot`, or `none` when every candidate failed). Served as `backgroundArtUrl` (a `t_1080p` CDN URL) and `backgroundSource` on `GameResponse`.

`games` also carries the Cellar score inputs: `cellar_rating_avg NUMERIC(4,2)`, `cellar_rating_count INTEGER` and `cellar_rating_run_id VARCHAR(36)`, all nullable. The first two are raw aggregates of what this site's members rated the game, posted nightly by library-service through `POST /internal/games/ratings`; both NULL means nobody here has rated it, which is deliberately distinct from an average of zero. The blend against `total_rating` is computed on read (`cellar = (n * avg + k * totalRating) / (n + k)`, `k` from `CELLAR_RATING_PRIOR_WEIGHT`) and served as `cellarRating` + `cellarRatingCount` on `GameResponse`, so the weight can change without recomputing anything stored. `cellarRating` is null only when the game has neither member ratings nor an IGDB score.

Note that the two IGDB scores are not what their names suggest: `rating` is the critic score (IGDB `aggregated_rating`), and `total_rating` is IGDB's own blend of critics and IGDB members, not a pure user score. Both are stored normalized to 0-10 (divide by 10). IGDB's member-only score is a separate field the catalog does not request.

`platforms` carries three curation columns (`is_preference_eligible BOOLEAN`, `category VARCHAR(20)`, `display_order INT`) used by `/api/v1/platforms/catalog` to drive the Preferences picker. Curation is declared in `src/main/resources/platform-curation.yaml` and is authoritative in both directions: a platform listed there is flagged eligible with its category and order, one absent from it is reset to the column defaults. Platform rows are created by the IGDB catalog sync at runtime, not by a migration, so the file is applied twice: `GameCacheService` curates a row as it inserts it, and `PlatformCurationReconciler` reconciles every existing row at startup. Edit the file and restart the service.

## API Endpoints

### Public catalog (JWT required)

| Method | Path                                  | Description                                                                                              |
|--------|---------------------------------------|----------------------------------------------------------------------------------------------------------|
| GET    | `/api/v1/games/search`                | Search with `query`, `platform`, `genre` (CSV, AND-match), `gameMode` (CSV, AND-match), `perspective` (CSV, AND-match), `gameType`, `ordering`, `releasedFrom`/`releasedTo` (epoch seconds), `tags` (CSV, AND-match), `ratingFrom` (BigDecimal, floor on `totalRating`). Response includes `availableTagCounts` / `availableGenreCounts` / `availableGameModeCounts` / `availablePerspectiveCounts` maps for count-badge + grayed-out UI (populated only when at least one user-filter active, cold-path skip otherwise). `pageSize` max 100, `page` 0-500. |
| GET    | `/api/v1/games/{igdbId}`              | Full game detail by IGDB ID.                                                                             |
| GET    | `/api/v1/games/popular`               | Popular games, optional platform filter.                                                                 |
| POST   | `/api/v1/games/upcoming`              | Upcoming releases. Body `{ platform, windowDays, limit, excludeIds, recentlyShownIds, page, pageSize }`. `page == null` = sample-mode (A-Res inverse-days, recency × 0.5). `page != null` = paginated deterministic sort by release date. |
| GET    | `/api/v1/games/random`                | Random games from the cache. `limit` max 500.                                                            |
| GET    | `/api/v1/games/genres`                | All genres (cache first, IGDB fallback).                                                                 |
| GET    | `/api/v1/games/platforms`             | All platform names from the local catalog.                                                               |
| GET    | `/api/v1/platforms/catalog`           | Curated platform catalog (`is_preference_eligible = TRUE`) with manufacturer category + display order. Drives the Preferences picker. |
| GET    | `/api/v1/games/tags/popular?limit=N`  | Top-N catalog tags by `game_tags` occurrence, with a curated junk blocklist applied at SQL level.        |
| GET    | `/api/v1/games/by-franchise/{name}`   | Games tagged with a given franchise. Filters to main games + remakes, `parentGameId IS NULL`. Optional `limit`, `excludeIgdbId`. |
| GET    | `/api/v1/games/by-collection/{name}`  | Games tagged with a given collection. Same filter shape as `by-franchise`. |
| GET    | `/api/v1/games/by-developer/{name}`   | Games where developer matches against the CSV `developers` column (exact word boundary). Optional `limit`, `excludeIgdbId`. |
| GET    | `/api/v1/games/{igdbId}/editions`     | Derivative releases of a main game (editions, remakes, remasters, ports, etc.). |

### Admin

| Method | Path                                          | Description                                                       |
|--------|-----------------------------------------------|-------------------------------------------------------------------|
| POST   | `/api/v1/admin/sync`                          | Full IGDB catalog walk (~100k games). Overlap-protected.          |
| POST   | `/api/v1/admin/sync/quick`                    | ~100-game quick sync.                                             |
| POST   | `/api/v1/admin/backfill-developers`           | One-shot loop over rows with `developers IS NULL`.                |
| POST   | `/api/v1/admin/background-art`                | Background art pass over rows with an undecided pool. Overlap-protected. |
| GET    | `/api/v1/admin/sync/status`                   | `{ running: bool }`.                                              |

### Internal service-to-service (no user JWT)

| Method | Path                                          | Description                                                       |
|--------|-----------------------------------------------|-------------------------------------------------------------------|
| GET    | `/internal/games/{igdbId}`                    | Single game (used by similar-graph traversal in worker).          |
| GET    | `/internal/games/popular?platform=...`        | Popular games per platform (Tier-3 fallback in worker).           |
| GET    | `/internal/games/random-quality?genre=...`    | Random-quality candidates by genre (Tier-1/2 in worker).          |
| POST   | `/internal/games/ratings?runId=...`           | Stores a batch of member-rating aggregates from library-service.  |
| POST   | `/internal/games/ratings/prune?runId=...`     | Closes that pass, clearing aggregates an older run wrote.         |

The two `ratings` paths take the nightly Cellar score pass from library-service. The body is a list of `{igdbGameId, average, count}`, aggregates only, so no per-user data crosses the boundary. Ids the catalog does not hold are skipped rather than rejected. The pass is a full replacement sent in batches of 500 under one `runId`, and the prune clears every row still tagged with an older run: without it a game whose last rating was deleted would keep its average forever, since it stops appearing in the aggregate rather than arriving with a zero.

The read endpoints are used by the recommendation-service per-user worker. Protected by `InternalAuthFilter`: requires header `X-Internal-Token: {INTERNAL_SERVICE_TOKEN}` (constant-time compare, fail-closed when the env var is unset). The api-gateway has no route for `/internal/**`, so the paths are only reachable inside the docker network.

## Configuration

| Variable                              | Default                            | Purpose                                          |
|---------------------------------------|------------------------------------|--------------------------------------------------|
| `GAME_SERVICE_PORT`                   | `8081`                             | Service port                                     |
| `GAME_DB_URL`                         | `jdbc:postgresql://localhost:5432/game_db` | Full JDBC URL                            |
| `GAME_DB_USERNAME`                    | `postgres`                         | DB user                                          |
| `GAME_DB_PASSWORD`                    | _none_                             | DB password                                      |
| `DDL_AUTO`                            | `validate`                         | Hibernate DDL mode                               |
| `KEYCLOAK_ISSUER_URI`                 | `http://localhost:8080/realms/game-cellar` | JWT issuer                               |
| `TWITCH_CLIENT_ID`                    | _none_                             | Twitch app client ID (IGDB inherits Twitch OAuth)|
| `TWITCH_CLIENT_SECRET`                | _none_                             | Twitch app secret                                |
| `IGDB_API_BASE_URL`                   | `https://api.igdb.com/v4`          | Override only for testing against a mock         |
| `IGDB_CONNECT_TIMEOUT` / `IGDB_READ_TIMEOUT` | `2000` / `10000`            | IGDB client timeouts (ms) on the request path: search, detail, browse fallback |
| `IGDB_WORKER_ENABLED`                 | `true`                             | Master switch for the nightly worker             |
| `IGDB_WORKER_DISCOVERY_PAGES`         | `50`                               | Pages per nightly run (50 x 500 = 25k)           |
| `IGDB_WORKER_DISCOVERY_LIMIT`         | `500`                              | Games per page (IGDB max)                        |
| `IGDB_WORKER_CRON`                    | `0 30 3 * * *`                     | Cron expression (default 03:30 daily)            |
| `IGDB_WORKER_RATE_LIMIT_DELAY_MS`     | `250`                              | Delay between IGDB calls inside the worker       |
| `IGDB_WORKER_READ_TIMEOUT`            | `30000`                            | Read timeout (ms) for the worker's bulk pages (catalog walk, new and upcoming releases, id batches); deep-offset pages run close to 10 s at IGDB |
| `IGDB_WORKER_RETRY_DELAY_MS`          | `2000`                             | Wait before the single retry of a failed worker page; a page fails for good only when the retry fails too |
| `IGDB_BACKGROUND_ART_ENABLED`         | `true`                             | Master switch for the nightly background art worker |
| `IGDB_BACKGROUND_ART_CRON`            | `0 30 4 * * *`                     | Art worker cron, one hour after the catalog worker so the night's harvested pools are in hand |
| `IGDB_BACKGROUND_ART_BATCH_SIZE`      | `5000`                             | Games decided per art pass; raise temporarily for catch-up |
| `IGDB_BACKGROUND_ART_DOWNLOAD_DELAY_MS` | `200`                            | Politeness delay between games; each game downloads at most three candidate images |
| `CELLAR_RATING_PRIOR_WEIGHT`          | `10`                               | Votes needed to pull a game halfway from the IGDB score to the members' average. Applied on read, so changing it needs a restart and no recomputation. |
| `INTERNAL_SERVICE_TOKEN`              | (required for /internal/** auth)   | Shared secret accepted by `InternalAuthFilter` on `/internal/**`. Fail-closed when unset. |
| `SENTRY_DSN`                          | (empty)                            | Sentry ingest endpoint. Empty makes the SDK a no-op, so local runs and tests send nothing. Set in production only. |
| `SENTRY_ENVIRONMENT`                  | `local`                            | Environment tag on every Sentry event            |
| `SENTRY_RELEASE`                      | (empty)                            | Commit sha, baked into the image at build time so Sentry can group regressions by deploy |

Register an application at [dev.twitch.tv/console](https://dev.twitch.tv/console) to obtain `TWITCH_CLIENT_ID` and `TWITCH_CLIENT_SECRET`. IGDB inherits Twitch OAuth.

## Run Locally

### Prerequisites

- Java 17+
- A running PostgreSQL 17 instance on port 5432, with a `game_db` database
- Twitch credentials (`TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET`)

### Direct

```bash
./mvnw spring-boot:run
```

Flyway runs migrations on startup. `baseline-on-migrate: true` stamps existing dev/prod DBs with V1 without re-execution; fresh databases run V1 to create the full schema.

### Via Docker Compose

```bash
docker compose up game-service
```

The worker is disabled in tests via `IGDB_WORKER_ENABLED=false`.

## Tests

```bash
./mvnw test
```

Covers controllers (`MockMvc`), services with mocked IGDB clients, the cache stale-check logic, the platform-name normalizer, and the worker's pagination behaviour.

## License

[MIT](./LICENSE)
