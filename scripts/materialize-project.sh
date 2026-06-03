#!/usr/bin/env bash
#
# materialize-project.sh — mechanically copy the scaffold into a generated project.
#
# Usage (from generated project root):
#   bash scripts/materialize-project.sh <app-name-package>
#
# Environment:
#   MATERIALIZE_DEST   — target directory (default: parent of scripts/)
#   SCAFFOLD_ROOT      — override scaffold source path
#   TEMPLATE_REPO_ROOT — repo root for .github/workflows/ci.yml + .template-version

set -euo pipefail

APP_NAME="${1:?Usage: materialize-project.sh <app-name-package>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEST="${MATERIALIZE_DEST:-$(cd "${SCRIPT_DIR}/.." && pwd)}"

fail() {
  echo "materialize-project: $*" >&2
  exit 1
}

resolve_scaffold() {
  if [ -n "${SCAFFOLD_ROOT:-}" ]; then
    printf '%s' "${SCAFFOLD_ROOT}"
    return
  fi
  local here="${SCRIPT_DIR}/.."
  if [ -f "${here}/backend/pom.xml" ] && [ -d "${here}/frontend" ]; then
    printf '%s' "${here}"
    return
  fi
  local from_template="${SCRIPT_DIR}/../../templates/generated-project/scaffold"
  if [ -f "${from_template}/backend/pom.xml" ]; then
    printf '%s' "${from_template}"
    return
  fi
  fail "Cannot locate scaffold source — set SCAFFOLD_ROOT"
}

resolve_template_repo() {
  if [ -n "${TEMPLATE_REPO_ROOT:-}" ]; then
    printf '%s' "${TEMPLATE_REPO_ROOT}"
    return
  fi
  local candidate
  candidate="$(cd "${SCRIPT_DIR}/../../../.." 2>/dev/null && pwd || true)"
  if [ -n "${candidate}" ] && [ -f "${candidate}/replit.md" ]; then
    printf '%s' "${candidate}"
    return
  fi
  candidate="$(cd "${SCRIPT_DIR}/../../.." 2>/dev/null && pwd || true)"
  if [ -n "${candidate}" ] && [ -f "${candidate}/templates/generated-project/scaffold/backend/pom.xml" ]; then
    printf '%s' "${candidate}"
    return
  fi
  printf '%s' ""
}

SCAFFOLD="$(resolve_scaffold)"
TEMPLATE_REPO="$(resolve_template_repo)"

RSYNC_EXCLUDES=(
  --exclude node_modules
  --exclude target
  --exclude generated-sources
  --exclude 'backend/application/src/main/resources/static'
  --exclude tsconfig.tsbuildinfo
)

RUNTIME_SCRIPTS=(
  replit-build.sh
  replit-run.sh
  local-verify.sh
  structure-lint.sh
  verify-gates.sh
  apply-package-name.sh
  strip-scaffold-samples.sh
  materialize-project.sh
  setup-project.sh
  configure-clerk-development.sh
  docker-local-smoke.sh
  docker-context-path-smoke.sh
)

ALREADY_MATERIALIZED=0
if [ -f "${DEST}/backend/pom.xml" ] && [ -f "${DEST}/.template-version" ]; then
  ALREADY_MATERIALIZED=1
fi

echo "==> Materialize scaffold from ${SCAFFOLD} to ${DEST}"
mkdir -p "${DEST}/scripts"

rsync -a "${RSYNC_EXCLUDES[@]}" "${SCAFFOLD}/backend/" "${DEST}/backend/"
rsync -a "${RSYNC_EXCLUDES[@]}" "${SCAFFOLD}/frontend/" "${DEST}/frontend/"

for file in .env.example .gitignore docker-compose.yml .replit replit.nix; do
  if [ -f "${SCAFFOLD}/${file}" ]; then
    cp "${SCAFFOLD}/${file}" "${DEST}/${file}"
  fi
done

if [ -f "${SCAFFOLD}/README.md.template" ] && [ ! -f "${DEST}/README.md" ]; then
  cp "${SCAFFOLD}/README.md.template" "${DEST}/README.md"
fi

for script in "${RUNTIME_SCRIPTS[@]}"; do
  if [ -f "${SCAFFOLD}/scripts/${script}" ]; then
    cp "${SCAFFOLD}/scripts/${script}" "${DEST}/scripts/${script}"
    chmod +x "${DEST}/scripts/${script}"
  fi
done
if [ -d "${SCAFFOLD}/scripts/lib" ]; then
  mkdir -p "${DEST}/scripts/lib"
  cp "${SCAFFOLD}/scripts/lib/"* "${DEST}/scripts/lib/"
  chmod +x "${DEST}/scripts/lib/"*.sh 2>/dev/null || true
fi

GENERATED_CI="${SCAFFOLD}/../.github/workflows/ci.yml"
if [ -f "${GENERATED_CI}" ]; then
  mkdir -p "${DEST}/.github/workflows"
  cp "${GENERATED_CI}" "${DEST}/.github/workflows/ci.yml"
elif [ -n "${TEMPLATE_REPO}" ] && [ -f "${TEMPLATE_REPO}/templates/generated-project/.github/workflows/ci.yml" ]; then
  mkdir -p "${DEST}/.github/workflows"
  cp "${TEMPLATE_REPO}/templates/generated-project/.github/workflows/ci.yml" "${DEST}/.github/workflows/ci.yml"
fi

version_file="${DEST}/.template-version"
if [ -n "${TEMPLATE_REPO}" ] && [ -d "${TEMPLATE_REPO}/.git" ]; then
  git -C "${TEMPLATE_REPO}" rev-parse HEAD > "${version_file}" 2>/dev/null \
    || date -u +"%Y-%m-%dT%H:%M:%SZ" > "${version_file}"
else
  date -u +"%Y-%m-%dT%H:%M:%SZ" > "${version_file}"
fi

cd "${DEST}"

echo "==> Apply package name ${APP_NAME}"
bash scripts/apply-package-name.sh "${APP_NAME}"

if [ "${ALREADY_MATERIALIZED}" -eq 0 ]; then
  echo "==> structure-lint + verify-gates"
  STRUCTURE_LINT_ALLOW_SAMPLE=1 bash scripts/structure-lint.sh
  bash scripts/verify-gates.sh
else
  echo "==> rematerialize: skipping verify-gates (project already materialized)"
fi

echo "==> materialize-project: passed"
