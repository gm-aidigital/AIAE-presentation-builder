   # CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

AIAE Presentation Builder is an enterprise application workspace with a Spring Boot backend and a React frontend.

Current top-level areas:

- `backend/` — Java 21, Spring Boot 3.5, multi-module Maven backend
- `frontend/` — React + TypeScript + Vite frontend
- `.claude/` — self-contained Claude rules, docs, skills, and task artifacts for this repository

## Start Here

1. Read `.claude/agent_docs/index.md`.
2. For backend work, read the relevant docs before changing code:
   - `project_structure.md`
   - `building_the_project.md`
   - `running_tests.md`
   - `code_conventions.md`
   - `database_schema.md`
   - `service_architecture.md`
3. For frontend work, read the relevant docs before changing code:
   - `project_structure.md`
   - `building_the_project.md`
   - `running_tests.md`
   - `frontend_architecture.md`
   - `frontend_style.md`
   - `frontend_testing.md`
4. Respect `.claude/rules/*.md`. These rules are repository-local and must not depend on external files.

## Enterprise Hard Constraints

- Do not replace these repository-local rules with references to another local path or external project.
- Do not hand-edit generated backend OpenAPI sources or generated frontend OpenAPI types.
- Do not add dependencies casually. Use the existing stack and local patterns first.
- Keep secrets out of frontend code and public environment variables.

## Backend Hard Constraints

- Backend stack is fixed: Java 21, Spring Boot 3.x, Maven multi-module, PostgreSQL, Liquibase.
- Backend production code stays under `com.aidigital.operationalhub.*`.
- Backend controllers implement generated OpenAPI interfaces and stay thin.
- Backend JPA repositories are accessed only through their paired entity service.
- Backend tests follow the project style from `.claude/rules/20-tests.md`.

## Frontend Hard Constraints

- Frontend stack is fixed: React, TypeScript, Vite, TanStack Query, Clerk, `openapi-fetch`, plain CSS with BEM.
- Frontend API calls go through the generated OpenAPI client boundary under `frontend/src/shared/api`.
- Frontend navigation is a left-nav app shell (`features/layout/app-shell` + `features/layout/sidebar`), explicitly chosen for this app; keep it rather than reverting to header-only navigation.
- Frontend styles follow BEM and semantic CSS tokens; do not introduce Tailwind, CSS Modules, styled-components, Emotion, or CSS-in-JS.
- Frontend tests follow the project style from `.claude/rules/50-frontend-tests.md`.
