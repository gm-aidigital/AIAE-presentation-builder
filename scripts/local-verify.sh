#!/usr/bin/env bash
#
# local-verify.sh — the one command engineering runs before any push.
# Mirrors the publish-gate part of mvp-safety-review. Exit non-zero on any failure.
#
# Steps:
#   1. structure-lint.sh + verify-gates.sh
#   2. Backend: `mvn -f backend/pom.xml verify`
#   3. Frontend: `npm test && npm run build`
#   4. docker-compose syntax check (does NOT run containers)

set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f scripts/structure-lint.sh ]; then
  bash scripts/structure-lint.sh
fi

bash scripts/verify-gates.sh

echo "==> Backend: mvn verify"
mvn -f backend/pom.xml -B verify

if [ -f frontend/package.json ]; then
  echo "==> Frontend: npm test + build"
  NPM_BIN="$(pwd)/backend/application/target/frontend-toolchain/node/npm"
  if [ -x "${NPM_BIN}" ]; then
    export PATH="$(dirname "${NPM_BIN}"):${PATH}"
  else
    NPM_BIN="npm"
  fi
  ( cd frontend && \
    { [ -f package-lock.json ] && "${NPM_BIN}" ci --no-audit --no-fund || "${NPM_BIN}" install --no-audit --no-fund; } && \
    "${NPM_BIN}" test && "${NPM_BIN}" run build )
fi

if [ -f docker-compose.yml ]; then
  echo "==> docker compose config (syntax check, no run)"
  docker compose --profile local config >/dev/null
fi

echo "==> local-verify.sh: all checks passed"
