#!/usr/bin/env bash
#
# verify-gates.sh — shared publish/runtime grep gates for generated MVPs.
# Sourced by local-verify.sh; also used by template CI (see --scaffold-source / --template-replit).
#
# Usage:
#   bash scripts/verify-gates.sh                    # generated project (strict)
#   bash scripts/verify-gates.sh --scaffold-source  # template scaffold tree (allows PACKAGE_REPLACE_ME)
#   VERIFY_TEMPLATE_REPO_ROOT=/path/to/repo bash scripts/verify-gates.sh --template-replit

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

SCAFFOLD_SOURCE=0
TEMPLATE_REPLIT=0
for arg in "$@"; do
  case "${arg}" in
    --scaffold-source) SCAFFOLD_SOURCE=1 ;;
    --template-replit) TEMPLATE_REPLIT=1 ;;
  esac
done

if [ -n "${VERIFY_ROOT:-}" ]; then
  ROOT="${VERIFY_ROOT}"
elif [ -d "$(pwd)/backend" ] && [ "$(pwd)" != "${DEFAULT_ROOT}" ]; then
  ROOT="$(pwd)"
else
  ROOT="${DEFAULT_ROOT}"
fi

cd "${ROOT}"

ALLOW_PLACEHOLDER="${VERIFY_ALLOW_PACKAGE_PLACEHOLDER:-0}"
if [ "${SCAFFOLD_SOURCE}" -eq 1 ]; then
  ALLOW_PLACEHOLDER=1
fi

fail() {
  echo "verify-gates: $*" >&2
  exit 1
}

verify_replit_file() {
  local replit_file="$1"
  [ -f "${replit_file}" ] || fail "Missing ${replit_file}"
  ! grep -Eq 'spring-boot:run.*backend/pom.xml|-pl application -am .*spring-boot:run' "${replit_file}" \
    || fail "${replit_file}: run spring-boot:run from backend/application/pom.xml, not parent reactor"
  grep -q 'mvn -f backend/application/pom.xml' "${replit_file}" \
    || fail "${replit_file}: backend workflow must use backend/application/pom.xml"
  grep -q 'npm run generate:api' "${replit_file}" \
    || fail "${replit_file}: frontend workflow must run npm run generate:api before Vite"
  grep -q 'PORT = "5000"' "${replit_file}" \
    || fail "${replit_file}: [env] must keep PORT = \"5000\""
  grep -q 'localPort = 5000' "${replit_file}" && grep -q 'externalPort = 80' "${replit_file}" \
    || fail "${replit_file}: expose exactly backend 5000 -> externalPort 80"
  ! grep -Eq 'npm run dev.*--port 5000|vite.*--port 5000|localPort = 5173' "${replit_file}" \
    || fail "${replit_file}: Vite must not run on/expose 5000"
}

echo "==> verify-gates root=${ROOT} scaffold_source=${SCAFFOLD_SOURCE}"

if [ "${TEMPLATE_REPLIT}" -eq 1 ]; then
  repo_root="${VERIFY_TEMPLATE_REPO_ROOT:-$(cd "${SCRIPT_DIR}/../../../.." && pwd)}"
  verify_replit_file "${repo_root}/.replit"
  echo "==> verify-gates (template .replit): passed"
  exit 0
fi

if [ -f README.md ]; then
  grep -q '^## What this is' README.md \
      && grep -q '^## API' README.md \
      && grep -q 'Swagger UI' README.md \
      && grep -q 'OpenAPI YAML' README.md || {
    fail "README must describe the app and include API, Swagger UI, and OpenAPI YAML links"
  }
fi

if [ -f .replit ]; then
  verify_replit_file ".replit"
fi

if [ -f frontend/src/shared/api/client.ts ]; then
  ! grep -RInE '(baseUrl|apiBaseUrl|BASE_URL)[[:space:]]*[:=][[:space:]]*["'\'']/api/v1/?["'\'']' \
      frontend/src/shared/api frontend/src/shared/config >/dev/null || {
    fail "Frontend baseUrl must not be /api/v1; OpenAPI paths already include /api/v1"
  }
  ! grep -RInE '^VITE_API_BASE_URL=/api/v1/?$' .env.example frontend/.env* 2>/dev/null || {
    fail "VITE_API_BASE_URL must not be /api/v1"
  }
  ! grep -RInE 'fetch[[:space:]]*\(|from[[:space:]]+["'\'']axios["'\'']|axios\.|new[[:space:]]+XMLHttpRequest' \
      --include='*.ts' --include='*.tsx' frontend/src \
      --exclude='*.test.ts' --exclude='*.test.tsx' >/dev/null || {
    fail "Frontend must use shared/api/client.ts, not raw fetch/axios/XMLHttpRequest"
  }
  if [ -f frontend/src/app/AppRoot.tsx ]; then
    grep -q 'ProtectedRoute' frontend/src/app/AppRoot.tsx \
      || fail "AppRoot.tsx must wrap authenticated routes with ProtectedRoute"
  else
    grep -q 'ProtectedRoute' frontend/src/main.tsx \
      || fail "main.tsx must wrap routes with ProtectedRoute (or migrate to app/AppRoot.tsx)"
  fi
  ! grep -q 'ClerkProvider' frontend/src/main.tsx 2>/dev/null \
    || fail "main.tsx must not mount ClerkProvider; use app/AppRoot.tsx + AuthProvider"
fi

if [ -d frontend/src ]; then
  ! grep -RInE 'React dev server is running|Switch the preview pane to port 5173|Open on port 5173|built React app is served directly from port 5000' \
      frontend/src >/dev/null 2>&1 || fail "Frontend must render the real app, not a port-switch placeholder"
  ! grep -RInE 'Sidebar|SideNav|LeftNav|side-nav|side-menu|left-nav|left-menu|app__sidebar|layout__sidebar' \
      --include='*.ts' --include='*.tsx' --include='*.css' frontend/src >/dev/null \
    || fail "Frontend must not use a left side menu/sidebar"
fi

if [ -f backend/application/src/main/resources/api/v1/specs/openapi.yaml ] && [ -d frontend/src ]; then
  bash "${SCRIPT_DIR}/lib/check-api-client-paths.sh" \
    "backend/application/src/main/resources/api/v1/specs/openapi.yaml" \
    "frontend/src"
fi

if [ -f frontend/vite.config.ts ] && grep -q '"@/\*"' frontend/tsconfig.json 2>/dev/null; then
  grep -q 'resolve:' frontend/vite.config.ts && grep -q '"@"' frontend/vite.config.ts \
    || fail "vite.config.ts must define resolve.alias for @"
  grep -q 'CLERK_PUBLISHABLE_KEY' frontend/vite.config.ts \
    || fail "vite.config.ts must map CLERK_PUBLISHABLE_KEY into VITE_CLERK_PUBLISHABLE_KEY"
  grep -q 'VITE_CLERK_JWT_TEMPLATE' frontend/vite.config.ts \
    || fail "vite.config.ts must define VITE_CLERK_JWT_TEMPLATE"
fi

if [ "${SCAFFOLD_SOURCE}" -eq 1 ]; then
  backend_docker="backend/Dockerfile"
  frontend_docker="frontend/Dockerfile"
  if [ -f "${backend_docker}" ]; then
    grep -q 'eclipse-temurin:21-jre' "${backend_docker}" \
      || fail "backend/Dockerfile must use Java 21 JRE runtime"
    grep -q 'skip.frontend=true' "${backend_docker}" \
      || fail "backend/Dockerfile must build with -Dskip.frontend=true"
    grep -q 'COPY frontend/' "${backend_docker}" \
      && fail "backend/Dockerfile must not COPY frontend/"
    grep -q 'node:' "${backend_docker}" \
      && fail "backend/Dockerfile must not contain a Node stage"
    grep -q '\-Duser.timezone=UTC -Xmx1G \$JAVA_OPTS' "${backend_docker}" \
      || fail "backend/Dockerfile ENTRYPOINT must use UTC timezone, 1G heap, and JAVA_OPTS"
  fi
  if [ -f "${frontend_docker}" ]; then
    grep -q 'node:22' "${frontend_docker}" \
      || fail "frontend/Dockerfile must use Node 22 builder"
    grep -q 'nginx' "${frontend_docker}" \
      || fail "frontend/Dockerfile must use nginx runtime"
    grep -q 'ARG VITE_CLERK_PUBLISHABLE_KEY' "${frontend_docker}" \
      || fail "frontend/Dockerfile must declare VITE_CLERK_PUBLISHABLE_KEY build arg"
  fi
  if [ -f docker-compose.yml ]; then
    grep -q 'dockerfile: backend/Dockerfile' docker-compose.yml \
      || fail "docker-compose.yml must define a backend service"
    grep -q 'dockerfile: frontend/Dockerfile' docker-compose.yml \
      || fail "docker-compose.yml must define a frontend service"
    grep -q 'JAVA_OPTS' docker-compose.yml \
      || fail "docker-compose.yml must pass JAVA_OPTS to backend"
    grep -q 'VITE_CLERK_PUBLISHABLE_KEY' docker-compose.yml \
      || fail "docker-compose.yml must pass VITE_CLERK_PUBLISHABLE_KEY to frontend build"
  fi
  email_domain_authz="$(find backend/application/src/main/java -path '*/security/CompanyEmailDomainAuthorizationManager.java' -print -quit)"
  [ -n "${email_domain_authz}" ] \
    || fail "CompanyEmailDomainAuthorizationManager must ship in the scaffold"
  id_aware_entity="$(find backend/domain/src/main/java -path '*/domain/common/entities/IdAwareEntity.java' -print -quit)"
  [ -n "${id_aware_entity}" ] \
    || fail "IdAwareEntity must ship in the domain module"
fi

if [ -d backend/application/src/main/resources ]; then
  group_id="$(sed -n 's:.*<groupId>\(.*\)</groupId>.*:\1:p' backend/pom.xml | head -n 1)"
  if [ "${ALLOW_PLACEHOLDER}" -eq 1 ] && [ "${group_id}" = "PACKAGE_REPLACE_ME" ]; then
    echo "    PACKAGE_REPLACE_ME allowed (scaffold source)"
  else
    [[ "${group_id}" =~ ^com\.aidigital\.[a-z][a-z0-9]*$ ]] \
      || fail "backend/pom.xml groupId must be com.aidigital.<app-name-package>: got ${group_id}"
    ! grep -RInE '^package[[:space:]]+(org\.example|com\.example|io\.replit|demo|[a-z][a-z0-9_]*);' \
        backend/application/src/main/java backend/service/src/main/java backend/domain/src/main/java >/dev/null 2>&1 \
      || fail "Java packages must use com.aidigital.*"
    ! grep -RInE '^package[[:space:]]+' \
        backend/application/src/main/java backend/service/src/main/java backend/domain/src/main/java \
        | grep -Ev '^.*:package[[:space:]]+com\.aidigital\.' >/dev/null 2>&1 \
      || fail "Every Java package must start with com.aidigital."
  fi
  grep -q 'port:[[:space:]]*\${PORT:5000}' backend/application/src/main/resources/application-replit.yml \
    || fail "application-replit.yml must keep server.port: \${PORT:5000}"
  grep -Fq 'url: jdbc:postgresql://${PGHOST' backend/application/src/main/resources/application-replit.yml \
    || fail "application-replit.yml must set datasource URL from PGHOST/PGPORT/PGDATABASE"
  grep -Fq 'username: ${PGUSER' backend/application/src/main/resources/application-replit.yml \
    || fail "application-replit.yml must set spring.datasource.username from PGUSER"
  grep -Fq 'password: ${PGPASSWORD' backend/application/src/main/resources/application-replit.yml \
    || fail "application-replit.yml must set spring.datasource.password from PGPASSWORD"
  ! grep -RIn '^AUTH_[A-Z_]*:[[:space:]]*\${AUTH_' backend/application/src/main/resources >/dev/null \
    || fail "Do not relay AUTH_* env vars as top-level YAML keys"
  ! grep -RIn '^[[:space:]]*spring\.security\.oauth2\.resourceserver\.jwt\|^[[:space:]]*resourceserver:' \
      backend/application/src/main/resources >/dev/null \
    || fail "SecurityConfig owns JwtDecoder — do not set resourceserver.jwt in YAML"
  ! grep -RIn 'sslmode=require' backend/application/src/main/resources/application-replit.yml >/dev/null 2>&1 \
    || fail "application-replit.yml must not force sslmode=require"
  ! grep -RInE 'ReplitDatabaseUrlPostProcessor|EnvironmentPostProcessor.imports|spring.factories' \
      backend/application/src/main >/dev/null 2>&1 \
    || fail "Do not use ReplitDatabaseUrlPostProcessor"
  auth_constants="$(find backend/application/src/main/java -path '*/security/AuthConstants.java' -print -quit)"
  [ -n "${auth_constants}" ] \
      && grep -Fq '"/"' "${auth_constants}" \
      && grep -Fq '"/assets/**"' "${auth_constants}" \
      && grep -Fq '"/sign-in/**"' "${auth_constants}" \
    || fail "AuthConstants must keep React shell + static assets + Clerk routes public"
  spa_fallback="$(find backend/application/src/main/java -path '*/web/SpaFallbackController.java' -print -quit)"
  [ -n "${spa_fallback}" ] || fail "SpaFallbackController required for deployment deep links"
  if grep -RInE 'MockJwtDecoder|MockTokenService|ReplitOidcSecurityConfig|AUTH_MODE|/auth/mock/login' \
      backend frontend/src 2>/dev/null | grep -v '/target/' | grep -q .; then
    fail "Clerk SSO only — remove mock/Replit OIDC auth code"
  fi
  if grep -RInE 'app_user|app_user_role|user_roles' backend/db/src/main/resources 2>/dev/null | grep -q .; then
    fail "Do not create local auth tables — Clerk SSO only"
  fi
  for module in $(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' backend/pom.xml); do
    module_dir="backend/${module}"
    [ -d "${module_dir}" ] || fail "backend/pom.xml lists missing module: ${module}"
    # -print -quit keeps this pipefail-safe (no SIGPIPE from a piped `grep -q`).
    [ -n "$(find "${module_dir}/src" -type f ! -path '*/target/*' -print -quit 2>/dev/null)" ] \
      || fail "Maven module must not be empty/POM-only: ${module}"
  done
  if [ ! -f backend/external-services/pom.xml ]; then
    ! grep -RIn '<artifactId>external-services</artifactId>\|<module>external-services</module>' \
        backend/pom.xml backend/service/pom.xml >/dev/null 2>&1 \
      || fail "external-services is optional; do not reference it when no module exists"
  fi
  ! grep -RIn '@AuthenticationPrincipal' backend/application/src/main/java >/dev/null 2>&1 \
    || fail "Do not use @AuthenticationPrincipal on generated OpenAPI controllers"
  ! grep -RInE '(^|[^.[:alnum:]_])getRequest[[:space:]]*\(' backend/application/src/main/java >/dev/null 2>&1 \
    || fail "Do not call getRequest() from generated OpenAPI interfaces"
  ! grep -RIn '@Slf4j\|private static final Logger log\|Logger log' backend/application/src/main/java >/dev/null 2>&1 \
    || fail "Use explicit private static final Logger LOG in framework glue"
  auth_props="$(find backend/application/src/main/java -path '*/security/AuthProperties.java' -print -quit)"
  [ -z "${auth_props}" ] || ! grep -RInE '@(Getter|Setter|Data|Value|RequiredArgsConstructor)' "${auth_props}" >/dev/null 2>&1 \
    || fail "AuthProperties must use explicit getters/setters, not Lombok"
  logbook_config="$(find backend/application/src/main/java -path '*/config/LogbookConfig.java' -print -quit)"
  [ -z "${logbook_config}" ] || grep -Fq 'new DefaultSink(new JsonHttpLogFormatter(), new DefaultHttpLogWriter())' "${logbook_config}" \
    || fail "Logbook DefaultSink must be built with formatter + writer"
  usage_persistence="$(find backend/event-logging-to-db-feature/src/main/java -path '*/usagelogging/persistence/UsageEventPersistenceService.java' -print -quit 2>/dev/null || true)"
  if [ -n "${usage_persistence}" ]; then
    grep -Fq '@Async("usageLoggingExecutor")' "${usage_persistence}" \
      && grep -Fq '@Transactional(propagation = Propagation.REQUIRES_NEW)' "${usage_persistence}" \
      || fail "UsageEventPersistenceService must own @Async + REQUIRES_NEW persistence"
    usage_aspect="$(find backend/event-logging-to-db-feature/src/main/java -path '*/usagelogging/UsageLoggingAspect.java' -print -quit)"
    [ -z "${usage_aspect}" ] || ! grep -q '^[[:space:]]*@Transactional' "${usage_aspect}" \
      || fail "Do not put @Transactional on UsageLoggingAspect"
    usage_entity="$(find backend/event-logging-to-db-feature/src/main/java -path '*/usagelogging/entities/UsageEventEntity.java' -print -quit)"
    [ -z "${usage_entity}" ] || {
      grep -Fq '@JdbcTypeCode(SqlTypes.JSON)' "${usage_entity}" \
        && grep -Fq 'Map<String, Object> attributes' "${usage_entity}" \
        || fail "UsageEventEntity.attributes must be JSONB Map<String,Object>"
    }
  fi
  ! grep -RInE "LOWER\\(CONCAT\\('%',[[:space:]]*:[A-Za-z0-9_]+" \
      backend/domain/src/main/java backend/service/src/main/java >/dev/null 2>&1 \
    || fail "Nullable JPQL LIKE params must use prebuilt patterns, not LOWER(CONCAT('%', :param))"
  ! grep -RInE 'ResponseEntity<[[:space:]]*byte\[\]|ResponseEntity<byte\[\]>' backend/application/src/main/java >/dev/null 2>&1 \
    || fail "Use ResponseEntity<Resource> for binary downloads, not byte[]"
fi

if [ -f backend/application/pom.xml ]; then
  grep -q '<groupId>org.postgresql</groupId>' backend/application/pom.xml \
      && grep -q '<artifactId>postgresql</artifactId>' backend/application/pom.xml \
    || fail "application/pom.xml must declare the PostgreSQL runtime driver"
fi

if [ -f backend/service/pom.xml ]; then
  ! grep -Eq 'spring-boot-starter-(security|oauth2-resource-server|web)|spring-security|jakarta.servlet-api|jjwt-' \
      backend/service/pom.xml || fail "service/pom.xml must not depend on web/security/JWT"
  ! grep -RInE 'SecurityContextHolder|JwtDecoder|JwtAuthenticationToken|io\.jsonwebtoken|org\.springframework\.security|org\.springframework\.web|jakarta\.servlet' \
      backend/service/src/main/java >/dev/null 2>&1 \
    || fail "service source must not import web/security/JWT/servlet APIs"
fi

if [ -d backend ] && [ "${SCAFFOLD_SOURCE}" -eq 0 ]; then
  [ -n "$(find backend -path '*/src/test/java/*' \( -name '*Test.java' -o -name '*IT.java' \) -print -quit)" ] \
    || fail "Backend tests are required"
  [ -n "$(find backend/application/src/test/java -iname '*SmokeTest.java' -print -quit)" ] \
    || fail "Application smoke test is required"
  if [ -d backend/db/src/main/resources/db/changelog ]; then
    [ -n "$(find backend/application/src/test/java \( -iname '*Liquibase*Test.java' -o -iname '*Changelog*Test.java' \) -print -quit)" ] \
      || fail "Liquibase smoke test is required when db changelogs exist"
  fi
fi

if [ -d frontend/src ]; then
  while IFS= read -r debounce_hook; do
    grep -q 'useEffect' "${debounce_hook}" && grep -q 'clearTimeout' "${debounce_hook}" \
      || fail "useDebounce hooks must use useEffect with cleanup: ${debounce_hook}"
  done < <(find frontend/src -type f \( -name '*useDebounce*.ts' -o -name '*useDebounce*.tsx' \))
fi

if [ -d backend ] && [ -f "${SCRIPT_DIR}/lib/check-production-static-methods.sh" ]; then
  bash "${SCRIPT_DIR}/lib/check-production-static-methods.sh"
fi

if [ -d backend ] && [ -f "${SCRIPT_DIR}/lib/check-production-magic-values.sh" ]; then
  bash "${SCRIPT_DIR}/lib/check-production-magic-values.sh"
fi

echo "==> verify-gates: passed"
