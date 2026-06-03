#!/usr/bin/env bash
#
# test-materialize-project.sh — contract tests for materialize-project.sh.
# Run from anywhere; creates and cleans up a temporary directory.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD="$(cd "${SCRIPT_DIR}/.." && pwd)"
TEMPLATE_ROOT="$(cd "${SCAFFOLD}/../../../.." && pwd)"
PASS=0
FAIL=0

pass() { echo "  PASS: $*"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $*" >&2; FAIL=$((FAIL + 1)); }

assert_file()   { [ -f "$1" ]  && pass "$1 present"  || fail "$1 should exist"; }
assert_absent() { [ ! -e "$1" ] && pass "$1 absent"  || fail "$1 should be absent"; }
assert_contains() { grep -Fq "$2" "$1" && pass "$1 contains '$2'" || fail "$1 should contain '$2'"; }
assert_not_contains() { grep -qF "$2" "$1" && fail "$1 should NOT contain '$2'" || pass "$1 does not contain '$2'"; }

run_materialize() {
  local dest="$1"; shift
  SCAFFOLD_ROOT="${SCAFFOLD}" \
    MATERIALIZE_DEST="${dest}" \
    TEMPLATE_REPO_ROOT="${TEMPLATE_ROOT}" \
    bash "${SCRIPT_DIR}/materialize-project.sh" "$@" 2>&1 >/dev/null
}

mktemp_dir() { mktemp -d; }

# ─── Test 1: complete generated root surface ──────────────────────────────────
echo "==> Test 1: materialized root contains required files"
T1="$(mktemp_dir)"
trap 'rm -rf "${T1}"' EXIT

run_materialize "${T1}" replitmvp
assert_file "${T1}/backend/pom.xml"
assert_file "${T1}/frontend/package.json"
assert_file "${T1}/.env.example"
assert_file "${T1}/.gitignore"
assert_file "${T1}/docker-compose.yml"
assert_file "${T1}/.replit"
assert_file "${T1}/replit.nix"
assert_file "${T1}/.template-version"
assert_file "${T1}/.github/workflows/ci.yml"
assert_file "${T1}/scripts/materialize-project.sh"
assert_file "${T1}/scripts/setup-project.sh"
assert_file "${T1}/scripts/local-verify.sh"
assert_file "${T1}/scripts/docker-local-smoke.sh"
assert_file "${T1}/scripts/docker-context-path-smoke.sh"
assert_file "${T1}/scripts/lib/scan-production-java.py"

trap - EXIT
rm -rf "${T1}"

# ─── Test 2: control-plane files excluded ─────────────────────────────────────
echo "==> Test 2: control-plane files excluded from generated root"
T2="$(mktemp_dir)"
trap 'rm -rf "${T2}"' EXIT

run_materialize "${T2}" replitmvp
assert_absent "${T2}/AGENTS.md"
assert_absent "${T2}/replit.md"
assert_absent "${T2}/custom_instruction"
assert_absent "${T2}/.agents"
assert_absent "${T2}/templates"
assert_absent "${T2}/scripts/ci-verify-scaffold.sh"

trap - EXIT
rm -rf "${T2}"

# ─── Test 3: package name replaced ────────────────────────────────────────────
echo "==> Test 3: PACKAGE_REPLACE_ME replaced after materialization"
T3="$(mktemp_dir)"
trap 'rm -rf "${T3}"' EXIT

run_materialize "${T3}" replitmvp
remaining="$(find "${T3}/backend" -path '*/src/*' -name '*.java' -print0 2>/dev/null \
  | xargs -0 grep -l 'PACKAGE_REPLACE_ME' 2>/dev/null | wc -l | tr -d ' ')" || remaining=0
[ "${remaining}" -eq 0 ] \
  && pass "no PACKAGE_REPLACE_ME in backend/src" \
  || fail "${remaining} files still contain PACKAGE_REPLACE_ME"

trap - EXIT
rm -rf "${T3}"

# ─── Test 4: README preserved on second run ───────────────────────────────────
echo "==> Test 4: user README survives repeated materialization"
T4="$(mktemp_dir)"
trap 'rm -rf "${T4}"' EXIT

run_materialize "${T4}" replitmvp
echo "MY REAL APP README" > "${T4}/README.md"
run_materialize "${T4}" replitmvp
assert_contains "${T4}/README.md" "MY REAL APP README"

trap - EXIT
rm -rf "${T4}"

# ─── Test 5: structural idempotency ───────────────────────────────────────────
echo "==> Test 5: second materialization produces no structural drift"
T5="$(mktemp_dir)"
trap 'rm -rf "${T5}"' EXIT

run_materialize "${T5}" replitmvp
HASH1="$(find "${T5}/backend" -name 'pom.xml' -exec md5 -q {} \; | sort | md5 -q)"
run_materialize "${T5}" replitmvp
HASH2="$(find "${T5}/backend" -name 'pom.xml' -exec md5 -q {} \; | sort | md5 -q)"
[ "${HASH1}" = "${HASH2}" ] \
  && pass "POM files identical after second run" \
  || fail "POM files changed on second materialization"

trap - EXIT
rm -rf "${T5}"

# ─── Test 6: .replit and replit.nix present ───────────────────────────────────
echo "==> Test 6: .replit and replit.nix materialized"
T6="$(mktemp_dir)"
trap 'rm -rf "${T6}"' EXIT

run_materialize "${T6}" replitmvp
assert_file "${T6}/.replit"
assert_file "${T6}/replit.nix"
assert_contains "${T6}/.replit" "onBoot"

trap - EXIT
rm -rf "${T6}"

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "==> test-materialize-project: ${PASS} passed, ${FAIL} failed"
[ "${FAIL}" -eq 0 ] && exit 0 || exit 1
