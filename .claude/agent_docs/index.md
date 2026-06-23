# AIAE Presentation Builder Claude Context Index

This directory contains repository-level Claude reference docs. Treat it as a self-contained enterprise rule set for this repository: do not rely on external repositories, generator folders, or machine-specific paths to understand the rules.

## Repository-local Claude layout

- `CLAUDE.md` is the repository entrypoint and should stay short.
- `.claude/agent_docs/` contains detailed repository context and engineering conventions.
- `.claude/rules/` contains concise always-on or path-scoped rules that Claude should load automatically. See `.claude/rules/README.md` for the naming/loading convention (always-on hard rules vs path-scoped topic rules).
- `.claude/skills/` contains reusable workflows and review gates: `task-workflow` (multi-role execution), `backend-rule-review`, `frontend-style-review`, `rule-compliance-audit` (whole-repo audit against the rule set), and `ui-designer` (visual-polish pass within the Elevate design system).
- `.claude/tasks/` stores plan, summary, review, and test artifacts for individual tasks.

## What to Read

- Read `project_structure.md` when locating modules, packages, generated code, frontend features, shared frontend utilities, or deciding where new classes/components belong.
- Read `building_the_project.md` before compiling, packaging, running Vite, or choosing Maven/npm commands.
- Read `running_tests.md` before adding or changing backend or frontend tests.
- Read `code_conventions.md` before changing backend production code.
- Read `database_schema.md` before touching backend JPA entities, repositories, JPQL/HQL, cache settings, or Liquibase.
- Read `service_architecture.md` before changing backend entity services, RBAC services, orchestrators, validators, or external-service boundaries.
- Read `frontend_architecture.md` before changing React components, frontend API access, auth flow, routing, feature layout, or Vite configuration.
- Read `frontend_style.md` before changing CSS, visual layout, component classes, or UI structure.
- Read `frontend_testing.md` before adding or changing frontend unit/component/API tests.

## Embedded Enterprise Rules

The rule groups are fully described in this repository-local `.claude` tree. Do not replace them with references to external paths. If a rule changes, update the local doc or rule file directly.

Key enterprise rule groups:

- Backend stack and package lock: Java 21, Spring Boot 3.x, Maven multi-module, PostgreSQL, `com.aidigital.operationalhub.*`.
- OpenAPI contract boundary: YAML is the contract, backend controllers implement generated interfaces, generated sources are not edited, frontend generated API types are not edited.
- Persistence baseline: PostgreSQL, Liquibase, `Long` ids, `TEXT` columns, JPA/Ehcache/Hikari configured at application level.
- Backend service boundaries: `1 entity = 1 repository = 1 service`, no repository access from controllers or cross-entity orchestration services.
- Backend configuration: use `@ConfigurationProperties`, not `@Value`.
- External integrations: outbound clients live under `backend/external-services`, use configurable HTTP clients, and keep credentials in configuration.
- Frontend stack: React + TypeScript + Vite, TanStack Query for server state, Clerk for auth, OpenAPI-generated types with `openapi-fetch`, and plain CSS with BEM.
- Frontend API boundary: no raw `fetch`, `axios`, `XMLHttpRequest`, or hardcoded backend URLs in `frontend/src`; all protected calls send Bearer JWT through the shared client.
- Frontend UI structure: this app uses a left-nav app shell (`features/layout/app-shell` + `features/layout/sidebar`), explicitly chosen for this app; keep it. Within pages use compact page headers, tabs, filter bars, segmented controls, and contextual toolbars.
- Frontend styling: BEM class names, semantic CSS custom properties, flat class selectors, no CSS Modules, Tailwind, styled-components, Emotion, or CSS-in-JS.
- Testing style: backend and frontend tests use `should ...` naming, `// Given:` / `// When:` / `// Then:` sections, local test setup, explicit assertions, and meaningful behavior coverage.
- Logging and observability: structured JSON logging expectations and centralized web/error handling.

## Maintenance Rules

- Keep this `.claude` tree self-contained.
- Add new enterprise rules here instead of pointing to another local repository.
- Keep `CLAUDE.md` short and route to these docs instead of duplicating them.
- Promote concise always-on or path-specific constraints into `.claude/rules/`.
