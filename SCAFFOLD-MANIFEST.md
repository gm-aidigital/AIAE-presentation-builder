# Scaffold manifest — copy verbatim, never regenerate

Generators strip Replit-specific fixes. **Rule:** `cp` from `templates/generated-project/scaffold/`,
replace only `PACKAGE_REPLACE_ME` and app placeholders.

`PACKAGE_REPLACE_ME` → `com.aidigital.<app-name-package>` via
`bash scripts/apply-package-name.sh <app-name-package>`.

## Replit / root orchestration

| File | Notes |
|---|---|
| `.replit` | Materialized into generated project root by `materialize-project.sh` |
| `replit.nix` | Materialized into generated project root; JDK 21 + Node 22 + Postgres 16 |
| `docker-compose.yml` | `--profile local`, port 8080, Postgres health-check |
| `.env.example` | Full `AUTH_*`, `USAGE_LOG_*`, `CLERK_*`, `VITE_*` placeholders |
| `.gitignore` | Excludes control plane (`.agents/`, `templates/`, etc.) |

## Backend (copy from scaffold/backend/)

| File | Notes |
|---|---|
| `backend/pom.xml` | Java 21, Spring Boot 3.4, pluginManagement |
| `backend/application/pom.xml` | `db` dep, openapi-generator, PostgreSQL driver |
| `backend/service/pom.xml` | No web/security deps |
| `backend/domain/pom.xml`, `backend/db/pom.xml` | Leaf modules |
| `backend/.../web/SpaFallbackController.java` | Deployment deep links |

## Frontend (copy from scaffold/frontend/)

| File | Notes |
|---|---|
| `vite.config.ts` | `allowedHosts`, `@/` alias, port 5173, static outDir |
| `package.json` | `generate:api`, `check:api`, `test` |
| `src/main.tsx` | Mounts `app/AppRoot` only — no ClerkProvider here |
| `src/app/AppRoot.tsx` | Router + AuthProvider + ProtectedRoute |
| `src/app/AppShell.tsx` | Top-header layout (no sidebar) |
| `src/shared/ui/AppHeader.tsx`, `PageHeader.tsx` | Elevate shell |
| `src/shared/hooks/useDebounce.ts` | Canonical debounce |
| `src/features/_template/` | Copyable feature module (panel + test) |
| `src/shared/api/client.ts` | Typed openapi-fetch boundary |

## Scripts (installed to project `scripts/` by setup-project.sh)

| Script | Role |
|---|---|
| `setup-project.sh` | onBoot: safe cleanup (no materialization without package name); prints next-step command |
| `apply-package-name.sh` | Package rename |
| `strip-scaffold-samples.sh` | Remove reference sample aggregate |
| `structure-lint.sh` | Architecture grep gate (`--scaffold` for template source) |
| `verify-gates.sh` | Shared runtime/publish grep gate (CI + local-verify) |
| `local-verify.sh` | Pre-push: lint + gates + mvn verify + frontend test/build |
| `ci-verify-scaffold.sh` | Template CI: materialize → strip samples → full verify |
| `replit-build.sh` / `replit-run.sh` | Deployment |

If you must regenerate (major upgrade): diff against this manifest — preserve every setting.
