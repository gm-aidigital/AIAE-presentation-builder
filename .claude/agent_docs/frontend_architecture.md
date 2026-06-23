# Frontend Architecture

## Stack

- React + TypeScript.
- Vite for development and production build.
- Vitest + Testing Library for behavior tests.
- TanStack Query for server state.
- Clerk React SDK for authentication.
- `openapi-typescript` + `openapi-fetch` for typed backend API access.
- Plain CSS with BEM naming for styling.

## Folder layout

Use this layout for new frontend code:

```text
frontend/src/app
frontend/src/pages
frontend/src/features
frontend/src/entities
frontend/src/shared/api
frontend/src/shared/auth
frontend/src/shared/ui
frontend/src/shared/lib
frontend/src/shared/config
frontend/src/test
```

Feature code belongs under `frontend/src/features/<feature-name>/` unless it is genuinely shared.
Shared primitives belong under `frontend/src/shared/`.
Do not create unrelated parallel structures for the same concern.

## Vite rules

- Vite dev and preview port stays `5173`.
- Backend runtime owns `5000`; Vite owns `5173`.
- `vite.config.ts` must keep explicit `server.allowedHosts` and `preview.allowedHosts` for proxied preview hosts.
- Runtime alias `@` must be configured in Vite, not only in `tsconfig.json`.
- API proxy target is configurable through environment variables, not hardcoded per component.
- The app UI must never render a placeholder telling the user to switch preview ports. The first screen is the real product UI.

## OpenAPI boundary

- OpenAPI YAML is the backend/frontend contract.
- Frontend generated types live in `frontend/src/shared/api/generated/schema.d.ts` and are not edited manually.
- `frontend/src/shared/api/client.ts` owns the `openapi-fetch` client and auth-aware request handling.
- Components and feature APIs must use the typed client. Raw `fetch`, `axios`, `XMLHttpRequest`, and hardcoded backend URLs are forbidden under `frontend/src`.
- `VITE_API_BASE_URL` and `VITE_API_CONTEXT_PATH` must not include `/api/v1` when OpenAPI path keys already include `/api/v1`.
- Run `npm run generate:api` before typecheck/build when the OpenAPI contract changes.

## Server state and async UI

- Use TanStack Query for backend reads and writes.
- Do not store server state in ad-hoc global state.
- Every server-backed UI surface must handle loading, empty, error, and success states.
- Mutations must invalidate or update the relevant queries explicitly.

## Auth

- Clerk is the frontend auth provider.
- Protected backend calls send `Authorization: Bearer <jwt>` through the shared API client.
- `/api/v1/auth/me` bootstraps the current application user.
- `401` routes the user back through the auth flow.
- `403` renders an access-denied or locked-state UI, not a generic crash.
- Frontend code never accesses service-account keys, database credentials, or backend secrets.

## Navigation and layout

- Navigation is top/header-first.
- Permanent left side menu, left rail, and sidebar navigation are forbidden unless the user explicitly asks for a new navigation model.
- Prefer page tabs, filter bars, segmented controls, and contextual toolbars.
- Operational screens should be dense, scannable, and work-focused rather than marketing-style landing pages.
