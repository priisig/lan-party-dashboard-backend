# LAN Dashboard – Backend

Spring Boot backend for the yearly LAN party dashboard (overview with schedule and announcements, tournaments with
Challonge brackets, seat map, nerd stats, admin area). The React frontend lives in the separate
[`lan-party-dashboard-frontend`](https://github.com/priisig/lan-party-dashboard-frontend) repository.
**This repository also contains the Docker deployment** (`deploy/`, [DEPLOYMENT.md](DEPLOYMENT.md)).

| | |
|---|---|
| Stack | Java 25, Spring Boot 4.1, Spring Data JPA, Spring Security, Flyway, PostgreSQL 17 |
| Build | Gradle 9 (Groovy DSL, wrapper included) |
| Tests | JUnit 5, Testcontainers (needs Docker) |

---

## Deployment (homeserver)

Images are built by GitHub Actions and deployed to the homeserver by a self-hosted runner:

| | |
|---|---|
| `git push` | `.github/workflows/ci.yml` runs `./gradlew check` |
| `git tag vX.Y.Z && git push origin vX.Y.Z` | `.github/workflows/release.yml` builds `ghcr.io/priisig/lan-party-dashboard-backend:X.Y.Z` and deploys it to `/docker/lan-party-dashboard` |

The production stack is `deploy/docker-compose.yml` with `deploy/.env.example` as template for the server's `.env`.
Server setup, reverse proxy (SSE!), backups and rollback: **[DEPLOYMENT.md](DEPLOYMENT.md)**.

| Service | What it does |
|---|---|
| `db` | PostgreSQL 17, data in the `lan-party_pgdata` volume |
| `backend` | this application (port 8080, internal only) |
| `frontend` | nginx: serves the React build and proxies `/api` (incl. Server-Sent Events) to the backend |

**Accounts:** participants register at `/registrieren`; an account is needed to reserve a seat or sign up for a
tournament. Organisers are accounts with the role *Orga*. The first one is created from `LAN_BOOTSTRAP_ADMIN_EMAIL` /
`LAN_BOOTSTRAP_ADMIN_PASSWORD`; further organisers register normally and get the role under **Admin → Teilnehmer**.
There is no e-mail: forgotten passwords are reset by an organiser (*Teilnehmer → Passwort zurücksetzen*).

> Upgrading from the admin-code version: migration `V4` removes the old admin codes. Set the three
> `LAN_BOOTSTRAP_ADMIN_*` variables before deploying, otherwise nobody can reach the admin panel.

**Beamer:** open `https://<domain>/?kiosk=1` in fullscreen (F11). The kiosk mode rotates through the views
configured under *Admin → Allgemein → Beamer / Kiosk-Modus*, hides the cursor and never scrolls.

**Internet access:** the backend calls Challonge. If Challonge is unreachable, the dashboard keeps showing the
last loaded bracket. Registrations are stored locally and pushed to Challonge as soon as it is reachable again.

### Demo data

`LAN_SEED_DEMO=true` creates a demo event like the design prototype when the database is empty. The times are
relative to the start time, so a tournament is live and a registration is about to close. Don't use it for the
real event, or delete the demo event afterwards.

---

## Yearly workflow

1. **Admin → Events → Neuer Event**: title (e.g. "VIVO LAN 2027"), start/end, *Einrichtung übernehmen von* last year.
   The clone copies:
   - infos, game servers, integrations and the logo
   - the seat layout, with orga seats still blocked
   - tournaments as templates, without registrations or Challonge link
   - the schedule, shifted by the difference between the start dates
2. Select the new event in the sidebar (*Bearbeiteter Event*) and prepare it. The dashboard keeps showing the active
   event until you switch.
3. On LAN day: **Events → Aktivieren**.

---

## Development

```bash
cp .env.example .env    # once; local settings (organiser login, demo data) – git-ignored
./gradlew bootRun       # Windows: gradlew.bat bootRun – or run LanDashboardApplication in the IDE
```

With Docker running, `bootRun` starts its own Postgres from `compose.yaml` (random host port, data in the
`lan-dashboard-dev-db` volume) and connects to it – no DB setup needed. The container keeps running after you stop
the app; `docker compose down` stops it, `docker compose down -v` also deletes the data. To use your own Postgres,
set `LAN_DEV_DOCKER_DB=false` and `DB_URL`/`DB_USER`/`DB_PASSWORD` in `.env`.

The default `.env` creates the organiser `orga@lan.local` / `orga-pass` and a demo event.

```bash
./gradlew test        # unit + integration tests (Testcontainers starts its own Postgres)
./gradlew bootJar     # build/libs/lan-dashboard-backend.jar
```

Start the frontend with `npm run dev` in the frontend repo. Vite proxies `/api` to `localhost:8080`.

### Configuration

All variables can also be put into `.env` for local development.

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/lan_dashboard` | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `lan` / `lan` | DB credentials |
| `LAN_BOOTSTRAP_ADMIN_EMAIL` | – | login of the first organiser account, created if no organiser exists |
| `LAN_BOOTSTRAP_ADMIN_PASSWORD` | – | its password (≥ 8 characters) |
| `LAN_BOOTSTRAP_ADMIN_NICKNAME` | `Orga` | its nickname |
| `LAN_SEED_DEMO` | `false` | demo event when the DB is empty |
| `CHALLONGE_BASE_URL` | `https://api.challonge.com/v1` | Challonge API |
| `COOKIE_SECURE` | `false` | session cookie only over HTTPS (`true` in production) |
| `PORT` | `8080` | HTTP port |
| `LAN_DEV_DOCKER_DB` | `true` | local development only: start Postgres from `compose.yaml` |

Polling intervals are set in `application.yml` (`lan.challonge-poll-ms`, `lan.server-query-ms`,
`lan.integration-poll-ms`). The Challonge API key and the push token are set in the admin UI and stored in the
database.

### Project structure

```
src/main/java/com/lanparty/dashboard/
  event/          Event (one LAN edition), activation, cloning, logo
  info/ announcement/ schedule/ server/   simple content; BannerService = manual + automatic banners
  tournament/     tournaments, registrations
  challonge/      ChallongeClient (API v1), ChallongeSyncService (polling + push), BracketMapper
  seating/        seat rows, seats, reservation requests, layout reshaping
  server/query/   A2S (Source), Quake 3, Minecraft Server List Ping, RCON
  stats/          integrations (Nerd Stats), pushed metrics; stats/provider = data sources
  admin/ auth/    admins with personal codes, login lock, global settings
  realtime/       Server-Sent Events: "topic X changed" → clients reload
  web/            REST controllers (public, admin, push)
  demo/           demo seed
src/main/resources/db/migration/   Flyway migrations
```

### Main business rules

- **Automatic banner:** a tournament with *registration open* and a *registration deadline* gets a banner
  "Anmeldung X schliesst um HH:MM" from 30 minutes before the deadline until the deadline
  (`BannerService.REGISTRATION_WARNING`). The frontend shows a live countdown.
- **Schedule status:** done / live / next / planned is computed on the server. An item without an end runs until
  the next item starts (the last one runs for 1 h). Items before 06:00 belong to the previous LAN day.
- **LIVE tag:** a running Challonge tournament plus its current round ("CS2 5v5 – Viertelfinal"). If none is
  running, the tag shows the live schedule item.
- **Seat map:** `beamerSide` LEFT/RIGHT draws rows as vertical columns. Changing the layout keeps assignments for
  every seat label that still exists.

### API overview

| Area | Endpoints |
|---|---|
| Public (`/api/public`) | `event`, `banners`, `live`, `schedule`, `servers`, `tournaments[/{id}]`, `POST tournaments/{id}/registrations`, `seats`, `POST seats/{label}/requests`, `stats`, `stream` (SSE), `events/{id}/logo` |
| Auth (`/api/auth`) | `POST login {code}`, `POST logout`, `GET me`, `GET csrf` |
| Admin (`/api/admin`) | `events[/{id}]` (+ `activate`, `logo`, `infos`, `announcements`, `servers`, `schedule`, `tournaments`, `seats`, `integrations`, `metrics`), `settings`, `admins`, `integration-types` |
| Push (`/api/push`) | `PUT metrics` with `Authorization: Bearer <token>` |

Admin requests use a session cookie. State-changing requests need the `X-XSRF-TOKEN` header (value from the
`XSRF-TOKEN` cookie).

### Challonge

1. On challonge.com go to *Settings → Developer API* and copy the API key into **Admin → Integrationen**.
2. Create the tournament on Challonge and enter its URL in the tournament settings
   (`https://challonge.com/xyz` or `xyz`; community tournaments `https://org.challonge.com/xyz` also work).
3. Sign-ups on the dashboard are pushed to Challonge as participants. The bracket is reloaded every 30 seconds
   and drawn in the dashboard's design. *Sync* and *Starten* in the admin area trigger this right away.

Scores are entered on Challonge (app or website).

### Nerd Stats: integrations

| Type | Config | Data |
|---|---|---|
| `uptime-kuma` | base URL + slug of a **public status page** | monitors, last 48 checks, 24 h uptime, ping |
| `minecraft` | host, port, optional RCON port and password | players, version, MOTD, player names; with RCON also TPS and in-game day |

Game servers with a query type (Source / Minecraft / Quake 3) are also queried directly for the player counts on
the overview.

**Adding a new data source:** write a Spring bean that implements `stats.provider.IntegrationProvider` (`type()`,
`label()`, `configFields()`, `fetch(config)`). Then register a widget for the same `type` in the frontend
(`src/widgets/registry.tsx`). Without a widget, the frontend falls back to a generic key/value view.

### Push API (router, monitoring scripts …)

```bash
curl -X PUT http://<server>/api/push/metrics \
  -H "Authorization: Bearer <token from Admin → Integrationen>" \
  -H "Content-Type: application/json" \
  -d '[{"key":"clients","label":"LAN-Clients online","value":"87"},
       {"key":"internet","label":"Internet ↓ / ↑","value":"742","unit":"/ 98 Mbit/s","sort":1}]'
```

Every key becomes a tile on Nerd Stats. Sending the same key again updates the tile.
