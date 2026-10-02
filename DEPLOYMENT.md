# Deployment – LAN Party Dashboard

Backend and frontend are two separate Docker images:

| Repo | Image |
| --- | --- |
| [`lan-party-dashboard-backend`](https://github.com/priisig/lan-party-dashboard-backend) | `ghcr.io/priisig/lan-party-dashboard-backend` |
| [`lan-party-dashboard-frontend`](https://github.com/priisig/lan-party-dashboard-frontend) | `ghcr.io/priisig/lan-party-dashboard-frontend` |

## Flow

```
git push            → ci.yml       tests (GitHub runner)
git tag vX.Y.Z      → release.yml  ├─ build:  build the image, push it to ghcr.io (GitHub runner)
                                   └─ deploy: on the server (self-hosted runner, label "homelab")
                                              set BACKEND_VERSION or FRONTEND_VERSION in
                                              /docker/lan-party-dashboard/.env, restart the service
```

The server runs `deploy/docker-compose.yml` with three containers:

```
Internet/LAN ── reverse proxy (TLS) ── :FRONTEND_PORT ── lan-party-frontend (nginx)
                                                           ├─ /      React app (SPA)
                                                           └─ /api/  ── lan-party-backend :8080 ── lan-party-db
```

Only the frontend container publishes a port. App and API must share one origin (session cookie,
`SameSite=Lax`, `Secure` with `COOKIE_SECURE=true`).

Persistent data: volume `lan-party_pgdata` (database, including the uploaded event logos).

---

## Server setup

The steps assume the same Ubuntu server as the wedding site, where Docker, log rotation, ufw and the
`github-runner` user already exist (see `DEPLOYMENT.md` in `hochzeits-website-anouk-jan-backend`, steps 1–3).
On a fresh server, do those steps first.

### 1. App directory and `.env`

The repo is public, so the two files can be fetched directly on the server:

```bash
mkdir -p /docker/lan-party-dashboard && cd /docker/lan-party-dashboard
BASE=https://raw.githubusercontent.com/priisig/lan-party-dashboard-backend/main/deploy
curl -fsSL "$BASE/docker-compose.yml" -o docker-compose.yml
curl -fsSL "$BASE/.env.example"       -o .env
sed -i "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(openssl rand -hex 24)/" .env
sed -i "s/^LAN_BOOTSTRAP_ADMIN_CODE=.*/LAN_BOOTSTRAP_ADMIN_CODE=$(openssl rand -hex 4)/" .env
grep LAN_BOOTSTRAP_ADMIN_CODE .env          # note the code for the first login
chown -R github-runner: /docker/lan-party-dashboard
chmod 600 .env
```

In `.env`, adjust `FRONTEND_PORT` if needed (default `8081`, because the wedding site uses `8080`). Docker-published
ports bypass `ufw`: bind it to the LAN IP (e.g. `FRONTEND_PORT=192.168.1.20:8081`) and/or restrict access to the
reverse proxy in the network firewall. `BACKEND_VERSION`/`FRONTEND_VERSION` must stay as lines – the deploy
jobs replace them via `sed`.

### 2. Register two self-hosted runners

`priisig` is a personal account, so runners are registered **per repository**: one for the backend and one for
the frontend repo, both with label `homelab`.

For each repo: GitHub → *Settings → Actions → Runners → New self-hosted runner* → Linux / x64. The page shows the
current runner version, download link, checksum and a registration token (valid 1 h). Then:

```bash
sudo -iu github-runner
mkdir ~/runner-lan-party-backend && cd ~/runner-lan-party-backend   # or ~/runner-lan-party-frontend
# copy the download + checksum commands from the GitHub page, e.g.:
curl -o actions-runner.tar.gz -L https://github.com/actions/runner/releases/download/v<VERSION>/actions-runner-linux-x64-<VERSION>.tar.gz
tar xzf actions-runner.tar.gz
./config.sh --unattended \
  --url https://github.com/priisig/lan-party-dashboard-backend \
  --token <TOKEN-FROM-GITHUB-PAGE> \
  --name lan-party-backend \
  --labels homelab
exit
```

Install as a service (as root, in the runner directory):

```bash
cd /home/github-runner/runner-lan-party-backend
./svc.sh install github-runner
./svc.sh start
```

Same for the frontend repo in `~/runner-lan-party-frontend` with
`--url https://github.com/priisig/lan-party-dashboard-frontend --name lan-party-frontend`.

Check: both GitHub runner pages show the runner as **Idle**; `systemctl status 'actions.runner.*'` lists the new
services.

### 3. Access to ghcr.io

New packages on ghcr.io are private by default. The deploy jobs log in with their own `GITHUB_TOKEN` and may pull
their repo's image. For manual `docker compose` commands (first start, rollback) the `github-runner` user needs a
login with a *classic* personal access token with scope `read:packages` (likely already done for the wedding site):

```bash
sudo -iu github-runner
echo '<PAT>' | docker login ghcr.io -u priisig --password-stdin
```

Alternatively make both packages public (GitHub → *Packages* → package → *Package settings* → *Change visibility*),
then no login is needed for pulling.

> The deploy jobs overwrite this login with their short-lived token. Log in again for manual commands if needed.

### 4. First release

Locally in **both** repos:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Wait for both release workflows (GitHub → Actions). Once both images exist, start the whole stack once:

```bash
sudo -iu github-runner
cd /docker/lan-party-dashboard
docker compose pull
docker compose up -d
docker compose ps                                  # all "running", backend "healthy"
curl -I http://localhost:8081                      # 200, React app
curl http://localhost:8081/api/public/event        # JSON from the backend (404 until an event exists)
```

### 5. Reverse proxy (existing)

Add a new host in the existing reverse proxy:

- domain → `http://<server-ip>:8081` (or the chosen `FRONTEND_PORT`)
- force HTTPS, certificate (e.g. Let's Encrypt). Required: with `COOKIE_SECURE=true` the admin login only works
  over `https://`.
- **Server-Sent Events:** `/api/public/stream` stays open and must not be buffered, otherwise live updates arrive
  late or not at all. Nginx Proxy Manager → *Advanced*:
  ```
  proxy_buffering off;
  proxy_read_timeout 1h;
  ```
- upload limit at least **4 MB** (event logo up to 3 MB): `client_max_body_size 4m;`
- pass `X-Forwarded-Proto`, `X-Forwarded-For`, `X-Forwarded-Host` (default in most proxies). The login lock works
  per client IP, so `X-Forwarded-For` matters.

### 6. After the first start

1. Open `https://<domain>/admin` and log in with `LAN_BOOTSTRAP_ADMIN_CODE`.
2. Create personal codes for every admin under **Admin → Admins**. Afterwards you can clear
   `LAN_BOOTSTRAP_ADMIN_CODE` in `.env` (it only creates an admin if none exists).
3. Create the event under **Admin → Events** (see *Yearly workflow* in `README.md`), add the Challonge API key and
   push token under **Admin → Integrationen**.

### 7. Backups

Back up the database nightly, keep 14 days (logos are in the database, there are no other files):

```bash
mkdir -p /backup/lan-party && chmod 700 /backup/lan-party
cat > /usr/local/bin/lan-party-backup <<'EOF'
#!/bin/sh
set -eu
D=/backup/lan-party
TS=$(date +%F)
docker exec lan-party-db pg_dump -U lan -d lan_dashboard --clean --if-exists | gzip > "$D/db-$TS.sql.gz"
find "$D" -type f -mtime +14 -delete
EOF
chmod 755 /usr/local/bin/lan-party-backup
echo '45 3 * * * root /usr/local/bin/lan-party-backup' > /etc/cron.d/lan-party-backup
/usr/local/bin/lan-party-backup && ls -la /backup/lan-party
```

Also copy the backups off the server (NAS, rsync, rclone, …).

Restore:

```bash
cd /docker/lan-party-dashboard
docker compose stop backend
gunzip -c /backup/lan-party/db-YYYY-MM-DD.sql.gz | docker exec -i lan-party-db psql -U lan -d lan_dashboard
docker compose start backend
```

---

## Operations

**Release:** commit and push, then tag in the affected repo. Backend and frontend are versioned independently.

```bash
git tag v0.2.0 && git push origin v0.2.0
```

**Rollback:** put an older version into `/docker/lan-party-dashboard/.env` and restart the service (database
migrations are not rolled back):

```bash
cd /docker/lan-party-dashboard
sed -i 's/^BACKEND_VERSION=.*/BACKEND_VERSION=0.1.0/' .env
docker compose up -d backend
```

**Logs / status:**

```bash
docker compose ps
docker compose logs -f backend      # or frontend, db
```

**Updates:** Postgres patch versions with `docker compose pull db && docker compose up -d db`. Runners update
themselves.

**Compose file changed:** fetch `docker-compose.yml` again (step 1, only the `curl` line for it), compare new
variables with `.env.example`, then `docker compose up -d`.

## Troubleshooting

- **Deploy job stays at "Waiting for a runner"** – the runner of *that* repo is offline or lacks the label
  `homelab`: `systemctl status 'actions.runner.*'`.
- **`sed: can't read .env`** in the deploy job – `/docker/lan-party-dashboard/.env` is missing or not owned by
  `github-runner`.
- **`denied` on `docker compose pull`** – manual: log in again with the PAT (step 3).
- **Admin login works, but you are logged out right away** – the page is opened via `http://` instead of
  `https://`; the cookie is `Secure`. For plain-HTTP access set `COOKIE_SECURE=false`.
- **Live updates are delayed / "Läuft gerade" only updates every 30 s** – the reverse proxy buffers the SSE stream
  (step 5).
- **Logo upload fails with 413** – upload limit in the reverse proxy too small (step 5).
- **Backend not healthy** – `docker compose logs backend`; often a wrong `POSTGRES_PASSWORD` after changing it
  (it is only applied to an empty DB volume).
