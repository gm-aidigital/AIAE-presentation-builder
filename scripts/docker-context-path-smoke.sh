#!/usr/bin/env bash
#
# docker-context-path-smoke.sh — verifies a non-empty APP_CONTEXT_PATH works.
# Run after docker-local-smoke passes with the default (empty) context path.
#
# Usage (from generated project root):
#   bash scripts/docker-context-path-smoke.sh
#
# Environment overrides:
#   APP_CONTEXT_PATH   — context path to test (default: /reviewapp)
#   SYNTHETIC_PK       — override the synthetic Clerk publishable key
#   COMPOSE_TIMEOUT    — seconds to wait for backend health (default: 90)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

APP_CONTEXT_PATH="${APP_CONTEXT_PATH:-/reviewapp}"
COMPOSE_TIMEOUT="${COMPOSE_TIMEOUT:-90}"
BACKEND_PORT="8080"
FRONTEND_PORT="5173"

if [ -z "${SYNTHETIC_PK:-}" ]; then
  SYNTHETIC_PK="pk_test_$(python3 -c "import base64; print(base64.urlsafe_b64encode(b'localhost.test\$').decode().rstrip('='))")"
fi

fail() { echo "docker-context-path-smoke: $*" >&2; exit 1; }

stop_compose() {
  echo "==> Stopping containers"
  APP_CONTEXT_PATH="${APP_CONTEXT_PATH}" docker compose --profile local down -v --remove-orphans 2>/dev/null || true
}
trap stop_compose EXIT

echo "==> docker-context-path-smoke: APP_CONTEXT_PATH=${APP_CONTEXT_PATH}"

CLERK_PUBLISHABLE_KEY="${SYNTHETIC_PK}" \
  VITE_CLERK_PUBLISHABLE_KEY="${SYNTHETIC_PK}" \
  VITE_CLERK_JWT_TEMPLATE="aidigital-api" \
  AUTH_ALLOWED_EMAIL_DOMAIN="aidigital.com" \
  AUTH_AUTHORIZED_PARTIES="http://localhost:${FRONTEND_PORT},http://localhost:${BACKEND_PORT}" \
  AUTH_ISSUER_URI="http://localhost:${BACKEND_PORT}${APP_CONTEXT_PATH}/fake-jwks-issuer" \
  AUTH_JWKS_URI="http://localhost:${BACKEND_PORT}${APP_CONTEXT_PATH}/fake-jwks-issuer/.well-known/jwks.json" \
  APP_CONTEXT_PATH="${APP_CONTEXT_PATH}" \
  JAVA_OPTS="-Duser.timezone=UTC" \
  docker compose --profile local up --build -d

# ── Wait for health ───────────────────────────────────────────────────────────
HEALTH_URL="http://localhost:${BACKEND_PORT}${APP_CONTEXT_PATH}/actuator/health"
echo "==> Waiting for health at ${HEALTH_URL}"
for i in $(seq 1 "${COMPOSE_TIMEOUT}"); do
  if curl -fsS "${HEALTH_URL}" >/dev/null 2>&1; then
    echo "    healthy after ${i}s"
    break
  fi
  if [ "${i}" -eq "${COMPOSE_TIMEOUT}" ]; then
    docker compose logs backend 2>&1 | tail -40
    fail "backend did not become healthy within ${COMPOSE_TIMEOUT}s at ${HEALTH_URL}"
  fi
  sleep 1
done

# ── Prometheus ────────────────────────────────────────────────────────────────
echo "==> Verifying Prometheus"
curl -fsS "http://localhost:${BACKEND_PORT}${APP_CONTEXT_PATH}/actuator/prometheus" | grep -q 'jvm_' \
  || fail "Prometheus endpoint missing jvm_* metrics"
echo "    Prometheus OK"

# ── OpenAPI YAML ──────────────────────────────────────────────────────────────
echo "==> Verifying OpenAPI YAML"
HTTP_CODE="$(curl -o /dev/null -sS -w '%{http_code}' \
  "http://localhost:${BACKEND_PORT}${APP_CONTEXT_PATH}/api/v1/specs/openapi.yaml" || echo "000")"
[ "${HTTP_CODE}" = "200" ] \
  || fail "OpenAPI YAML not accessible at context path (HTTP ${HTTP_CODE})"
echo "    OpenAPI YAML OK"

# ── Swagger UI ────────────────────────────────────────────────────────────────
echo "==> Verifying Swagger UI"
HTTP_SWAGGER="$(curl -o /dev/null -sS -w '%{http_code}' \
  "http://localhost:${BACKEND_PORT}${APP_CONTEXT_PATH}/swagger-ui/index.html" || echo "000")"
[ "${HTTP_SWAGGER}" = "200" ] \
  || fail "Swagger UI not accessible at context path (HTTP ${HTTP_SWAGGER})"
echo "    Swagger UI OK"

# ── Frontend API proxy ────────────────────────────────────────────────────────
echo "==> Verifying frontend /api proxy passes context path"
HTTP_PROXY="$(curl -o /dev/null -sS -w '%{http_code}' \
  "http://localhost:${FRONTEND_PORT}/api/v1/specs/openapi.yaml" || echo "000")"
[ "${HTTP_PROXY}" = "200" ] \
  || fail "Frontend /api proxy must reach OpenAPI at context path (HTTP ${HTTP_PROXY})"
echo "    /api proxy with context path OK"

# ── SPA deep link ─────────────────────────────────────────────────────────────
echo "==> Verifying SPA deep link"
SPA_BODY="$(curl -fsS "http://localhost:${FRONTEND_PORT}/some/deep/link" 2>/dev/null || true)"
echo "${SPA_BODY}" | grep -qi 'html' \
  || fail "SPA deep link did not return HTML (nginx try_files may be misconfigured)"
echo "    SPA deep link OK"

echo "==> docker-context-path-smoke: passed"
