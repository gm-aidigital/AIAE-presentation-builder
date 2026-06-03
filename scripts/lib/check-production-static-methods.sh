#!/usr/bin/env bash
#
# check-production-static-methods.sh — rejects project-owned static methods.
#
# Permitted: Application.main, static final constants, Logger/LOG, nested types,
# static initializers, Spring @Bean factory methods.
#
# Usage:
#   bash scripts/lib/check-production-static-methods.sh [src_root...]

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
  echo "check-production-static-methods: no source directories to scan"
  exit 0
fi

exec python3 "${SCRIPT_DIR}/scan-production-java.py" static "$@"
