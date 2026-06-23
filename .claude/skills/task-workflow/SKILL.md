---
name: task-workflow
description: Multi-role iterative enterprise development workflow with context-isolated task artifacts
argument-hint: "<task ID or short description> [| <context prompt for the analytic role>]"
---

Run a structured multi-role enterprise development workflow for: **$ARGUMENTS**

Each role works through files in `.claude/tasks/<task>/`. The orchestrator should stay lightweight and use those files as the task state.

Parse `$ARGUMENTS` by splitting on the first ` | ` separator:
- **Part 1**: task identifier or short description
- **Part 2**: optional context for the analytic role

Derive `<task>` from Part 1:
- if it already looks like a task ID, keep it as-is
- otherwise slugify the description

## Step 1 — Analytic role

Create `.claude/tasks/<task>/plan.md`.

The plan must contain:

### 1. Context / Problem Statement
Describe the current behavior and the problem to solve.

### 2. Acceptance Criteria
Each item starts with `[ ]` and is testable.

### 3. Step-by-Step Implementation Plan
Numbered steps. Each step names the exact file paths to modify or create and explains why.

### 4. Test Coverage Plan
For this repository, consider only the test layers that actually exist.

Backend layers:
- service unit tests
- MVC/controller tests
- repository query tests for custom or heavy queries

Frontend layers:
- component/feature behavior tests
- API/client unit tests
- config/helper unit tests

If no dedicated `*IT` integration-test layer exists for this task, explicitly write:
`No dedicated integration-test layer in this repository.`

### 5. Verification Approach
Explain how to verify the implementation with compilation, tests, build checks, and manual checks if needed.

After writing the plan, ask the user to review it before implementation.
If the user requests changes, revise the same `plan.md` file.

## Step 2 — Developer role

Read `.claude/tasks/<task>/plan.md` in full before editing code.

Backend implementation rules:
- Java 21 + Spring Boot 3.x + Maven multi-module
- production code stays under `com.aidigital.operationalhub.*`
- controllers implement generated OpenAPI interfaces
- use `@ConfigurationProperties`, not `@Value`
- follow `1 entity = 1 repository = 1 service to work with that entity`
- orchestration services must not use repositories directly
- external integrations belong to `backend/external-services`
- do not edit generated sources under `backend/application/target/generated-sources`

Frontend implementation rules:
- React + TypeScript + Vite
- use TanStack Query for backend server state
- use Clerk through shared auth code for authentication
- use `openapi-fetch` through `frontend/src/shared/api/client.ts`
- do not use raw `fetch`, `axios`, `XMLHttpRequest`, or hardcoded backend URLs under `frontend/src`
- do not edit generated types under `frontend/src/shared/api/generated/schema.d.ts`
- use plain CSS with BEM class names and semantic CSS tokens
- do not introduce CSS Modules, Tailwind, styled-components, Emotion, or CSS-in-JS
- navigation is top/header-first; do not add a permanent left menu/sidebar/left rail unless explicitly requested
- frontend code must not access backend secrets, database credentials, service-account keys, or private API keys

Test rules for this repository:
- backend tests use JUnit 5; frontend tests use Vitest and Testing Library
- test method names use `should ...` phrasing; backend Java methods use `should...Test()`
- use `// Given:`, `// When:`, `// Then:` sections
- build behavior-relevant setup directly in each test
- avoid shared mutable fixtures and broad hidden setup
- prefer Instancio for backend entities/models/DTOs
- frontend typed factories are allowed for DTO defaults, but tests must override behavior-relevant fields locally
- backend `any()` / `anyList()` are forbidden for normal verification; use captors or explicit values
- frontend assertions should be explicit and should capture important calls instead of relying on loose matchers
- if a backend service test needs to isolate a non-private self-call, use `spy(new ...ServiceImpl(...))` with `doReturn` / `doThrow`

After creating any new file, stage it with git so it does not stay untracked.

Compile/check commands for touched areas:

```bash
mvn -f backend/pom.xml compile -pl <affected-modules> -am -q
cd frontend && npm run typecheck
```

When the OpenAPI contract changes, also run:

```bash
cd frontend && npm run generate:api
```

Then write `.claude/tasks/<task>/dev-summary.md` with:

```md
## Changed Files
- path — change summary

## Affected Areas
- backend/<module>
- frontend/<area>

## Tests Added or Expanded
- path — what was covered
- OR: None

## Summary
2-3 sentences describing the implementation.
```

## Step 3 — Reviewer role

Read `.claude/tasks/<task>/dev-summary.md`, inspect the diff, and review the changed files against repository rules.

Review at least these points:
1. Backend package root stays under `com.aidigital.operationalhub.*`
2. Controllers and orchestration services do not inject repositories directly
3. The backend `1 entity = 1 repository = 1 service` rule is preserved
4. Backend controllers still implement generated OpenAPI interfaces
5. Configuration uses `@ConfigurationProperties`, not `@Value`
6. Business codes are enums/constants, not magic inline literals
7. New backend logging follows structured JSON logging expectations if logging code was changed
8. Backend service interfaces/public methods keep JavaDoc where business semantics matter
9. Backend and frontend tests follow repository style
10. Generated backend sources and generated frontend API types were not edited manually
11. Frontend API access uses the shared OpenAPI client, not raw `fetch`/`axios`
12. Frontend styles use BEM/plain CSS/tokens and do not add forbidden styling frameworks
13. Frontend auth uses Clerk/shared auth code and never exposes secrets
14. Frontend navigation remains top/header-first unless the user explicitly requested another navigation model

Write `.claude/tasks/<task>/review-report.md` with:
- `STATUS: APPROVED` or `STATUS: CHANGES_REQUESTED`
- Summary
- Critical Issues
- Recommendations
- Positive Observations

If the review requests changes, return to the Developer role with the report as mandatory input.

## Step 4 — Tester role

Read `.claude/tasks/<task>/dev-summary.md` and run tests for the affected areas.

Backend default command:

```bash
mvn -f backend/pom.xml test -pl <module1>,<module2> -am
```

Frontend default commands:

```bash
cd frontend && npm test
cd frontend && npm run typecheck
```

When frontend build, Vite config, generated API types, or production asset behavior changed, also run:

```bash
cd frontend && npm run build
```

Check:
- whether tests pass
- whether changed classes/components have matching service/MVC/repository/component/API coverage where appropriate
- whether any important acceptance-criteria path is still untested

Write `.claude/tasks/<task>/test-report.md` with:
- `STATUS: PASSED` or `STATUS: FAILED`
- Test Run Results
- Failures (if any)
- Coverage Notes

If tests fail, return to the Developer role with the test report as mandatory input.

## Loop termination

The workflow ends when the review is approved and the test report is `STATUS: PASSED`, or when the user stops the workflow.

## Final summary

At the end, read `plan.md`, `dev-summary.md`, and `test-report.md`, then provide:
- Technical Resolution
- Business Resolution
- Commit Message

Commit message format:

```text
<TASK-ID-OR-SHORT-SLUG> <Short imperative description>
```

Do not assume any fixed ticket prefix unless the user explicitly supplied one.
