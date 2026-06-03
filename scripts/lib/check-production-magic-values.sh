#!/usr/bin/env bash
#
# check-production-magic-values.sh — rejects inline semantic literals in method bodies.
#
# Usage:
#   bash scripts/lib/check-production-magic-values.sh [src_root...]
# When omitted, scans every backend/*/src/main/java tree (including external-services).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCAFFOLD_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

shopt -s nullglob
if [ $# -eq 0 ]; then
  cd "${SCAFFOLD_ROOT}"
  set -- backend/*/src/main/java
else
  cd "${PROJECT_ROOT:-$(pwd)}"
fi
if [ $# -eq 0 ]; then
  echo "check-production-magic-values: no source directories to scan"
  exit 0
fi

exec python3 "${SCRIPT_DIR}/scan-production-java.py" magic "$@"
