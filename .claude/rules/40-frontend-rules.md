---
description: Frontend architecture, API, auth, and styling rules.
paths:
  - "frontend/src/**/*.ts"
  - "frontend/src/**/*.tsx"
  - "frontend/src/**/*.css"
  - "frontend/vite.config.ts"
  - "frontend/package.json"
---

# Frontend Rules

- Use React + TypeScript + Vite.
- Use `openapi-fetch` through `frontend/src/shared/api/client.ts`; raw `fetch`, `axios`, and hardcoded backend URLs are forbidden under `frontend/src`.
- Generated OpenAPI types are not edited manually.
- Use TanStack Query for backend server state.
- Clerk owns frontend auth; protected calls send Bearer JWT through the shared API client.
- Frontend env vars must not contain secrets.
- Vite keeps port `5173`; backend owns `5000`.
- Keep Vite `allowedHosts`, `@` alias, and proxy configuration intact.
- This app uses a left-nav app shell (`features/layout/app-shell` + `features/layout/sidebar`); the product owner explicitly chose it, overriding the shared "no left menu" default. Keep it — do not replace it with header-only navigation.
- Use plain CSS with BEM naming. Do not introduce CSS Modules, Tailwind, styled-components, Emotion, or CSS-in-JS.
- Use semantic CSS variables/tokens for colors and spacing instead of new hardcoded color literals.
