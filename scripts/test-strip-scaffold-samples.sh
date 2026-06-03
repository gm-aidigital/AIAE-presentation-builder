#!/usr/bin/env bash
#
# test-strip-scaffold-samples.sh — template CI smoke: materialize, strip samples,
# verify sample removal (structure-lint strict). Does not run verify-gates after
# strip because domain/ is empty until the first real aggregate lands.
#
# Usage (from template repo root):
#   bash templates/generated-project/scaffold/scripts/test-strip-scaffold-samples.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
SCAFFOLD="${REPO_ROOT}/templates/generated-project/scaffold"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

echo "==> Materialize to ${WORK}"
SCAFFOLD_ROOT="${SCAFFOLD}" \
  TEMPLATE_REPO_ROOT="${REPO_ROOT}" \
  MATERIALIZE_DEST="${WORK}" \
  bash "${SCAFFOLD}/scripts/materialize-project.sh" replitmvp

echo "==> Strip reference sample aggregate"
bash "${WORK}/scripts/strip-scaffold-samples.sh"

cd "${WORK}"
! find backend/domain/src/main/java -type d -path '*/domain/sample' 2>/dev/null | grep -q . \
  || { echo "domain/sample still present after strip"; exit 1; }
! find backend/service/src/main/java -type d -path '*/service/sample' 2>/dev/null | grep -q . \
  || { echo "service/sample still present after strip"; exit 1; }
grep -q '0002-sample-reference.xml' backend/db/src/main/resources/db/changelog/db.changelog-master.xml \
  && { echo "0002-sample-reference still in master changelog"; exit 1; } || true

echo "==> structure-lint (strict — samples must be gone)"
bash scripts/structure-lint.sh

echo "==> test-strip-scaffold-samples: passed"
