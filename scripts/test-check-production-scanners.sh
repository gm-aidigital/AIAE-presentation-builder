#!/usr/bin/env bash
#
# test-check-production-scanners.sh — contract tests for production Java scanners.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD="$(cd "${SCRIPT_DIR}/.." && pwd)"
LIB="${SCRIPT_DIR}/lib"
PASS=0
FAIL=0

pass() { echo "  PASS: $*"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $*" >&2; FAIL=$((FAIL + 1)); }

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT
JAVA_DIR="${WORK}/backend/demo/src/main/java/com/example/demo"
mkdir -p "${JAVA_DIR}"

echo "==> magic scanner accepts named constants"
cat > "${JAVA_DIR}/OnlyGood.java" <<'EOF'
package com.example.demo;
public class OnlyGood {
    private static final String CLAIM = "user_id";
    public void run() {
        doWork(CLAIM);
    }
    private void doWork(String claim) {}
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-magic-values.sh" backend/demo/src/main/java); then
  pass "named constant allowed"
else
  fail "named constant should be allowed"
fi

echo "==> magic scanner rejects inline literals"
rm -f "${JAVA_DIR}/OnlyGood.java"
cat > "${JAVA_DIR}/OnlyBad.java" <<'EOF'
package com.example.demo;
public class OnlyBad {
    public void run() {
        check("user_id");
    }
    private void check(String c) {}
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-magic-values.sh" backend/demo/src/main/java 2>/dev/null); then
  fail "inline literal should be rejected"
else
  pass "inline literal rejected"
fi

echo "==> static scanner detects project static methods"
rm -f "${JAVA_DIR}/"*.java
cat > "${JAVA_DIR}/StaticBad.java" <<'EOF'
package com.example.demo;
public class StaticBad {
    private static String helper(String v) {
        return v;
    }
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-static-methods.sh" backend/demo/src/main/java 2>/dev/null); then
  fail "static helper should be rejected"
else
  pass "static helper rejected"
fi

echo "==> static scanner accepts constants and main"
rm -f "${JAVA_DIR}/StaticBad.java"
cat > "${JAVA_DIR}/StaticGood.java" <<'EOF'
package com.example.demo;
public class StaticGood {
    private static final String OK = "x";
    public static void main(String[] args) {}
}
EOF
if (cd "${WORK}" && bash "${LIB}/check-production-static-methods.sh" backend/demo/src/main/java); then
  pass "constants and main allowed"
else
  fail "constants and main should be allowed"
fi

echo "==> canonical scaffold passes both scanners"
if (cd "${SCAFFOLD}" && bash "${LIB}/check-production-magic-values.sh"); then
  pass "scaffold passes magic scanner"
else
  fail "scaffold must pass magic scanner"
fi

if (cd "${SCAFFOLD}" && bash "${LIB}/check-production-static-methods.sh"); then
  pass "scaffold passes static scanner"
else
  fail "scaffold must pass static scanner"
fi

echo ""
echo "==> test-check-production-scanners: ${PASS} passed, ${FAIL} failed"
[ "${FAIL}" -eq 0 ] && exit 0 || exit 1
