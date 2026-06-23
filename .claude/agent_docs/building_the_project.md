# Building The Project

All commands below assume the repository root:

## Common backend commands

```bash
mvn -f backend/pom.xml clean verify
mvn -f backend/pom.xml -pl service -am test
mvn -f backend/pom.xml -pl domain -am test
mvn -f backend/pom.xml -pl application -am test
mvn -f backend/pom.xml -pl application -am package
mvn -f backend/pom.xml -pl application -am spring-boot:run -Dspring-boot.run.profiles=local
```

## Common frontend commands

```bash
cd frontend && npm run generate:api
cd frontend && npm run typecheck
cd frontend && npm test
cd frontend && npm run build
cd frontend && npm run dev
```

Run `npm run generate:api` before frontend typecheck/build when the backend OpenAPI YAML changes.

## Build behavior to remember

- Backend Checkstyle runs in `validate`.
- Backend OpenAPI interfaces are generated during `backend/application` `generate-sources`.
- Backend generated OpenAPI sources are build output and must not be edited manually.
- Frontend OpenAPI types are generated into `frontend/src/shared/api/generated/schema.d.ts` and must not be edited manually.
- Surefire runs JUnit 5 backend tests and enables parallel execution.
- Do not assume a dedicated backend integration-test layer unless the repository actually contains one.
- JaCoCo enforces backend line coverage during `test`.
- Vite dev server owns port `5173`; backend runtime owns port `5000`.

## Fast local loop

Backend compile/debug loop:

```bash
mvn -f backend/pom.xml -DskipTests package
```

Frontend type/debug loop:

```bash
cd frontend && npm run typecheck
```

Use fast loops only while iterating. Final work must pass the relevant real tests and build checks for the touched areas.
