# Running Tests

## Backend test stack

- JUnit 5
- Mockito + `@ExtendWith(MockitoExtension.class)` for unit tests
- AssertJ for assertions
- Instancio for entities, models, and DTOs
- MockMvc standalone tests for controllers
- `@DataJpaTest` + embedded PostgreSQL for non-trivial repository queries

## Frontend test stack

- Vitest
- Testing Library for React behavior tests
- `@testing-library/user-event` for user interactions
- `@testing-library/jest-dom` for DOM assertions
- Typed DTO factories in `frontend/src/test/factories.ts`

## Current test layers in this repository

The backend currently uses:

- service unit tests
- controller/MVC tests
- repository query tests

The frontend currently uses:

- component/feature behavior tests
- API/client unit tests
- config/helper unit tests

There is currently **no dedicated `*IT` integration-test layer** in this repository.
Do not describe or require one unless the repository actually gains it.

## Backend unit test commands

```bash
mvn -f backend/pom.xml -pl service -am test
mvn -f backend/pom.xml -pl application -am test
mvn -f backend/pom.xml -pl domain -am test
```

## Full backend test commands

```bash
mvn -f backend/pom.xml test
mvn -f backend/pom.xml verify
```

## Frontend test commands

```bash
cd frontend && npm test
cd frontend && npm run typecheck
cd frontend && npm run build
```

Run `cd frontend && npm run generate:api` before typecheck/build when the backend OpenAPI YAML changes.

## Mandatory backend test style

- Use `should...Test()` naming.
- Structure every test with `// Given:`, `// When:`, and `// Then:` comments.
- For exception assertions, `// When-Then:` is acceptable when it keeps the test shorter and clearer.
- Create test data inside each test method. Do not use shared fixtures, common setup helpers, or reusable builder methods.
- Prefer Instancio for entities, models, and DTOs. Manual construction is acceptable only when the test must keep a JSON body or a tiny scalar request object explicit.
- `any()` / `anyList()` are forbidden for normal argument verification. Use `ArgumentCaptor`, `eq`, `same`, or explicit values.
- When a public service method calls another non-private method of the same class and the test needs isolation, instantiate the SUT as a Mockito `spy(...)` and stub the inner call with `doReturn` / `doThrow`.

## Mandatory frontend test style

- Use `it("should ...")` or `test("should ...")` naming.
- Structure every test with `// Given:`, `// When:`, and `// Then:` comments.
- Create behavior-relevant state inside each test. Do not hide scenario setup in shared mutable fixtures or broad `beforeEach` blocks.
- Shared typed DTO factories in `frontend/src/test/factories.ts` are allowed, but each test must override the fields that matter for the behavior it verifies.
- Prefer explicit expectations and captured calls over loose matchers.
- Reset mocks between tests when module-level mocks are used.
- Do not use snapshot-only tests as meaningful coverage.
- Mock network at the feature API/client boundary, not by changing production code.

## Service-layer backend tests

- Service tests are pure Mockito unit tests.
- Build the service under test directly inside each test method.
- Mock collaborators at the layer boundary.
- Do not reach around service boundaries into another entity's repository.
- Follow the project's architectural boundary: if a service coordinates another method of itself, use `spy`; if it coordinates another entity, mock that entity service.

## MVC backend tests

- Prefer `MockMvcBuilders.standaloneSetup(...)`.
- Register `GlobalExceptionHandler` explicitly when error mapping is part of the behavior under test.
- Keep controller tests at the contract boundary: request path, status, body, and collaboration arguments.

## Backend repository query tests

- Write `@DataJpaTest` only for custom or heavy queries worth protecting.
- Seed query scenarios with `@Sql` scripts.
- Use embedded PostgreSQL, not H2.
- Do not spend repository tests on trivial derived queries unless the query is business-critical.

## Frontend behavior tests

- Cover loading, error, empty, and success states for server-backed surfaces.
- Cover critical user actions with success and validation/error cases.
- Cover auth/session UI paths used by the real app.
- Cover role-dependent allowed and locked states.
- API wrapper tests verify request method, path, body, auth header behavior, and important error handling.
