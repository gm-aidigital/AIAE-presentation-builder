# Project Structure

## Repository root

Claude entrypoint files live at the repository root.

Current top-level directories:

- `backend/` — Java 21 Spring Boot backend covered by backend architecture, database, OpenAPI, and test rules
- `frontend/` — React + TypeScript frontend covered by frontend architecture, style, API, auth, and test rules
- `scripts/` — repository scripts
- `.claude/` — repository-local engineering guidance and task workflow artifacts

## Backend package root

All backend production Java code lives under:

```text
com.aidigital.operationalhub.*
```

Do not introduce `com.example`, `org.example`, `io.replit`, or a second package root.

## Backend Maven modules

### `backend/domain`

- Owns JPA entities and Spring Data repositories only.
- Leaf module.
- No business orchestration, controllers, HTTP clients, or Liquibase changelogs.

### `backend/db`

- Owns Liquibase changelogs and SQL resources only.
- No services, controllers, or repositories.

### `backend/external-services`

- Owns outbound integrations and integration-specific configuration.
- Current example: NetSuite auth, token, and SuiteQL clients.
- No controller code and no core business orchestration.

### `backend/service`

- Owns business orchestration, per-entity services, RBAC services, validators, mappers, and exception model.
- `service/entity` contains the paired service for each entity/repository.
- `service/rbac` contains cross-entity orchestration that must depend on entity services, not repositories.

### `backend/application`

- Owns Spring Boot runtime configuration, security, controllers, OpenAPI contract implementations, application-level mappers, and exception translation.
- Controllers implement generated `*Api` interfaces.
- Generated OpenAPI sources under `backend/application/target/generated-sources/openapi` are build output and must not be edited manually.

## Backend layering rules

- `application` may call `service` contracts and application-level mappers.
- `service` may call `domain` through the paired entity services and may call `external-services` clients.
- `domain` does not depend on `service`, `application`, or `external-services`.
- Controllers never inject repositories.
- Cross-entity services never inject repositories directly.

## Frontend source layout

Use this layout for frontend code:

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

## Frontend ownership rules

- `frontend/src/app` owns application composition, providers, shell-level layout, app styles, and design tokens.
- `frontend/src/pages` owns route-level page composition when routing grows beyond a single page.
- `frontend/src/features/<feature-name>` owns feature-specific UI, hooks, API adapters, and tests.
- `frontend/src/entities/<entity-name>` owns reusable entity-specific view models/components when shared by multiple features.
- `frontend/src/shared/api` owns generated OpenAPI types, the typed API client, and low-level API helpers.
- `frontend/src/shared/auth` owns Clerk/session integration and auth-aware helpers.
- `frontend/src/shared/ui` owns reusable presentational primitives.
- `frontend/src/shared/lib` owns framework-neutral utilities.
- `frontend/src/shared/config` owns runtime config parsing and validation.
- `frontend/src/test` owns test setup and typed factories.

Do not create duplicate parallel structures for the same concern. Prefer moving code into the existing layer that already owns that responsibility.
