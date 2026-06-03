#!/usr/bin/env bash
#
# test-configure-clerk-development.sh — contract tests for configure-clerk-development.sh.
# Uses a local fake HTTP server (Python http.server + a simple dispatch script).
# Requires: python3, bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="${SCRIPT_DIR}/configure-clerk-development.sh"
PASS=0
FAIL=0

pass() { echo "  PASS: $*"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $*" >&2; FAIL=$((FAIL + 1)); }

# ── Fake HTTP server helpers ───────────────────────────────────────────────────

# Writes a fake Clerk API server script to a temp file and starts it on a
# free port. Sets FAKE_PORT and FAKE_PID. Caller must stop it with stop_fake_server.
FAKE_SERVER_PY=""
FAKE_PID=""
FAKE_PORT=""

start_fake_server() {
  local mode="$1"      # first_run | second_run | rejected | state_mismatch
  local port_file
  port_file="$(mktemp)"

  FAKE_SERVER_PY="$(mktemp).py"
  cat > "${FAKE_SERVER_PY}" <<'PYEOF'
import sys, json, http.server, threading, os

MODE = sys.argv[1]
PORT = int(sys.argv[2])

templates  = []
allowlist  = []
restrictions_enabled = False
call_log   = []

def next_id():
    return f"id_{len(call_log) + 1}"

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args): pass

    def read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        return self.rfile.read(length).decode() if length else ""

    def respond(self, code, body=None):
        data = json.dumps(body or {}).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.startswith("/jwt_templates"):
            if "/jwt_templates/" in self.path:
                tid = self.path.split("/")[-1].split("?")[0]
                hit = next((t for t in templates if t["id"] == tid), None)
                self.respond(200 if hit else 404, hit or {"error": "not found"})
            else:
                self.respond(200, {"data": templates})
        elif self.path.startswith("/allowlist_identifiers"):
            self.respond(200, {"data": allowlist})
        else:
            self.respond(404, {"error": "not found"})

    def do_POST(self):
        body = json.loads(self.read_body() or "{}")
        if self.path == "/jwt_templates":
            t = {"id": next_id(), "name": body.get("name"), "claims": body.get("claims",{})}
            templates.append(t)
            call_log.append(("POST", "/jwt_templates"))
            self.respond(200, t)
        elif self.path == "/allowlist_identifiers":
            a = {"id": next_id(), "identifier": body.get("identifier")}
            allowlist.append(a)
            call_log.append(("POST", "/allowlist_identifiers"))
            self.respond(200, a)
        else:
            self.respond(404, {"error": "not found"})

    def do_PATCH(self):
        body = json.loads(self.read_body() or "{}")
        if self.path.startswith("/jwt_templates/"):
            tid = self.path.split("/")[-1]
            hit = next((t for t in templates if t["id"] == tid), None)
            if hit:
                if "claims" in body:
                    hit["claims"] = body["claims"]
                call_log.append(("PATCH", f"/jwt_templates/{tid}"))
                self.respond(200, hit)
            else:
                self.respond(404, {"error": "not found"})
        elif self.path == "/instance/restrictions":
            global restrictions_enabled
            if MODE == "rejected":
                self.respond(403, {"error": "forbidden"})
            else:
                restrictions_enabled = True
                call_log.append(("PATCH", "/instance/restrictions"))
                self.respond(200, {"allowlist": True})
        else:
            self.respond(404, {"error": "not found"})

server = http.server.HTTPServer(("127.0.0.1", PORT), Handler)
# Write "ready" once listening
with open(sys.argv[3], "w") as f:
    f.write("ready")
server.serve_forever()
PYEOF

  FAKE_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("",0)); print(s.getsockname()[1]); s.close()')"
  python3 "${FAKE_SERVER_PY}" "${mode}" "${FAKE_PORT}" "${port_file}" &
  FAKE_PID=$!
  # Wait for server to be ready (max 5s)
  for i in $(seq 1 50); do
    [ -s "${port_file}" ] && break
    sleep 0.1
  done
  rm -f "${port_file}"
}

stop_fake_server() {
  [ -n "${FAKE_PID}" ] && kill "${FAKE_PID}" 2>/dev/null || true
  rm -f "${FAKE_SERVER_PY}"
  FAKE_PID=""
}

run_script() {
  local domain="${1:-aidigital.com}"
  CLERK_PUBLISHABLE_KEY="pk_test_dummy" \
  CLERK_SECRET_KEY="sk_test_dummy" \
  AUTH_ALLOWED_EMAIL_DOMAIN="${domain}" \
  CLERK_API_BASE_URL="http://127.0.0.1:${FAKE_PORT}/v1" \
  bash "${SCRIPT}" 2>&1
}

run_script_expect_fail() {
  local domain="${1:-aidigital.com}"
  run_script "${domain}" && return 1 || return 0
}

# ─── Test 1: no credentials → fail immediately ────────────────────────────────
echo "==> Test 1: no credentials → fail"
output="$(CLERK_PUBLISHABLE_KEY="" CLERK_SECRET_KEY="" \
  bash "${SCRIPT}" 2>&1 || true)"
echo "${output}" | grep -qi "required\|missing\|auth" \
  && pass "correct error message for missing credentials" \
  || fail "unexpected output: ${output}"

# ─── Test 2: invalid domain → fail before HTTP ────────────────────────────────
echo "==> Test 2: invalid domain rejected before HTTP"
start_fake_server first_run

output="$(CLERK_PUBLISHABLE_KEY="pk_test_dummy" CLERK_SECRET_KEY="sk_test_dummy" \
  AUTH_ALLOWED_EMAIL_DOMAIN="https://evil.com" \
  CLERK_API_BASE_URL="http://127.0.0.1:${FAKE_PORT}/v1" \
  bash "${SCRIPT}" 2>&1 || true)"
echo "${output}" | grep -qi "invalid\|error\|://" \
  && pass "domain with protocol rejected" \
  || fail "expected domain rejection, got: ${output}"

output2="$(CLERK_PUBLISHABLE_KEY="pk_test_dummy" CLERK_SECRET_KEY="sk_test_dummy" \
  AUTH_ALLOWED_EMAIL_DOMAIN="" \
  CLERK_API_BASE_URL="http://127.0.0.1:${FAKE_PORT}/v1" \
  bash "${SCRIPT}" 2>&1 || true)"
echo "${output2}" | grep -qi "empty\|blank\|invalid" \
  && pass "empty domain rejected" \
  || fail "expected empty domain rejection, got: ${output2}"

stop_fake_server

# ─── Test 3: first run creates template + allowlist ───────────────────────────
echo "==> Test 3: first run creates JWT template and allowlist"
start_fake_server first_run

output="$(run_script aidigital.com)"
echo "${output}" | grep -qi "passed" \
  && pass "first run exits successfully" \
  || fail "first run failed: ${output}"
echo "${output}" | grep -qi "created\|added" \
  && pass "first run reports creation" \
  || fail "first run should report creation: ${output}"

stop_fake_server

# ─── Test 4: second run is idempotent ─────────────────────────────────────────
echo "==> Test 4: second run idempotency"
start_fake_server first_run

run_script aidigital.com >/dev/null 2>&1
output="$(run_script aidigital.com)"
echo "${output}" | grep -qi "passed" \
  && pass "second run exits successfully" \
  || fail "second run failed: ${output}"
echo "${output}" | grep -qi "up-to-date\|already" \
  && pass "second run reports no changes" \
  || fail "second run should report no changes: ${output}"

stop_fake_server

# ─── Test 5: restriction-mode API returns 403 → emits manual step ─────────────
echo "==> Test 5: restriction-mode 403 → manual step emitted"
start_fake_server rejected

output="$(run_script aidigital.com)"
echo "${output}" | grep -qi "manual\|dashboard\|restriction" \
  && pass "manual step emitted when API rejected" \
  || fail "expected manual step instruction, got: ${output}"
echo "${output}" | grep -qi "passed" \
  && pass "script still exits 0 when restriction is manual" \
  || fail "script should still exit 0 with manual restriction: ${output}"

stop_fake_server

# ─── Test 6: secret never appears in stdout or stderr ─────────────────────────
echo "==> Test 6: secret never in output"
start_fake_server first_run

output="$(CLERK_PUBLISHABLE_KEY="pk_test_TOPSECRET" \
  CLERK_SECRET_KEY="sk_test_SUPERSECRET" \
  AUTH_ALLOWED_EMAIL_DOMAIN="aidigital.com" \
  CLERK_API_BASE_URL="http://127.0.0.1:${FAKE_PORT}/v1" \
  bash "${SCRIPT}" 2>&1 || true)"
echo "${output}" | grep -q "SUPERSECRET" \
  && fail "CLERK_SECRET_KEY leaked into output" \
  || pass "CLERK_SECRET_KEY not in output"

stop_fake_server

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "==> test-configure-clerk-development: ${PASS} passed, ${FAIL} failed"
[ "${FAIL}" -eq 0 ] && exit 0 || exit 1
