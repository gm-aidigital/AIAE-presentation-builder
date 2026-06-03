#!/usr/bin/env bash
#
# setup-project.sh — idempotent boot hook for a generated Replit project.
#
# Without arguments: safe cleanup only (no materialization).
# With <app-name-package>: cleanup + materialize if backend/ is absent.
#
# Usage:
#   bash scripts/setup-project.sh                   # cleanup only (onBoot)
#   bash scripts/setup-project.sh <app-name-package>  # cleanup + materialize
#
# Environment:
#   APP_NAME_PACKAGE — alternative to positional argument

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Determine project root: if this script lives under scripts/, root is the
# parent; if it lives under templates/.../scripts/, root is 4 levels up.
is_control_plane_root() {
  local root="$1"
  [ -f "${root}/replit.md" ] && [ -d "${root}/templates/generated-project/scaffold" ]
}

resolve_root() {
  local candidate repo_candidate
  if [ -n "${PROJECT_ROOT:-}" ] && [ -d "${PROJECT_ROOT}" ]; then
    printf '%s' "$(cd "${PROJECT_ROOT}" && pwd)"
    return
  fi
  if [ -n "${MATERIALIZE_DEST:-}" ] && [ -d "${MATERIALIZE_DEST}" ]; then
    printf '%s' "$(cd "${MATERIALIZE_DEST}" && pwd)"
    return
  fi
  candidate="$(cd "${SCRIPT_DIR}/.." && pwd)"
  # Template control plane: scaffold/scripts invoked from repo root onBoot — use repo root.
  case "${candidate}" in
    */templates/generated-project/scaffold)
      repo_candidate="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
      if [ -f "${repo_candidate}/replit.md" ] \
          && [ -f "${repo_candidate}/custom_instruction/instructions.md" ]; then
        printf '%s' "${repo_candidate}"
        return
      fi
      ;;
  esac
  if [ -f "${candidate}/backend/pom.xml" ] && [ -f "${candidate}/scripts/setup-project.sh" ]; then
    printf '%s' "${candidate}"
    return
  fi
  if [ -f "${candidate}/.gitignore" ] && [[ "${candidate}" != */templates/generated-project/scaffold ]]; then
    printf '%s' "${candidate}"
    return
  fi
  repo_candidate="$(cd "${SCRIPT_DIR}/../../../.." 2>/dev/null && pwd || true)"
  if [ -n "${repo_candidate}" ] \
      && [ -f "${repo_candidate}/templates/generated-project/scaffold/scripts/setup-project.sh" ]; then
    printf '%s' "${repo_candidate}"
    return
  fi
  printf '%s' "${candidate}"
}

ROOT="$(resolve_root)"
APP_NAME="${1:-${APP_NAME_PACKAGE:-}}"

if is_control_plane_root "${ROOT}" \
    && [ -z "${PROJECT_ROOT:-}" ] \
    && [ -z "${MATERIALIZE_DEST:-}" ] \
    && [ -n "${APP_NAME}" ] \
    && [ "${ALLOW_CONTROL_PLANE_SETUP:-0}" != "1" ]; then
  echo "setup-project: refusing to materialize into template control plane." >&2
  echo "  Set PROJECT_ROOT or MATERIALIZE_DEST to a sandbox directory." >&2
  exit 1
fi

# Locate scaffold (works both from generated project and template repo)
resolve_scaffold() {
  if [ -n "${SCAFFOLD_ROOT:-}" ]; then
    printf '%s' "${SCAFFOLD_ROOT}"
    return
  fi
  # Generated project: scripts/ is at project root/scripts/
  local beside="${SCRIPT_DIR}/../backend"
  if [ -f "${SCRIPT_DIR}/../backend/pom.xml" ]; then
    # Already materialized — no scaffold needed for cleanup steps
    printf ''
    return
  fi
  # Template repo: scripts/ is at templates/generated-project/scaffold/scripts/
  local from_template="${SCRIPT_DIR}/../../../templates/generated-project/scaffold"
  if [ -f "${from_template}/backend/pom.xml" ]; then
    printf '%s' "$(cd "${from_template}" && pwd)"
    return
  fi
  local template_root="${SCRIPT_DIR}/../../../.."
  if [ -f "${template_root}/templates/generated-project/scaffold/backend/pom.xml" ]; then
    printf '%s' "$(cd "${template_root}/templates/generated-project/scaffold" && pwd)"
    return
  fi
  printf ''
}

SCAFFOLD="$(resolve_scaffold)"

# ── Safe cleanup (always runs, even without an app name) ─────────────────────

echo "==> setup-project: cleanup start"

# Install canonical .gitignore
if [ -n "${SCAFFOLD}" ] && [ -f "${SCAFFOLD}/.gitignore" ]; then
  cp "${SCAFFOLD}/.gitignore" "${ROOT}/.gitignore"
  echo "    installed .gitignore"
fi

# Remove Replit-injected Python scaffolding
for f in main.py pyproject.toml uv.lock poetry.lock requirements.txt Pipfile Pipfile.lock; do
  if [ -f "${ROOT}/${f}" ]; then
    rm -f "${ROOT}/${f}"
    echo "    removed ${f}"
  fi
done
[ -d "${ROOT}/__pycache__" ] && rm -rf "${ROOT}/__pycache__" && echo "    removed __pycache__"
[ -d "${ROOT}/.venv" ]      && rm -rf "${ROOT}/.venv"      && echo "    removed .venv"

# Remove python-* module and Flask/Django/FastAPI integrations from .replit
if [ -f "${ROOT}/.replit" ]; then
  # Use a temp file + move to be safe under set -e
  local_replit="${ROOT}/.replit"
  tmp_replit="${local_replit}.tmp"
  sed -E \
    -e 's/"python-[0-9.]+"[[:space:]]*,?[[:space:]]*//g' \
    -e 's/,([[:space:]]*\])/\1/g' \
    "${local_replit}" > "${tmp_replit}"
  # Remove lines matching Flask/Django/FastAPI integrations
  grep -Ev 'integrations.*=.*\[.*"(flask|django|fastapi)_' "${tmp_replit}" > "${local_replit}" || true
  rm -f "${tmp_replit}"
  echo "    cleaned .replit"
fi

# Install runtime scripts into project root scripts/ (idempotent copy)
if [ -n "${SCAFFOLD}" ] && [ -d "${SCAFFOLD}/scripts" ]; then
  mkdir -p "${ROOT}/scripts" "${ROOT}/scripts/lib"
  RUNTIME_SCRIPTS=(
    replit-build.sh replit-run.sh local-verify.sh structure-lint.sh verify-gates.sh
    apply-package-name.sh strip-scaffold-samples.sh materialize-project.sh
    configure-clerk-development.sh setup-project.sh
    docker-local-smoke.sh docker-context-path-smoke.sh
  )
  for s in "${RUNTIME_SCRIPTS[@]}"; do
    if [ -f "${SCAFFOLD}/scripts/${s}" ]; then
      cp "${SCAFFOLD}/scripts/${s}" "${ROOT}/scripts/${s}"
      chmod +x "${ROOT}/scripts/${s}"
    fi
  done
  if [ -d "${SCAFFOLD}/scripts/lib" ]; then
    cp "${SCAFFOLD}/scripts/lib/"* "${ROOT}/scripts/lib/" 2>/dev/null || true
    chmod +x "${ROOT}/scripts/lib/"*.sh 2>/dev/null || true
  fi
  echo "    runtime scripts installed"
fi

# Untrack control-plane paths from git index (if tracked)
cd "${ROOT}"
if [ -d .git ]; then
  for path in .agents templates custom_instruction AGENTS.md replit.md \
              main.py pyproject.toml uv.lock; do
    if git ls-files --error-unmatch "${path}" >/dev/null 2>&1; then
      git rm -r --cached "${path}" >/dev/null 2>&1
      echo "    untracked ${path} from git index"
    fi
  done
fi

echo "==> setup-project: cleanup done"

# ── Materialization (only when backend/ is absent and name is provided) ───────

if [ -d "${ROOT}/backend" ]; then
  echo "==> backend/ already present — skipping materialize"
  exit 0
fi

if [ -z "${APP_NAME}" ]; then
  echo ""
  echo "    backend/ not yet materialized. Run:"
  echo "      bash scripts/materialize-project.sh <app-name-package>"
  echo ""
  exit 0
fi

if [ -z "${SCAFFOLD}" ]; then
  echo "setup-project: cannot locate scaffold for materialization — set SCAFFOLD_ROOT" >&2
  exit 1
fi

echo "==> Materializing scaffold for ${APP_NAME}"
SCAFFOLD_ROOT="${SCAFFOLD}" \
  TEMPLATE_REPO_ROOT="${ROOT}" \
  MATERIALIZE_DEST="${ROOT}" \
  bash "${SCAFFOLD}/scripts/materialize-project.sh" "${APP_NAME}"

echo "==> Done."
echo "    Next: provision Replit-managed Clerk Auth, then:"
echo "      bash scripts/configure-clerk-development.sh"
