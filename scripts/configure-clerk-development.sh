#!/usr/bin/env bash
#
# configure-clerk-development.sh — idempotent Clerk Development tenant setup.
# Uses Clerk Backend API only; never prints secrets.
#
# Usage (from generated project root):
#   bash scripts/configure-clerk-development.sh
#
# Required env:
#   CLERK_PUBLISHABLE_KEY, CLERK_SECRET_KEY
#
# Optional env:
#   AUTH_ALLOWED_EMAIL_DOMAIN   (default: aidigital.com)
#   CLERK_API_BASE_URL          (default: https://api.clerk.com/v1; override for tests)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

CLERK_API_BASE_URL="${CLERK_API_BASE_URL:-https://api.clerk.com/v1}"
TEMPLATE_NAME="aidigital-api"

# ── Trap: clean up any temporary files ────────────────────────────────────────
TMPFILES=()
cleanup_tmpfiles() {
  for f in "${TMPFILES[@]:-}"; do
    rm -f "${f}" 2>/dev/null || true
  done
}
trap cleanup_tmpfiles EXIT

make_tmp() {
  local t
  t="$(mktemp)"
  TMPFILES+=("${t}")
  echo "${t}"
}

# ── Helpers ───────────────────────────────────────────────────────────────────
fail() {
  echo "configure-clerk-development: $*" >&2
  exit 1
}

log() { echo "    $*"; }

# ── Credential check ──────────────────────────────────────────────────────────
if [ -z "${CLERK_PUBLISHABLE_KEY:-}" ] || [ -z "${CLERK_SECRET_KEY:-}" ]; then
  fail "Replit-managed Clerk Auth is required. Add Clerk Auth in the Replit Auth pane"\
    "so CLERK_PUBLISHABLE_KEY and CLERK_SECRET_KEY are injected."
fi

# ── Domain validation ─────────────────────────────────────────────────────────
if [ -z "${AUTH_ALLOWED_EMAIL_DOMAIN+x}" ]; then
  RAW_DOMAIN="aidigital.com"
elif [ -z "$(printf '%s' "${AUTH_ALLOWED_EMAIL_DOMAIN}" | tr -d ' \t')" ]; then
  fail "AUTH_ALLOWED_EMAIL_DOMAIN is empty"
else
  RAW_DOMAIN="${AUTH_ALLOWED_EMAIL_DOMAIN}"
fi
DOMAIN="$(printf '%s' "${RAW_DOMAIN}" | tr '[:upper:]' '[:lower:]' | tr -d ' \t')"

# Strip leading @
if [ "${DOMAIN:0:1}" = "@" ]; then
  DOMAIN="${DOMAIN:1}"
fi

# Reject empty, protocol, slash, whitespace, wildcard, embedded @
if [ -z "${DOMAIN}" ]; then
  fail "AUTH_ALLOWED_EMAIL_DOMAIN is empty"
fi
for bad in "://" "/" " " "*" "@"; do
  case "${DOMAIN}" in
    *"${bad}"*) fail "AUTH_ALLOWED_EMAIL_DOMAIN '${DOMAIN}' contains invalid character: ${bad}" ;;
  esac
done

ALLOWLIST_IDENTIFIER="*@${DOMAIN}"

# ── API helper ────────────────────────────────────────────────────────────────
clerk_api() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local tmp
  tmp="$(make_tmp)"
  local code
  if [ -n "${body}" ]; then
    code="$(curl -sS -o "${tmp}" -w '%{http_code}' -X "${method}" \
      -H "Authorization: Bearer ${CLERK_SECRET_KEY}" \
      -H "Content-Type: application/json" \
      -d "${body}" \
      "${CLERK_API_BASE_URL}${path}")"
  else
    code="$(curl -sS -o "${tmp}" -w '%{http_code}' -X "${method}" \
      -H "Authorization: Bearer ${CLERK_SECRET_KEY}" \
      "${CLERK_API_BASE_URL}${path}")"
  fi
  if [ "${code}" -lt 200 ] || [ "${code}" -ge 300 ]; then
    echo "Clerk API ${method} ${path} -> HTTP ${code}" >&2
    cat "${tmp}" >&2
    fail "Clerk administrative operation rejected (HTTP ${code})"
  fi
  cat "${tmp}"
}

# jq-less JSON extraction using python3 (available everywhere)
json_get() {
  local json="$1"
  local key="$2"
  python3 -c "
import json, sys
data = json.loads(sys.argv[1])
val = data.get(sys.argv[2], '')
if isinstance(val, list):
    print('[' + ','.join(json.dumps(v) for v in val) + ']')
else:
    print(val)
" "${json}" "${key}" 2>/dev/null || echo ""
}

json_find_by_name() {
  local json="$1"
  local name="$2"
  python3 -c "
import json, sys
items = json.loads(sys.argv[1])
if isinstance(items, dict):
    items = items.get('data', [])
hit = next((i for i in items if i.get('name') == sys.argv[2]), None)
print(hit['id'] if hit else '')
" "${json}" "${name}" 2>/dev/null || echo ""
}

json_find_by_identifier() {
  local json="$1"
  local ident="$2"
  python3 -c "
import json, sys
items = json.loads(sys.argv[1])
if isinstance(items, dict):
    items = items.get('data', [])
hit = next((i for i in items if i.get('identifier') == sys.argv[2]), None)
print(hit['id'] if hit else '')
" "${json}" "${ident}" 2>/dev/null || echo ""
}

claims_match() {
  local json="$1"
  python3 -c "
import json, sys
data = json.loads(sys.argv[1])
claims = data.get('claims', {})
expected = {
    'email': '{{user.primary_email_address}}',
    'user_id': '{{user.id}}',
    'full_name': '{{user.full_name || user.primary_email_address}}'
}
print('ok' if claims == expected else 'mismatch')
" "${json}" 2>/dev/null || echo "error"
}

CLAIMS_PAYLOAD='{"email":"{{user.primary_email_address}}","user_id":"{{user.id}}","full_name":"{{user.full_name || user.primary_email_address}}"}'

# ── Step 1: JWT template ──────────────────────────────────────────────────────
echo "==> Ensuring JWT template ${TEMPLATE_NAME}"
existing_templates="$(clerk_api GET "/jwt_templates?limit=100")"
template_id="$(json_find_by_name "${existing_templates}" "${TEMPLATE_NAME}")"

if [ -z "${template_id}" ]; then
  clerk_api POST "/jwt_templates" \
    "{\"name\":\"${TEMPLATE_NAME}\",\"claims\":${CLAIMS_PAYLOAD}}" >/dev/null
  log "created ${TEMPLATE_NAME}"
else
  current_match="$(claims_match "$(clerk_api GET "/jwt_templates/${template_id}")")"
  if [ "${current_match}" != "ok" ]; then
    clerk_api PATCH "/jwt_templates/${template_id}" \
      "{\"claims\":${CLAIMS_PAYLOAD}}" >/dev/null
    log "updated ${TEMPLATE_NAME} (claims changed)"
  else
    log "${TEMPLATE_NAME} already up-to-date"
  fi
fi

# ── Step 2: Allowlist identifier ──────────────────────────────────────────────
echo "==> Ensuring allowlist ${ALLOWLIST_IDENTIFIER}"
allowlist="$(clerk_api GET "/allowlist_identifiers?limit=100")"
allowlist_id="$(json_find_by_identifier "${allowlist}" "${ALLOWLIST_IDENTIFIER}")"

if [ -z "${allowlist_id}" ]; then
  clerk_api POST "/allowlist_identifiers" \
    "{\"identifier\":\"${ALLOWLIST_IDENTIFIER}\",\"notify\":false}" >/dev/null
  log "added ${ALLOWLIST_IDENTIFIER}"
else
  log "allowlist identifier already present"
fi

# ── Step 3: Allowlist restriction mode ───────────────────────────────────────
# Clerk's instance-restrictions endpoint controls allowlist enforcement.
# The Clerk Backend API v1 exposes PATCH /instance/restrictions.
# We attempt to enable it; if the endpoint is not available on this tenant
# (e.g. Replit-managed Clerk does not expose it), we emit a clear manual step.
echo "==> Attempting to enable allowlist restriction mode"
RESTRICT_STATUS="$(curl -sS -o /dev/null -w '%{http_code}' \
  -X PATCH \
  -H "Authorization: Bearer ${CLERK_SECRET_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"allowlist":true}' \
  "${CLERK_API_BASE_URL}/instance/restrictions" 2>/dev/null || echo "000")"

if [ "${RESTRICT_STATUS}" -ge 200 ] && [ "${RESTRICT_STATUS}" -lt 300 ]; then
  log "allowlist restriction mode enabled (HTTP ${RESTRICT_STATUS})"
elif [ "${RESTRICT_STATUS}" = "000" ] || [ "${RESTRICT_STATUS}" -eq 404 ] || [ "${RESTRICT_STATUS}" -eq 403 ]; then
  echo ""
  echo "  ⚠ MANUAL STEP REQUIRED (HTTP ${RESTRICT_STATUS}):"
  echo "    Replit-managed Clerk may not expose the instance-restrictions endpoint."
  echo "    In the Clerk Dashboard → User & Authentication → Restrictions:"
  echo "      Enable 'Allowlist' so only *@${DOMAIN} can sign in."
  echo "    The backend 403 domain check (CompanyEmailDomainAuthorizationManager)"
  echo "    is the mandatory defense-in-depth control regardless."
  echo ""
else
  echo "  Warning: restriction-mode API returned HTTP ${RESTRICT_STATUS} — verify manually."
fi

# ── Step 4: Final state verification ─────────────────────────────────────────
echo "==> Verifying final state"

final_templates="$(clerk_api GET "/jwt_templates?limit=100")"
final_id="$(json_find_by_name "${final_templates}" "${TEMPLATE_NAME}")"
[ -n "${final_id}" ] || fail "final verification: ${TEMPLATE_NAME} template not found"

final_template="$(clerk_api GET "/jwt_templates/${final_id}")"
final_match="$(claims_match "${final_template}")"
[ "${final_match}" = "ok" ] || fail "final verification: ${TEMPLATE_NAME} claims do not match expected values"
log "template claims verified"

final_allowlist="$(clerk_api GET "/allowlist_identifiers?limit=100")"
final_allowlist_id="$(json_find_by_identifier "${final_allowlist}" "${ALLOWLIST_IDENTIFIER}")"
[ -n "${final_allowlist_id}" ] || fail "final verification: allowlist ${ALLOWLIST_IDENTIFIER} not found"
log "allowlist identifier verified"

echo "==> configure-clerk-development: passed"
