# Report Constructor

Internal tool that turns a marketing-campaign **Google Sheet** into a generated **Google Slides**
report — resolving campaign/tactic placeholders, matching line items, generating narrative copy with
Claude, and building native Sheets-linked charts. Migrated from a legacy PHP app onto the AI Digital
locked stack (Java 21 + Spring Boot + React/TS + Clerk SSO).

> **MVP / work-in-progress.** Scaffolded from the AI Digital Custom Template. Feature stages are
> implemented per `../reporting tool/migration-plan/` (Stage 0 = this scaffold; Stages 1–8 add auth,
> integrations, sheet read, matching, preview, generation, charts, hardening).

## Owner
- Product owner: gmozhayskiy@quimteq.com
- Tech contact: <fill in>

## What this is

A signed-in user (Clerk SSO, `@quimteq.com`) pastes a campaign brief, connects a Media Plan sheet
and an Elevate/BigQuery-export sheet, confirms an auto line-item match, optionally previews the
placeholder map, and clicks Generate. The backend resolves ~40 placeholders, (optionally) calls
Claude for narrative fields, clones a fixed Slides template, fills/trims it, builds charts, and
returns a link to the deck. Generation runs as a persisted async job, polled by id.

**Google access model:** a **service account** owns the generated decks and **shares** them
(writer) with the signed-in user's email. The Slides template, chart-template sheets, and the user's
source sheets must be shared with the service account.

## How to run

### Local developer machine
1. `cp .env.example .env` and fill values (Clerk keys, `AUTH_ALLOWED_EMAIL_DOMAIN=quimteq.com`,
   `AUTH_AUTHORIZED_PARTIES`, Google service-account JSON path, `CLAUDE_*`, `APP_REPORT_TEMPLATE_*`).
2. `docker compose --profile local up --build -d` (Postgres + backend + frontend).
3. Backend `http://localhost:8080/<context-path>/`, frontend `http://localhost:5173/`.
4. `docker compose --profile local down -v` to clean up.

Backend only (requires JDK 21): `mvn -f backend/application/pom.xml spring-boot:run` (profile `local`).
Frontend only: `cd frontend && npm ci && npm run generate:api && npm run dev`.

### Replit (deployment)
Fork/import, set Secrets (`CLERK_*`, `AUTH_*`, `PG*` auto-injected, `GOOGLE_WORKSPACE_*`, `CLAUDE_*`,
`APP_REPORT_TEMPLATE_*`, `USAGE_LOG_*`), click **Run** (backend 5000, Vite 5173). Deploy as
**Reserved VM** (not Autoscale). Copy Secrets into Deployment Secrets.

## API

- OpenAPI YAML: `/<context-path>/api/v1/specs/openapi.yaml`
- Swagger UI: `/<context-path>/swagger-ui/index.html`

| Method | Path | Purpose | Auth |
|---|---|---|---|
| `GET`  | `/api/v1/auth/me` | Current authenticated user. | Bearer JWT |
| `POST` | `/api/v1/sheets/read` | Read one tab of a Google Sheet. | Bearer JWT |
| `POST` | `/api/v1/line-items/match` | Auto-match tactics → line-item IDs. | Bearer JWT |
| `POST` | `/api/v1/placeholders/preview` | Compute the placeholder→value map (dry-run). | Bearer JWT |
| `POST` | `/api/v1/report-jobs` | Enqueue a Slides generation job. | Bearer JWT |
| `GET`  | `/api/v1/report-jobs/{jobId}` | Poll a generation job. | Bearer JWT |

## Required env vars

See `.env.example`. Real values live in Replit Secrets / local `.env` (gitignored). Never commit the
Google service-account JSON or any API key.

## MVP limitations
- Claude is OFF by default (`CLAUDE_ENABLED=false`) — narrative fields fall back to manual/"—" until a key is provisioned.
- Generated decks are owned by the service account and shared with the user (not in the user's own Drive).
- Monthly charts are feature-flagged off until their template object IDs are harvested/validated.
- Demo Clerk tenant + Replit-managed Postgres; UI strings are Russian (verbatim from the legacy tool).

## Architecture
Java 21 + Spring Boot 3 multi-module Maven (`application`/`service`/`domain`/`db`/
`event-logging-to-db-feature`/`external-services`), package root `com.aidigital.reportconstructor`.
React + TS + Vite frontend typed from this OpenAPI. Clerk SSO (JWTs validated vs Clerk JWKS).
PostgreSQL via Liquibase. JSON logs + Actuator + `usage_events`.
