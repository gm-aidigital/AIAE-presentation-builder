#!/usr/bin/env bash
#
# test-setup-project.sh — contract tests for setup-project.sh.
# Run from anywhere; creates and cleans up a temporary directory.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD="$(cd "${SCRIPT_DIR}/.." && pwd)"
TEMPLATE_ROOT="$(cd "${SCAFFOLD}/../../../.." && pwd)"
PASS=0
FAIL=0

pass() { echo "  PASS: $*"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $*" >&2; FAIL=$((FAIL + 1)); }

assert_exists()  { [ -e "$1" ]  && pass "$1 exists"       || fail "$1 should exist"; }
assert_absent()  { [ ! -e "$1" ] && pass "$1 absent"      || fail "$1 should be absent"; }
assert_file()    { [ -f "$1" ]  && pass "$1 is a file"     || fail "$1 should be a file"; }

setup_fork() {
  local dir
  dir="$(mktemp -d)"
  git init -q "${dir}"
  touch "${dir}/main.py" "${dir}/pyproject.toml" "${dir}/uv.lock"
  printf '[nix]\nchannel = "stable-24_11"\n' > "${dir}/.replit"
  printf '' > "${dir}/replit.nix"
  printf 'PLACEHOLDER\n' > "${dir}/.gitignore"
  echo "${dir}"
}

cleanup() {
  local dir="$1"
  rm -rf "${dir}"
}

pom_tree_hash() {
  local root="$1"
  find "${root}/backend" -name 'pom.xml' -print0 \
    | sort -z \
    | xargs -0 shasum -a 256 \
    | shasum -a 256 \
    | awk '{print $1}'
}

# ─── Test 1: safe cleanup without package name ────────────────────────────────
echo "==> Test 1: cleanup without app name succeeds"
T1="$(setup_fork)"
trap 'cleanup "${T1}"' EXIT

PROJECT_ROOT="${T1}" \
  SCAFFOLD_ROOT="${SCAFFOLD}" \
  bash "${SCRIPT_DIR}/setup-project.sh" 2>&1 | grep -v '^$' >/dev/null
RC=$?
[ "${RC}" -eq 0 ] && pass "exit 0 with no args" || fail "expected exit 0, got ${RC}"
assert_absent "${T1}/backend"
assert_absent "${T1}/main.py"
assert_absent "${T1}/pyproject.toml"
assert_absent "${T1}/uv.lock"

trap - EXIT
cleanup "${T1}"

# ─── Test 2: setup with replitmvp materializes backend/ ──────────────────────
echo "==> Test 2: setup with app name materializes backend/"
T2="$(setup_fork)"
trap 'cleanup "${T2}"' EXIT

PROJECT_ROOT="${T2}" \
  SCAFFOLD_ROOT="${SCAFFOLD}" \
  TEMPLATE_REPO_ROOT="${TEMPLATE_ROOT}" \
  bash "${SCRIPT_DIR}/setup-project.sh" replitmvp 2>&1 >/dev/null
assert_file "${T2}/backend/pom.xml"
assert_file "${T2}/frontend/package.json"
assert_file "${T2}/.replit"
assert_file "${T2}/replit.nix"
assert_file "${T2}/.template-version"
assert_file "${T2}/.github/workflows/ci.yml"
assert_absent "${T2}/main.py"
assert_absent "${T2}/pyproject.toml"

trap - EXIT
cleanup "${T2}"

# ─── Test 3: setup idempotent when backend/ already present ──────────────────
echo "==> Test 3: setup is safe when backend/ already present"
T3="$(setup_fork)"
trap 'cleanup "${T3}"' EXIT

PROJECT_ROOT="${T3}" \
  SCAFFOLD_ROOT="${SCAFFOLD}" \
  TEMPLATE_REPO_ROOT="${TEMPLATE_ROOT}" \
  bash "${SCRIPT_DIR}/setup-project.sh" replitmvp 2>&1 >/dev/null
FIRST_HASH="$(pom_tree_hash "${T3}")"
PROJECT_ROOT="${T3}" \
  SCAFFOLD_ROOT="${SCAFFOLD}" \
  TEMPLATE_REPO_ROOT="${TEMPLATE_ROOT}" \
  bash "${SCRIPT_DIR}/setup-project.sh" replitmvp 2>&1 >/dev/null
SECOND_HASH="$(pom_tree_hash "${T3}")"
[ "${FIRST_HASH}" = "${SECOND_HASH}" ] \
  && pass "second run produced no POM drift" \
  || fail "POM files changed on second run"

trap - EXIT
cleanup "${T3}"

# ─── Test 4: Python cleanup ───────────────────────────────────────────────────
echo "==> Test 4: Python files removed after cleanup"
T4="$(setup_fork)"
trap 'cleanup "${T4}"' EXIT

PROJECT_ROOT="${T4}" \
  SCAFFOLD_ROOT="${SCAFFOLD}" \
  bash "${SCRIPT_DIR}/setup-project.sh" 2>&1 >/dev/null
assert_absent "${T4}/main.py"
assert_absent "${T4}/pyproject.toml"
assert_absent "${T4}/uv.lock"

trap - EXIT
cleanup "${T4}"

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "==> test-setup-project: ${PASS} passed, ${FAIL} failed"
[ "${FAIL}" -eq 0 ] && exit 0 || exit 1
