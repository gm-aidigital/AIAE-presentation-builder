#!/usr/bin/env bash
#
# apply-package-name.sh — mechanical PACKAGE_REPLACE_ME → com.aidigital.<app>
#
# Run once when scaffolding a generated project (before first compile).
# Idempotent when the target package is already applied.
#
# Usage (from project root):
#   bash scripts/apply-package-name.sh <app-name-package>
# Example:
#   bash scripts/apply-package-name.sh employeedirectory

set -euo pipefail

APP_PACKAGE="${1:-}"
if [ -z "${APP_PACKAGE}" ]; then
    echo "Usage: apply-package-name.sh <app-name-package>"
    echo "Example: apply-package-name.sh employeedirectory"
    exit 1
fi

if ! echo "${APP_PACKAGE}" | grep -qE '^[a-z][a-z0-9]*$'; then
    echo "app-name-package must be lowercase alphanumeric, start with a letter: ${APP_PACKAGE}"
    exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

GROUP_ID="com.aidigital.${APP_PACKAGE}"
TOKEN="PACKAGE_REPLACE_ME"

if [ ! -d backend ]; then
    echo "backend/ not found — copy scaffold/ into the project first"
    exit 1
fi

echo "==> Applying Java package ${GROUP_ID}"

if grep -q "${TOKEN}" backend/pom.xml 2>/dev/null; then
    sed -i.bak "s|<groupId>${TOKEN}</groupId>|<groupId>${GROUP_ID}</groupId>|g" backend/pom.xml
    rm -f backend/pom.xml.bak
fi

while IFS= read -r dir; do
    parent="$(dirname "${dir}")"
    mkdir -p "${parent}/com/aidigital"
    if [ -d "${parent}/com/aidigital/${APP_PACKAGE}" ]; then
        rm -rf "${dir}"
    else
        mv "${dir}" "${parent}/com/aidigital/${APP_PACKAGE}"
    fi
done < <(find backend -depth -type d -name "${TOKEN}" 2>/dev/null || true)

find backend -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name '*.yaml' \) \
    -exec grep -l "${TOKEN}" {} + 2>/dev/null | while IFS= read -r f; do
    sed -i.bak "s/${TOKEN}/${GROUP_ID}/g" "${f}"
    rm -f "${f}.bak"
done

for yml in backend/application/src/main/resources/application*.yml; do
    [ -f "${yml}" ] || continue
    sed -i.bak "s/generated-mvp/${APP_PACKAGE}/g" "${yml}"
    rm -f "${yml}.bak"
done

if [ -f .env.example ]; then
    sed -i.bak "s/APP_SERVICE_NAME=generated-mvp/APP_SERVICE_NAME=${APP_PACKAGE}/g" .env.example
    sed -i.bak "s/USAGE_LOG_SERVICE_NAME=generated-mvp/USAGE_LOG_SERVICE_NAME=${APP_PACKAGE}/g" .env.example
    rm -f .env.example.bak
fi

if [ -f frontend/package.json ]; then
    sed -i.bak "s/replit-mvp-frontend/${APP_PACKAGE}-frontend/g" frontend/package.json
    rm -f frontend/package.json.bak
fi

echo "==> Done. Verify with: bash scripts/local-verify.sh"
