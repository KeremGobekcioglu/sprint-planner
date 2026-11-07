# Deploying Sprint Planner to a VPS

Stack: **Caddy** (HTTPS + static frontend + `/api` proxy) → **Spring Boot** → **PostgreSQL**, all in Docker.
Only ports 80/443 are exposed; the backend and database are internal-only.

## 1. One-time setup

1. **DNS**: create an `A` record for your domain (e.g. `planner.example.com`) pointing to the VPS IP. Wait until `nslookup planner.example.com` shows it.
2. **VPS** (Ubuntu/Debian assumed):
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-v2 git ufw
   sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443 && sudo ufw enable
   ```
   Also: SSH keys only (`PasswordAuthentication no` in `/etc/ssh/sshd_config`), and a VPS with **≥ 2 GB RAM** (1 GB works only with swap).
3. **Code**: `git clone <your repo> && cd sprint-planner`
4. **Config**: `cp .env.prod.example .env.prod`, then edit it. Generate secrets with `openssl rand -base64 48`.
   Required: `DOMAIN`, `PGPASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`.
5. **Start**:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
   docker compose -f docker-compose.prod.yml logs -f backend   # wait for "Started SprintPlannerApplication"
   ```
6. Open `https://your-domain` → sign up / log in. Caddy fetches the certificate on first request (can take ~30 s).

## 2. Day-2 operations

| Task | Command |
|---|---|
| Update after `git pull` | `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build` |
| Logs | `docker compose -f docker-compose.prod.yml logs -f backend` |
| Close public signups | set `REGISTRATION_ENABLED=false` in `.env.prod`, re-run `up -d` |
| DB backup | `docker compose -f docker-compose.prod.yml exec -T db pg_dump -U sprintplanner sprintplanner \| gzip > backup-$(date +%F).sql.gz` |
| DB restore | `gunzip -c backup.sql.gz \| docker compose -f docker-compose.prod.yml exec -T db psql -U sprintplanner sprintplanner` |

Put the backup command in `crontab -e` (daily) and copy the files off the server occasionally.
Database schema changes are applied automatically by Flyway on startup.

## 3. What protects the deployment

- **Tenant isolation**: every team/sprint/task endpoint checks ownership (`AccessGuard`); other users' IDs return 404.
- **Admin**: created only from `ADMIN_EMAIL`/`ADMIN_PASSWORD`; public signups are always `USER` in prod. The dev seeder (`dev@sprint.local` / `dev123`) only runs in the `dev` profile - the Docker image forces `prod`.
- **Brute force**: login/register limited to 10 requests/min per IP.
- **Gemini quota protection** (`AiUsageLimiter`): per-user hourly/daily caps and a global daily cap. A full plan costs 4 units, guidance/retrospective 1 unit. Tune with `AI_*` variables. Exceeding a cap returns HTTP 429 with a readable message.
- **Input caps** on plan requests (epic count/length, imported context size).
- Actuator exposes only `/actuator/health`; error responses never include stack traces; JWT tokens are no longer written to logs.
- Caddy adds HSTS, CSP, `X-Frame-Options`, etc.

## 4. Gemini free tier

Free-tier quotas are per Google project, differ per model, and Google has changed them several times - **check your real numbers** at https://aistudio.google.com (Rate limits / usage). One sprint plan makes ~3-5 Gemini requests (breakdown, estimation, distribution, plus retries/fallbacks), guidance and retrospective one each. For a portfolio demo with a handful of visitors this is normally fine as long as the daily caps stay below your quota; set `AI_GLOBAL_DAILY` accordingly (units ≈ requests). Do not put a billing-enabled key on a public demo without those caps.

## 5. Known limitations (accepted for a demo)

- JWT is kept in `localStorage` (mitigated by the strict CSP); tokens last 24 h and cannot be revoked individually.
- Trello tokens are stored unencrypted in the database.
- Rate limiters are in-memory (reset on restart, single instance only).
- No email verification / password reset flow.
