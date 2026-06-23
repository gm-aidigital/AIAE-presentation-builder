---
description: Frontend unit test rules.
paths:
  - "frontend/src/**/*.test.ts"
  - "frontend/src/**/*.test.tsx"
  - "frontend/src/test/**/*.ts"
---

# Frontend Test Rules

- Use Vitest and Testing Library.
- Test names use `should ...` phrasing.
- Structure tests with `// Given:`, `// When:`, and `// Then:`.
- Keep behavior setup inside each test; avoid common mutable fixtures.
- Shared typed DTO factories are allowed, but tests must override behavior-relevant fields locally.
- Prefer explicit assertions and captured calls over loose matchers.
- Reset mocks between tests when module-level mocks are used.
- Cover loading, error, empty, and success states for server-backed surfaces.
- Cover critical user actions with success and validation/error cases.
- Do not use snapshot-only tests as meaningful coverage.
