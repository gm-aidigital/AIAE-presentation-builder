# Frontend Testing

## Test stack

- Vitest.
- Testing Library for React behavior tests.
- `@testing-library/user-event` for user interactions.
- `@testing-library/jest-dom` for DOM assertions.

## Commands

Run from the repository root:

```bash
cd frontend && npm test
cd frontend && npm run typecheck
cd frontend && npm run build
```

Run `npm run generate:api` before typecheck/build when the backend OpenAPI YAML changes.

## Unit test style

Frontend unit tests follow the same discipline as backend tests:

- Test names use `should ...` phrasing.
- Structure tests with `// Given:`, `// When:`, and `// Then:` comments.
- Build relevant test state inside each test method.
- Avoid shared mutable fixtures and broad `beforeEach` scenario setup.
- Shared typed factories in `frontend/src/test/factories.ts` are allowed for OpenAPI DTO defaults, but each test must override the fields that matter for that behavior locally.
- Prefer explicit expectations and captured calls over loose matchers.
- Reset mocks between tests when module-level mocks are used.
- Do not use snapshot-only tests as meaningful coverage.

## What to cover

- Main route renders without crashing.
- Auth/session state follows the same UI path used by the app.
- Primary server-backed surfaces cover loading, error, empty, and success states.
- Critical forms/actions cover successful outcome and at least one validation or error path.
- API wrapper tests verify request method/path/body and important auth/error behavior.
- Role-dependent UI must cover allowed and locked states.

## Test boundaries

- Mock network at the feature API/client boundary, not by changing production code.
- Do not call real Clerk, backend, database, or third-party services from frontend unit tests.
- Do not mock away the behavior that the test claims to verify.
