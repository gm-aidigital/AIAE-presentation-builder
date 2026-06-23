---
description: Backend test style rules for AIAE Presentation Builder.
paths:
  - "backend/application/src/test/**/*.java"
  - "backend/domain/src/test/**/*.java"
  - "backend/service/src/test/**/*.java"
  - "backend/external-services/src/test/**/*.java"
---

# Backend Test Rules

- Use JUnit 5.
- Use `should...Test()` naming.
- Structure tests with `// Given:`, `// When:`, and `// Then:`.
- `// When-Then:` is acceptable for direct exception assertions.
- Create fixtures inside each test method. Do not add common fixture methods or shared mutable setup.
- Use Instancio for entities, models, and DTOs instead of manual object creation whenever practical.
- `any()` / `anyList()` are forbidden for normal verification. Use `ArgumentCaptor`, `eq`, `same`, or explicit values.
- When a public service method delegates to another non-private method of the same class and the test needs isolation, mock that inner call via `spy(...)` and `doReturn` / `doThrow`. Production code keeps such methods package-private (never `private`) precisely so they stay spyable.
- Service-layer tests are pure Mockito unit tests.
- Repository tests with `@Sql` are worth writing only for custom or heavy queries, not for trivial derived methods.
