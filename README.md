# Game Service

> IGDB API client and local game catalog cache. Serves search, browse, and game-detail data to the rest of the system and runs a nightly background worker that walks IGDB to keep the cache warm.

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

## Caching Strategy

Two ingestion paths share the local cache:

- **User-triggered (`getGameById`)**: narrow stale check. Returns immediately if the cached row has core data; refetches from IGDB only when `tags`, `genres`, `description`, or `developers` are missing. Each refresh costs an IGDB round trip, so the bar is high.
- **Worker / search-side (`cacheIfAbsent`)**: wider stale check. Refreshes when any of `tags`, `genres`, `description`, `developers`, `category`, `rating_count`, `screenshots`, `videos`, `dlc_ids`, `expansion_ids`, `similar_game_ids`, `age_ratings`, `release_dates`, `multiplayer_modes` is missing. The DTO is already in hand, so the marginal cost is zero.

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

A small set of columns are stored as JSON-as-`TEXT` (`screenshots`, `videos`, `dlc_ids`, `expansion_ids`, `similar_game_ids`, `age_ratings`, `release_dates`, `multiplayer_modes`) since nothing queries into them; they round-trip through the mapper.

`platforms` carries three curation columns (`is_preference_eligible BOOLEAN`, `category VARCHAR(20)`, `display_order INT`) used by `/api/v1/platforms/catalog` to drive the Preferences picker. Curation is manual via Flyway / SQL.

## API Endpoints

### Public catalog (JWT required)

| Method | Path                                  | Description                                                                                              |
|--------|---------------------------------------|----------------------------------------------------------------------------------------------------------|
| GET    | `/api/v1/games/search`                | Search with `query`, `platform`, `genre` (CSV, AND-match), `gameMode` (CSV, AND-match), `perspective` (CSV, AND-match), `gameType`, `ordering`, `releasedFrom`/`releasedTo` (epoch seconds), `tags` (CSV, AND-match), `ratingFrom` (BigDecimal, floor on `totalRating`). Response includes `availableTagCounts` / `availableGenreCounts` / `availableGameModeCounts` / `availablePerspectiveCounts` maps for count-badge + grayed-out UI (populated only when at least one user-filter active, cold-path skip otherwise). `pageSize` max 100, `page` 0-500. |
| GET    | `/api/v1/games/{igdbId}`              | Full game detail by IGDB ID.                                                                             |
| GET    | `/api/v1/games/popular`               | Popular games, optional platform filter.                                                                 |
| GET    | `/api/v1/games/upcoming`              | Upcoming releases in the next 12 months.                                                                 |
| GET    | `/api/v1/games/random`                | Random games from the cache. `limit` max 100.                                                            |
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
| GET    | `/api/v1/admin/sync/status`                   | `{ running: bool }`.                                              |

### Internal service-to-service (no user JWT)

| Method | Path                                          | Description                                                       |
|--------|-----------------------------------------------|-------------------------------------------------------------------|
| GET    | `/internal/games/{igdbId}`                    | Single game (used by similar-graph traversal in worker).          |
| GET    | `/internal/games/popular?platform=...`        | Popular games per platform (Tier-3 fallback in worker).           |
| GET    | `/internal/games/random-quality?genre=...`    | Random-quality candidates by genre (Tier-1/2 in worker).          |

Used by the recommendation-service per-user worker. Protected by `InternalAuthFilter`: requires header `X-Internal-Token: {INTERNAL_SERVICE_TOKEN}` (constant-time compare, fail-closed when the env var is unset). The api-gateway has no route for `/internal/**`, so the paths are only reachable inside the docker network.

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
| `IGDB_WORKER_ENABLED`                 | `true`                             | Master switch for the nightly worker             |
| `IGDB_WORKER_DISCOVERY_PAGES`         | `200`                              | Pages per nightly run                            |
| `IGDB_WORKER_DISCOVERY_LIMIT`         | `500`                              | Games per page (IGDB max)                        |
| `IGDB_WORKER_ENRICHMENT_LIMIT`        | `400`                              | Stubs to enrich per run                          |
| `IGDB_WORKER_CRON`                    | `0 30 3 * * *`                     | Cron expression (default 03:30 daily)            |
| `IGDB_WORKER_RATE_LIMIT_DELAY_MS`     | `250`                              | Delay between IGDB calls inside the worker       |
| `INTERNAL_SERVICE_TOKEN`              | (required for /internal/** auth)   | Shared secret accepted by `InternalAuthFilter` on `/internal/**`. Fail-closed when unset. |

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
