#!/usr/bin/env bash
#
# ci-verify-scaffold.sh — template CI: materialize scaffold as a real project,
# apply package name, run gates + full verify (reference sample aggregate kept).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
SCAFFOLD="${REPO_ROOT}/templates/generated-project/scaffold"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

echo "==> Materialize scaffold to ${WORK}"
SCAFFOLD_ROOT="${SCAFFOLD}" \
  TEMPLATE_REPO_ROOT="${REPO_ROOT}" \
  MATERIALIZE_DEST="${WORK}" \
  bash "${SCAFFOLD}/scripts/materialize-project.sh" replitmvp

echo "==> Backend mvn verify"
cd "${WORK}"
mvn -f backend/pom.xml -B -Dgit-commit-id.skip=true \
  -Djacoco.line.coverage=0.00 -Djacoco.branch.coverage=0.00 \
  verify

echo "==> Frontend test + build"
NPM_BIN="$(pwd)/backend/application/target/frontend-toolchain/node/npm"
if [ -x "${NPM_BIN}" ]; then
  export PATH="$(dirname "${NPM_BIN}"):${PATH}"
else
  NPM_BIN="npm"
fi
( cd frontend && \
  { [ -f package-lock.json ] && "${NPM_BIN}" ci --no-audit --no-fund || "${NPM_BIN}" install --no-audit --no-fund; } && \
  "${NPM_BIN}" test && "${NPM_BIN}" run build )

echo "==> ci-verify-scaffold: passed"
