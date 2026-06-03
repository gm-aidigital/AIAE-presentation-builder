#!/usr/bin/env bash
# Validates frontend apiClient.* calls against openapi.yaml paths.
set -euo pipefail

spec_path="${1:-backend/application/src/main/resources/api/v1/specs/openapi.yaml}"
frontend_root="${2:-frontend/src}"

if [ ! -f "${spec_path}" ] || [ ! -d "${frontend_root}" ]; then
  exit 0
fi

allowed="$(mktemp)"
trap 'rm -f "${allowed}"' EXIT

current_path=""
while IFS= read -r line; do
  if [[ "${line}" =~ ^[[:space:]]*(/[^[:space:]:]+):[[:space:]]*$ ]]; then
    current_path="${BASH_REMATCH[1]}"
    continue
  fi
  if [ -n "${current_path}" ] && [[ "${line}" =~ ^[[:space:]]+(get|post|put|patch|delete|options|head):[[:space:]]*$ ]]; then
    method="$(echo "${BASH_REMATCH[1]}" | tr '[:lower:]' '[:upper:]')"
    printf '%s %s\n' "${method}" "${current_path}" >> "${allowed}"
  fi
  if [[ "${line}" =~ ^[a-zA-Z] ]]; then
    current_path=""
  fi
done < "${spec_path}"

errors=0
while IFS= read -r file; do
  while IFS= read -r match; do
    method="$(sed -n 's/.*apiClient\.\([A-Z]*\)(.*/\1/p' <<< "${match}")"
    path="$(sed -n 's/.*apiClient\.[A-Z]*("\([^"]*\)".*/\1/p' <<< "${match}")"
    key="${method} ${path}"
    if ! grep -Fxq "${key}" "${allowed}"; then
      echo "${file}: apiClient.${method}(\"${path}\") is not defined in ${spec_path}" >&2
      errors=1
    fi
  done < <(grep -oE 'apiClient\.(GET|POST|PUT|PATCH|DELETE|OPTIONS|HEAD)\("[^"]+"\)' "${file}" || true)
done < <(find "${frontend_root}" -type f \( -name '*.ts' -o -name '*.tsx' \))

exit "${errors}"
